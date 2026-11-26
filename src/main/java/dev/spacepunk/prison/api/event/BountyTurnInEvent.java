package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player turns bounty tags in at the fence. {@link #points()} are gang leaderboard points (the gangs
 * module adds them to the player's gang). Credits are already paid when this fires.
 */
public class BountyTurnInEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int tags;
    private final long credits;
    private final long points;

    public BountyTurnInEvent(Player player, int tags, long credits, long points) {
        this.player = player;
        this.tags = tags;
        this.credits = credits;
        this.points = points;
    }

    public Player player() { return player; }
    public int tags() { return tags; }
    public long credits() { return credits; }
    public long points() { return points; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
