package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.api.Currency;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Parsed drill.yml. Immutable; a reload builds a new one. */
public final class DrillConfig {
    public final Material itemMaterial;
    public final boolean useItemModels;

    public final double tokenHandChance;
    public final int tokenMin;
    public final int tokenMax;
    public final double tokenEnchantRate;
    /** Token drop amounts (base find, Siphon, Salvage Sense caches) x this per mine grade above 1. */
    public final double tokenGradeGrowth;
    /** Token drop amounts x this per prestige level. */
    public final double tokenPrestigeGrowth;

    public final double moduleChance;
    public final boolean moduleBroadcast;

    public final List<FrameDef> frames;
    private final Map<String, FrameDef> framesById;
    private final EnumMap<Enchant, EnchantDef> enchants = new EnumMap<>(Enchant.class);
    public final Map<String, ModuleDef> modules;
    public final Map<String, FamilyDef> families;
    public final Map<String, ComboDef> combos;
    /** Module marks (Mk I..max): pros are raised to proPower[mk], cons to conPower[mk]. Index 0 unused. */
    public final int maxMark;
    private final double[] proPower;
    private final double[] conPower;
    /** Parts needed to fuse two modules into the given mark (key = resulting mark). */
    public final Map<Integer, PartCost> fuseParts;
    public final List<ExpansionDef> expansions;
    public final List<String> startingSchematics;
    /** Rod Rig size per rod tier (fishing.yml rod.tiers order; tiers past the list keep the last one). */
    public final List<RigSize> rodRigs;
    /** Weapon Rig size per gun id ("default" for guns not listed). */
    public final Map<String, RigSize> gunRigs;
    /** Chance per event to find a random module of a kind: "rod.fish", "rod.sea-creature", "gun.undercroft-kill", "gun.smuggle". */
    public final Map<String, Double> moduleDrops;
    /** Exo-Rig tiers, bought in order (index 0 = the first tier; players start with none). */
    public final List<ExoTier> exoTiers;
    /** Seconds between gadget uses, by gadget flag ("gadget-dash"). */
    public final Map<String, Double> gadgetCooldowns;

    public final Heat heat;

