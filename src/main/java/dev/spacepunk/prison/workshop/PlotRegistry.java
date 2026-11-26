package dev.spacepunk.prison.workshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which bay plot belongs to whom, plus the settings that must work while the owner is offline (size, open to
 * visitors, crew). Global, saved to plugins/SpacepunkPrison/workshop/plots.json. Main thread only.
 */
final class PlotRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class Plot {
        int index;
        String owner;
        String name;
        int level;
        boolean built;
        boolean open;
        Set<String> crew = new LinkedHashSet<>();

        UUID ownerId() { return UUID.fromString(owner); }

        boolean isCrew(UUID player) { return crew.contains(player.toString()); }
    }

    private static final class Saved {
        int next = 0;
        Map<String, Plot> plots = new LinkedHashMap<>();
    }

    private final Plugin plugin;
    private final File file;
    private Saved saved = new Saved();
    private final Map<Integer, Plot> byIndex = new HashMap<>();
    private final Map<UUID, Plot> byOwner = new HashMap<>();
    private boolean saveQueued;

    PlotRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(new File(plugin.getDataFolder(), "workshop"), "plots.json");
        load();
    }

    private void load() {
        if (file.exists()) {
            try {
                Saved s = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), Saved.class);
                if (s != null) saved = s;
            } catch (Exception e) {
                plugin.getLogger().severe("workshop/plots.json is unreadable (" + e.getMessage() + "); bays keep their plots on disk but the file will be rewritten");
                try {
                    Files.copy(file.toPath(), new File(file.getParentFile(), "plots.broken.json").toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // best effort backup
                }
            }
        }
        if (saved.plots == null) saved.plots = new LinkedHashMap<>();
        for (Plot p : saved.plots.values()) {
            if (p.crew == null) p.crew = new LinkedHashSet<>();
            byIndex.put(p.index, p);
            try {
                byOwner.put(p.ownerId(), p);
            } catch (IllegalArgumentException ignored) {
                // skip corrupt owner ids
            }
            saved.next = Math.max(saved.next, p.index + 1);
        }
    }

    Plot get(UUID owner) { return byOwner.get(owner); }

    Plot at(int index) { return byIndex.get(index); }

    /** The owner's plot, claiming the next free index on first use. */
    Plot claim(UUID owner, String name) {
        Plot p = byOwner.get(owner);
        if (p != null) {
            if (name != null && !name.equals(p.name)) {
                p.name = name;
                save();
            }
            return p;
        }
        p = new Plot();
        p.index = saved.next++;
        p.owner = owner.toString();
        p.name = name;
        saved.plots.put(Integer.toString(p.index), p);
        byIndex.put(p.index, p);
        byOwner.put(owner, p);
        save();
        return p;
    }

    /** Finds a plot by owner name (case-insensitive), for /ws visit while the owner is offline. */
    Plot byName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (Plot p : byIndex.values()) if (p.name != null && p.name.toLowerCase(Locale.ROOT).equals(n)) return p;
        return null;
    }

    Iterable<Plot> all() { return byIndex.values(); }

    /** Saves on the next tick (coalesced), writing the file off the main thread. */
    void save() {
        if (saveQueued) return;
        saveQueued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            saveQueued = false;
            String json = GSON.toJson(saved);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(json));
        });
    }

    /** Synchronous save for shutdown. */
    void saveNow() {
        saveQueued = false;
        write(GSON.toJson(saved));
    }

    private synchronized void write(String json) {
        try {
            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            File tmp = new File(dir, "plots.json.tmp");
            Files.writeString(tmp.toPath(), json, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save workshop/plots.json: " + e.getMessage());
        }
    }
}
