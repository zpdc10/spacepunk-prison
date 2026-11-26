package dev.spacepunk.prison.fishing;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Parsed fishing.yml. Immutable; a reload builds a new one. */
final class FishingConfig {
    // world
    final String worldName;
    final boolean autoBuild;
    final int baseY, spacing, poolX, poolDepth, poolZ, walkway;
    final String hubName;
    // items
    final boolean useItemModels;
    final Material rodMaterial;
    // bite
    final int minWait, maxWait;
    final double maxBiteReduction;
    // catch
    final double creatureChance, maxCreatureChance, junkWeight, fishWeight, treasureWeight;
    final Map<String, Double> rarityWeights = new LinkedHashMap<>();
    // value
    final double valueGrowth, levelBonus;
    // levels
    final int maxLevel;
    final double xpBase, xpGrowth;
    // collections
    final List<Integer> collectionTiers, legendaryTiers, collectionScales;
    final double collectionBonus;
    // leaderboard
    final List<Integer> weeklyRewards;
    final int leaderboardSize;
    // creatures
    final int creatureLifetime, maxCreatures;

    final Map<String, Rarity> rarities = new LinkedHashMap<>();
    final List<RodTier> tiers = new ArrayList<>();
    final Map<String, EnchantDef> enchants = new LinkedHashMap<>();
    final Map<String, LureDef> lures = new LinkedHashMap<>();
    final Map<String, BaitDef> baits = new LinkedHashMap<>();
    final Map<String, Area> areas = new LinkedHashMap<>();
    final Map<String, FishDef> fish = new LinkedHashMap<>();
    final Map<String, JunkDef> junk = new LinkedHashMap<>();
    final Map<String, TreasureDef> treasure = new LinkedHashMap<>();
    final Map<String, CreatureDef> creatures = new LinkedHashMap<>();

    record Minigame(double zone, double speed, double erratic, double gain, double drain) {}

    record Rarity(String id, String name, String color, int xp, int scales, Minigame minigame, int order) {
        String colored() { return color + name; }
    }

    record RodTier(String id, String name, String color, int level, long scales, int maxEnchant, int lureSlots, String lore, int index) {
        String colored() { return color + name; }
    }

    record EnchantDef(String id, String name, String color, double costBase, double costGrowth, double perLevel, String effect,
                      String description) {
        String colored() { return color + name; }

        long cost(int currentLevel) { return Math.max(1, Math.round(costBase * Math.pow(costGrowth, currentLevel))); }
    }

    record LureDef(String id, String name, String rarity, Effects effects, String lore) {}

    record BaitDef(String id, String name, double price, int amount, Effects effects, String lore) {}

    record Area(String id, String name, String color, int grade, int maxGrade, double valueMult, String biome,
                Material wall, Material floor, Material light, String description, int index) {
        String colored() { return color + name; }
    }

    record FishDef(String id, String name, String area, String rarity, double value, double minKg, double maxKg,
                   Material base, String lore) {}

    record JunkDef(String id, String name, double value, Material base, List<String> areas) {}

    record TreasureDef(String id, String name, String type, long min, long max, double weight, List<String> areas,
                       String lure, String bait, String command, boolean broadcast) {}

    record Drop(String lure, String bait, double chance, int amount) {}

    record CreatureDef(String id, String name, String area, EntityType entity, int size, int level, double health,
                       double damage, double speed, double scale, double weight, int xp, long minScales, long maxScales,
                       long minCredits, long maxCredits, boolean broadcast, List<Drop> drops) {}

