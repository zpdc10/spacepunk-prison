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
                        s.getString("lore", "")));
            }
        }
        if (fl.isEmpty()) {
            log.warning("drill.yml: no frames defined; using a single default frame");
            fl.add(new FrameDef("rusted", 0, "Rusted", "#8a6a55", 1, 0, Currency.TOKENS, 0, 0, 0.5, 0, ""));
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
                Map<String, Double> params = new HashMap<>();
                for (String k : s.getKeys(false)) {
                    if (s.isDouble(k) || s.isInt(k) || s.isLong(k)) params.put(k, s.getDouble(k));
                }
                ml.put(id, new ModuleDef(id, s.getString("name", id), r, Math.max(0, s.getDouble("weight", 10)),
                        material(s.getString("material"), Material.PRISMARINE_SHARD, log), s.getString("model", id), s.getString("lore", ""),
                        s.getStringList("pros"), s.getStringList("cons"), Map.copyOf(params)));
            }
        }
        modules = Collections.unmodifiableMap(ml);

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

    public record FrameDef(String id, int index, String name, String color, int grade, int prestige,
                           Currency costCurrency, long cost, int sockets, double baseSpeed, double heatCapacity,
                           String lore) {
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

    public record ModuleDef(String id, String name, Rarity rarity, double weight, Material material, String model, String lore,
                            List<String> pros, List<String> cons, Map<String, Double> params) {
        public double p(String key, double def) { return params.getOrDefault(key, def); }

        public String coloredName() { return rarity.color + name + "</" + rarity.color.substring(1); }
    }

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
