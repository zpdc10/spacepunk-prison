package dev.spacepunk.prison.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-player fishing state, stored as JSON in PlayerProfile.moduleData("fishing"). Gson fills the fields. */
final class FishingData {
    int rodTier;
    Map<String, Integer> enchants = new HashMap<>();
    /** Lure ids slotted in the rod (null entries = empty slot). */
    List<String> lures = new ArrayList<>();
    String activeBait;
    Map<String, Integer> bait = new HashMap<>();
    long scales;
    int level = 1;
    long xp;
    long catches;
    long creaturesKilled;
    long creditsEarned;
    /** Species id -> stats. */
    Map<String, Species> species = new HashMap<>();
    /** Fish that didn't fit in the inventory: item key -> amount. */
    Map<String, Long> cooler = new LinkedHashMap<>();
    boolean rodGiven;
    boolean hideTrack;

    static final class Species {
        long caught;
        double heaviest;
        int tierClaimed;
    }

    void normalize() {
        if (enchants == null) enchants = new HashMap<>();
        if (lures == null) lures = new ArrayList<>();
        if (bait == null) bait = new HashMap<>();
        if (species == null) species = new HashMap<>();
        if (cooler == null) cooler = new LinkedHashMap<>();
        if (level < 1) level = 1;
    }

    int enchant(String id) { return enchants.getOrDefault(id, 0); }

    Species species(String id) { return species.computeIfAbsent(id, k -> new Species()); }

    String lure(int slot) { return slot < lures.size() ? lures.get(slot) : null; }

    void lure(int slot, String id) {
        while (lures.size() <= slot) lures.add(null);
        lures.set(slot, id);
    }

    int baitCount(String id) { return id == null ? 0 : bait.getOrDefault(id, 0); }
}
