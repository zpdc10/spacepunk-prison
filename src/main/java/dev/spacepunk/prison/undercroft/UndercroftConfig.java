package dev.spacepunk.prison.undercroft;

import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** undercroft.yml, parsed once. Immutable; reload builds a new one. */
public final class UndercroftConfig {
    public record Ring(int number, String id, String name, int xMin, int xMax, int prestige, String hotOre,
                       Material oreBlock, int veins, int veinSize, int cover, Material light,
                       Map<String, Double> ingredients) {}

    public record HotOre(String id, String name, String lore, long value) {}

    public record Ingredient(String id, String name, String lore) {}

    /** One phase (high or comedown) of a stim. */
    public record Phase(double sell, double mining, double gunDamage, Map<PotionEffectType, Integer> effects) {
        static final Phase NONE = new Phase(1, 0, 1, Map.of());
    }

    public record Stim(String id, String name, String lore, int duration, Phase high, int comedownDuration,
                       Phase comedown, long fenceValue) {}

    public record LabSite(String id, String name, int x, int z, List<String> recipes) {}

    public record Recipe(String id, Map<String, Integer> ingredients, int doses, int seconds) {}

    public record Loot(String type, String id, int min, int max, int weight, String command, String label) {}

    // world
    public final String world;
    public final boolean autoGenerate;
    public final int floorY, halfWidth, ceilingHeight;
    public final Difficulty difficulty;
    public final String fallbackWorld;
    // entry + combat
    public final int minGrade, graceSeconds, newGraceSeconds, newMaxGrade, tagSeconds;
    public final List<String> allowedCommands;
    public final boolean killOnLogout;
    // layout
    public final String checkpointName;
    public final int checkpointXMin, checkpointXMax;
    public final List<Ring> rings = new ArrayList<>();
    // contraband
    public final Material depletedBlock;
    public final int regrowSeconds;
    public final Map<String, HotOre> hotOres = new LinkedHashMap<>();
    public final Map<String, Ingredient> ingredients = new LinkedHashMap<>();
    public final Map<String, Stim> stims = new LinkedHashMap<>();
    // labs
    public final Material labBlock;
    public final double cookRadius, controllerCut;
    public final boolean announceCooking;
    public final Map<String, LabSite> labs = new LinkedHashMap<>();
    public final Map<String, Recipe> recipes = new LinkedHashMap<>();
    public final int bonusDoses;
    // bounty
    public final long tagValue, bountyPerKill, pointsPerTag;
    public final int repeatCooldownMinutes;
    // fence + smuggling
    public final double growth;
    public final long liningPrice;
    public final double detectChance;
    public final int liningHides;
    public final long bribeBase;
    public final double bribeShare;
    // supply drops
    public final boolean dropsEnabled;
    public final int dropInterval, dropWarning, dropDespawn, dropMinPlayers, dropRolls;
    public final List<Integer> ringWeights;
    public final List<Loot> dropLoot;
    public final int crateRolls;
    public final List<Loot> crateLoot;

