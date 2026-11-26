package dev.spacepunk.prison.mines;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Parsed mines.yml. Every tuning number lives in the file; defaults here only guard against missing keys. */
public final class MinesConfig {

    /** A themed group of five mines. Shell materials are used by /mineadmin generate. */
    public record Deck(String id, String name, String color, int sizeX, int sizeY, int sizeZ,
                       Material wall, Material rim, Material light) {}

    /** One mine as designed in config (placement lives in mines-data.yml). */
    public record MineDef(String id, String name, String deck, int grade, BlockMix mix, boolean custom) {}

    public record Settings(
            String world, boolean createWorld, int originX, int baseY, int originZ, int spacing, int rimWidth,
            double refillMinedFraction, int refillIntervalSeconds, int warnSeconds, boolean refillOnStartup,
            int blocksPerTick, boolean useFawe, long maxVolume,
            boolean pvp, boolean fallDamage, boolean protectWorld, boolean bossBar, boolean setWorldSpawn,
            boolean enclosed, int roomHeight) {}

    private final Settings settings;
    private final Map<String, Deck> decks;
    private final Map<String, MineDef> mines;

    private MinesConfig(Settings settings, Map<String, Deck> decks, Map<String, MineDef> mines) {
        this.settings = settings;
        this.decks = decks;
        this.mines = mines;
    }

    public Settings settings() { return settings; }
    public Map<String, Deck> decks() { return decks; }
    /** Ordered by grade, then config order. */
    public Map<String, MineDef> mines() { return mines; }

    public static MinesConfig load(File file, ConfigurationSection custom, Logger log) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        Settings s = new Settings(
                y.getString("world.name", "mines"),
                y.getBoolean("world.create-if-missing", true),
                y.getInt("layout.origin-x", 0),
                y.getInt("layout.base-y", 40),
                y.getInt("layout.origin-z", 0),
                y.getInt("layout.spacing", 150),
                y.getInt("layout.rim-width", 4),
                clamp(y.getDouble("refill.mined-fraction", 0.65), 0.05, 1.0),
                Math.max(0, y.getInt("refill.interval-seconds", 600)),
                Math.max(0, y.getInt("refill.warn-seconds", 3)),
                y.getBoolean("refill.on-startup", true),
                Math.max(500, y.getInt("refill.native-blocks-per-tick", 20000)),
                y.getBoolean("refill.use-fawe", true),
                Math.max(1, y.getLong("limits.max-volume", 250_000)),
                y.getBoolean("protection.pvp", false),
                y.getBoolean("protection.fall-damage", false),
                y.getBoolean("protection.protect-world", true),
                y.getBoolean("display.boss-bar", true),
                y.getBoolean("world.set-spawn-to-first-mine", true),
                y.getBoolean("layout.enclosed", true),
                Math.max(4, y.getInt("layout.room-height", 9)));

        Map<String, Deck> decks = new LinkedHashMap<>();
        ConfigurationSection ds = y.getConfigurationSection("decks");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection d = ds.getConfigurationSection(id);
                if (d == null) continue;
                List<Integer> size = d.getIntegerList("size");
                int sx = size.size() > 0 ? size.get(0) : 33, sy = size.size() > 1 ? size.get(1) : 24, sz = size.size() > 2 ? size.get(2) : 33;
                decks.put(id, new Deck(id, d.getString("name", id), d.getString("color", "<gray>"),
                        Math.max(1, sx), Math.max(1, sy), Math.max(1, sz),
                        material(d.getString("wall"), Material.POLISHED_DEEPSLATE, log),
                        material(d.getString("rim"), Material.SMOOTH_STONE, log),
                        material(d.getString("light"), Material.SEA_LANTERN, log)));
            }
        }

        List<MineDef> list = new ArrayList<>();
        ConfigurationSection ms = y.getConfigurationSection("mines");
        if (ms != null) for (String id : ms.getKeys(false)) {
            MineDef def = parseMine(id, ms.getConfigurationSection(id), false, log);
            if (def != null) list.add(def);
        }
        if (custom != null) for (String id : custom.getKeys(false)) {
            MineDef def = parseMine(id, custom.getConfigurationSection(id), true, log);
            if (def != null) list.add(def);
        }
        list.sort((a, b) -> Integer.compare(a.grade(), b.grade()));
        Map<String, MineDef> mines = new LinkedHashMap<>();
        for (MineDef d : list) {
            if (mines.containsKey(d.id())) log.warning("[mines] duplicate mine id " + d.id() + ", keeping the first");
            else mines.put(d.id(), d);
        }
        return new MinesConfig(s, decks, mines);
    }

    static MineDef parseMine(String id, ConfigurationSection m, boolean custom, Logger log) {
        if (m == null) return null;
        Map<Material, Double> weights = new LinkedHashMap<>();
        ConfigurationSection bs = m.getConfigurationSection("blocks");
        if (bs != null) for (String key : bs.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null || !mat.isBlock()) {
                log.warning("[mines] " + id + ": unknown block '" + key + "', skipped");
                continue;
            }
            weights.put(mat, bs.getDouble(key));
        }
        if (weights.isEmpty()) log.warning("[mines] " + id + " has no valid blocks; using stone");
        return new MineDef(id.toLowerCase(Locale.ROOT), m.getString("name", id), m.getString("deck", ""),
                Math.max(1, m.getInt("grade", 1)), new BlockMix(weights), custom);
    }

    private static Material material(String name, Material fallback, Logger log) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isBlock()) {
            log.warning("[mines] unknown shell block '" + name + "', using " + fallback);
            return fallback;
        }
        return m;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
