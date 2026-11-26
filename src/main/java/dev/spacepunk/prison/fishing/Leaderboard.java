package dev.spacepunk.prison.fishing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global fishing records (not per player): the weekly heaviest-catch board, fishing levels for the level board
 * (so offline players still rank), last week's winners and Scales owed to winners who were offline.
 * Stored in fishing-records.json, written off the main thread.
 */
final class Leaderboard {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class Catch {
        String name;
        String fish;
        double kg;
        long at;
    }

    static final class LevelRow {
        String name;
        int level;
        long xp;
    }

    static final class State {
        String week;
        Map<String, Catch> weekly = new HashMap<>();
        Map<String, LevelRow> levels = new HashMap<>();
        Map<String, Long> pendingScales = new HashMap<>();
        List<String> lastWinners = new ArrayList<>();
        /** All-time heaviest per species: fish id -> catch. */
        Map<String, Catch> records = new HashMap<>();
    }

    private final Plugin plugin;
    private final File file;
    private State state = new State();
    private boolean dirty;

    Leaderboard(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fishing-records.json");
        load();
    }

    private void load() {
        if (!file.exists()) {
            state.week = weekKey();
            return;
        }
        try {
            State s = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), State.class);
            if (s != null) state = s;
        } catch (Exception e) {
            plugin.getLogger().warning("[fishing] could not read " + file.getName() + ", starting fresh: " + e.getMessage());
        }
        if (state.weekly == null) state.weekly = new HashMap<>();
        if (state.levels == null) state.levels = new HashMap<>();
        if (state.pendingScales == null) state.pendingScales = new HashMap<>();
        if (state.lastWinners == null) state.lastWinners = new ArrayList<>();
        if (state.records == null) state.records = new HashMap<>();
        if (state.week == null) state.week = weekKey();
    }

    /** Writes the file async if anything changed. Pass sync=true on shutdown. */
    void save(boolean sync) {
        if (!dirty) return;
        dirty = false;
        String json = GSON.toJson(state);
        Runnable write = () -> {
            try {
                File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
                Files.writeString(tmp.toPath(), json, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("[fishing] could not save " + file.getName() + ": " + e.getMessage());
            }
        };
        if (sync) write.run();
        else Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
    }

    static String weekKey() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return now.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    String week() { return state.week; }

    /** Returns true if this is the player's new best of the week. */
    boolean recordWeekly(UUID id, String name, String fishId, double kg) {
        Catch c = state.weekly.get(id.toString());
        if (c != null && c.kg >= kg) return false;
        Catch n = new Catch();
        n.name = name;
        n.fish = fishId;
        n.kg = kg;
        n.at = System.currentTimeMillis();
        state.weekly.put(id.toString(), n);
        dirty = true;
        return true;
    }

    /** Returns the previous all-time record for this species if the new catch beats it (or null if no record yet but it is set). */
    boolean recordSpecies(String name, String fishId, double kg) {
        Catch c = state.records.get(fishId);
        if (c != null && c.kg >= kg) return false;
        Catch n = new Catch();
        n.name = name;
        n.fish = fishId;
        n.kg = kg;
        n.at = System.currentTimeMillis();
        state.records.put(fishId, n);
        dirty = true;
        return c != null;
    }

    Catch speciesRecord(String fishId) { return state.records.get(fishId); }

    void level(UUID id, String name, int level, long xp) {
        LevelRow r = state.levels.computeIfAbsent(id.toString(), k -> new LevelRow());
        if (r.level == level && r.xp == xp && name.equals(r.name)) return;
        r.name = name;
        r.level = level;
        r.xp = xp;
        dirty = true;
    }

    List<Map.Entry<String, Catch>> weeklyTop(int n) {
        return state.weekly.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, Catch> e) -> e.getValue().kg).reversed())
                .limit(n).toList();
    }

    List<Map.Entry<String, LevelRow>> levelTop(int n) {
        return state.levels.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, LevelRow> e) -> e.getValue().level)
                        .thenComparingLong(e -> e.getValue().xp).reversed())
                .limit(n).toList();
    }

    List<String> lastWinners() { return state.lastWinners; }

    /**
     * If the week changed, pays the old week's top places (into pendingScales) and clears the board.
     * Returns the winner lines for a broadcast, or null if the week did not change.
     */
    List<String> rollWeek(List<Integer> rewards, java.util.function.Function<String, String> fishName) {
        String now = weekKey();
        if (now.equals(state.week)) return null;
        List<String> lines = new ArrayList<>();
        List<Map.Entry<String, Catch>> top = weeklyTop(Math.max(3, rewards.size()));
        for (int i = 0; i < top.size() && i < rewards.size(); i++) {
            Catch c = top.get(i).getValue();
            long r = rewards.get(i);
            state.pendingScales.merge(top.get(i).getKey(), r, Long::sum);
            lines.add("#" + (i + 1) + " " + c.name + " · " + fishName.apply(c.fish) + " " + FishingItems.kg(c.kg) + " · +" + r + " Scales");
        }
        state.lastWinners = new ArrayList<>(lines);
        state.weekly.clear();
        state.week = now;
        dirty = true;
        return lines;
    }

    /** Scales owed to this player from a weekly reward; clears it. */
    long claimPending(UUID id) {
        Long v = state.pendingScales.remove(id.toString());
        if (v != null) dirty = true;
        return v == null ? 0 : v;
    }

    void clearWeek() {
        state.weekly.clear();
        dirty = true;
    }
}
