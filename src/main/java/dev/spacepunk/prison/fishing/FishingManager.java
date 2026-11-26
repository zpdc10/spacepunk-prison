package dev.spacepunk.prison.fishing;

import com.google.gson.Gson;
import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.FishingService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.TokenDrops;
import dev.spacepunk.prison.api.event.FishCaughtEvent;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.fishing.FishingConfig.Area;
import dev.spacepunk.prison.fishing.FishingConfig.BaitDef;
import dev.spacepunk.prison.fishing.FishingConfig.EnchantDef;
import dev.spacepunk.prison.fishing.FishingConfig.FishDef;
import dev.spacepunk.prison.fishing.FishingConfig.JunkDef;
import dev.spacepunk.prison.fishing.FishingConfig.LureDef;
import dev.spacepunk.prison.fishing.FishingConfig.Rarity;
import dev.spacepunk.prison.fishing.FishingConfig.RodTier;
import dev.spacepunk.prison.fishing.FishingConfig.TreasureDef;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Per-player fishing state and every rule: catches, minigame sessions, values, XP, upgrades. Main thread. */
final class FishingManager implements FishingService {
    static final String DATA_KEY = "fishing";
    private static final Gson GSON = new Gson();

    private final FishingModule module;
    private final Logger log;
    private FishingConfig cfg;
    private final FishingItems items;
    private final Map<UUID, FishingData> data = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    FishingManager(FishingModule module, FishingConfig cfg, FishingItems items) {
        this.module = module;
        this.log = module.plugin().getLogger();
        this.cfg = cfg;
        this.items = items;
    }

    FishingConfig cfg() { return cfg; }

    FishingItems items() { return items; }

    void config(FishingConfig cfg) {
        this.cfg = cfg;
        items.config(cfg);
    }

    // ------------------------------------------------------------------ data

    FishingData data(UUID id) { return data.get(id); }

    void load(PlayerProfile profile) {
        FishingData d = null;
        String json = profile.moduleData(DATA_KEY);
        if (json != null) {
            try {
                d = GSON.fromJson(json, FishingData.class);
            } catch (RuntimeException e) {
                log.warning("[fishing] bad data for " + profile.uuid() + ", keeping a copy and starting fresh: " + e.getMessage());
                profile.moduleData(DATA_KEY + "_corrupt", json);
            }
        }
        if (d == null) d = new FishingData();
        d.normalize();
        data.put(profile.uuid(), d);
    }

    void unload(UUID id) {
        save(id);
        data.remove(id);
        Session s = sessions.remove(id);
        if (s != null) hideBar(s);
    }

    void save(UUID id) {
        FishingData d = data.get(id);
        if (d == null) return;
        Services.find(ProfileService.class).flatMap(ps -> ps.get(id)).ifPresent(p -> {
            p.moduleData(DATA_KEY, GSON.toJson(d));
            Services.get(ProfileService.class).markDirty(id);
        });
    }

    void saveAll() {
        for (UUID id : data.keySet()) save(id);
    }

    /** Saves and refreshes the rod item after any change. */
    void changed(Player p) {
        save(p.getUniqueId());
        refreshRod(p);
        module.leaderboard().level(p.getUniqueId(), p.getName(), data(p.getUniqueId()).level, data(p.getUniqueId()).xp);
    }

    // ------------------------------------------------------------------ rod item

