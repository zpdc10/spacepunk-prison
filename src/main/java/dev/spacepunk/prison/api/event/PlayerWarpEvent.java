package dev.spacepunk.prison.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a player goes through a {@link dev.spacepunk.prison.api.WarpService} warp. Cancel it to stop the trip
 * (e.g. a contraband scan at the Undercroft checkpoint) and optionally set a MiniMessage reason the player sees,
 * or change the destination.
 */
public class PlayerWarpEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String warpId;
    private final Location from;
    private Location to;
    private boolean cancelled;
    private String cancelReason;

    public PlayerWarpEvent(Player player, String warpId, Location from, Location to) {
        this.player = player;
        this.warpId = warpId;
        this.from = from;
        this.to = to;
    }

    public Player player() { return player; }
    public String warpId() { return warpId; }
    public Location from() { return from.clone(); }
    public Location to() { return to.clone(); }
    public void to(Location to) { this.to = to.clone(); }

    /** MiniMessage shown to the player when cancelled (null = a generic message). */
    public String cancelReason() { return cancelReason; }
    public void cancel(String reasonMiniMessage) { this.cancelled = true; this.cancelReason = reasonMiniMessage; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
