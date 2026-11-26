package dev.spacepunk.prison.undercroft;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** undercroft-data.yml: whether the layout was built, where the hot ore veins are, and who controls each lab. */
final class UndercroftStore {
    record Vein(int x, int y, int z, int ring) {}
    record Controller(String id, String name) {}

    private final File file;
    private final Logger log;
    boolean generated;
    final List<Vein> veins = new ArrayList<>();
    final Map<String, Controller> controllers = new LinkedHashMap<>();

    UndercroftStore(File file, Logger log) {
        this.file = file;
        this.log = log;
        load();
    }

    void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        generated = y.getBoolean("generated", false);
        veins.clear();
        for (String s : y.getStringList("veins")) {
            String[] p = s.split(",");
            if (p.length < 4) continue;
            try {
                veins.add(new Vein(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            } catch (NumberFormatException ignored) {
                // skip bad line
            }
        }
        controllers.clear();
        ConfigurationSection c = y.getConfigurationSection("labs");
        if (c != null) for (String id : c.getKeys(false)) {
            String cid = c.getString(id + ".controller");
            if (cid != null) controllers.put(id, new Controller(cid, c.getString(id + ".name", cid)));
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("generated", generated);
        y.set("veins", veins.stream().map(v -> v.x() + "," + v.y() + "," + v.z() + "," + v.ring()).toList());
        for (Map.Entry<String, Controller> e : controllers.entrySet()) {
            y.set("labs." + e.getKey() + ".controller", e.getValue().id());
            y.set("labs." + e.getKey() + ".name", e.getValue().name());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            log.log(Level.WARNING, "[undercroft] could not save " + file.getName(), e);
        }
    }
}
