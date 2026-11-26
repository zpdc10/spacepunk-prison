package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired by progression when a player who meets every prestige requirement tries to prestige (the /prestige
 * preview and the confirm). Cancel it with {@link #deny(String)} to block the prestige, for example while the
 * player has Credits held in escrow somewhere (auction bids) that the prestige wipe would not reach.
 */
public class PrestigeAttemptEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private boolean cancelled;
    private String denyMessage;

    public PrestigeAttemptEvent(Player player) { this.player = player; }

    public Player player() { return player; }

    /** Blocks the prestige and sets the MiniMessage line shown to the player. */
    public void deny(String miniMessage) {
        this.cancelled = true;
        this.denyMessage = miniMessage;
    }

    /** Why it was blocked (MiniMessage), or null. */
    public String denyMessage() { return denyMessage; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