    /** Makes sure the player has exactly one rod of their own. Returns true if one was given. */
    boolean ensureRod(Player p) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return false;
        PlayerInventory inv = p.getInventory();
        boolean found = false;
        for (int i = 0; i < inv.getSize(); i++) {
            UUID owner = items.rodOwner(inv.getItem(i));
            if (owner == null) continue;
            if (!owner.equals(p.getUniqueId()) || found) {
                inv.setItem(i, null);
                continue;
            }
            found = true;
            items.apply(inv.getItem(i), p.getUniqueId(), d);
        }
        if (found) return false;
        ItemStack rod = items.rod(p.getUniqueId(), d);
        Map<Integer, ItemStack> left = inv.addItem(rod);
        if (!left.isEmpty()) {
            int slot = inv.getHeldItemSlot();
            ItemStack displaced = inv.getItem(slot);
            if (displaced != null && items.rodOwner(displaced) == null) {
                // Make room in the hand slot; the displaced item goes to the cooler-safe spot: dropped at feet.
                p.getWorld().dropItemNaturally(p.getLocation(), displaced);
            }
            inv.setItem(slot, rod);
        }
        if (!d.rodGiven) {
            d.rodGiven = true;
            save(p.getUniqueId());
        }
        return true;
    }

    void refreshRod(Player p) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && p.getUniqueId().equals(items.rodOwner(it))) items.apply(it, p.getUniqueId(), d);
        }
    }

    boolean holdingOwnRod(Player p) {
        return p.getUniqueId().equals(items.rodOwner(p.getInventory().getItemInMainHand()))
                || p.getUniqueId().equals(items.rodOwner(p.getInventory().getItemInOffHand()));
    }

    // ------------------------------------------------------------------ effects and values

    /** Everything that changes a cast: enchants, slotted lures, active bait. */
    Effects effects(UUID id) {
        Effects e = new Effects();
        FishingData d = data(id);
        if (d == null) return e;
        for (EnchantDef def : cfg.enchants.values()) {
            int lvl = d.enchant(def.id());
            if (lvl > 0) e.add(def.effect(), def.perLevel() * lvl);
        }
        RodTier t = cfg.tier(d.rodTier);
        for (int i = 0; i < t.lureSlots(); i++) {
            LureDef l = cfg.lures.get(d.lure(i));
            if (l != null) e.add(l.effects());
        }
        BaitDef b = activeBait(d);
        if (b != null) e.add(b.effects());
        for (Map.Entry<String, Double> m : rodModules(id).entrySet()) e.add(m.getKey(), m.getValue() - 1.0);
        return e;
    }

    /** Rod Rig module multipliers (drill module system): stat -> multiplier. */
    private Map<String, Double> rodModules(UUID id) {
        return Services.find(dev.spacepunk.prison.api.DrillService.class).map(s -> s.gearMultipliers(id, "rod")).orElse(Map.of());
    }

    BaitDef activeBait(FishingData d) {
        BaitDef b = cfg.baits.get(d.activeBait);
        return b != null && d.baitCount(b.id()) > 0 ? b : null;
    }

    int grade(UUID id) { return Services.find(ProgressionService.class).map(pr -> pr.grade(id)).orElse(1); }

    double gradeScale(UUID id, Area a) {
        int g = grade(id);
        if (a != null) g = Math.min(g, a.maxGrade());
        return Math.pow(cfg.valueGrowth, Math.max(0, g - 1));
    }

    /** Credits for one unit of something with this base value, caught in this area (fish species for collection bonus). */
    long unitValue(Player p, double base, String areaId, String fishId) {
        UUID id = p.getUniqueId();
        FishingData d = data(id);
        Area a = cfg.areas.get(areaId);
        double mult = gradeScale(id, a) * (a == null ? 1.0 : a.valueMult());
        double bonus = 1.0;
        if (d != null) {
            bonus += cfg.levelBonus * (d.level - 1);
            bonus += valueEffect(id, d);
            if (fishId != null) {
                FishDef f = cfg.fish.get(fishId);
                FishingData.Species s = d.species.get(fishId);
                if (f != null && s != null) bonus += cfg.collectionBonus * Math.max(0, cfg.collectionTier(f, s.caught) - 1);
            }
        }
        double sell = Services.find(ProgressionService.class).map(pr -> pr.sellMultiplier(id)).orElse(1.0);
        return Math.max(1, Math.round(base * mult * bonus * sell));
    }

    /** Fillet + lure value bonus (bait is used up per catch, so it does not count when selling later). */
    private double valueEffect(UUID id, FishingData d) {
        double v = 0;
        for (EnchantDef def : cfg.enchants.values()) {
            if ("value".equals(def.effect())) v += def.perLevel() * d.enchant(def.id());
        }
        RodTier t = cfg.tier(d.rodTier);
        for (int i = 0; i < t.lureSlots(); i++) {
            LureDef l = cfg.lures.get(d.lure(i));
            if (l != null) v += l.effects().value;
        }
        Double mod = rodModules(id).get("value");
        if (mod != null) v += mod - 1.0;
        return v;
    }

    /** Value of one item stack's unit, or 0 if the market does not buy it. */
    long itemValue(Player p, ItemStack item) {
        String tag = items.tag(item);
        String kind = items.kind(tag), id = items.id(tag);
        if (kind == null) return 0;
        return switch (kind) {
            case "fish" -> {
                FishDef f = cfg.fish.get(id);
                yield f == null ? 0 : unitValue(p, f.value(), f.area(), f.id());
            }
            case "trophy" -> {
                FishDef f = cfg.fish.get(id);
                if (f == null) yield 0;
                double avg = (f.minKg() + f.maxKg()) / 2;
                double kg = items.weight(item);
                yield unitValue(p, f.value() * Math.max(0.5, kg / Math.max(0.01, avg)), f.area(), f.id());
            }
            case "junk" -> {
                JunkDef j = cfg.junk.get(id);
                yield j == null ? 0 : unitValue(p, j.value(), areaFor(p), null);
            }
            default -> 0;
        };
    }

    /** Area used to scale junk value: where the player stands, else their best unlocked area. */
    private String areaFor(Player p) {
        Area here = module.world().areaNear(p.getLocation());
        if (here != null) return here.id();
        int g = grade(p.getUniqueId());
        String best = null;
        for (Area a : cfg.areas.values()) if (a.grade() <= g) best = a.id();
        return best;
    }

    /** Scales for gutting one unit, or 0 if it cannot be gutted. */
    long gutValue(ItemStack item) {
        String tag = items.tag(item);
        String kind = items.kind(tag);
        if (!"fish".equals(kind) && !"trophy".equals(kind)) return 0;
        FishDef f = cfg.fish.get(items.id(tag));
        return f == null ? 0 : cfg.rarity(f.rarity()).scales();
    }

    // ------------------------------------------------------------------ casting and bites

    /** Bite wait range in ticks for this player's next cast. */
    int[] waitTicks(UUID id) {
        double cut = Math.min(cfg.maxBiteReduction, Math.max(0, effects(id).biteSpeed));
        int min = (int) Math.max(10, Math.round(cfg.minWait * (1 - cut)));
        int max = (int) Math.max(min + 1, Math.round(cfg.maxWait * (1 - cut)));
        return new int[]{min, max};
    }

    /** What a bite turned out to be. */
    record Catch(String kind, Area area, FishDef fish, JunkDef junk, TreasureDef treasure, FishingConfig.CreatureDef creature,
                 double kg, int amount) {}

    Catch roll(Player p, Area area) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Effects e = effects(p.getUniqueId());
        // Sea creature?
        List<FishingConfig.CreatureDef> cs = cfg.creatures.values().stream().filter(c -> c.area().equals(area.id())).toList();
        double cc = Math.min(cfg.maxCreatureChance, cfg.creatureChance * (1 + Math.max(0, e.creature)));
        if (!cs.isEmpty() && r.nextDouble() < cc && module.creatures().count(p.getUniqueId()) < cfg.maxCreatures) {
            FishingConfig.CreatureDef c = pick(cs, FishingConfig.CreatureDef::weight);
            if (c != null) return new Catch("creature", area, null, null, null, c, 0, 1);
        }
        double tl = Math.max(0, e.treasure);
        List<JunkDef> junk = cfg.junk.values().stream().filter(j -> j.areas().isEmpty() || j.areas().contains(area.id())).toList();
        List<TreasureDef> tre = cfg.treasure.values().stream().filter(t -> t.areas().isEmpty() || t.areas().contains(area.id())).toList();
        List<FishDef> fish = cfg.fishIn(area.id());
        double jw = junk.isEmpty() ? 0 : cfg.junkWeight / (1 + tl);
        double tw = tre.isEmpty() ? 0 : cfg.treasureWeight * (1 + tl);
        double fw = fish.isEmpty() ? 0 : cfg.fishWeight;
        double roll = r.nextDouble() * (jw + tw + fw);
        if (roll < jw) return new Catch("junk", area, null, pick(junk, x -> 1.0), null, null, 0, 1);
        if (roll < jw + tw) return new Catch("treasure", area, null, null, pick(tre, TreasureDef::weight), null, 0, 1);
        if (fish.isEmpty()) return new Catch("junk", area, null, junk.isEmpty() ? null : junk.get(0), null, null, 0, 1);
        // Rarity first, then a species of that rarity from this area.
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map.Entry<String, Double> w : cfg.rarityWeights.entrySet()) {
            String rar = w.getKey();
            if (fish.stream().noneMatch(f -> f.rarity().equals(rar))) continue;
            double v = w.getValue() * (rar.equals("common") ? 1 : 1 + Math.max(0, e.rareLuck));
            weights.put(rar, v);
        }
        String rarity = weights.isEmpty() ? fish.get(0).rarity() : pick(new ArrayList<>(weights.keySet()), weights::get);
        List<FishDef> pool = fish.stream().filter(f -> f.rarity().equals(rarity)).toList();
        FishDef f = pool.isEmpty() ? fish.get(r.nextInt(fish.size())) : pool.get(r.nextInt(pool.size()));
        double kg = f.minKg() + (f.maxKg() - f.minKg()) * Math.pow(r.nextDouble(), 2.2);
        kg = Math.round(kg * 100) / 100.0;
        int amount = !"legendary".equals(f.rarity()) && r.nextDouble() < Math.min(0.6, e.doubleCatch) ? 2 : 1;
        return new Catch("fish", area, f, null, null, null, kg, amount);
    }

    private static <T> T pick(List<T> list, java.util.function.ToDoubleFunction<T> weight) {
        if (list.isEmpty()) return null;
        double total = 0;
        for (T t : list) total += Math.max(0, weight.applyAsDouble(t));
        if (total <= 0) return list.get(ThreadLocalRandom.current().nextInt(list.size()));
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (T t : list) {
            roll -= Math.max(0, weight.applyAsDouble(t));
            if (roll <= 0) return t;
        }
        return list.get(list.size() - 1);
    }

    /** Called when the player hooks a bite in an area. */
    void hooked(Player p, Area area, Location hook) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return;
        Catch c = roll(p, area);
        if (c.creature() != null) {
            consumeBait(p, d);
            module.creatures().spawn(p, c.creature(), area, hook);
            return;
        }
        Rarity r = c.fish() != null ? cfg.rarity(c.fish().rarity()) : null;
        if (r != null && r.minigame() != null) {
            startGame(p, c, r, hook);
            return;
        }
        land(p, c, hook);
    }

    // ------------------------------------------------------------------ minigame sessions

    static final class Session {
        final Player player;
        final Catch c;
        final ReelGame game;
        final BossBar bar;
        final Location hook;
        int state = Integer.MIN_VALUE;

        Session(Player player, Catch c, ReelGame game, BossBar bar, Location hook) {
            this.player = player;
            this.c = c;
            this.game = game;
            this.bar = bar;
            this.hook = hook;
        }
    }

    boolean reeling(UUID id) { return sessions.containsKey(id); }

    private void startGame(Player p, Catch c, Rarity r, Location hook) {
        Effects e = effects(p.getUniqueId());
        ReelGame g = new ReelGame(r.minigame(), e.zone, e.strength);
        BossBar bar = BossBar.bossBar(Text.mm(r.color() + "Something " + r.name().toUpperCase() + " <gray>is on the line"),
                (float) g.progress, barColor(r.id()), BossBar.Overlay.PROGRESS);
        p.showBossBar(bar);
        Session s = new Session(p, c, g, bar, hook);
        sessions.put(p.getUniqueId(), s);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 0.6f);
        render(s);
        Text.send(p, r.color() + "It fights! <gray>Your <white>XP bar</white> is the line: hold <white>SNEAK</white> to pull it up, "
                + "let go to let it drop. Keep it in the <white>middle</white> to reel. The number is your catch progress.");
    }

    private static BossBar.Color barColor(String rarity) {
        return switch (rarity) {
            case "uncommon" -> BossBar.Color.GREEN;
            case "rare" -> BossBar.Color.BLUE;
            case "epic" -> BossBar.Color.PURPLE;
            case "legendary" -> BossBar.Color.YELLOW;
            default -> BossBar.Color.WHITE;
        };
    }

    /** Every tick. */
    void tickGames() {
        if (sessions.isEmpty()) return;
        Iterator<Session> it = sessions.values().iterator();
        List<Runnable> after = new ArrayList<>();
        while (it.hasNext()) {
            Session s = it.next();
            Player p = s.player;
            if (!p.isOnline() || !module.world().isWaters(p.getWorld()) || p.getLocation().distanceSquared(s.hook) > 32 * 32) {
                it.remove();
                hideBar(s);
                if (p.isOnline()) Text.send(p, "<gray>The line went slack. It got away.");
                continue;
            }
            boolean reeling = p.isSneaking();
            ReelGame.Result res = s.game.tick(reeling);
            if (res == ReelGame.Result.CONTINUE) {
                render(s);
                if (reeling && s.game.wasInside && s.game.ticks % 4 == 0) p.playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.25f, 1.6f);
                if (s.game.ticks % 10 == 0) s.hook.getWorld().spawnParticle(Particle.SPLASH, s.hook, 6, 0.3, 0.1, 0.3, 0.05);
                continue;
            }
            it.remove();
            hideBar(s);
            if (res == ReelGame.Result.LANDED) after.add(() -> land(p, s.c, s.hook));
            else after.add(() -> escaped(p, s.c));
        }
        after.forEach(Runnable::run);
    }

    /**
     * Every tick: the XP bar shows line tension and the level number the catch percent. This is a client-only display
     * packet ({@link Player#sendExperienceChange}); the player's real XP is never touched, so nothing can be gained
     * or lost, and {@link #hideBar} sends the real values back.
     */
    private void render(Session s) {
        ReelGame g = s.game;
        s.player.sendExperienceChange(g.bar(), g.percent());
        s.bar.progress((float) Math.max(0, Math.min(1, g.progress)));
        int state = g.wasInside ? 0 : g.offset() > 0 ? 1 : -1;
        if (state == s.state) return;
        if (s.state != Integer.MIN_VALUE && state != 0) s.player.playSound(s.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        s.state = state;
        Rarity r = cfg.rarity(s.c.fish().rarity());
        String what = r.color() + r.name().toUpperCase() + " <gray>on the line  ";
        s.bar.name(Text.mm(what + switch (state) {
            case 0 -> "<#9cf27a><bold>REELING</bold> <gray>· keep the XP bar in the middle";
            case 1 -> "<#ff5a3c><bold>TOO TIGHT</bold> <gray>· let go of sneak";
            default -> "<#ffb43a><bold>TOO SLACK</bold> <gray>· hold sneak";
        }));
    }

    private void hideBar(Session s) {
        Player p = s.player;
        if (!p.isOnline()) return;
        p.hideBossBar(s.bar);
        p.sendExperienceChange(p.getExp(), p.getLevel());
    }

    void cancelAll() {
        for (Session s : sessions.values()) hideBar(s);
        sessions.clear();
    }

    private void escaped(Player p, Catch c) {
        Rarity r = cfg.rarity(c.fish().rarity());
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.2f);
        Text.send(p, "<gray>The " + r.color() + r.name().toLowerCase() + "<gray> fish snapped free. <dark_gray>Try keeping it inside the zone.");
        FishingData d = data(p.getUniqueId());
        if (d != null) consumeBait(p, d);
    }

    // ------------------------------------------------------------------ landing a catch

    void land(Player p, Catch c, Location hook) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return;
        consumeBait(p, d);
        d.catches++;
        switch (c.kind()) {
            case "fish" -> landFish(p, d, c);
            case "junk" -> {
                if (c.junk() != null) {
                    give(p, d, items.junk(c.junk(), 1), "junk:" + c.junk().id(), 1);
                    flash(p, cfg.rarity("junk").color() + "+1 " + c.junk().name());
                }
                addXp(p, d, cfg.rarity("junk").xp());
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.7f);
                Bukkit.getPluginManager().callEvent(new FishCaughtEvent(p, c.area().id(), "junk",
                        c.junk() == null ? "" : c.junk().id(), "junk", 0, 1));
            }
            case "treasure" -> {
                if (c.treasure() != null) treasure(p, d, c.treasure(), c.area());
                addXp(p, d, 10);
                Bukkit.getPluginManager().callEvent(new FishCaughtEvent(p, c.area().id(), "treasure",
                        c.treasure() == null ? "" : c.treasure().id(), "treasure", 0, 1));
            }
            default -> { }
        }
        tokenFind(p);
        if (hook != null && hook.getWorld() != null) hook.getWorld().spawnParticle(Particle.SPLASH, hook, 20, 0.4, 0.1, 0.4, 0.1);
        changed(p);
    }

    /** Uncommon Token find on any landed catch. The HUD's gain summary shows the amount too. */
    private void tokenFind(Player p) {
        if (cfg.tokenFindChance <= 0 || ThreadLocalRandom.current().nextDouble() >= cfg.tokenFindChance) return;
        var eco = economy();
        if (eco.isEmpty()) return;
        long amount = TokenDrops.roll(p.getUniqueId(), cfg.tokenFindMin, cfg.tokenFindMax);
        eco.get().give(p.getUniqueId(), Currency.TOKENS, amount, "fishing:token-find");
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
        Text.send(p, "<aqua>✦ Token find</aqua> <gray>tangled in the line: <aqua>+" + Numbers.shortFormat(amount) + " Tokens");
    }

    private void landFish(Player p, FishingData d, Catch c) {
        FishDef f = c.fish();
        Rarity r = cfg.rarity(f.rarity());
        boolean legendary = "legendary".equals(f.rarity());
        if (legendary) {
            ItemStack trophy = items.trophy(f, c.kg(), p.getName());
            give(p, d, trophy, "trophy:" + f.id() + "@" + c.kg(), 1);
        } else {
            give(p, d, items.fish(f, c.amount()), "fish:" + f.id(), c.amount());
        }
        FishingData.Species s = d.species(f.id());
        boolean first = s.caught == 0;
        s.caught += c.amount();
        boolean personalBest = c.kg() > s.heaviest;
        if (personalBest) s.heaviest = c.kg();
        int xp = r.xp() * c.amount();
        addXp(p, d, xp);
        collection(p, d, f, s);

        Leaderboard lb = module.leaderboard();
        boolean weeklyBest = lb.recordWeekly(p.getUniqueId(), p.getName(), f.id(), c.kg());
        boolean record = lb.recordSpecies(p.getName(), f.id(), c.kg());

        String amount = c.amount() > 1 ? "<white>2x</white> " : "";
        flash(p, r.color() + "+ " + amount + f.name() + " <gray>" + FishingItems.kg(c.kg()) + " <dark_aqua>+" + xp + " XP");
        if (c.amount() > 1) Text.send(p, "<#6fd35a>Double catch! <gray>Two " + r.color() + f.name() + "<gray> on one hook.");
        if (first) Text.send(p, "<#5fb4c8>New species: " + r.color() + f.name() + " <dark_gray>(added to your collection)");
        if (record) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<#5fb4c8>" + p.getName() + "<gray> set the station record for "
                    + r.color() + f.name() + "<gray>: <white>" + FishingItems.kg(c.kg())));
        }
        switch (f.rarity()) {
            case "legendary" -> {
                Bukkit.broadcast(Text.mm(Text.PREFIX + r.color() + "<bold>LEGENDARY CATCH!</bold> <gray>" + p.getName()
                        + " landed " + r.color() + f.name() + " <gray>(" + FishingItems.kg(c.kg()) + ")"));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
            }
            case "epic" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
                Text.send(p, r.color() + "Epic catch: " + f.name() + " <gray>(" + FishingItems.kg(c.kg()) + ")");
            }
            case "rare" -> p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
            default -> p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 1.1f);
        }
        if (weeklyBest && !legendary) {
            List<Map.Entry<String, Leaderboard.Catch>> top = lb.weeklyTop(cfg.leaderboardSize);
            for (int i = 0; i < top.size(); i++) {
                if (top.get(i).getKey().equals(p.getUniqueId().toString())) {
                    if (i < 3) Text.send(p, "<#ffd23f>Your " + f.name() + " is #" + (i + 1) + " on this week's biggest-catch board.");
                    break;
                }
            }
        }
        Bukkit.getPluginManager().callEvent(new FishCaughtEvent(p, c.area().id(), "fish", f.id(), f.rarity(), c.kg(), c.amount()));
    }

    private void collection(Player p, FishingData d, FishDef f, FishingData.Species s) {
        int tier = cfg.collectionTier(f, s.caught);
        while (s.tierClaimed < tier) {
            s.tierClaimed++;
            int idx = s.tierClaimed - 1;
            long reward = idx < cfg.collectionScales.size() ? cfg.collectionScales.get(idx) : 0;
            d.scales += reward;
            if (s.tierClaimed > 1) {
                Text.send(p, "<#5fb4c8>Collection: <white>" + f.name() + " tier " + s.tierClaimed + "</white> <gray>· +"
                        + Effects.pct(cfg.collectionBonus * (s.tierClaimed - 1)).substring(1) + " value"
                        + (reward > 0 ? " · <#9cf27a>+" + reward + " Scales" : ""));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            }
        }
    }

    private void treasure(Player p, FishingData d, TreasureDef t, Area area) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long amount = t.min() >= t.max() ? t.min() : r.nextLong(t.min(), t.max() + 1);
        String got;
        switch (t.type()) {
            case "scales" -> {
                d.scales += amount;
                got = "<#9cf27a>+" + amount + " Scales";
            }
            case "credits" -> {
                long credits = Math.max(1, Math.round(amount * gradeScale(p.getUniqueId(), area) * area.valueMult()));
                payCredits(p, credits);
                got = economy().map(e -> e.format(Currency.CREDITS, credits)).orElse(credits + " Credits");
            }
            case "tokens" -> {
                long tokens = cfg.tokenTreasureScaled ? Math.max(1, Math.round(amount * TokenDrops.scale(p.getUniqueId()))) : amount;
                economy().ifPresent(e -> e.give(p.getUniqueId(), Currency.TOKENS, tokens, "fishing:treasure"));
                got = economy().map(e -> e.format(Currency.TOKENS, tokens)).orElse(tokens + " Tokens");
            }
            case "bait" -> {
                BaitDef b = cfg.baits.get(t.bait());
                if (b == null) return;
                d.bait.merge(b.id(), (int) amount, Integer::sum);
                if (d.activeBait == null || d.baitCount(d.activeBait) <= 0) d.activeBait = b.id();
                got = "<white>" + amount + "x " + b.name();
            }
            case "lure" -> {
                LureDef l = cfg.lures.get(t.lure());
                if (l == null) return;
                give(p, d, items.lure(l), "lure:" + l.id(), 1);
                got = items.lureName(l) + " <gray>lure";
            }
            case "command" -> {
                if (t.command() == null || t.command().isBlank()) return;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), t.command().replace("{player}", p.getName()));
                got = t.name();
            }
            default -> {
                return;
            }
        }
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.3f);
        p.getWorld().spawnParticle(Particle.WAX_OFF, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.1);
        flash(p, "<#ffd23f>Treasure! <gray>" + t.name() + " <dark_gray>→ " + got);
        Text.send(p, "<#ffd23f>Treasure! <gray>" + t.name() + "<gray>: " + got);
        if (t.broadcast()) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<#ffd23f>" + p.getName() + "<gray> fished up " + t.name() + "<gray>!"));
        }
    }

    private void consumeBait(Player p, FishingData d) {
        BaitDef b = activeBait(d);
        if (b == null) return;
        int left = d.baitCount(b.id()) - 1;
        if (left <= 0) {
            d.bait.remove(b.id());
            Text.send(p, "<gray>You used your last " + b.name() + ".");
            // Switch to another bait you still have, if any.
            d.activeBait = d.bait.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).findFirst().orElse(null);
        } else {
            d.bait.put(b.id(), left);
        }
    }

    /** Puts an item in the inventory; whatever does not fit goes to the cooler under coolerKey. */
    void give(Player p, FishingData d, ItemStack item, String coolerKey, int amount) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        int over = left.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (over > 0) {
            d.cooler.merge(coolerKey, (long) over, Long::sum);
            flash(p, "<#8fd3ff>Inventory full: sent to your cooler <dark_gray>(/fish)");
        }
    }

    void addXp(Player p, FishingData d, long xp) {
        if (xp <= 0 || d.level >= cfg.maxLevel) return;
        d.xp += xp;
        while (d.level < cfg.maxLevel && d.xp >= cfg.xpFor(d.level)) {
            d.xp -= cfg.xpFor(d.level);
            d.level++;
            levelUp(p, d);
        }
        if (d.level >= cfg.maxLevel) d.xp = 0;
    }

    private void levelUp(Player p, FishingData d) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
        Text.send(p, "<#5fb4c8><bold>FISHING LEVEL " + d.level + "</bold> <gray>· fish sell for "
                + Effects.pct(cfg.levelBonus * (d.level - 1)) + " more");
        for (RodTier t : cfg.tiers) {
            if (t.level() == d.level && t.index() > d.rodTier) {
                Text.send(p, "<gray>You can now fit the " + t.colored() + "<gray> in the rod bay (/rod).");
            }
        }
        if (d.level % 10 == 0) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<#5fb4c8>" + p.getName() + "<gray> reached fishing level <white>" + d.level));
        }
    }

    private void flash(Player p, String mm) {
        var hud = Services.find(HudService.class);
        if (hud.isPresent()) hud.get().flash(p, mm);
        else p.sendActionBar(Text.mm(mm));
    }

    // ------------------------------------------------------------------ money

    java.util.Optional<EconomyService> economy() { return Services.find(EconomyService.class); }

    void payCredits(Player p, long credits) {
        if (credits <= 0) return;
        economy().ifPresent(e -> e.give(p.getUniqueId(), Currency.CREDITS, credits, "income:fishing"));
        FishingData d = data(p.getUniqueId());
        if (d != null) d.creditsEarned += credits;
    }

    record Sale(long credits, long units, long scales) {}

    /**
     * Sells (or guts) market items in the player's inventory and cooler. Trophies are skipped unless
     * includeTrophies. Gutting skips junk. Returns totals.
     */
    Sale sellAll(Player p, boolean gut, boolean includeTrophies) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return new Sale(0, 0, 0);
        long credits = 0, units = 0, scales = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!items.isSellable(it)) continue;
            String kind = items.kind(items.tag(it));
            if ("trophy".equals(kind) && !includeTrophies) continue;
            if (gut) {
                long g = gutValue(it);
                if (g <= 0) continue;
                scales += g * it.getAmount();
            } else {
                credits += itemValue(p, it) * it.getAmount();
            }
            units += it.getAmount();
            contents[i] = null;
        }
        p.getInventory().setStorageContents(contents);
        // Cooler
        Iterator<Map.Entry<String, Long>> ci = d.cooler.entrySet().iterator();
        while (ci.hasNext()) {
            Map.Entry<String, Long> e = ci.next();
            String key = e.getKey();
            if (key.startsWith("trophy:") && !includeTrophies) continue;
            if (key.startsWith("lure:")) continue;
            ItemStack sample = coolerItem(key, p.getName());
            if (sample == null) {
                ci.remove();
                continue;
            }
            long n = e.getValue();
            if (gut) {
                long g = gutValue(sample);
                if (g <= 0) continue;
                scales += g * n;
            } else {
                credits += itemValue(p, sample) * n;
            }
            units += n;
            ci.remove();
        }
        if (credits > 0) payCredits(p, credits);
        if (scales > 0) d.scales += scales;
        changed(p);
        return new Sale(credits, units, scales);
    }

    /** Sells or guts exactly this stack (clicked in the market). */
    Sale sellStack(Player p, ItemStack it, boolean gut) {
        FishingData d = data(p.getUniqueId());
        if (d == null || !items.isSellable(it)) return new Sale(0, 0, 0);
        long n = it.getAmount();
        if (gut) {
            long g = gutValue(it);
            if (g <= 0) return new Sale(0, 0, 0);
            d.scales += g * n;
            changed(p);
            return new Sale(0, n, g * n);
        }
        long c = itemValue(p, it) * n;
        payCredits(p, c);
        changed(p);
        return new Sale(c, n, 0);
    }

    /** Rebuilds an item from a cooler key ("fish:id", "junk:id", "lure:id", "trophy:id@kg"). */
    ItemStack coolerItem(String key, String angler) {
        String kind = key.substring(0, Math.max(0, key.indexOf(':')));
        String rest = key.substring(key.indexOf(':') + 1);
        switch (kind) {
            case "fish" -> {
                FishDef f = cfg.fish.get(rest);
                return f == null ? null : items.fish(f, 1);
            }
            case "junk" -> {
                JunkDef j = cfg.junk.get(rest);
                return j == null ? null : items.junk(j, 1);
            }
            case "lure" -> {
                LureDef l = cfg.lures.get(rest);
                return l == null ? null : items.lure(l);
            }
            case "trophy" -> {
                int at = rest.indexOf('@');
                FishDef f = cfg.fish.get(at < 0 ? rest : rest.substring(0, at));
                double kg = 0;
                try {
                    kg = at < 0 ? 0 : Double.parseDouble(rest.substring(at + 1));
                } catch (NumberFormatException ignored) {
                    // keep 0
                }
                return f == null ? null : items.trophy(f, kg > 0 ? kg : f.minKg(), angler);
            }
            default -> {
                return null;
            }
        }
    }

    /** Moves as much of the cooler as fits back into the inventory. Returns units moved. */
    long claimCooler(Player p) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return 0;
        long moved = 0;
        Iterator<Map.Entry<String, Long>> it = d.cooler.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            ItemStack sample = coolerItem(e.getKey(), p.getName());
            if (sample == null) {
                it.remove();
                continue;
            }
            long left = e.getValue();
            while (left > 0) {
                ItemStack give = sample.clone();
                int n = (int) Math.min(left, Math.max(1, give.getMaxStackSize()));
                give.setAmount(n);
                Map<Integer, ItemStack> over = p.getInventory().addItem(give);
                int notFit = over.values().stream().mapToInt(ItemStack::getAmount).sum();
                moved += n - notFit;
                left -= n - notFit;
                if (notFit > 0) break;
            }
            if (left <= 0) it.remove();
            else {
                e.setValue(left);
                break;
            }
        }
        changed(p);
        return moved;
    }

    long coolerUnits(UUID id) {
        FishingData d = data(id);
        return d == null ? 0 : d.cooler.values().stream().mapToLong(Long::longValue).sum();
    }

    // ------------------------------------------------------------------ upgrades

    enum Buy { OK, MAX, CAPPED, POOR }

    Buy buyEnchant(Player p, EnchantDef e, int levels) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return Buy.POOR;
        RodTier t = cfg.tier(d.rodTier);
        int bought = 0;
        Buy last = Buy.OK;
        for (int i = 0; i < levels; i++) {
            int lvl = d.enchant(e.id());
            if (lvl >= t.maxEnchant()) {
                last = t.index() >= cfg.tiers.size() - 1 ? Buy.MAX : Buy.CAPPED;
                break;
            }
            long cost = e.cost(lvl);
            if (d.scales < cost) {
                last = Buy.POOR;
                break;
            }
            d.scales -= cost;
            d.enchants.put(e.id(), lvl + 1);
            bought++;
        }
        if (bought > 0) {
            changed(p);
            return Buy.OK;
        }
        return last;
    }

    enum Upgrade { OK, MAX, LEVEL, POOR }

    Upgrade upgradeRod(Player p) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return Upgrade.POOR;
        if (d.rodTier >= cfg.tiers.size() - 1) return Upgrade.MAX;
        RodTier next = cfg.tier(d.rodTier + 1);
        if (d.level < next.level()) return Upgrade.LEVEL;
        if (d.scales < next.scales()) return Upgrade.POOR;
        d.scales -= next.scales();
        d.rodTier++;
        changed(p);
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
        Bukkit.broadcast(Text.mm(Text.PREFIX + "<#5fb4c8>" + p.getName() + "<gray> upgraded to the " + next.colored()));
        return Upgrade.OK;
    }

    /** Slots the lure in this inventory item. Returns false if no free slot. */
    boolean socketLure(Player p, ItemStack item) {
        FishingData d = data(p.getUniqueId());
        String tag = items.tag(item);
        if (d == null || !"lure".equals(items.kind(tag))) return false;
        LureDef l = cfg.lures.get(items.id(tag));
        if (l == null) return false;
        RodTier t = cfg.tier(d.rodTier);
        for (int i = 0; i < t.lureSlots(); i++) {
            if (cfg.lures.get(d.lure(i)) == null) {
                d.lure(i, l.id());
                item.setAmount(item.getAmount() - 1);
                changed(p);
                return true;
            }
        }
        return false;
    }

    void unsocketLure(Player p, int slot) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return;
        LureDef l = cfg.lures.get(d.lure(slot));
        if (l == null) return;
        d.lure(slot, null);
        give(p, d, items.lure(l), "lure:" + l.id(), 1);
        changed(p);
    }

    long baitPrice(UUID id, BaitDef b) {
        return Math.max(1, Math.round(b.price() * Math.pow(cfg.valueGrowth, Math.max(0, grade(id) - 1))));
    }

    boolean buyBait(Player p, BaitDef b, int packs) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return false;
        long cost = baitPrice(p.getUniqueId(), b) * packs;
        EconomyService eco = economy().orElse(null);
        if (eco == null || !eco.take(p.getUniqueId(), Currency.CREDITS, cost, "fishing:bait")) return false;
        d.bait.merge(b.id(), b.amount() * packs, Integer::sum);
        if (d.activeBait == null || d.baitCount(d.activeBait) <= 0) d.activeBait = b.id();
        changed(p);
        return true;
    }

    void selectBait(Player p, String id) {
        FishingData d = data(p.getUniqueId());
        if (d == null) return;
        d.activeBait = id;
        changed(p);
    }

    /** Scales owed from a weekly leaderboard win, paid on join. */
    void claimWeekly(Player p) {
        long owed = module.leaderboard().claimPending(p.getUniqueId());
        FishingData d = data(p.getUniqueId());
        if (owed <= 0 || d == null) return;
        d.scales += owed;
        save(p.getUniqueId());
        Text.send(p, "<#ffd23f>You placed on last week's biggest-catch board: <#9cf27a>+" + owed + " Scales");
    }

    // ------------------------------------------------------------------ FishingService

    @Override public int rodTier(UUID player) {
        FishingData d = data(player);
        return d == null ? 0 : d.rodTier;
    }

    @Override public int fishingLevel(UUID player) {
        FishingData d = data(player);
        return d == null ? 0 : d.level;
    }

    @Override public long totalCatches(UUID player) {
        FishingData d = data(player);
        return d == null ? 0 : d.catches;
    }

    @Override public long caught(UUID player, String fishId) {
        FishingData d = data(player);
        if (d == null) return 0;
        FishingData.Species s = d.species.get(fishId);
        return s == null ? 0 : s.caught;
    }

    @Override public long scales(UUID player) {
        FishingData d = data(player);
        return d == null ? 0 : d.scales;
    }

    @Override public void giveScales(UUID player, long amount, String reason) {
        FishingData d = data(player);
        if (d == null || amount <= 0) return;
        d.scales += amount;
        Player p = Bukkit.getPlayer(player);
        if (p != null) changed(p);
        else save(player);
    }

    String formatScales(long n) { return "<#9cf27a>" + Numbers.shortFormat(n) + " Scales"; }
}
