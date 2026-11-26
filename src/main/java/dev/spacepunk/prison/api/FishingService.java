package dev.spacepunk.prison.api;

import java.util.UUID;

/**
 * Fishing: the station's flooded decks, rods, catches and sea creatures. Implemented by the fishing module.
 * Main thread, online players only (offline players read as 0).
 */
public interface FishingService {
    /** Fishing skill level (1..max). */
    int fishingLevel(UUID player);

    /** Total catches (fish, junk and treasure) since the player started fishing. */
    long totalCatches(UUID player);

    /** Times this player has caught one species, by fish id (e.g. "rust_minnow"). */
    long caught(UUID player, String fishId);

    /** Scales: the fishing upgrade material (gut fish, fight sea creatures). */
    long scales(UUID player);

    /** Adds Scales, for example as a quest reward. */
    void giveScales(UUID player, long amount, String reason);
}
