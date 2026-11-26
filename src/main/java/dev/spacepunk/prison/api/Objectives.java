package dev.spacepunk.prison.api;

import org.bukkit.entity.Player;

/** Null-safe helpers for reporting quest progress; they do nothing when the quests module is not installed. */
public final class Objectives {
    private Objectives() {}

    /** Reports progress to {@link QuestService#progress}, if quests are installed. Main thread only. */
    public static void report(Player player, String type, long amount, String... targets) {
        if (player == null || amount <= 0) return;
        Services.find(QuestService.class).ifPresent(q -> q.progress(player, type, amount, targets));
    }

    /** Registers an objective type with {@link QuestService#registerObjectiveType}, if quests are installed. */
    public static void register(String type, String displayName) {
        Services.find(QuestService.class).ifPresent(q -> q.registerObjectiveType(type, displayName));
    }
}
