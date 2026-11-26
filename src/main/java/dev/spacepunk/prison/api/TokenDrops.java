package dev.spacepunk.prison.api;

import java.util.UUID;

/**
 * Shared scaling for Tokens found during activities (mining, fishing, workshop). Prestige pays no Tokens; instead
 * every activity drop is multiplied by gradeGrowth^(grade - 1) x prestigeGrowth^prestige, so finds keep pace with
 * drill enchant costs. The drill module sets the numbers from drill.yml (tokens.grade-growth / prestige-growth).
 */
public final class TokenDrops {
    private static volatile double gradeGrowth = 1.10;
    private static volatile double prestigeGrowth = 1.15;

    private TokenDrops() {}

    public static void configure(double grade, double prestige) {
        gradeGrowth = Math.max(1.0, grade);
        prestigeGrowth = Math.max(1.0, prestige);
    }

    /** Token drop multiplier gained per prestige level (1.15 = +15%). */
    public static double prestigeGrowth() { return prestigeGrowth; }

    /** Multiplier for a drop at this grade (mine grade, or the player's grade) and prestige level. */
    public static double scale(int grade, int prestige) {
        return Math.pow(gradeGrowth, Math.max(0, grade - 1)) * Math.pow(prestigeGrowth, Math.max(0, prestige));
    }

    /** Multiplier for a drop to this player, using their current grade and prestige. */
    public static double scale(UUID player) {
        return Services.find(ProgressionService.class)
                .map(s -> scale(s.grade(player), s.prestige(player)))
                .orElse(1.0);
    }

    /** A random amount in [min, max] times the player's scale, at least 1. */
    public static long roll(UUID player, long min, long max) {
        long base = max <= min ? min : java.util.concurrent.ThreadLocalRandom.current().nextLong(min, max + 1);
        return Math.max(1, Math.round(base * scale(player)));
    }
}
