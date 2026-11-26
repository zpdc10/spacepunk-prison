package dev.spacepunk.prison.parkour;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Parsed parkour.yml. */
final class ParkourConfig {
    enum Shape { SINGLE, LINE2, LINE3, WIDE, SLAB, TRAPDOOR }

    record Tier(String name, int checkpointEvery, int gapMin, int gapMax, int upGap, double upChance, double downChance,
                double lateralChance, Map<Shape, Integer> shapes) {}

    record Theme(List<Material> blocks, Material slab, Material trapdoor, Material pad, Material light, Material chain,
                 Material lantern, double decorChance) {}

    /** One line of a reward table. credits: base + gradeCost x the player's next rank-up cost. */
    record Reward(int weight, long creditsBase, double creditsGradeCost, long tokensMin, long tokensMax, long shards,
                  List<String> commands, String text, boolean rare) {}

    final ZoneId zone;
    final int halfWidth, depth, rowGap, maxRise;
    final long seed;
    final double jumpsBase, jumpsPerGrade;
    final boolean returnToMine, displays;
    final Map<Integer, Tier> tiers = new HashMap<>();
    final Map<Integer, Theme> themes = new HashMap<>();
    final Map<Integer, List<Reward>> rewards = new HashMap<>();
    final String broadcast;

    private ParkourConfig(YamlConfiguration y, Logger log) {
        ConfigurationSection s = sec(y, "settings");
        String tz = s.getString("timezone", "");
        ZoneId z;
        try {
            z = tz == null || tz.isBlank() ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            log.warning("[parkour] bad settings.timezone '" + tz + "', using the server's zone");
            z = ZoneId.systemDefault();
        }
        zone = z;
        returnToMine = s.getBoolean("return-to-mine", true);
        displays = s.getBoolean("signs", true);
        broadcast = s.getString("rare-broadcast", "<gold>{player}</gold> <gray>found <white>{reward}</white> on the {mine} maintenance run!");

        ConfigurationSection l = sec(y, "layout");
        halfWidth = Math.max(6, l.getInt("half-width", 20));
        depth = Math.max(10, l.getInt("depth", 44));
        rowGap = Math.max(5, l.getInt("row-gap", 7));
        maxRise = Math.max(5, l.getInt("max-rise", 40));
        seed = l.getLong("seed", 0);
        ConfigurationSection j = sec(y, "jumps");
        jumpsBase = j.getDouble("base", 14);
        jumpsPerGrade = j.getDouble("per-grade", 1.0);

        ConfigurationSection t = sec(y, "tiers");
        for (String k : t.getKeys(false)) {
            ConfigurationSection c = t.getConfigurationSection(k);
            Integer deck = deck(k, log);
            if (c == null || deck == null) continue;
            List<Integer> gap = c.getIntegerList("gap");
            int gmin = gap.isEmpty() ? 1 : Math.max(1, gap.get(0));
            int gmax = gap.size() < 2 ? gmin + 1 : Math.max(gmin, Math.min(4, gap.get(1)));
            Map<Shape, Integer> shapes = new EnumMap<>(Shape.class);
            ConfigurationSection sh = c.getConfigurationSection("shapes");
            if (sh != null) for (String key : sh.getKeys(false)) {
                try {
                    shapes.put(Shape.valueOf(key.toUpperCase(Locale.ROOT)), Math.max(0, sh.getInt(key)));
                } catch (IllegalArgumentException e) {
                    log.warning("[parkour] unknown shape " + key);
                }
            }
            if (shapes.values().stream().mapToInt(Integer::intValue).sum() <= 0) shapes.put(Shape.LINE2, 1);
            tiers.put(deck, new Tier(c.getString("name", "Deck " + deck), Math.max(2, c.getInt("checkpoint-every", 6)), gmin, gmax,
                    Math.max(1, Math.min(3, c.getInt("up-gap", 2))), c.getDouble("up-chance", 0.3), c.getDouble("down-chance", 0.1),
                    c.getDouble("lateral-chance", 0), shapes));
        }

        ConfigurationSection th = sec(y, "themes");
        for (String k : th.getKeys(false)) {
            ConfigurationSection c = th.getConfigurationSection(k);
            Integer deck = deck(k, log);
            if (c == null || deck == null) continue;
            List<Material> blocks = new ArrayList<>();
            for (String b : c.getStringList("blocks")) {
                Material m = block(b, null, log);
                if (m != null) blocks.add(m);
            }
            if (blocks.isEmpty()) blocks.add(Material.SMOOTH_STONE);
            themes.put(deck, new Theme(List.copyOf(blocks), block(c.getString("slab"), Material.SMOOTH_STONE_SLAB, log),
                    block(c.getString("trapdoor"), Material.IRON_TRAPDOOR, log), block(c.getString("pad"), blocks.get(0), log),
                    block(c.getString("light"), Material.SEA_LANTERN, log), block(c.getString("chain"), null, log),
                    block(c.getString("lantern"), null, log), c.getDouble("decor-chance", 0.3)));
        }

        ConfigurationSection r = sec(y, "rewards");
        for (String k : r.getKeys(false)) {
            Integer deck = deck(k, log);
            if (deck == null) continue;
            List<Reward> list = new ArrayList<>();
            for (Map<?, ?> raw : r.getMapList(k)) {
                ConfigurationSection e = new MemoryConfiguration().createSection("x", raw == null ? Collections.emptyMap() : raw);
                List<Long> tok = e.isList("tokens") ? e.getLongList("tokens") : List.of(e.getLong("tokens", 0), e.getLong("tokens", 0));
                long tmin = tok.isEmpty() ? 0 : tok.get(0), tmax = tok.size() < 2 ? tmin : Math.max(tmin, tok.get(1));
                List<String> cmds = new ArrayList<>();
                for (String c : e.getStringList("commands")) cmds.add(c.startsWith("/") ? c.substring(1) : c);
                list.add(new Reward(Math.max(1, e.getInt("weight", 10)), e.getLong("credits", 0), e.getDouble("credits-grade-cost", 0),
                        tmin, tmax, e.getLong("shards", 0), List.copyOf(cmds), e.getString("text", ""), e.getBoolean("rare", false)));
            }
            rewards.put(deck, List.copyOf(list));
        }
    }

