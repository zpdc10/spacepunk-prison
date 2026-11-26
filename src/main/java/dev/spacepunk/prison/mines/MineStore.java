package dev.spacepunk.prison.mines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * mines-data.yml: where each mine is placed (region + spawn) and admin-created mines. Kept apart from
 * mines.yml so commands never rewrite the hand-edited design file.
 */
final class MineStore {
    private final File file;
    private final Logger log;
    private YamlConfiguration yaml;

    MineStore(File file, Logger log) {
        this.file = file;
        this.log = log;
        reload();
    }

    void reload() { yaml = YamlConfiguration.loadConfiguration(file); }

    /** Version of the auto layout the generated mines were last built with (0 = never recorded). */
    int layoutVersion() { return yaml.getInt("layout-version", 0); }

    void layoutVersion(int v) {
        yaml.set("layout-version", v);
        save();
    }

    ConfigurationSection custom() { return yaml.getConfigurationSection("custom"); }

    Region region(String id) {
        ConfigurationSection s = yaml.getConfigurationSection("placements." + id);
        if (s == null || !s.isString("world")) return null;
        List<Integer> min = s.getIntegerList("min"), max = s.getIntegerList("max");
        if (min.size() != 3 || max.size() != 3) return null;
        return Region.of(s.getString("world"), min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
    }

    Location spawn(String id) {
        ConfigurationSection s = yaml.getConfigurationSection("placements." + id + ".spawn");
        if (s == null) return null;
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w == null) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    void savePlacement(String id, Region r, Location spawn) {
        String p = "placements." + id;
        yaml.set(p, null);
        if (r != null) {
            yaml.set(p + ".world", r.world());
            yaml.set(p + ".min", List.of(r.minX(), r.minY(), r.minZ()));
            yaml.set(p + ".max", List.of(r.maxX(), r.maxY(), r.maxZ()));
            if (spawn != null && spawn.getWorld() != null) {
                yaml.set(p + ".spawn.world", spawn.getWorld().getName());
                yaml.set(p + ".spawn.x", spawn.getX());
                yaml.set(p + ".spawn.y", spawn.getY());
                yaml.set(p + ".spawn.z", spawn.getZ());
                yaml.set(p + ".spawn.yaw", (double) spawn.getYaw());
                yaml.set(p + ".spawn.pitch", (double) spawn.getPitch());
            }
        }
        save();
    }

    void saveCustom(MinesConfig.MineDef def) {
        String p = "custom." + def.id();
        yaml.set(p, null);
        yaml.set(p + ".name", def.name());
        yaml.set(p + ".deck", def.deck());
        yaml.set(p + ".grade", def.grade());
        for (Map.Entry<Material, Double> e : def.mix().weights().entrySet()) {
            yaml.set(p + ".blocks." + e.getKey().name(), e.getValue());
        }
        save();
    }

    void removeCustom(String id) {
        yaml.set("custom." + id, null);
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            log.warning("[mines] could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