    private FishingConfig(YamlConfiguration y, Logger log) {
        worldName = y.getString("world.name", "station_waters");
        autoBuild = y.getBoolean("world.auto-build", true);
        baseY = y.getInt("world.base-y", 64);
        spacing = Math.max(24, y.getInt("world.spacing", 44));
        List<Integer> ps = y.getIntegerList("world.pool-size");
        poolX = ps.size() > 0 ? Math.max(5, ps.get(0)) : 19;
        poolDepth = ps.size() > 1 ? Math.max(2, ps.get(1)) : 6;
        poolZ = ps.size() > 2 ? Math.max(5, ps.get(2)) : 19;
        walkway = Math.max(2, y.getInt("world.walkway", 4));
        hubName = y.getString("world.hub-name", "Bilge Hub");

        useItemModels = y.getBoolean("items.use-item-models", true);
        rodMaterial = material(y.getString("items.rod-material"), Material.FISHING_ROD, log);

        minWait = Math.max(20, y.getInt("bite.min-wait", 100));
        maxWait = Math.max(minWait, y.getInt("bite.max-wait", 300));
        maxBiteReduction = clamp(y.getDouble("bite.max-bite-reduction", 0.75), 0, 0.95);

        creatureChance = y.getDouble("catch.creature-chance", 0.035);
        maxCreatureChance = y.getDouble("catch.max-creature-chance", 0.3);
        junkWeight = y.getDouble("catch.junk", 12);
        fishWeight = y.getDouble("catch.fish", 85);
        treasureWeight = y.getDouble("catch.treasure", 3);
        ConfigurationSection rw = y.getConfigurationSection("catch.rarity-weights");
        if (rw != null) for (String k : rw.getKeys(false)) rarityWeights.put(k, rw.getDouble(k));

        valueGrowth = y.getDouble("value.value-growth", 1.2584);
        levelBonus = y.getDouble("value.level-bonus", 0.01);

        maxLevel = Math.max(1, y.getInt("levels.max", 50));
        xpBase = y.getDouble("levels.xp-base", 60);
        xpGrowth = y.getDouble("levels.xp-growth", 1.13);

        collectionTiers = y.getIntegerList("collections.tiers");
        legendaryTiers = y.getIntegerList("collections.legendary-tiers");
        collectionScales = y.getIntegerList("collections.scales");
        collectionBonus = y.getDouble("collections.value-bonus", 0.05);

        weeklyRewards = y.getIntegerList("leaderboard.weekly-rewards");
        leaderboardSize = Math.max(3, y.getInt("leaderboard.size", 10));

        creatureLifetime = Math.max(10, y.getInt("creature-settings.lifetime", 90));
        maxCreatures = Math.max(1, y.getInt("creature-settings.max-per-player", 3));

        ConfigurationSection rs = y.getConfigurationSection("rarities");
        if (rs != null) {
            int i = 0;
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (s == null) continue;
                Minigame mg = null;
                ConfigurationSection m = s.getConfigurationSection("minigame");
                if (m != null) {
                    mg = new Minigame(clamp(m.getDouble("zone", 0.25), 0.05, 0.9), m.getDouble("speed", 0.7),
                            clamp(m.getDouble("erratic", 0.04), 0, 1), m.getDouble("gain", 0.013), m.getDouble("drain", 0.009));
                }
                rarities.put(id, new Rarity(id, s.getString("name", id), s.getString("color", "<white>"), s.getInt("xp", 1),
                        s.getInt("scales", 0), mg, i++));
            }
        }
        rarities.putIfAbsent("junk", new Rarity("junk", "Junk", "<gray>", 1, 0, null, -1));

        ConfigurationSection ts = y.getConfigurationSection("rod.tiers");
        if (ts != null) {
            int i = 0;
            for (String id : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(id);
                if (s == null) continue;
                tiers.add(new RodTier(id, s.getString("name", id), s.getString("color", "<white>"), s.getInt("level", 1),
                        s.getLong("scales", 0), Math.max(1, s.getInt("max-enchant", 10)), Math.max(0, s.getInt("lure-slots", 1)),
                        s.getString("lore", ""), i++));
            }
        }
        if (tiers.isEmpty()) tiers.add(new RodTier("bent_wire", "Rod", "<white>", 1, 0, 10, 1, "", 0));

