package dev.spacepunk.prison.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gangs (crews). Implemented by the gangs module. Main thread only, except {@link #gangOf} and
 * {@link #sameGang}, which are safe from async chat.
 *
 * Hooks for other modules:
 * <ul>
 *   <li>Contracts: call {@link #registerContractObjective} once in enable(), then
 *       {@link #addContractProgress} whenever a player does the thing (for example "fish_caught").</li>
 *   <li>Undercroft turf: {@link #setTerritoryOwner} when a gang captures a lab or control point,
 *       {@link #depositFromServer} to pay the owning gang its share.</li>
 *   <li>PvP: gang members already can't damage each other (gangs.yml chat.friendly-fire);
 *       use {@link #sameGang} for anything else (bounties, kill credit).</li>
 * </ul>
 */
public interface GangService {
    /** The player's gang, online or offline. Thread-safe. */
    Optional<GangInfo> gangOf(UUID player);

    /** A gang by id. */
    Optional<GangInfo> gang(String gangId);

    /** True if both players are in the same gang. Thread-safe. */
    boolean sameGang(UUID a, UUID b);

    // ---- Contracts ----

    /**
     * Makes an objective available for weekly gang contracts (pool entries in gangs.yml using it get rolled).
     * Built in: blocks_mined, ore_sold, credits_earned, rankups, prestiges.
     */
    void registerContractObjective(String objective, String displayName);

    /** Adds progress to every active contract of the player's gang with this objective. No-op without a gang. */
    void addContractProgress(UUID player, String objective, long amount);

    // ---- Points, bank, turf ----

    /** Adds gang points (gang level + weekly leaderboard) to the player's gang. */
    void addPoints(UUID player, long points, String reason);

    /**
     * Pays Credits from the server into a gang bank, for example a drug-lab share. Never counts as any
     * member's "earned this run" and is never prestige-locked. Capped by the bank's capacity; returns what fit.
     */
    long depositFromServer(String gangId, long credits, String reason);

    /** Gang id holding a territory (gangs.yml territories), if any. */
    Optional<String> territoryOwner(String territoryId);

    /** Sets or clears (null gangId) a territory's owner and announces it. */
    void setTerritoryOwner(String territoryId, String gangId);

    /** territory id -> owning gang id, for every held territory. */
    Map<String, String> territories();

    /** Read-only view of a gang. */
    interface GangInfo {
        String id();
        String name();
        /** Short tag, plain text, e.g. "RUST". */
        String tag();
        /** Tag in its color as MiniMessage, e.g. "&lt;#c8743a&gt;RUST&lt;/#c8743a&gt;". */
        String coloredTag();
        int level();
        Set<UUID> members();
        /** "leader", "officer", "member" or "recruit"; null if not a member. */
        String rankOf(UUID player);
        long bank();
        long weeklyPoints();
    }
}