    private UndercroftConfig(YamlConfiguration y, Logger log) {
        world = y.getString("world.name", "undercroft");
        autoGenerate = y.getBoolean("world.auto-generate", true);
        floorY = y.getInt("world.floor-y", 64);
        halfWidth = Math.max(8, y.getInt("world.half-width", 36));
        ceilingHeight = Math.max(5, y.getInt("world.ceiling-height", 13));
        Difficulty d;
        try { d = Difficulty.valueOf(y.getString("world.difficulty", "EASY").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { d = Difficulty.EASY; }
        difficulty = d;
        fallbackWorld = y.getString("exit.fallback-world", "mines");

        minGrade = y.getInt("entry.min-grade", 1);
        graceSeconds = y.getInt("entry.grace-seconds", 10);
        newGraceSeconds = y.getInt("entry.new-player-grace-seconds", 30);
        newMaxGrade = y.getInt("entry.new-player-max-grade", 5);
        tagSeconds = y.getInt("combat.tag-seconds", 15);
        allowedCommands = y.getStringList("combat.allowed-commands").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        killOnLogout = y.getBoolean("combat.kill-on-logout", true);

        checkpointName = y.getString("checkpoint.name", "Guard Checkpoint");
        checkpointXMin = y.getInt("checkpoint.x-min", -16);
        checkpointXMax = y.getInt("checkpoint.x-max", 0);
        int prev = checkpointXMax;
        int n = 1;
        for (Map<?, ?> m : y.getMapList("rings")) {
            ConfigurationSection s = section(m);
            int xMax = s.getInt("x-max", prev + 50);
            if (xMax <= prev) { log.warning("[undercroft] ring " + s.getString("id") + " x-max must grow; skipped"); continue; }
            rings.add(new Ring(n++, s.getString("id", "ring" + n), s.getString("name", "Ring " + n), prev, xMax,
                    s.getInt("prestige", 0), s.getString("hot-ore", "hot_scrap"),
                    material(s.getString("ore-block"), Material.NETHER_GOLD_ORE, log), s.getInt("veins", 8),
                    s.getInt("vein-size", 5), s.getInt("cover", 20), material(s.getString("light"), Material.OCHRE_FROGLIGHT, log),
                    doubles(s.getConfigurationSection("ingredients"))));
            prev = xMax;
        }
        if (rings.isEmpty()) rings.add(new Ring(1, "outer", "Outer Bilge", checkpointXMax, checkpointXMax + 60, 0,
                "hot_scrap", Material.NETHER_GOLD_ORE, 8, 5, 20, Material.OCHRE_FROGLIGHT, Map.of()));

        depletedBlock = material(y.getString("hot-ore.depleted-block"), Material.BLACKSTONE, log);
        regrowSeconds = y.getInt("hot-ore.regrow-seconds", 60);
        ConfigurationSection hk = y.getConfigurationSection("hot-ore.kinds");
        if (hk != null) for (String id : hk.getKeys(false)) {
            ConfigurationSection s = hk.getConfigurationSection(id);
            if (s != null) hotOres.put(id, new HotOre(id, s.getString("name", id), s.getString("lore", ""), s.getLong("value", 30)));
        }
        ConfigurationSection ig = y.getConfigurationSection("ingredients");
        if (ig != null) for (String id : ig.getKeys(false)) {
            ConfigurationSection s = ig.getConfigurationSection(id);
            if (s != null) ingredients.put(id, new Ingredient(id, s.getString("name", id), s.getString("lore", "")));
        }
        ConfigurationSection st = y.getConfigurationSection("stims");
        if (st != null) for (String id : st.getKeys(false)) {
            ConfigurationSection s = st.getConfigurationSection(id);
            if (s == null) continue;
            stims.put(id, new Stim(id, s.getString("name", id), s.getString("lore", ""), s.getInt("duration", 120),
                    phase(s.getConfigurationSection("high"), log), s.getInt("comedown-duration", 60),
                    phase(s.getConfigurationSection("comedown"), log), s.getLong("fence-value", 100)));
        }

        labBlock = material(y.getString("labs.lab-block"), Material.BREWING_STAND, log);
        cookRadius = y.getDouble("labs.cook-radius", 6);
        controllerCut = Math.max(0, Math.min(1, y.getDouble("labs.controller-cut", 0.25)));
        announceCooking = y.getBoolean("labs.announce-cooking", true);
        ConfigurationSection ls = y.getConfigurationSection("labs.sites");
        if (ls != null) for (String id : ls.getKeys(false)) {
            ConfigurationSection s = ls.getConfigurationSection(id);
            if (s != null) labs.put(id, new LabSite(id, s.getString("name", id), s.getInt("x"), s.getInt("z"), s.getStringList("recipes")));
        }
        ConfigurationSection rs = y.getConfigurationSection("recipes");
        if (rs != null) for (String id : rs.getKeys(false)) {
            ConfigurationSection s = rs.getConfigurationSection(id);
            if (s == null) continue;
            Map<String, Integer> ing = new LinkedHashMap<>();
            ConfigurationSection is = s.getConfigurationSection("ingredients");
            if (is != null) for (String k : is.getKeys(false)) ing.put(k, Math.max(1, is.getInt(k)));
            if (!stims.containsKey(id)) log.warning("[undercroft] recipe " + id + " has no matching stim");
            recipes.put(id, new Recipe(id, ing, Math.max(1, s.getInt("doses", 2)), Math.max(1, s.getInt("seconds", 20))));
        }
        bonusDoses = y.getInt("bonus-doses", 1);

        tagValue = y.getLong("bounty.tag-value", 400);
        bountyPerKill = y.getLong("bounty.bounty-per-kill", 250);
        pointsPerTag = y.getLong("bounty.points-per-tag", 1);
        repeatCooldownMinutes = y.getInt("bounty.repeat-cooldown-minutes", 30);

        growth = y.getDouble("fence.price-grade-growth", 1.21);
        liningPrice = y.getLong("fence.lining-price", 25000);
        detectChance = y.getDouble("smuggling.detect-chance", 0.4);
        liningHides = y.getInt("smuggling.lining-hides", 3);
        bribeBase = y.getLong("smuggling.bribe-base", 3000);
        bribeShare = y.getDouble("smuggling.bribe-share", 0.3);

        dropsEnabled = y.getBoolean("supply-drops.enabled", true);
        dropInterval = Math.max(1, y.getInt("supply-drops.interval-minutes", 12));
        dropWarning = y.getInt("supply-drops.warning-seconds", 30);
        dropDespawn = Math.max(1, y.getInt("supply-drops.despawn-minutes", 4));
        dropMinPlayers = y.getInt("supply-drops.min-players-in-zone", 1);
        dropRolls = Math.max(1, y.getInt("supply-drops.rolls", 4));
        ringWeights = y.getIntegerList("supply-drops.ring-weights");
        dropLoot = loot(y.getMapList("supply-drops.loot"));
        crateRolls = Math.max(1, y.getInt("tech-crate.rolls", 2));
        crateLoot = loot(y.getMapList("tech-crate.loot"));
    }

    public static UndercroftConfig load(File file, Logger log) {
        return new UndercroftConfig(YamlConfiguration.loadConfiguration(file), log);
    }

    public Ring ring(int number) { return number >= 1 && number <= rings.size() ? rings.get(number - 1) : null; }

    public int xEnd() { return rings.getLast().xMax(); }

    private static ConfigurationSection section(Map<?, ?> m) {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> sub) c.createSection(String.valueOf(e.getKey()), sub);
            else c.set(String.valueOf(e.getKey()), e.getValue());
        }
        return c;
    }