        ConfigurationSection es = y.getConfigurationSection("rod.enchants");
        if (es != null) {
            for (String id : es.getKeys(false)) {
                ConfigurationSection s = es.getConfigurationSection(id);
                if (s == null) continue;
                String effect = s.getString("effect", "");
                if (!Effects.known(effect)) log.warning("[fishing] enchant " + id + " has unknown effect '" + effect + "'");
                enchants.put(id, new EnchantDef(id, s.getString("name", id), s.getString("color", "<white>"),
                        s.getDouble("cost-base", 10), Math.max(1.0, s.getDouble("cost-growth", 1.09)), s.getDouble("per-level", 0.01),
                        effect, s.getString("description", "")));
            }
        }

        ConfigurationSection ls = y.getConfigurationSection("lures");
        if (ls != null) {
            for (String id : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(id);
                if (s == null) continue;
                lures.put(id, new LureDef(id, s.getString("name", id), s.getString("rarity", "uncommon"),
                        Effects.parse(s.getConfigurationSection("effects")), s.getString("lore", "")));
            }
        }

        ConfigurationSection bs = y.getConfigurationSection("bait");
        if (bs != null) {
            for (String id : bs.getKeys(false)) {
                ConfigurationSection s = bs.getConfigurationSection(id);
                if (s == null) continue;
                baits.put(id, new BaitDef(id, s.getString("name", id), s.getDouble("price", 50), Math.max(1, s.getInt("amount", 16)),
                        Effects.parse(s.getConfigurationSection("effects")), s.getString("lore", "")));
            }
        }

        ConfigurationSection as = y.getConfigurationSection("areas");
        if (as != null) {
            int i = 0;
            for (String id : as.getKeys(false)) {
                ConfigurationSection s = as.getConfigurationSection(id);
                if (s == null) continue;
                areas.put(id, new Area(id, s.getString("name", id), s.getString("color", "<white>"), Math.max(1, s.getInt("grade", 1)),
                        Math.max(1, s.getInt("max-grade", 5)), s.getDouble("value-mult", 1.0), s.getString("biome", ""),
                        material(s.getString("wall"), Material.POLISHED_DEEPSLATE, log),
                        material(s.getString("floor"), Material.SMOOTH_STONE, log),
                        material(s.getString("light"), Material.SEA_LANTERN, log), s.getString("description", ""), i++));
            }
        }

        ConfigurationSection fs = y.getConfigurationSection("fish");
        if (fs != null) {
            for (String id : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(id);
                if (s == null) continue;
                String area = s.getString("area", "");
                String rarity = s.getString("rarity", "common");
                if (!areas.containsKey(area)) log.warning("[fishing] fish " + id + " has unknown area '" + area + "'");
                if (!rarities.containsKey(rarity)) {
                    log.warning("[fishing] fish " + id + " has unknown rarity '" + rarity + "', using common");
                    rarity = "common";
                }
                List<Double> w = s.getDoubleList("weight");
                double min = w.size() > 0 ? w.get(0) : 0.5, max = w.size() > 1 ? Math.max(min, w.get(1)) : min * 3;
                fish.put(id, new FishDef(id, s.getString("name", id), area, rarity, s.getDouble("value", 5), min, max,
                        material(s.getString("base"), Material.COD, log), s.getString("lore", "")));
            }
        }

        ConfigurationSection js = y.getConfigurationSection("junk");
        if (js != null) {
            for (String id : js.getKeys(false)) {
                ConfigurationSection s = js.getConfigurationSection(id);
                if (s == null) continue;
                junk.put(id, new JunkDef(id, s.getString("name", id), s.getDouble("value", 1),
                        material(s.getString("base"), Material.IRON_NUGGET, log), s.getStringList("areas")));
            }
        }

