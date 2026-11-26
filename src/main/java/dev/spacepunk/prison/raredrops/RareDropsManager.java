package dev.spacepunk.prison.raredrops;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.ObjectiveTypes;
import dev.spacepunk.prison.api.Objectives;
import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.raredrops.RareConfig.Drop;
import dev.spacepunk.prison.raredrops.RareConfig.RelicSet;
import dev.spacepunk.prison.raredrops.RareConfig.Tier;
import dev.spacepunk.prison.raredrops.RareConfig.Type;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Rolls, awards and announces rare drops; owns the collection log, caches and module assembly. Main thread. */
final class RareDropsManager {
    static final String DATA_KEY = "raredrops";
    private static final Gson GSON = new Gson();

    private final Logger log;
    private final RareItems items;
    private RareConfig cfg;
    private final Map<UUID, RareData> data = new HashMap<>();

    RareDropsManager(Logger log, RareItems items, RareConfig cfg) {
        this.log = log;
        this.items = items;
        this.cfg = cfg;
    }

    RareConfig cfg() { return cfg; }

    RareItems items() { return items; }

    void config(RareConfig cfg) {
        this.cfg = cfg;
        items.config(cfg);
    }

    // ---- data ----

    void load(PlayerProfile profile) {
        RareData d = null;
        String json = profile.moduleData(DATA_KEY);
        if (json != null && !json.isBlank()) {
            try {
                d = GSON.fromJson(json, RareData.class);
            } catch (JsonParseException e) {
                log.warning("Bad raredrops data for " + profile.uuid() + ", starting fresh: " + e.getMessage());
            }
        }
        if (d == null) d = new RareData();
        d.normalize();
        data.put(profile.uuid(), d);
    }

    void unload(UUID id) { data.remove(id); }

    RareData data(UUID id) { return data.get(id); }

    void save(UUID id) {
        RareData d = data.get(id);
        if (d == null) return;
        ProfileService profiles = Services.get(ProfileService.class);
        profiles.get(id).ifPresent(p -> {
            p.moduleData(DATA_KEY, GSON.toJson(d));
            profiles.markDirty(id);
        });
    }

    // ---- rolling ----

    /** Called for every block batch a player mines. deck is 1..5 from the mine's required grade. */
    void roll(Player p, int blocks, boolean hand, int deck) {
        RareData d = data.get(p.getUniqueId());
        if (d == null || blocks <= 0) return;
        double mul = Services.find(DrillService.class).map(s -> s.rareDropMultiplier(p.getUniqueId())).orElse(1.0);
        double chance = cfg.baseChance * mul * (hand ? 1.0 : cfg.enchantRate);
        if (chance <= 0) return;
        int hits = successes(blocks, Math.min(1.0, chance), cfg.maxPerEvent);
        for (int i = 0; i < hits; i++) {
            Drop drop = pick(deck, null);
            if (drop != null) award(p, d, drop, true);
        }
    }

