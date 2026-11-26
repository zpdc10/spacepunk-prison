package dev.spacepunk.prison.quests;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-player quest state, stored as JSON in moduleData("quests"). Main thread only. */
final class QuestData {
    /** Active story step and tutorials, by quest id. */
    Map<String, Active> story = new LinkedHashMap<>();
    Map<String, Active> tutorials = new LinkedHashMap<>();
    /** Finished story steps and tutorials (kept forever, also through prestige). */
    Set<String> done = new HashSet<>();

    List<Active> daily = new ArrayList<>();
    String dailyKey = "";
    boolean dailyBonus;
    List<Active> weekly = new ArrayList<>();
    String weeklyKey = "";

    /** Contract points this month and the track tiers already claimed. */
    String monthKey = "";
    long points;
    Set<Integer> claimed = new HashSet<>();

    /** Tracked quest key, e.g. "story:scrapyard.arrival", "daily:0". Null = pick automatically. */
    String tracked;
    /** Last known rank-up cost (used to scale rewards once at max grade). */
    long lastRankCost;
    long completedContracts;

    void normalize() {
        if (story == null) story = new LinkedHashMap<>();
        if (tutorials == null) tutorials = new LinkedHashMap<>();
        if (done == null) done = new HashSet<>();
        if (daily == null) daily = new ArrayList<>();
        if (weekly == null) weekly = new ArrayList<>();
        if (claimed == null) claimed = new HashSet<>();
        if (dailyKey == null) dailyKey = "";
        if (weeklyKey == null) weeklyKey = "";
        if (monthKey == null) monthKey = "";
        story.values().removeIf(a -> a == null || !a.valid());
        tutorials.values().removeIf(a -> a == null || !a.valid());
        daily.removeIf(a -> a == null || !a.valid());
        weekly.removeIf(a -> a == null || !a.valid());
    }

    /** One started quest or contract, with every number resolved when it started. */
    static final class Active {
        String id;
        String kind;
        String name;
        String[] types;
        /** Accepted targets per objective (empty = any). */
        List<List<String>> targets;
        String[] texts;
        long[] have;
        long[] need;
        long credits, tokens, shards, points;
        boolean done;

        boolean valid() {
            return id != null && types != null && have != null && need != null && texts != null && targets != null
                    && types.length == have.length && have.length == need.length && need.length == texts.length
                    && texts.length == targets.size();
        }

        boolean complete() {
            for (int i = 0; i < have.length; i++) if (have[i] < need[i]) return false;
            return true;
        }

        /** 0..1 progress over all objectives. */
        double fraction() {
            double sum = 0;
            for (int i = 0; i < have.length; i++) sum += need[i] <= 0 ? 1 : Math.min(1.0, (double) have[i] / need[i]);
            return have.length == 0 ? 1 : sum / have.length;
        }

        /** Index of the first unfinished objective, or -1. */
        int current() {
            for (int i = 0; i < have.length; i++) if (have[i] < need[i]) return i;
            return -1;
        }
    }
}
