package dev.spacepunk.prison.undercroft;

/** Per-player Undercroft state, stored as JSON in PlayerProfile.moduleData("undercroft"). */
final class PlayerData {
    /** Credits added to the bounty tag whoever kills this player next will get. */
    long bounty;
    int streak;
    int kills;
    int deaths;
    int smuggled;
    /** Active stim id, or null. */
    String stim;
    /** True while in the stim's comedown. */
    boolean comedown;
    /** Seconds left in the current phase. */
    int remaining;
    /** Comedown seconds carried over from a stim taken during an earlier comedown. */
    int carry;
}