    private static List<Loot> loot(List<Map<?, ?>> list) {
        List<Loot> out = new ArrayList<>();
        for (Map<?, ?> m : list) {
            ConfigurationSection s = section(m);
            int min = Math.max(1, s.getInt("min", 1));
            out.add(new Loot(s.getString("type", "parts").toLowerCase(Locale.ROOT), s.getString("id", ""), min,
                    Math.max(min, s.getInt("max", min)), Math.max(1, s.getInt("weight", 1)), s.getString("command", ""),
                    s.getString("label", "")));
        }
        return out;
    }

    private static Map<String, Double> doubles(ConfigurationSection s) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (s != null) for (String k : s.getKeys(false)) out.put(k, s.getDouble(k));
        return out;
    }

    private static Phase phase(ConfigurationSection s, Logger log) {
        if (s == null) return Phase.NONE;
        Map<PotionEffectType, Integer> fx = new LinkedHashMap<>();
        ConfigurationSection es = s.getConfigurationSection("effects");
        if (es != null) for (String k : es.getKeys(false)) {
            PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(k.toLowerCase(Locale.ROOT)));
            if (t == null) { log.warning("[undercroft] unknown potion effect " + k); continue; }
            fx.put(t, Math.max(1, es.getInt(k, 1)));
        }
        return new Phase(s.getDouble("sell", 1.0), s.getDouble("mining", 0.0), s.getDouble("gun-damage", 1.0), fx);
    }

    private static Material material(String name, Material def, Logger log) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name);
        if (m == null) {
            log.warning("[undercroft] unknown material " + name + ", using " + def);
            return def;
        }
        return m;
    }
}
