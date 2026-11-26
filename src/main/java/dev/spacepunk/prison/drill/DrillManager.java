package dev.spacepunk.prison.drill;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.drill.DrillConfig.EnchantDef;
import dev.spacepunk.prison.drill.DrillConfig.FrameDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;
import dev.spacepunk.prison.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Owns per-player drill state, the drill item, and purchases. Main thread only. */
public final class DrillManager implements DrillService {
    public static final String DATA_KEY = "drill";
    private static final Gson GSON = new Gson();

    private final Logger log;
    private final DrillItems items;
    private DrillConfig cfg;
    private final Map<UUID, DrillData> data = new HashMap<>();
    private final Map<UUID, DrillMath.Effective> effective = new HashMap<>();
    /** Rod / Weapon Rig totals, by player then rig key; dropped on any change. */
    private final Map<UUID, Map<String, GearTotals>> gearTotals = new HashMap<>();

    private record GearTotals(int width, int height, ModuleRig.Totals totals) {}
    /** Players whose stats changed since the last flush to their profile. */
    private final Set<UUID> statsDirty = ConcurrentHashMap.newKeySet();

    public DrillManager(Logger log, DrillItems items, DrillConfig cfg) {
        this.log = log;
        this.items = items;
        this.cfg = cfg;
    }

    public DrillConfig cfg() { return cfg; }

    public DrillItems items() { return items; }

    public void config(DrillConfig cfg) {
        this.cfg = cfg;
        items.config(cfg);
        effective.clear();
        gearTotals.clear();
    }

    // ---- state ----

    public DrillData data(UUID id) { return data.get(id); }

    public DrillMath.Effective effective(UUID id) {
        DrillData d = data.get(id);
        if (d == null) return null;
        return effective.computeIfAbsent(id, k -> new DrillMath.Effective(cfg, d));
    }

    /** Loads (or creates) state from the profile. Returns true if this is a brand-new drill. */
    public boolean load(PlayerProfile profile) {
        String json = profile.moduleData(DATA_KEY);
        DrillData d = null;
        if (json != null) {
            try {
                d = GSON.fromJson(json, DrillData.class);
            } catch (JsonParseException e) {
                log.warning("Corrupt drill data for " + profile.lastName() + "; keeping a backup and starting fresh");
                profile.moduleData(DATA_KEY + "_corrupt", json);
            }
        }
        boolean fresh = d == null;
        if (fresh) d = new DrillData();
        d.normalize(cfg.firstFrame().id());
        data.put(profile.uuid(), d);
        effective.remove(profile.uuid());
        if (fresh) save(profile.uuid());
        return fresh;
    }

    public void unload(UUID id) {
        flush(id);
        data.remove(id);
        effective.remove(id);
        gearTotals.remove(id);
        statsDirty.remove(id);
    }

    /** Writes state into the profile now. */
    public void save(UUID id) {
        DrillData d = data.get(id);
        if (d == null) return;
        ProfileService ps = Services.get(ProfileService.class);
        ps.get(id).ifPresent(p -> {
            p.moduleData(DATA_KEY, GSON.toJson(d));
            ps.markDirty(id);
        });
        statsDirty.remove(id);
    }

    public void markStatsDirty(UUID id) { statsDirty.add(id); }

    public void flush(UUID id) {
        if (statsDirty.contains(id)) save(id);
    }

    public void flushAll() {
        for (UUID id : Set.copyOf(statsDirty)) save(id);
    }

    /** Call after any change that affects effects or the item. */
    public void changed(Player p) {
        effective.remove(p.getUniqueId());
        gearTotals.remove(p.getUniqueId());
        save(p.getUniqueId());
        refreshItem(p);
        applyExo(p);
    }

    // ---- the item ----

