package dev.spacepunk.prison.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Trade state in plugins/SpacepunkPrison/trades.json (escrow, item returns, prestige locks, recent log), plus the
 * permanent append-only log trades.log (one JSON line per completed trade, items included in full). Written off
 * the main thread.
 */
final class TradeStore {
    static final class ReturnLock {
        long amount;
        long until;
        String senderName;
    }

    static final class State {
        long nextId = 1;
        /** Player uuid -> items they currently have on the table (encoded). Survives a crash mid-trade. */
        Map<String, List<String>> escrow = new LinkedHashMap<>();
        /** Player uuid -> items to hand back on their next join. */
        Map<String, List<String>> returns = new LinkedHashMap<>();
        /** Sender uuid -> (receiver uuid -> net Credits sent this prestige run). */
        Map<String, Map<String, Long>> runSent = new LinkedHashMap<>();
        /** Holder uuid -> (sender uuid -> lock): the holder can't send Credits back to the sender until it ends. */
        Map<String, Map<String, ReturnLock>> returnLocks = new LinkedHashMap<>();
        /** Players who turned trade requests off. */
        Set<String> blocking = new LinkedHashSet<>();
        /** Newest last. */
        List<TradeRecord> recent = new ArrayList<>();

        void normalize() {
            if (escrow == null) escrow = new LinkedHashMap<>();
            if (returns == null) returns = new LinkedHashMap<>();
            if (runSent == null) runSent = new LinkedHashMap<>();
            if (returnLocks == null) returnLocks = new LinkedHashMap<>();
            if (blocking == null) blocking = new LinkedHashSet<>();
            if (recent == null) recent = new ArrayList<>();
            recent.removeIf(r -> r == null || r.a == null || r.b == null);
        }
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final Plugin plugin;
    private final File file;
    private final File logFile;
    private State state = new State();
    private boolean dirty;
    private final Object ioLock = new Object();

    TradeStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trades.json");
        this.logFile = new File(plugin.getDataFolder(), "trades.log");
    }

    void load() throws IOException {
        if (!file.exists()) {
            state = new State();
            return;
        }
        try {
            State s = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), State.class);
            state = s == null ? new State() : s;
        } catch (JsonParseException e) {
            File bad = new File(file.getParentFile(), "trades.broken-" + System.currentTimeMillis() + ".json");
            Files.copy(file.toPath(), bad.toPath());
            plugin.getLogger().severe("trades.json is unreadable; copied it to " + bad.getName() + " and started empty");
            state = new State();
        }
        state.normalize();
    }

    State state() { return state; }

    void markDirty() { dirty = true; }

    void flushAsync() {
        if (!dirty) return;
        dirty = false;
        String json = GSON.toJson(state);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(json));
    }

    void flushSync() {
        dirty = false;
        write(GSON.toJson(state));
    }

    /** Appends one completed trade (full record, items encoded) to trades.log. */
    void appendLog(Object fullRecord) {
        String line = GSON.toJson(fullRecord) + "\n";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (ioLock) {
                try {
                    logFile.getParentFile().mkdirs();
                    Files.writeString(logFile.toPath(), line, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not append to trades.log", e);
                }
            }
        });
    }

    private void write(String json) {
        synchronized (ioLock) {
            try {
                file.getParentFile().mkdirs();
                File tmp = new File(file.getParentFile(), "trades.json.tmp");
                Files.writeString(tmp.toPath(), json, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save trades.json", e);
            }
        }
    }
}