    private DrillConfig(YamlConfiguration y, Logger log) {
        itemMaterial = material(y.getString("item.material"), Material.NETHERITE_PICKAXE, log);
        useItemModels = y.getBoolean("item.use-item-models", true);

        tokenHandChance = y.getDouble("tokens.hand-chance", 0.006);
        tokenMin = y.getInt("tokens.min", 4);
        tokenMax = Math.max(tokenMin, y.getInt("tokens.max", 7));
        tokenEnchantRate = y.getDouble("tokens.enchant-block-rate", 0.05);
        tokenGradeGrowth = Math.max(1.0, y.getDouble("tokens.grade-growth", 1.10));
        tokenPrestigeGrowth = Math.max(1.0, y.getDouble("tokens.prestige-growth", 1.15));
        dev.spacepunk.prison.api.TokenDrops.configure(tokenGradeGrowth, tokenPrestigeGrowth);

        moduleChance = y.getDouble("salvage.module-chance", 0.00002);
        moduleBroadcast = y.getBoolean("salvage.broadcast", true);

        List<FrameDef> fl = new ArrayList<>();
        ConfigurationSection fs = y.getConfigurationSection("frames");
        if (fs != null) {
            int i = 0;
            for (String id : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(id);
                if (s == null) continue;
                Currency cur;
                try {
                    cur = Currency.valueOf(s.getString("cost-currency", "TOKENS").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    log.warning("drill.yml: frame " + id + " has an unknown cost-currency; using TOKENS");
                    cur = Currency.TOKENS;
                }
                fl.add(new FrameDef(id, i++, s.getString("name", id), s.getString("color", "#aaaaaa"),
                        s.getInt("grade", 1), s.getInt("prestige", 0), cur, s.getLong("cost", 0),
                        s.getInt("sockets", 0), s.getDouble("base-speed", 0.5), s.getDouble("heat-capacity", 0),
                        s.getString("lore", ""), s.getInt("rig.width", 0), s.getInt("rig.height", 0), cells(s.getList("rig.core"))));
            }
        }
        if (fl.isEmpty()) {
            log.warning("drill.yml: no frames defined; using a single default frame");
            fl.add(new FrameDef("rusted", 0, "Rusted", "#8a6a55", 1, 0, Currency.TOKENS, 0, 0, 0.5, 0, "", 0, 0, List.of()));
        }
        frames = List.copyOf(fl);
        Map<String, FrameDef> byId = new LinkedHashMap<>();
        for (FrameDef f : frames) byId.put(f.id(), f);
        framesById = Collections.unmodifiableMap(byId);

        ConfigurationSection es = y.getConfigurationSection("enchants");
        for (Enchant e : Enchant.values()) {
            ConfigurationSection s = es == null ? null : es.getConfigurationSection(e.id());
            if (s == null) {
                log.warning("drill.yml: enchant " + e.id() + " missing; it is disabled");
                enchants.put(e, new EnchantDef(e, e.id(), "#aaaaaa", Material.BARRIER, "", 1, 1,
                        new int[frames.size()], Map.of()));
                continue;
            }
            List<Integer> caps = s.getIntegerList("max-level");
            int[] capArr = new int[frames.size()];
            for (int i = 0; i < capArr.length; i++) {
                // Frames beyond the list keep the last listed cap.
                capArr[i] = caps.isEmpty() ? 0 : caps.get(Math.min(i, caps.size() - 1));
            }
            Map<String, Double> params = new HashMap<>();
            for (String k : s.getKeys(false)) {
                if (s.isDouble(k) || s.isInt(k) || s.isLong(k)) params.put(k, s.getDouble(k));
            }
            enchants.put(e, new EnchantDef(e, s.getString("name", e.id()), s.getString("color", "#aaaaaa"),
                    material(s.getString("icon"), Material.PAPER, log), s.getString("description", ""),
                    s.getDouble("base", 100), s.getDouble("growth", 1.1), capArr, Map.copyOf(params)));
        }

        Map<String, FamilyDef> fam = new LinkedHashMap<>();
        ConfigurationSection fms = y.getConfigurationSection("families");
        if (fms != null) {
            for (String id : fms.getKeys(false)) {
                ConfigurationSection s = fms.getConfigurationSection(id);
                if (s == null) continue;
                Map<Integer, Map<String, Double>> bonuses = new java.util.TreeMap<>();
                ConfigurationSection bs = s.getConfigurationSection("bonuses");
                if (bs != null) {
                    for (String k : bs.getKeys(false)) {
                        try {
                            bonuses.put(Integer.parseInt(k), numbers(bs.getConfigurationSection(k)));
                        } catch (NumberFormatException ignored) {
                            log.warning("drill.yml: family " + id + " bonus key " + k + " is not a number");
                        }
                    }
                }
                fam.put(id.toLowerCase(Locale.ROOT), new FamilyDef(id.toLowerCase(Locale.ROOT), s.getString("name", id),
                        s.getString("color", "#aaaaaa"), s.getInt("max", 99), s.getString("lore", ""),
                        Collections.unmodifiableMap(bonuses)));
            }
        }
        families = Collections.unmodifiableMap(fam);

        Map<String, ModuleDef> ml = new LinkedHashMap<>();
        ConfigurationSection ms = y.getConfigurationSection("modules");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(id);
                if (s == null) continue;
                Rarity r;
                try {
                    r = Rarity.valueOf(s.getString("rarity", "COMMON").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    r = Rarity.COMMON;
                }
                Set<String> flags = new java.util.HashSet<>();
                Map<String, Double> pros = effects(s.getConfigurationSection("pros"), flags);
                Map<String, Double> cons = effects(s.getConfigurationSection("cons"), flags);
                String family = s.getString("family", "").toLowerCase(Locale.ROOT);
                if (!family.isEmpty() && !fam.containsKey(family)) log.warning("drill.yml: module " + id + " has unknown family " + family);
                ml.put(id, new ModuleDef(id, s.getString("name", id), r, Math.max(0, s.getDouble("weight", 10)),
                        material(s.getString("material"), Material.PRISMARINE_SHARD, log), s.getString("model", id), s.getString("lore", ""),
                        family, ModuleShape.parse(s.getString("shape"), ModuleShape.DOT), pros, cons, Set.copyOf(flags),
                        s.getBoolean("rules.edge", false), lower(s.getString("rules.needs")), lower(s.getString("rules.avoid")),
                        Math.max(1.0, s.getDouble("rules.drain", 1.0)), lower(s.getString("core-part")),
                        kind(s.getString("kind"), id, log)));
            }
        }
        modules = Collections.unmodifiableMap(ml);

        Map<String, ComboDef> cl = new LinkedHashMap<>();
        ConfigurationSection cs = y.getConfigurationSection("combos");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                List<String> pair = s.getStringList("modules");
                if (pair.size() != 2) {
                    log.warning("drill.yml: combo " + id + " needs exactly two modules");
                    continue;
                }
                Set<String> none = new java.util.HashSet<>();
                cl.put(id, new ComboDef(id, s.getString("name", id), pair.get(0), pair.get(1), s.getString("lore", ""),
                        effects(s.getConfigurationSection("effects"), none), Set.copyOf(s.getStringList("clears"))));
            }
        }
        combos = Collections.unmodifiableMap(cl);

        List<Double> pe = y.getDoubleList("module-marks.pro-power");
        List<Double> ce = y.getDoubleList("module-marks.con-power");
        maxMark = Math.max(1, y.getInt("module-marks.max", 5));
        proPower = new double[maxMark + 1];
        conPower = new double[maxMark + 1];
        for (int mk = 1; mk <= maxMark; mk++) {
            proPower[mk] = pe.isEmpty() ? 1 + 0.2 * (mk - 1) : pe.get(Math.min(mk - 1, pe.size() - 1));
            conPower[mk] = ce.isEmpty() ? Math.max(0.3, 1 - 0.15 * (mk - 1)) : ce.get(Math.min(mk - 1, ce.size() - 1));
        }
        Map<Integer, PartCost> mc = new java.util.TreeMap<>();
        ConfigurationSection mcs = y.getConfigurationSection("module-marks.fuse-parts");
        if (mcs != null) {
            for (String k : mcs.getKeys(false)) {
                ConfigurationSection s = mcs.getConfigurationSection(k);
                if (s == null) continue;
                try {
                    mc.put(Integer.parseInt(k), new PartCost(s.getString("state", "WORKING").toUpperCase(Locale.ROOT), Math.max(0, s.getInt("amount", 0))));
                } catch (NumberFormatException ignored) {
                    log.warning("drill.yml: module-marks.fuse-parts key " + k + " is not a number");
                }
            }
        }
        fuseParts = Collections.unmodifiableMap(mc);

        List<ExpansionDef> ex = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("rig-expansion")) {
            ex.add(new ExpansionDef(num(m.get("shards"), 0).longValue(), Math.max(1, num(m.get("columns"), 1).intValue()),
                    String.valueOf(m.get("frame") == null ? "" : m.get("frame"))));
        }
        expansions = List.copyOf(ex);
        startingSchematics = List.copyOf(y.getStringList("starting-schematics"));

        List<RigSize> rr = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("gear-rigs.rod")) rr.add(rigSize(m));
        if (rr.isEmpty()) rr.add(new RigSize(2, 2, List.of()));
        rodRigs = List.copyOf(rr);
        Map<String, RigSize> gr = new LinkedHashMap<>();
        ConfigurationSection gs = y.getConfigurationSection("gear-rigs.gun");
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection s = gs.getConfigurationSection(id);
                if (s == null) continue;
                gr.put(id, new RigSize(Math.max(1, Math.min(7, s.getInt("width", 2))), Math.max(1, Math.min(4, s.getInt("height", 2))),
                        cells(s.getList("core"))));
            }
        }
        gunRigs = Collections.unmodifiableMap(gr);
        Map<String, Double> md = new LinkedHashMap<>();
        ConfigurationSection mds = y.getConfigurationSection("module-drops");
        if (mds != null) {
            for (String kind : mds.getKeys(false)) {
                ConfigurationSection s = mds.getConfigurationSection(kind);
                if (s == null) continue;
                for (String k : s.getKeys(false)) md.put(kind + "." + k, Math.max(0, s.getDouble(k)));
            }
        }
        moduleDrops = Collections.unmodifiableMap(md);

        List<ExoTier> et = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("exo-rig.tiers")) {
            Currency cur;
            try {
                cur = Currency.valueOf(String.valueOf(m.get("currency") == null ? "CREDITS" : m.get("currency")).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                cur = Currency.CREDITS;
            }
            RigSize size = rigSize(m);
            et.add(new ExoTier(String.valueOf(m.get("name") == null ? "Exo-Rig" : m.get("name")), num(m.get("grade"), 1).intValue(),
                    num(m.get("prestige"), 0).intValue(), cur, num(m.get("cost"), 0).longValue(), size));
        }
        exoTiers = List.copyOf(et);
        Map<String, Double> gc = new LinkedHashMap<>();
        ConfigurationSection gcs = y.getConfigurationSection("exo-rig.gadget-cooldowns");
        if (gcs != null) for (String k : gcs.getKeys(false)) gc.put(k, Math.max(1, gcs.getDouble(k)));
        gadgetCooldowns = Collections.unmodifiableMap(gc);

        List<Double> deck = y.getDoubleList("heat.deck-multiplier");
        heat = new Heat(
                y.getDouble("heat.base-capacity", 100),
                y.getDouble("heat.hand-block", 0.35),
                y.getDouble("heat.decay-per-second", 12),
                y.getDouble("heat.active-decay-per-second", 0.6),
                y.getDouble("heat.decay-delay", 1.5),
                deck.isEmpty() ? List.of(1.0) : List.copyOf(deck),
                y.getDouble("heat.overheat.stall-seconds", 3),
                y.getDouble("heat.overheat.heat-after", 0.4),
                y.getDouble("heat.vent.min-fraction", 0.3),
                y.getDouble("heat.vent.cooldown-seconds", 6),
                y.getDouble("heat.vent.burst-seconds-base", 2),
                y.getDouble("heat.vent.burst-seconds-per-fraction", 4),
                y.getDouble("heat.vent.burst-speed", 0.4),
                y.getDouble("heat.vent.perfect-min", 0.75),
                y.getDouble("heat.vent.perfect-max", 0.95),
                y.getDouble("heat.vent.perfect-multiplier", 2));
    }

    public static DrillConfig load(YamlConfiguration yaml, Logger log) { return new DrillConfig(yaml, log); }

    private static Material material(String name, Material fallback, Logger log) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name);
        if (m == null) {
            log.warning("drill.yml: unknown material " + name + "; using " + fallback);
            return fallback;
        }
        return m;
    }

    private static Map<String, Double> numbers(ConfigurationSection s) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (s == null) return out;
        for (String k : s.getKeys(false)) if (s.isDouble(k) || s.isInt(k) || s.isLong(k)) out.put(k, s.getDouble(k));
        return Collections.unmodifiableMap(out);
    }

    /** Numeric entries become multipliers; true booleans become flags. */
    private static Map<String, Double> effects(ConfigurationSection s, Set<String> flags) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            if (s.isBoolean(k)) {
                if (s.getBoolean(k)) flags.add(k);
            } else if (s.isDouble(k) || s.isInt(k) || s.isLong(k)) {
                out.put(k, s.getDouble(k));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<int[]> cells(List<?> raw) {
        List<int[]> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object o : raw) {
            if (o instanceof List<?> l && l.size() >= 2) out.add(new int[]{num(l.get(0), 0).intValue(), num(l.get(1), 0).intValue()});
        }
        return List.copyOf(out);
    }

    private static Number num(Object o, Number def) {
        if (o instanceof Number n) return n;
        if (o instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static RigSize rigSize(Map<?, ?> m) {
        int w = Math.max(1, Math.min(7, num(m.get("width"), 2).intValue()));
        int h = Math.max(1, Math.min(4, num(m.get("height"), 2).intValue()));
        return new RigSize(w, h, m.get("core") instanceof List<?> l ? cells(l) : List.<int[]>of());
    }

    private static String kind(String raw, String id, Logger log) {
        String k = raw == null ? KIND_DRILL : raw.trim().toLowerCase(Locale.ROOT);
        if (!k.equals(KIND_DRILL) && !k.equals(KIND_ROD) && !k.equals(KIND_GUN) && !k.equals(KIND_EXO)) {
            log.warning("drill.yml: module " + id + " has unknown kind " + raw + "; using drill");
            return KIND_DRILL;
        }
        return k;
    }

    private static String lower(String s) { return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT); }

    public FrameDef frame(String id) {
        FrameDef f = framesById.get(id);
        return f != null ? f : frames.getFirst();
    }

    public FrameDef firstFrame() { return frames.getFirst(); }

    /** Next frame after this one, or null at the top. */
    public FrameDef nextFrame(FrameDef f) { return f.index() + 1 < frames.size() ? frames.get(f.index() + 1) : null; }

    public EnchantDef enchant(Enchant e) { return enchants.get(e); }

    public ModuleDef module(String id) { return id == null ? null : modules.get(id); }

    // ---- definitions ----

    /** Module kinds: which rig a module fits. */
    public static final String KIND_DRILL = "drill";
    public static final String KIND_ROD = "rod";
    public static final String KIND_GUN = "gun";
    public static final String KIND_EXO = "exo";

    /** Player-facing name of a kind's modules ("drill module"). */
    public static String kindLabel(String kind) {
        return switch (kind) {
            case KIND_ROD -> "rod module";
            case KIND_GUN -> "weapon module";
            case KIND_EXO -> "exo module";
            default -> "drill module";
        };
    }

    /** An Exo-Rig tier: requirement, price and grid. */
    public record ExoTier(String name, int grade, int prestige, Currency currency, long cost, RigSize size) {
        public boolean unlockedFor(int playerGrade, int playerPrestige) {
            return playerPrestige > prestige || (playerPrestige == prestige && playerGrade >= grade);
        }

        public String requirementText() {
            if (prestige <= 0) return "Grade " + grade;
            if (grade <= 1) return "Prestige " + prestige;
            return "Prestige " + prestige + ", Grade " + grade;
        }
    }

    /** A gear rig's grid. core = Legendary-only cells. */
    public record RigSize(int width, int height, List<int[]> core) {}

    /** Rod Rig size for a rod tier index. */
    public RigSize rodRig(int tier) { return rodRigs.get(Math.max(0, Math.min(rodRigs.size() - 1, tier))); }

    /** Weapon Rig size for a gun id. */
    public RigSize gunRig(String gunId) {
        RigSize r = gunRigs.get(gunId);
        if (r == null) r = gunRigs.get("default");
        return r == null ? new RigSize(2, 2, List.of()) : r;
    }

    /** Guns with their own listed Weapon Rig (tab order in the rig menu). */
    public List<String> rigGuns() {
        List<String> out = new ArrayList<>();
        for (String id : gunRigs.keySet()) if (!id.equals("default")) out.add(id);
        return out;
    }

    public record FrameDef(String id, int index, String name, String color, int grade, int prestige,
                           Currency costCurrency, long cost, int sockets, double baseSpeed, double heatCapacity,
                           String lore, int rigWidth, int rigHeight, List<int[]> coreCells) {
        public boolean hasRig() { return rigWidth > 0 && rigHeight > 0; }

        public int rigCells() { return rigWidth * rigHeight; }

        /** True if (prestige, grade) meets this frame's requirement. */
        public boolean unlockedFor(int playerGrade, int playerPrestige) {
            return playerPrestige > prestige || (playerPrestige == prestige && playerGrade >= grade);
        }

        public String coloredName() { return "<" + color + ">" + name + "</" + color + ">"; }

        public String requirementText() {
            if (prestige <= 0) return "Grade " + grade;
            if (grade <= 1) return "Prestige " + prestige;
            return "Prestige " + prestige + ", Grade " + grade;
        }
    }

    public record EnchantDef(Enchant type, String name, String color, Material icon, String description,
                             double baseCost, double growth, int[] maxLevels, Map<String, Double> params) {
        public double p(String key, double def) { return params.getOrDefault(key, def); }

        public int cap(FrameDef frame) { return maxLevels.length == 0 ? 0 : maxLevels[Math.min(frame.index(), maxLevels.length - 1)]; }

        /** Cost to go from level-1 to level. */
        public long cost(int level) {
            double c = baseCost * Math.pow(growth, Math.max(0, level - 1));
            if (c >= Long.MAX_VALUE / 4.0) return Long.MAX_VALUE / 4;
            return Math.max(1, Math.round(c));
        }

        public String coloredName() { return "<" + color + ">" + name + "</" + color + ">"; }
    }

    public enum Rarity {
        COMMON("<gray>", "Common"), RARE("<#5aa0ff>", "Rare"), EPIC("<#c070ff>", "Epic"), LEGENDARY("<#ffb030>", "Legendary");

        public final String color;
        public final String label;

        Rarity(String color, String label) {
            this.color = color;
            this.label = label;
        }
    }

    /**
     * A drill module. pros and cons map a stat (see {@link DrillMath#STATS}) to its Mk I multiplier; marks raise pros
     * and shrink cons. flags are on/off effects (auto-sell). Rules: edge = must touch the rig's border, needs =
     * must touch a module of that family, avoid = must not touch one, drain = cons of touching modules grow by this.
     */
    public record ModuleDef(String id, String name, Rarity rarity, double weight, Material material, String model, String lore,
                            String family, ModuleShape shape, Map<String, Double> pros, Map<String, Double> cons,
                            Set<String> flags, boolean edge, String needs, String avoid, double drain, String corePart,
                            String kind) {
        public String coloredName() { return rarity.color + name + "</" + rarity.color.substring(1); }

        public String coloredName(int mk) { return rarity.color + name + " " + DrillMath.roman(mk) + "</" + rarity.color.substring(1); }

        /** Core cells take only Legendary modules, and Legendary modules need a core cell. */
        public boolean needsCore() { return rarity == Rarity.LEGENDARY; }
    }

    public record FamilyDef(String id, String name, String color, int max, String lore, Map<Integer, Map<String, Double>> bonuses) {
        public String coloredName() { return "<" + color + ">" + name + "</" + color + ">"; }
    }

    /** A hidden combo: the two modules touching on the rig, both online. clears = flags it switches off. */
    public record ComboDef(String id, String name, String a, String b, String lore, Map<String, Double> effects, Set<String> clears) {
        public boolean matches(String x, String y) { return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x)); }
    }

    /** state = FRIED / WORKING / PRISTINE (minimum quality) of the module's core part. */
    public record PartCost(String state, int amount) {}

    /** Rig expansion bought with Shards: adds columns to the rig. frame = lowest frame id that can take it. */
    public record ExpansionDef(long shards, int columns, String frame) {}

    public double proPower(int mk) { return proPower[Math.max(1, Math.min(maxMark, mk))]; }

    public double conPower(int mk) { return conPower[Math.max(1, Math.min(maxMark, mk))]; }

    public FamilyDef family(String id) { return id == null ? null : families.get(id); }

    public record Heat(double baseCapacity, double handBlock, double decayPerSecond, double activeDecayPerSecond,
                       double decayDelay, List<Double> deckMultiplier, double stallSeconds, double heatAfter,
                       double ventMinFraction, double ventCooldown, double burstBase, double burstPerFraction,
                       double burstSpeed, double perfectMin, double perfectMax, double perfectMultiplier) {
        /** Deck index from a mine's required grade (5 grades per deck). */
        public double deckMultiplier(int requiredGrade) {
            int deck = Math.max(0, (requiredGrade - 1) / 5);
            return deckMultiplier.get(Math.min(deck, deckMultiplier.size() - 1));
        }
    }

    /** Multiplier on every mining Token drop for this mine grade and prestige level. */
    public double tokenScale(int mineGrade, int prestige) {
        return dev.spacepunk.prison.api.TokenDrops.scale(mineGrade, prestige);
    }
}
