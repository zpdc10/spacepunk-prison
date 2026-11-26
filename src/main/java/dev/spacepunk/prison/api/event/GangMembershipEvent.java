package dev.spacepunk.prison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired after a player joins or leaves a gang, or the gang is disbanded or renamed/recolored
 * (so name tags, tab list and sidebar can refresh). Informational.
 */
public class GangMembershipEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private final String gangId;
    private final Change change;

    public GangMembershipEvent(UUID player, String gangId, Change change) {
        this.player = player;
        this.gangId = gangId;
        this.change = change;
    }

    /** The player affected (null for gang-wide changes such as UPDATED). */
    public UUID player() { return player; }
    public String gangId() { return gangId; }
    public Change change() { return change; }

    public enum Change { JOINED, LEFT, KICKED, DISBANDED, RANK, UPDATED }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