    /** Finds the player's own drill in their inventory, or -1. */
    public int drillSlot(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (p.getUniqueId().equals(items.owner(inv.getItem(i)))) return i;
        }
        return -1;
    }

    public boolean holdingOwnDrill(Player p) {
        return p.getUniqueId().equals(items.owner(p.getInventory().getItemInMainHand()));
    }

    /**
     * Makes sure the player has exactly one drill: removes duplicates and drills owned by someone else,
     * gives one if missing. Returns true if a drill had to be given.
     */
    public boolean ensureDrill(Player p) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return false;
        PlayerInventory inv = p.getInventory();
        boolean found = false;
        for (int i = 0; i < inv.getSize(); i++) {
            UUID owner = items.owner(inv.getItem(i));
            if (owner == null) continue;
            if (!owner.equals(p.getUniqueId()) || found) {
                inv.setItem(i, null);
                continue;
            }
            found = true;
            items.apply(inv.getItem(i), p.getUniqueId(), d);
        }
        if (found) return false;
        ItemStack drill = items.drill(p.getUniqueId(), d);
        if (inv.getItem(0) == null || inv.getItem(0).getType().isAir()) {
            inv.setItem(0, drill);
        } else {
            Map<Integer, ItemStack> left = inv.addItem(drill);
            if (!left.isEmpty()) {
                // Inventory full: the drill takes slot 0 and the old item drops at the player's feet.
                ItemStack displaced = inv.getItem(0);
                inv.setItem(0, drill);
                if (displaced != null) p.getWorld().dropItemNaturally(p.getLocation(), displaced);
            }
        }
        return true;
    }

    public void refreshItem(Player p) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return;
        int slot = drillSlot(p);
        if (slot < 0) return;
        ItemStack it = p.getInventory().getItem(slot);
        items.apply(it, p.getUniqueId(), d);
        p.getInventory().setItem(slot, it);
    }

    // ---- grade / prestige ----

    public int grade(Player p) {
        return Services.find(ProgressionService.class).map(s -> s.grade(p.getUniqueId()))
                .orElseGet(() -> Services.get(ProfileService.class).get(p.getUniqueId()).map(PlayerProfile::grade).orElse(1));
    }

    public int prestige(Player p) {
        return Services.find(ProgressionService.class).map(s -> s.prestige(p.getUniqueId()))
                .orElseGet(() -> Services.get(ProfileService.class).get(p.getUniqueId()).map(PlayerProfile::prestige).orElse(0));
    }

    // ---- purchases ----

    public record Quote(int levels, long cost) {}

    /** How many levels (up to want) the player can buy right now, and the total cost. */
    public Quote quote(Player p, Enchant e, int want) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return new Quote(0, 0);
        EnchantDef def = cfg.enchant(e);
        int cap = def.cap(cfg.frame(d.frame));
        long bal = Services.get(EconomyService.class).balance(p.getUniqueId(), Currency.TOKENS);
        int lvl = d.level(e);
        int n = 0;
        long total = 0;
        while (n < want && lvl + n < cap) {
            long c = def.cost(lvl + n + 1);
            if (total + c > bal) break;
            total += c;
            n++;
        }
        return new Quote(n, total);
    }

    /** Buys up to want levels. Returns the number bought (0 if none). */
    public int buy(Player p, Enchant e, int want) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return 0;
        Quote q = quote(p, e, want);
        if (q.levels() <= 0) return 0;
        if (!Services.get(EconomyService.class).take(p.getUniqueId(), Currency.TOKENS, q.cost(), "drill:enchant:" + e.id())) return 0;
        d.level(e, d.level(e) + q.levels());
        changed(p);
        return q.levels();
    }

    public enum FrameResult { OK, MAX, LOCKED, POOR }

    public FrameResult upgradeFrame(Player p) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return FrameResult.LOCKED;
        FrameDef next = cfg.nextFrame(cfg.frame(d.frame));
        if (next == null) return FrameResult.MAX;
        if (!next.unlockedFor(grade(p), prestige(p))) return FrameResult.LOCKED;
        if (next.cost() > 0 && !Services.get(EconomyService.class).take(p.getUniqueId(), next.costCurrency(), next.cost(), "drill:frame:" + next.id())) {
            return FrameResult.POOR;
        }
        d.frame = next.id();
        changed(p);
        return FrameResult.OK;
    }

    /** Admin: set a frame directly. Modules that no longer fit the rig go back to the player. */
    public void setFrame(Player p, FrameDef f) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return;
        d.frame = f.id();
        effective.remove(p.getUniqueId());
        ejectMisfits(p);
        changed(p);
    }

    // ---- module rig ----

    /** Gives back modules from the old socket system (version 1 data) as items. Returns how many. */
    public int migrateSockets(Player p) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null || d.sockets.isEmpty()) return 0;
        int n = 0;
        for (String id : d.sockets) {
            ModuleDef m = cfg.module(id);
            if (m == null) continue;
            give(p, items.module(m, 1));
            n++;
        }
        d.sockets.clear();
        d.version = 2;
        changed(p);
        return n;
    }

    /** Modules that no longer fit the rig (frame or config change) go back to the player as items. */
    public int ejectMisfits(Player p) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return 0;
        ModuleRig rig = ModuleRig.build(cfg, d);
        List<DrillData.Placed> keep = new ArrayList<>();
        int n = 0;
        for (DrillData.Placed pl : d.rig) {
            ModuleRig.Entry e = null;
            for (ModuleRig.Entry x : rig.entries) if (x.placed == pl) e = x;
            if (e == null) continue; // module removed from config: nothing to give back
            if ("Doesn't fit this rig".equals(e.problem)) {
                give(p, items.module(e.def, pl.mk));
                n++;
            } else {
                keep.add(pl);
            }
        }
        if (n > 0 || keep.size() != d.rig.size()) {
            d.rig = keep;
            changed(p);
        }
        return n;
    }

    // ---- rig keys: "drill", "rod", "gun:<gun id>" ----

    public static final String RIG_DRILL = "drill";
    public static final String RIG_ROD = "rod";
    public static final String RIG_EXO = "exo";

    public static String gunRigKey(String gunId) { return "gun:" + gunId; }

    /** Module kind a rig key takes. */
    public static String kindOf(String rigKey) {
        if (RIG_ROD.equals(rigKey)) return DrillConfig.KIND_ROD;
        if (RIG_EXO.equals(rigKey)) return DrillConfig.KIND_EXO;
        if (rigKey != null && rigKey.startsWith("gun:")) return DrillConfig.KIND_GUN;
        return DrillConfig.KIND_DRILL;
    }

    /** True for "drill", "rod" and "gun:<id>" of a gun with a Weapon Rig. */
    public boolean validRig(String rigKey) {
        if (RIG_DRILL.equals(rigKey) || RIG_ROD.equals(rigKey)) return true;
        if (RIG_EXO.equals(rigKey)) return !cfg.exoTiers.isEmpty();
        return rigKey != null && rigKey.startsWith("gun:") && cfg.rigGuns().contains(rigKey.substring(4));
    }

    /** Menu title of a rig. */
    public String rigTitle(String rigKey) {
        return switch (kindOf(rigKey)) {
            case DrillConfig.KIND_ROD -> "ROD RIG";
            case DrillConfig.KIND_EXO -> "EXO-RIG";
            case DrillConfig.KIND_GUN -> "WEAPON RIG · " + gunName(rigKey.substring(4)).toUpperCase(java.util.Locale.ROOT);
            default -> "MODULE RIG";
        };
    }

    /** "bolt_rifle" -> "Bolt Rifle". */
    public static String gunName(String gunId) {
        StringBuilder sb = new StringBuilder();
        for (String w : gunId.split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** The placements list a rig key stores into (created if missing). */
    List<DrillData.Placed> placements(DrillData d, String rigKey) {
        if (RIG_DRILL.equals(rigKey)) return d.rig;
        return d.gear.computeIfAbsent(rigKey, k -> new ArrayList<>());
    }

    public int rodTier(UUID id) {
        return Services.find(dev.spacepunk.prison.api.FishingService.class).map(f -> f.rodTier(id)).orElse(0);
    }

    /** The grid size of a non-drill rig for this player right now. */
    public DrillConfig.RigSize gearSize(UUID id, String rigKey) {
        if (RIG_EXO.equals(rigKey)) {
            DrillConfig.ExoTier t = exoTier(id);
            return t == null ? new DrillConfig.RigSize(0, 0, List.of()) : t.size();
        }
        return RIG_ROD.equals(rigKey) ? cfg.rodRig(rodTier(id)) : cfg.gunRig(rigKey.substring(4));
    }

    // ---- Exo-Rig ----

    /** The player's current Exo-Rig tier, or null if they have none yet. */
    public DrillConfig.ExoTier exoTier(UUID id) {
        DrillData d = data.get(id);
        if (d == null || d.exoTier <= 0 || cfg.exoTiers.isEmpty()) return null;
        return cfg.exoTiers.get(Math.min(d.exoTier, cfg.exoTiers.size()) - 1);
    }

    /** The next Exo-Rig tier to buy, or null at the top. */
    public DrillConfig.ExoTier nextExoTier(UUID id) {
        DrillData d = data.get(id);
        if (d == null) return null;
        return d.exoTier < cfg.exoTiers.size() ? cfg.exoTiers.get(d.exoTier) : null;
    }

    public enum ExoResult { OK, MAX, LOCKED, POOR }

    public ExoResult upgradeExo(Player p) {
        DrillData d = data.get(p.getUniqueId());
        DrillConfig.ExoTier next = nextExoTier(p.getUniqueId());
        if (d == null || next == null) return ExoResult.MAX;
        if (!next.unlockedFor(grade(p), prestige(p))) return ExoResult.LOCKED;
        if (next.cost() > 0 && !Services.get(EconomyService.class).take(p.getUniqueId(), next.currency(), next.cost(), "drill:exo:" + d.exoTier)) {
            return ExoResult.POOR;
        }
        d.exoTier++;
        changed(p);
        return ExoResult.OK;
    }

    private static final String EXO_NS = "spacepunk";

    /** Exo stat -> (attribute, how the multiplier becomes a modifier amount). */
    private record ExoAttr(String stat, org.bukkit.attribute.Attribute attribute, org.bukkit.attribute.AttributeModifier.Operation op, double scale) {}

    private static final List<ExoAttr> EXO_ATTRS = List.of(
            new ExoAttr("speed", org.bukkit.attribute.Attribute.MOVEMENT_SPEED, org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1, 1),
            new ExoAttr("health", org.bukkit.attribute.Attribute.MAX_HEALTH, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, 20),
            new ExoAttr("armor", org.bukkit.attribute.Attribute.ARMOR, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, 20),
            new ExoAttr("toughness", org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, 1),
            new ExoAttr("jump", org.bukkit.attribute.Attribute.JUMP_STRENGTH, org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1, 1),
            new ExoAttr("fall", org.bukkit.attribute.Attribute.FALL_DAMAGE_MULTIPLIER, org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1, 1));

    /** Puts the Exo-Rig's stats on the player as attribute modifiers (removing the old ones first). */
    public void applyExo(Player p) {
        Map<String, Double> m = gearTotals(p.getUniqueId(), RIG_EXO).mul();
        for (ExoAttr a : EXO_ATTRS) {
            org.bukkit.attribute.AttributeInstance inst = p.getAttribute(a.attribute());
            if (inst == null) continue;
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(EXO_NS, "exo_" + a.stat());
            for (org.bukkit.attribute.AttributeModifier old : List.copyOf(inst.getModifiers())) {
                if (key.equals(old.getKey())) inst.removeModifier(old);
            }
            double v = m.getOrDefault(a.stat(), 1.0);
            if (Math.abs(v - 1.0) < 1e-6) continue;
            inst.addModifier(new org.bukkit.attribute.AttributeModifier(key, (v - 1.0) * a.scale(), a.op(), org.bukkit.inventory.EquipmentSlotGroup.ANY));
        }
        org.bukkit.attribute.AttributeInstance hp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (hp != null && p.getHealth() > hp.getValue()) p.setHealth(hp.getValue());
    }

    /** Removes every Exo-Rig attribute modifier (plugin disable). */
    public static void clearExo(Player p) {
        for (ExoAttr a : EXO_ATTRS) {
            org.bukkit.attribute.AttributeInstance inst = p.getAttribute(a.attribute());
            if (inst == null) continue;
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(EXO_NS, "exo_" + a.stat());
            for (org.bukkit.attribute.AttributeModifier old : List.copyOf(inst.getModifiers())) {
                if (key.equals(old.getKey())) inst.removeModifier(old);
            }
        }
    }

    /** The gadget the Exo-Rig runs (its "gadget-*" flag), or null. */
    public String gadget(UUID id) {
        for (String f : gearTotals(id, RIG_EXO).flags()) if (f.startsWith("gadget-")) return f;
        return null;
    }

    /** Builds a rig for this player (null if their data isn't loaded). */
    public ModuleRig rig(UUID id, String rigKey) {
        DrillData d = data.get(id);
        if (d == null) return null;
        if (RIG_DRILL.equals(rigKey)) {
            DrillMath.Effective e = effective(id);
            return e == null ? null : e.rig;
        }
        DrillConfig.RigSize size = gearSize(id, rigKey);
        return ModuleRig.build(cfg, kindOf(rigKey), size.width(), size.height(), size.core(), placements(d, rigKey));
    }

    /** Stat multipliers of a Rod or Weapon Rig (empty if none or not loaded). Cached until the rig changes. */
    public ModuleRig.Totals gearTotals(UUID id, String rigKey) {
        DrillData d = data.get(id);
        if (d == null || RIG_DRILL.equals(rigKey) || !validRig(rigKey)) return new ModuleRig.Totals(Map.of(), Set.of());
        List<DrillData.Placed> list = d.gear.get(rigKey);
        if (list == null || list.isEmpty()) return new ModuleRig.Totals(Map.of(), Set.of());
        DrillConfig.RigSize size = gearSize(id, rigKey);
        Map<String, GearTotals> mine = gearTotals.computeIfAbsent(id, k -> new HashMap<>());
        GearTotals cached = mine.get(rigKey);
        if (cached != null && cached.width() == size.width() && cached.height() == size.height()) return cached.totals();
        ModuleRig.Totals t = rig(id, rigKey).totals(cfg);
        mine.put(rigKey, new GearTotals(size.width(), size.height(), t));
        return t;
    }

    /** Fits a module on the drill rig. */
    public String place(Player p, ModuleDef m, int mk, int x, int y, int rot) { return place(p, RIG_DRILL, m, mk, x, y, rot); }

    /** Fits a module with its pivot on (x, y). Returns null on success, else why not. Does not touch the item. */
    public String place(Player p, String rigKey, ModuleDef m, int mk, int x, int y, int rot) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return "Your drill is still loading.";
        ModuleRig rig = rig(p.getUniqueId(), rigKey);
        if (rig == null) return "Your drill is still loading.";
        String why = rig.canPlace(cfg, m, x, y, rot, -1);
        if (why != null) return why;
        Set<String> combosBefore = activeComboIds(rig);
        placements(d, rigKey).add(new DrillData.Placed(m.id(), Math.max(1, Math.min(cfg.maxMark, mk)), x, y, Math.floorMod(rot, 4)));
        changed(p);
        announceCombos(p, d, rigKey, combosBefore);
        return null;
    }

    /** Takes a module off the drill rig. */
    public ItemStack takeOut(Player p, int entryIndex) { return takeOut(p, RIG_DRILL, entryIndex); }

    /** Takes a fitted module off a rig and returns it as an item (or null if the index is wrong). */
    public ItemStack takeOut(Player p, String rigKey, int entryIndex) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return null;
        ModuleRig rig = rig(p.getUniqueId(), rigKey);
        if (rig == null || entryIndex < 0 || entryIndex >= rig.entries.size()) return null;
        ModuleRig.Entry e = rig.entries.get(entryIndex);
        placements(d, rigKey).remove(e.placed);
        changed(p);
        return items.module(e.def, e.placed.mk);
    }

    /** Number of modules placed on a rig (including ones that are offline or don't fit). */
    public int placedCount(UUID id, String rigKey) {
        DrillData d = data.get(id);
        if (d == null) return 0;
        if (RIG_DRILL.equals(rigKey)) return d.rig.size();
        List<DrillData.Placed> l = d.gear.get(rigKey);
        return l == null ? 0 : l.size();
    }

    // ---- module finds outside mining ----

    /** A random module of a kind, weighted, or null if none. */
    public ModuleDef randomModule(String kind) {
        double total = 0;
        for (ModuleDef m : cfg.modules.values()) if (m.kind().equals(kind)) total += m.weight();
        if (total <= 0) return null;
        double r = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * total;
        for (ModuleDef m : cfg.modules.values()) {
            if (!m.kind().equals(kind)) continue;
            r -= m.weight();
            if (r < 0) return m;
        }
        return null;
    }

    /** Rolls a module find of a kind (chance from drill.yml module-drops "kind.source"). Returns the module found, or null. */
    public ModuleDef rollFind(Player p, String kind, String source) {
        double chance = cfg.moduleDrops.getOrDefault(kind + "." + source, 0.0);
        if (chance <= 0 || java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) return null;
        ModuleDef m = randomModule(kind);
        if (m == null) return null;
        give(p, items.module(m, 1));
        DrillData d = data.get(p.getUniqueId());
        if (d != null) {
            d.modulesFound++;
            markStatsDirty(p.getUniqueId());
        }
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        String where = switch (kind) {
            case DrillConfig.KIND_ROD -> "/rig rod";
            case DrillConfig.KIND_GUN -> "/rig gun";
            case DrillConfig.KIND_EXO -> "/rig exo";
            default -> "/rig";
        };
        p.showTitle(net.kyori.adventure.title.Title.title(Text.mm(m.coloredName()),
                Text.mm("<gray>" + capital(DrillConfig.kindLabel(kind)) + " found! Fit it with <white>" + where)));
        if (cfg.moduleBroadcast && m.rarity().ordinal() >= DrillConfig.Rarity.RARE.ordinal()) {
            org.bukkit.Bukkit.broadcast(Text.mm(Text.PREFIX + "<white>" + p.getName() + "</white> <gray>found a "
                    + m.rarity().color + m.rarity().label + "</" + m.rarity().color.substring(1) + " <gray>" + DrillConfig.kindLabel(kind) + ": " + m.coloredName()));
        } else {
            Text.send(p, "<gray>You found a " + DrillConfig.kindLabel(kind) + ": " + m.coloredName());
        }
        return m;
    }

    private static String capital(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static Set<String> activeComboIds(ModuleRig rig) {
        Set<String> out = new java.util.HashSet<>();
        for (DrillConfig.ComboDef c : rig.activeCombos) out.add(c.id());
        return out;
    }

    private void announceCombos(Player p, DrillData d, String rigKey, Set<String> before) {
        ModuleRig rig = rig(p.getUniqueId(), rigKey);
        if (rig == null) return;
        for (DrillConfig.ComboDef c : rig.activeCombos) {
            if (before.contains(c.id())) continue;
            boolean first = d.combos.add(c.id());
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
            if (first) {
                p.showTitle(net.kyori.adventure.title.Title.title(Text.mm("<gold>" + c.name()), Text.mm("<gray>Hidden module combo discovered!")));
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
                org.bukkit.Bukkit.broadcast(Text.mm(Text.PREFIX + "<white>" + p.getName() + "</white> <gray>discovered the hidden module combo <gold>"
                        + c.name() + "</gold><gray>."));
                save(p.getUniqueId());
            }
            Text.send(p, "<gold>✦ " + c.name() + "</gold> <gray>online: " + c.lore());
        }
    }

    // ---- fusing ----

    public record FuseCost(String partId, String state, int amount) {}

    /** Parts needed to fuse two modules of this mark into the next one (amount 0 = none). */
    public FuseCost fuseCost(ModuleDef m, int mk) {
        DrillConfig.PartCost pc = cfg.fuseParts.get(mk + 1);
        if (pc == null || pc.amount() <= 0 || m.corePart() == null) return new FuseCost(m.corePart(), "WORKING", 0);
        return new FuseCost(m.corePart(), pc.state(), pc.amount());
    }

    /** Fuses two modules of (id, mk) from the inventory into one of mk + 1. Returns null on success, else why not. */
    public String fuse(Player p, ModuleDef m, int mk) {
        if (mk >= cfg.maxMark) return m.name() + " is already at the top mark.";
        PlayerInventory inv = p.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize() && slots.size() < 2; i++) {
            ItemStack it = inv.getItem(i);
            if (m.id().equals(items.moduleId(it)) && items.moduleMark(it) == mk) slots.add(i);
        }
        if (slots.size() < 2) return "You need two " + m.name() + " " + DrillMath.roman(mk) + " in your inventory.";
        FuseCost cost = fuseCost(m, mk);
        dev.spacepunk.prison.api.ScrapService scrap = Services.find(dev.spacepunk.prison.api.ScrapService.class).orElse(null);
        if (cost.amount() > 0) {
            if (scrap == null) return "Fusing to " + DrillMath.roman(mk + 1) + " needs scrap parts, which aren't available right now.";
            dev.spacepunk.prison.api.ScrapService.State st = dev.spacepunk.prison.api.ScrapService.State.parse(cost.state());
            if (scrap.count(p, cost.partId(), st) < cost.amount()) {
                return "Fusing to " + DrillMath.roman(mk + 1) + " needs " + cost.amount() + "x " + scrap.partName(cost.partId(), st) + "<red>.";
            }
            scrap.take(p, cost.partId(), st, cost.amount());
        }
        for (int slot : slots) inv.setItem(slot, null);
        give(p, items.module(m, mk + 1));
        DrillData d = data.get(p.getUniqueId());
        if (d != null) {
            d.modulesFused++;
            markStatsDirty(p.getUniqueId());
        }
        return null;
    }

    // ---- schematics ----

    public boolean knows(UUID id, String moduleId) {
        DrillData d = data.get(id);
        return d != null && (d.schematics.contains(moduleId) || cfg.startingSchematics.contains(moduleId));
    }

    /** Learns a schematic. Returns false if already known or unknown module. */
    public boolean learn(UUID id, String moduleId) {
        DrillData d = data.get(id);
        if (d == null || cfg.module(moduleId) == null || knows(id, moduleId)) return false;
        d.schematics.add(moduleId);
        save(id);
        return true;
    }

    // ---- rig expansion ----

    public enum ExpandResult { OK, MAX, FRAME, POOR }

    /** The next rig expansion, or null if all are bought. */
    public DrillConfig.ExpansionDef nextExpansion(DrillData d) {
        return d.expansion < cfg.expansions.size() ? cfg.expansions.get(d.expansion) : null;
    }

    public ExpandResult expand(Player p) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return ExpandResult.MAX;
        DrillConfig.ExpansionDef next = nextExpansion(d);
        if (next == null) return ExpandResult.MAX;
        FrameDef f = cfg.frame(d.frame);
        FrameDef min = next.frame().isEmpty() ? null : cfg.frame(next.frame());
        if (!f.hasRig() || (min != null && f.index() < min.index())) return ExpandResult.FRAME;
        if (next.shards() > 0 && !Services.get(EconomyService.class).take(p.getUniqueId(), Currency.SHARDS, next.shards(), "drill:rig-expansion")) {
            return ExpandResult.POOR;
        }
        d.expansion++;
        changed(p);
        return ExpandResult.OK;
    }

    public void give(Player p, ItemStack item) {
        for (ItemStack left : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    // ---- DrillService ----

    @Override public boolean autoSell(UUID player) {
        DrillMath.Effective e = effective(player);
        return e != null && e.autoSell;
    }

    @Override public double sellMultiplier(UUID player) {
        DrillMath.Effective e = effective(player);
        return (e == null ? 1.0 : e.sellMultiplier) * gearTotals(player, RIG_EXO).m("sell");
    }

    @Override public double rareDropMultiplier(UUID player) {
        DrillMath.Effective e = effective(player);
        return e == null ? 1.0 : e.rareMultiplier;
    }

    @Override public int enchantLevel(UUID player, String enchantId) {
        DrillData d = data.get(player);
        Enchant e = Enchant.byId(enchantId);
        return d == null || e == null ? 0 : d.level(e);
    }

    @Override public String frameId(UUID player) {
        DrillData d = data.get(player);
        return d == null ? cfg.firstFrame().id() : cfg.frame(d.frame).id();
    }

    @Override public boolean isDrill(ItemStack item) { return items.isDrill(item); }

    @Override public java.util.Map<String, Double> moduleWeights(String rarity) {
        java.util.Map<String, Double> out = new java.util.LinkedHashMap<>();
        for (ModuleDef m : cfg.modules.values()) {
            if (m.rarity().name().equalsIgnoreCase(rarity) && m.weight() > 0) out.put(m.id(), m.weight());
        }
        return out;
    }

    @Override public ItemStack createModuleItem(String moduleId) { return createModuleItem(moduleId, 1); }

    @Override public ItemStack createModuleItem(String moduleId, int mark) {
        ModuleDef m = cfg.module(moduleId);
        return m == null ? null : items.module(m, mark);
    }

    @Override public java.util.List<String> moduleIds() { return java.util.List.copyOf(cfg.modules.keySet()); }

    @Override public String moduleName(String moduleId) {
        ModuleDef m = cfg.module(moduleId);
        return m == null ? moduleId : m.coloredName();
    }

    @Override public String moduleRarity(String moduleId) {
        ModuleDef m = cfg.module(moduleId);
        return m == null ? null : m.rarity().name();
    }

    @Override public boolean knowsSchematic(UUID player, String moduleId) { return knows(player, moduleId); }

    @Override public boolean learnSchematic(UUID player, String moduleId) { return learn(player, moduleId); }

    @Override public String learnRandomSchematic(UUID player, String rarity) {
        java.util.List<String> pool = new java.util.ArrayList<>();
        double total = 0;
        for (ModuleDef m : cfg.modules.values()) {
            if (m.rarity().name().equalsIgnoreCase(rarity) && m.weight() > 0 && !knows(player, m.id())) {
                pool.add(m.id());
                total += m.weight();
            }
        }
        if (pool.isEmpty()) return null;
        double r = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * total;
        String chosen = pool.getLast();
        for (String id : pool) {
            r -= cfg.module(id).weight();
            if (r <= 0) { chosen = id; break; }
        }
        return learn(player, chosen) ? chosen : null;
    }

    @Override public double scrapMultiplier(UUID player) {
        DrillMath.Effective e = effective(player);
        return e == null ? 1.0 : e.scrapMultiplier;
    }

    @Override public java.util.Map<String, Double> gearMultipliers(UUID player, String rigKey) {
        return gearTotals(player, rigKey).mul();
    }

    private RigMenu rigMenu;

    void rigMenu(RigMenu menu) { this.rigMenu = menu; }

    @Override public void rollModuleFind(Player player, String kind, String source) { rollFind(player, kind, source); }

    @Override public void openRig(Player player, String rigKey) {
        if (rigMenu != null) rigMenu.open(player, validRig(rigKey) ? rigKey : RIG_DRILL, -1);
    }

    @Override public String moduleId(ItemStack item) { return items.moduleId(item); }

    @Override public int moduleMark(ItemStack item) { return items.moduleMark(item); }

    @Override public boolean isModule(ItemStack item) { return items.moduleId(item) != null; }
}
