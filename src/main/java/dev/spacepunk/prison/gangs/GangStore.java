package dev.spacepunk.prison.gangs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * All gang state in one JSON file (plugins/SpacepunkPrison/gangs-data.json). Serialized on the main thread,
 * written on a background thread through a temp file so a crash mid-write can't corrupt it.
 */
final class GangStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    static final class Root {
        int version = 1;
        Map<String, Gang> gangs = new LinkedHashMap<>();
        /** territory id -> gang id */
        Map<String, String> territories = new HashMap<>();
        /** Start (epoch ms) of the week the weekly points and contracts belong to. */
        long weekStart;
    }

    private final Plugin plugin;
    private final File file;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spacepunk-gangs-io");
        t.setDaemon(true);
        return t;
    });
    private boolean dirty;

    GangStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gangs-data.json");
    }

    Root load() {
        if (!file.exists()) return new Root();
        try {
            Root r = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), Root.class);
            if (r == null) return new Root();
            if (r.gangs == null) r.gangs = new LinkedHashMap<>();
            if (r.territories == null) r.territories = new HashMap<>();
            for (Gang g : r.gangs.values()) fix(g);
            return r;
        } catch (Exception e) {
            // Keep the broken file so nothing is lost, and start empty.
            File bad = new File(file.getParentFile(), "gangs-data.broken-" + System.currentTimeMillis() + ".json");
            plugin.getLogger().log(Level.SEVERE, "Could not read gangs-data.json; moved it to " + bad.getName(), e);
            file.renameTo(bad);
            return new Root();
        }
    }

    private static void fix(Gang g) {
        if (g.members == null) g.members = new LinkedHashMap<>();
        if (g.parked == null) g.parked = new HashMap<>();
        if (g.log == null) g.log = new java.util.ArrayList<>();
        if (g.upgrades == null) g.upgrades = new HashMap<>();
        if (g.contracts == null) g.contracts = new java.util.ArrayList<>();
        if (g.motd == null) g.motd = "";
        if (g.color == null) g.color = "#c8743a";
        for (Gang.Member m : g.members.values()) if (m.rank == null) m.rank = Rank.RECRUIT;
    }

    void markDirty() { dirty = true; }

    /** Writes if anything changed. Main thread (serializes here, writes off-thread). */
    void saveIfDirty(Root root) {
        if (!dirty) return;
        dirty = false;
        String json = GSON.toJson(root);
        io.submit(() -> write(json));
    }

    void shutdown(Root root) {
        dirty = true;
        saveIfDirty(root);
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) plugin.getLogger().warning("Gang data did not finish saving in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(String json) {
        try {
            File parent = file.getParentFile();
            if (!parent.exists()) parent.mkdirs();
            File tmp = new File(parent, "gangs-data.json.tmp");
            Files.writeString(tmp.toPath(), json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save gangs-data.json", e);
        }
    }
}