    static ParkourConfig load(File f, Logger log) { return new ParkourConfig(YamlConfiguration.loadConfiguration(f), log); }

    static int deckOf(int grade) { return Math.max(1, Math.min(5, (grade - 1) / 5 + 1)); }

    Tier tier(int deck) {
        Tier t = tiers.get(deck);
        if (t != null) return t;
        return tiers.getOrDefault(1, new Tier("Deck", 6, 1, 2, 2, 0.3, 0.1, 0, Map.of(Shape.LINE2, 1)));
    }

    Theme theme(int deck) {
        Theme t = themes.get(deck);
        if (t != null) return t;
        return themes.getOrDefault(1, new Theme(List.of(Material.SMOOTH_STONE), Material.SMOOTH_STONE_SLAB, Material.IRON_TRAPDOOR,
                Material.SMOOTH_STONE, Material.SEA_LANTERN, null, null, 0));
    }

    int jumps(int grade) { return (int) Math.round(jumpsBase + jumpsPerGrade * (Math.max(1, grade) - 1)); }

    private static Integer deck(String key, Logger log) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            log.warning("[parkour] deck keys must be 1-5, got " + key);
            return null;
        }
    }

    private static Material block(String name, Material fallback, Logger log) {
        if (name == null || name.isBlank()) return fallback;
        Material m = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (m == null || !m.isBlock()) {
            if (log != null) log.warning("[parkour] unknown block " + name + (fallback == null ? "" : ", using " + fallback));
            return fallback;
        }
        return m;
    }

    private static ConfigurationSection sec(ConfigurationSection y, String key) {
        ConfigurationSection s = y.getConfigurationSection(key);
        return s != null ? s : new MemoryConfiguration();
    }
}
