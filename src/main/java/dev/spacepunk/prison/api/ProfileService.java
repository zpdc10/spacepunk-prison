package dev.spacepunk.prison.api;

import dev.spacepunk.prison.data.PlayerProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Loaded player profiles. Profiles load async on join, save async on quit and every few minutes.
 * Only online players have a profile in memory.
 */
public interface ProfileService {
    /** Profile of an online, fully loaded player. Empty while still loading or if offline. */
    Optional<PlayerProfile> get(UUID player);

    /** Same as get(...).orElseThrow(); use when the player is known to be online and loaded. */
    PlayerProfile require(UUID player);

    /** Marks the profile dirty so it is written on the next save pass. */
    void markDirty(UUID player);
}
