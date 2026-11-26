package dev.spacepunk.prison.progression;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.data.PlayerProfile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Per-player cargo hold, cached in memory and written to {@code moduleData("progression")} as JSON.
 * Contents are keyed by item id ("minecraft:copper_ore", later "spacepunk:copper_ingot" for workshop goods).
 * Mining changes it many times a second, so writes are batched: {@link #flush()} runs every second,
 * and on quit and disable. Main thread only.
 */
final class CargoStore {
    static final String KEY = "progression";
    private static final Gson GSON = new Gson();

    /** Gson-serialized state. Field names are the JSON keys; keep them stable. */
    static final class Data {
        int level = 0;
        boolean autoSellUnlocked = false;
        boolean autoSell = false;
        /** Credits earned from the server (not other players) since the last prestige (drives the prestige Token payout). */
        long runEarnings = 0;
        Map<String, Long> items = new LinkedHashMap<>();

        transient long used;
    }

    private final ProfileService profiles;
    private final Logger log;
    private final Map<UUID, Data> cache = new HashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    CargoStore(ProfileService profiles, Logger log) {
        this.profiles = profiles;
        this.log = log;
    }

    void load(PlayerProfile profile) {
        Data d = null;
        String json = profile.moduleData(KEY);
        if (json != null) {
            try {
                d = GSON.fromJson(json, Data.class);
            } catch (JsonSyntaxException e) {
                log.warning("Bad progression data for " + profile.uuid() + ", resetting cargo: " + e.getMessage());
            }
        }
        if (d == null) d = new Data();
        Map<String, Long> clean = new LinkedHashMap<>();
        if (d.items != null) {
            d.items.forEach((k, v) -> {
                if (k != null && v != null && v > 0) clean.merge(k.toLowerCase(Locale.ROOT), v, Long::sum);
            });
        }
        d.items = clean;
        d.used = clean.values().stream().mapToLong(Long::longValue).sum();
        cache.put(profile.uuid(), d);
    }

    /** Null if the player's profile isn't loaded. */
    Data get(UUID id) { return cache.get(id); }

    void markDirty(UUID id) { dirty.add(id); }

    /** Adds up to {@code amount}; returns how many actually fit. */
    long add(UUID id, Data d, String item, long amount, long capacity) {
        long space = Math.max(0, capacity - d.used);
        long take = Math.min(space, amount);
        if (take <= 0) return 0;
        d.items.merge(item, take, Long::sum);
        d.used += take;
        dirty.add(id);
        return take;
    }

    /** Removes up to {@code amount}; returns how many were removed. */
    long remove(UUID id, Data d, String item, long amount) {
        long have = d.items.getOrDefault(item, 0L);
        long take = Math.min(have, Math.max(0, amount));
        if (take <= 0) return 0;
        if (have - take == 0) d.items.remove(item); else d.items.put(item, have - take);
        d.used -= take;
        dirty.add(id);
        return take;
    }

    /** Removes everything and returns what was there. */
    Map<String, Long> drain(UUID id, Data d) {
        Map<String, Long> out = new LinkedHashMap<>(d.items);
        d.items.clear();
        d.used = 0;
        dirty.add(id);
        return out;
    }

    void unload(UUID id) {
        write(id);
        dirty.remove(id);
        cache.remove(id);
    }

    void flush() {
        for (UUID id : Set.copyOf(dirty)) {
            dirty.remove(id);
            write(id);
        }
    }

    void flushAll() {
        for (UUID id : Set.copyOf(cache.keySet())) write(id);
        dirty.clear();
    }

    private void write(UUID id) {
        Data d = cache.get(id);
        if (d == null) return;
        PlayerProfile p = profiles.get(id).orElse(null);
        if (p == null) return;
        p.moduleData(KEY, GSON.toJson(d));
        profiles.markDirty(id);
    }
}