        ConfigurationSection trs = y.getConfigurationSection("treasure");
        if (trs != null) {
            for (String id : trs.getKeys(false)) {
                ConfigurationSection s = trs.getConfigurationSection(id);
                if (s == null) continue;
                List<Long> amt = s.getLongList("amount");
                long min = amt.size() > 0 ? amt.get(0) : 1, max = amt.size() > 1 ? Math.max(min, amt.get(1)) : min;
                String type = s.getString("type", "scales").toLowerCase(Locale.ROOT);
                treasure.put(id, new TreasureDef(id, s.getString("name", id), type, min, max, s.getDouble("weight", 1),
                        s.getStringList("areas"), s.getString("lure"), s.getString("bait"), s.getString("command"),
                        s.getBoolean("broadcast", false)));
                if (type.equals("lure") && !lures.containsKey(s.getString("lure", "")))
                    log.warning("[fishing] treasure " + id + " names unknown lure " + s.getString("lure"));
            }
        }

        ConfigurationSection cs = y.getConfigurationSection("creatures");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                EntityType type;
                try {
                    type = EntityType.valueOf(s.getString("entity", "DROWNED").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    log.warning("[fishing] creature " + id + " has unknown entity " + s.getString("entity") + ", using DROWNED");
                    type = EntityType.DROWNED;
                }
                if (type.getEntityClass() == null || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                    log.warning("[fishing] creature " + id + " is not a living entity, skipping");
                    continue;
                }
                List<Long> sc = s.getLongList("scales"), cr = s.getLongList("credits");
                List<Drop> drops = new ArrayList<>();
                for (Map<?, ?> m : s.getMapList("drops")) {
                    Object lure = m.get("lure"), bait = m.get("bait"), ch = m.get("chance"), am = m.get("amount");
                    drops.add(new Drop(lure == null ? null : lure.toString(), bait == null ? null : bait.toString(),
                            ch instanceof Number n ? n.doubleValue() : 0.0, am instanceof Number n2 ? n2.intValue() : 1));
                }
                creatures.put(id, new CreatureDef(id, s.getString("name", id), s.getString("area", ""), type, s.getInt("size", 2),
                        s.getInt("level", 1), Math.max(1, s.getDouble("health", 20)), s.getDouble("damage", 3), s.getDouble("speed", 0.25),
                        s.getDouble("scale", 1.0), s.getDouble("weight", 10), s.getInt("xp", 10),
                        sc.size() > 0 ? sc.get(0) : 0, sc.size() > 1 ? sc.get(1) : (sc.size() > 0 ? sc.get(0) : 0),
                        cr.size() > 0 ? cr.get(0) : 0, cr.size() > 1 ? cr.get(1) : (cr.size() > 0 ? cr.get(0) : 0),
                        s.getBoolean("broadcast", false), drops));
            }
        }
    }

    static FishingConfig load(YamlConfiguration yaml, Logger log) { return new FishingConfig(yaml, log); }

    Rarity rarity(String id) { return rarities.getOrDefault(id, rarities.get("junk")); }

    RodTier tier(int index) { return tiers.get(Math.max(0, Math.min(tiers.size() - 1, index))); }

    /** XP needed to go from level to level + 1. */
    long xpFor(int level) { return Math.max(1, Math.round(xpBase * Math.pow(xpGrowth, level - 1))); }

    /** Collection tier for a catch count (0 = undiscovered). */
    int collectionTier(FishDef f, long count) {
        List<Integer> t = "legendary".equals(f.rarity()) ? legendaryTiers : collectionTiers;
        int tier = 0;
        for (int need : t) if (count >= need) tier++;
        return tier;
    }

    /** Count needed for the next collection tier, or -1 at max. */
    long nextCollectionAt(FishDef f, long count) {
        List<Integer> t = "legendary".equals(f.rarity()) ? legendaryTiers : collectionTiers;
        for (int need : t) if (count < need) return need;
        return -1;
    }

    List<FishDef> fishIn(String area) { return fish.values().stream().filter(f -> f.area().equals(area)).toList(); }

    private static Material material(String name, Material fallback, Logger log) {
        if (name == null || name.isBlank()) return fallback;
        Material m = Material.matchMaterial(name);
        if (m == null) {
            log.warning("[fishing] unknown material " + name + ", using " + fallback);
            return fallback;
        }
        return m;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
