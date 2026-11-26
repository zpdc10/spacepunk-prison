package dev.spacepunk.prison.auction;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Global Black Market state in plugins/SpacepunkPrison/auctions.json. Written off the main thread. */
final class AuctionStore {
    static final class State {
        long nextId = 1;
        List<Listing> listings = new ArrayList<>();
        /** Player uuid -> items waiting in their collection bin (encoded like Listing#item). */
        Map<String, List<String>> claims = new LinkedHashMap<>();
        /** Player uuid -> Credits owed while they were offline (sales, refunds). */
        Map<String, Long> pending = new LinkedHashMap<>();
        /** Player uuid -> messages to show on their next join. */
        Map<String, List<String>> notices = new LinkedHashMap<>();

        void normalize() {
            if (listings == null) listings = new ArrayList<>();
            if (claims == null) claims = new LinkedHashMap<>();
            if (pending == null) pending = new LinkedHashMap<>();
            if (notices == null) notices = new LinkedHashMap<>();
            listings.removeIf(l -> l == null || l.seller == null || l.item == null);
        }
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final Plugin plugin;
    private final File file;
    private State state = new State();
    private boolean dirty;
    private final Object ioLock = new Object();

    AuctionStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.json");
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
            File bad = new File(file.getParentFile(), "auctions.broken-" + System.currentTimeMillis() + ".json");
            Files.copy(file.toPath(), bad.toPath());
            plugin.getLogger().severe("auctions.json is unreadable; copied it to " + bad.getName() + " and started empty");
            state = new State();
        }
        state.normalize();
    }

    State state() { return state; }

    void markDirty() { dirty = true; }

    /** Snapshot on the main thread, write async. */
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

    private void write(String json) {
        synchronized (ioLock) {
            try {
                file.getParentFile().mkdirs();
                File tmp = new File(file.getParentFile(), "auctions.json.tmp");
                Files.writeString(tmp.toPath(), json, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save auctions.json", e);
            }
        }
    }
}