    /** Picks a drop: a tier by weight (or the given one), then an entry of that tier allowed on this deck. */
    Drop pick(int deck, Tier forced) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Tier tier = forced;
        if (tier == null) {
            double total = 0;
            for (Tier t : cfg.tiers.values()) total += Math.max(0, t.weight());
            double r = rng.nextDouble() * total;
            for (Tier t : cfg.tiers.values()) {
                r -= Math.max(0, t.weight());
                if (r <= 0) { tier = t; break; }
            }
            if (tier == null) tier = cfg.tiers.values().iterator().next();
        }
        // Walk down the tiers if nothing in this tier can drop on this deck.
        List<Tier> order = new ArrayList<>(cfg.tiers.values());
        for (int ti = tier.index(); ti >= 0; ti--) {
            Tier t = order.get(ti);
            List<Drop> pool = new ArrayList<>();
            double total = 0;
            for (Drop d : cfg.drops.values()) {
                if (d.tier() == t && d.allowedOn(deck) && d.weight() > 0) {
                    pool.add(d);
                    total += d.weight();
                }
            }
            if (pool.isEmpty()) continue;
            double r = rng.nextDouble() * total;
            for (Drop d : pool) {
                r -= d.weight();
                if (r <= 0) return d;
            }
            return pool.getLast();
        }
        return null;
    }

    /** Gives the drop, records it and plays the tier's announcement. */
    void award(Player p, RareData d, Drop drop, boolean announce) {
        give(p, d, drop, 1);
        d.totalFinds++;
        d.found.merge(drop.id(), 1L, Long::sum);
        save(p.getUniqueId());
        if (announce) announce(p, drop);
        Objectives.report(p, ObjectiveTypes.RARE_DROP, 1, drop.id(), "rarity:" + drop.tier().id());
    }

    /** Puts items in the inventory; whatever does not fit waits in the /salvage stash (never dropped in the mine). */
    void give(Player p, RareData d, Drop drop, long amount) {
        long left = amount;
        while (left > 0) {
            int n = (int) Math.min(64, left);
            left -= n;
            Map<Integer, ItemStack> over = p.getInventory().addItem(items.create(drop, n));
            long overflow = over.values().stream().mapToLong(ItemStack::getAmount).sum();
            if (overflow > 0) {
                d.stash.merge(drop.id(), overflow + left, Long::sum);
                Text.send(p, "<yellow>Inventory full:</yellow> <gray>" + drop.coloredName()
                        + " <gray>went to your stash in <white>/salvage</white>.");
                return;
            }
        }
    }

    private void announce(Player p, Drop drop) {
        Tier t = drop.tier();
        String label = t.colored("<bold>" + t.name().toUpperCase() + " DROP!</bold>");
        Sound sound = Sound.sound(Key.key(t.sound()), Sound.Source.MASTER, 0.9f, t.pitch());
        p.playSound(sound);
        String flash = label + " <gray>" + drop.coloredName();
        HudService hud = Services.find(HudService.class).orElse(null);
        if (hud != null) hud.flash(p, flash);
        else p.sendActionBar(Text.mm(flash));
        if (t.title()) p.showTitle(Title.title(Text.mm(drop.coloredName()), Text.mm(label)));
        Component name = Text.mm(drop.coloredName()).hoverEvent(items.create(drop, 1).asHoverEvent());
        if (t.broadcast()) {
            Component msg = Text.mm(Text.PREFIX + label + " <white>" + p.getName() + "</white> <gray>dug up ")
                    .append(name);
            for (Player o : Bukkit.getOnlinePlayers()) {
                o.sendMessage(msg);
                if (t.globalSound() && o != p) o.playSound(sound);
            }
            Bukkit.getConsoleSender().sendMessage(msg);
        } else {
            p.sendMessage(Text.mm(Text.PREFIX + label + " <gray>You found ").append(name));
        }
    }

    // ---- relics ----

    enum CatalogResult { ADDED, DUPLICATE, NOT_RELIC, NOT_LOADED }

    /** Catalogs one relic from the item (consumes one on success). */
    CatalogResult catalog(Player p, ItemStack item) {
        RareData d = data.get(p.getUniqueId());
        if (d == null) return CatalogResult.NOT_LOADED;
        Drop drop = items.drop(item);
        if (drop == null || drop.type() != Type.RELIC) return CatalogResult.NOT_RELIC;
        if (d.catalogued.contains(drop.id())) return CatalogResult.DUPLICATE;
        item.setAmount(item.getAmount() - 1);
        d.catalogued.add(drop.id());
        p.playSound(Sound.sound(Key.key("block.enchantment_table.use"), Sound.Source.MASTER, 0.8f, 1.2f));
        Text.send(p, "<gray>Catalogued " + drop.coloredName() + "<gray>.");
        checkSets(p, d);
        save(p.getUniqueId());
        return CatalogResult.ADDED;
    }

    /** Catalogs every new relic in the inventory. Returns how many were added. */
    int catalogAll(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null) continue;
            Drop drop = items.drop(it);
            if (drop != null && drop.type() == Type.RELIC && catalog(p, it) == CatalogResult.ADDED) n++;
        }
        return n;
    }

    private void checkSets(Player p, RareData d) {
        EconomyService eco = Services.get(EconomyService.class);
        for (RelicSet s : cfg.sets.values()) {
            if (s.relics().isEmpty() || d.setsClaimed.contains(s.id()) || !d.catalogued.containsAll(s.relics())) continue;
            d.setsClaimed.add(s.id());
            if (s.tokens() > 0) eco.give(p.getUniqueId(), Currency.TOKENS, s.tokens(), "raredrops:collection");
            p.showTitle(Title.title(Text.mm(s.coloredName()), Text.mm("<gray>Collection complete")));
            p.playSound(Sound.sound(Key.key("ui.toast.challenge_complete"), Sound.Source.MASTER, 1f, 1f));
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<white>" + p.getName() + "</white> <gray>completed the "
                    + s.coloredName() + " <gray>collection!"));
            String reward = (s.sellBonus() > 0 ? "<green>+" + pct(s.sellBonus()) + " sell price</green> forever" : "")
                    + (s.tokens() > 0 ? (s.sellBonus() > 0 ? "<gray> and " : "") + eco.format(Currency.TOKENS, s.tokens()) : "");
            if (!reward.isEmpty()) Text.send(p, "<gray>Reward: " + reward);
        }
    }

    /** Permanent sell multiplier from completed collections (1.0 = none). */
    double sellMultiplier(UUID id) {
        RareData d = data.get(id);
        if (d == null) return 1.0;
        double bonus = 0;
        for (String s : d.setsClaimed) {
            RelicSet set = cfg.sets.get(s);
            if (set != null) bonus += set.sellBonus();
        }
        return 1.0 + bonus;
    }

    // ---- caches ----

    /** Opens caches from the stack in hand (one, or all of them). Returns how many were opened. */
    int openCaches(Player p, ItemStack stack, boolean all) {
        Drop drop = items.drop(stack);
        RareData d = data.get(p.getUniqueId());
        if (drop == null || drop.type() != Type.CACHE || d == null) return 0;
        int n = all ? stack.getAmount() : 1;
        stack.setAmount(stack.getAmount() - n);
        double scale = cacheScale(p.getUniqueId());
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        long credits = 0, tokens = 0;
        for (int i = 0; i < n; i++) {
            if (drop.creditsMax() > 0) credits += Math.round(rng.nextLong(drop.creditsMin(), drop.creditsMax() + 1) * scale);
            if (drop.tokensMax() > 0) tokens += rng.nextLong(drop.tokensMin(), drop.tokensMax() + 1);
        }
        EconomyService eco = Services.get(EconomyService.class);
        // Server-paid Credits use the income: prefix so they count toward this run's prestige payout.
        if (credits > 0) eco.give(p.getUniqueId(), Currency.CREDITS, credits, "income:salvage-cache");
        if (tokens > 0) eco.give(p.getUniqueId(), Currency.TOKENS, tokens, "raredrops:cache");
        d.cachesOpened += n;
        save(p.getUniqueId());
        p.playSound(Sound.sound(Key.key("block.chest.open"), Sound.Source.MASTER, 0.8f, 0.9f));
        p.playSound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.5f, 1.6f));
        String got = (credits > 0 ? eco.format(Currency.CREDITS, credits) : "")
                + (credits > 0 && tokens > 0 ? "<gray> and " : "") + (tokens > 0 ? eco.format(Currency.TOKENS, tokens) : "");
        Text.send(p, "<gray>Cracked " + (n > 1 ? "<white>" + n + "x</white> " : "") + drop.coloredName()
                + "<gray>: " + (got.isEmpty() ? "<dark_gray>empty" : got));
        return n;
    }

    /** Credits in caches grow with the opener's grade and prestige, like rank-up costs, so they never go stale. */
    double cacheScale(UUID id) {
        ProgressionService prog = Services.find(ProgressionService.class).orElse(null);
        if (prog == null) return 1.0;
        return Math.pow(cfg.cachePerGrade, Math.max(0, prog.grade(id) - 1)) * (1 + cfg.cachePerPrestige * prog.prestige(id));
    }

    // ---- fragments ----

    enum AssembleResult { OK, NOT_ENOUGH, NO_MODULES, NO_DRILL, NOT_LOADED }

    /** Consumes fragments of a rarity from the inventory and gives a random drill module of that rarity. */
    AssembleResult assemble(Player p, String rarity) {
        RareData d = data.get(p.getUniqueId());
        if (d == null) return AssembleResult.NOT_LOADED;
        DrillService drill = Services.find(DrillService.class).orElse(null);
        if (drill == null) return AssembleResult.NO_DRILL;
        Drop frag = cfg.fragmentFor(rarity);
        if (frag == null) return AssembleResult.NO_MODULES;
        int need = cfg.fragmentsNeeded(rarity);
        if (count(p, frag) < need) return AssembleResult.NOT_ENOUGH;
        Map<String, Double> pool = drill.moduleWeights(rarity);
        if (pool.isEmpty()) return AssembleResult.NO_MODULES;
        double total = pool.values().stream().mapToDouble(Double::doubleValue).sum();
        double r = ThreadLocalRandom.current().nextDouble() * total;
        String chosen = null;
        for (var e : pool.entrySet()) {
            r -= e.getValue();
            chosen = e.getKey();
            if (r <= 0) break;
        }
        ItemStack module = drill.createModuleItem(chosen);
        if (module == null) return AssembleResult.NO_MODULES;
        take(p, frag, need);
        for (ItemStack left : p.getInventory().addItem(module).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        d.modulesAssembled++;
        save(p.getUniqueId());
        Component name = module.effectiveName().hoverEvent(module.asHoverEvent());
        p.playSound(Sound.sound(Key.key("block.smithing_table.use"), Sound.Source.MASTER, 1f, 0.9f));
        p.playSound(Sound.sound(Key.key("ui.toast.challenge_complete"), Sound.Source.MASTER, 0.7f, 1.3f));
        p.showTitle(Title.title(module.effectiveName(), Text.mm("<gray>Module assembled")));
        if (java.util.Set.of("EPIC", "LEGENDARY").contains(rarity.toUpperCase(java.util.Locale.ROOT))) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<white>" + p.getName() + "</white> <gray>assembled ").append(name)
                    .append(Text.mm(" <gray>from blueprint fragments.")));
        } else {
            p.sendMessage(Text.mm(Text.PREFIX + "<gray>Assembled ").append(name));
        }
        return AssembleResult.OK;
    }

    int count(Player p, Drop drop) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && drop.id().equals(items.dropId(it))) n += it.getAmount();
        }
        return n;
    }

    private void take(Player p, Drop drop, int amount) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || !drop.id().equals(items.dropId(it))) continue;
            int n = Math.min(amount, it.getAmount());
            it.setAmount(it.getAmount() - n);
            amount -= n;
        }
        inv.setStorageContents(contents);
    }

    // ---- stash ----

    /** Moves stashed drops into the inventory as far as space allows. Returns how many moved. */
    long claimStash(Player p) {
        RareData d = data.get(p.getUniqueId());
        if (d == null || d.stash.isEmpty()) return 0;
        long moved = 0;
        var it = d.stash.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Drop drop = cfg.drop(e.getKey());
            if (drop == null) { it.remove(); continue; }
            long left = e.getValue();
            while (left > 0) {
                int n = (int) Math.min(64, left);
                Map<Integer, ItemStack> over = p.getInventory().addItem(items.create(drop, n));
                int overflow = over.values().stream().mapToInt(ItemStack::getAmount).sum();
                moved += n - overflow;
                left -= n - overflow;
                if (overflow > 0) break;
            }
            if (left <= 0) it.remove();
            else { e.setValue(left); break; }
        }
        save(p.getUniqueId());
        return moved;
    }

    // ---- helpers ----

    /** Number of successes in n trials of chance p, capped. Exact for small n, Poisson for large n. */
    static int successes(int n, double p, int cap) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (n <= 64) {
            int k = 0;
            for (int i = 0; i < n && k < cap; i++) if (rng.nextDouble() < p) k++;
            return k;
        }
        double lambda = n * p;
        double l = Math.exp(-Math.min(lambda, 50)), prod = 1;
        int k = -1;
        do {
            k++;
            prod *= rng.nextDouble();
        } while (prod > l && k < cap);
        return Math.min(k, cap);
    }

    static String pct(double f) {
        double v = f * 100;
        return (v == Math.rint(v) ? Long.toString(Math.round(v)) : String.format("%.1f", v)) + "%";
    }

    static String fmt(long v) { return Numbers.shortFormat(v); }
}
