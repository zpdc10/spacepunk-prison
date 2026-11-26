package dev.spacepunk.prison.workshop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One player's workshop state, stored as JSON in {@code moduleData("workshop")}. Field names are the JSON keys;
 * keep them stable. Who may enter and the bay's size live in {@link PlotRegistry} so they work while the owner
 * is offline. Main thread only.
 *
 * Item keys are item ids ("minecraft:copper_ore", "spacepunk:copper_ingot") with an optional quality suffix
 * ("spacepunk:copper_ingot#1" = Refined, "#2" = Pristine). See {@link #base} and {@link #quality}.
 */
final class BayData {
    int nextMachineId = 1;
    List<MachineData> machines = new ArrayList<>();
    Map<String, Long> locker = new LinkedHashMap<>();
    /** Machines bought per kind (each extra one costs more). */
    Map<String, Integer> bought = new HashMap<>();
    /** Conveyor blocks in the bay: "x,y,z" -> direction items flow (NORTH/EAST/SOUTH/WEST). */
    Map<String, String> conveyors = new HashMap<>();
    /** Epoch millis of the last simulation step, for offline catch-up. */
    long lastTick = 0;
    /** Lifetime Credits from workshop sales. */
    long lifetimeSales = 0;
    /** Lifetime goods produced (units). */
    long lifetimeCrafted = 0;
    /** Refurbisher mark (0 = Mk I). */
    int refurbLevel = 0;
    /** Lifetime scrap parts repaired. */
    long refurbished = 0;
    /** Lifetime drill modules built at the Assembly Bench. */
    long modulesBuilt = 0;
    /** True once the one-time starter fuel has been put in the locker. */
    boolean starterFuel = false;

    static final class MachineData {
        int id;
        String type;
        int tier;
        int x, y, z;
        /** Output direction: NORTH, EAST, SOUTH or WEST. */
        String facing = "NORTH";
        boolean enabled = true;
        Map<String, Long> input = new LinkedHashMap<>();
        Map<String, Long> output = new LinkedHashMap<>();
        double heat;
        double progress;
        boolean overheated;
        /** UUID of the item display entity showing the model. */
        String display;
        /** Source machines: "cargo" or "locker". */
        String source = "cargo";
        /** Source machines: item id to pull, or "any" (whatever the next machine accepts). */
        String filter = "any";
        /** Generators: seconds of burn left. */
        double fuel;
        /** Round-robin pointer over recipes so mixed inputs all get processed. */
        int rr;

        transient String status = "";
        transient boolean running;
        transient double lastEfficiency;

        long inputTotal() { return input.values().stream().mapToLong(Long::longValue).sum(); }
        long outputTotal() { return output.values().stream().mapToLong(Long::longValue).sum(); }
    }

    long lockerUsed() { return locker.values().stream().mapToLong(Long::longValue).sum(); }

    MachineData machine(int id) {
        for (MachineData m : machines) if (m.id == id) return m;
        return null;
    }

    // ---- item key helpers ----

    static String pos(int x, int y, int z) { return x + "," + y + "," + z; }

    static String base(String key) {
        int i = key.indexOf('#');
        return i < 0 ? key : key.substring(0, i);
    }

    static int quality(String key) {
        int i = key.indexOf('#');
        if (i < 0) return 0;
        try {
            return Math.max(0, Integer.parseInt(key.substring(i + 1)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String key(String base, int quality) { return quality <= 0 ? base : base + "#" + quality; }

    static void add(Map<String, Long> map, String key, long n) {
        if (n > 0) map.merge(key, n, Long::sum);
    }

    /** Removes up to n; returns how many were removed. */
    static long take(Map<String, Long> map, String key, long n) {
        Long have = map.get(key);
        if (have == null || n <= 0) return 0;
        long t = Math.min(have, n);
        if (have - t <= 0) map.remove(key); else map.put(key, have - t);
        return t;
    }

    /** Total units of an item across all qualities. */
    static long countBase(Map<String, Long> map, String base) {
        long n = 0;
        for (Map.Entry<String, Long> e : map.entrySet()) if (base(e.getKey()).equals(base)) n += e.getValue();
        return n;
    }
}
