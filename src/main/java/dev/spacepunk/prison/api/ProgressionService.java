package dev.spacepunk.prison.api;

import java.util.UUID;

/**
 * Grades (clearance ranks 1..25) and prestige. Implemented by the progression module.
 * Values live on PlayerProfile; this service owns the rules (costs, rank-up, prestige).
 */
public interface ProgressionService {
    int MAX_GRADE = 25;

    int grade(UUID player);

    int prestige(UUID player);

    /** Credits needed to go from current grade to the next. -1 at max grade. */
    long nextGradeCost(UUID player);

    /** Permanent sell-price multiplier from grade + prestige (1.0 = no bonus). */
    double sellMultiplier(UUID player);
}
