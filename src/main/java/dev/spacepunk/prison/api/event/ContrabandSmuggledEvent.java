package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player gets contraband out of the Undercroft through the checkpoint. {@link #items()} counts the
 * contraband items that made it out (after confiscation). For gang contracts, quests and leaderboards.
 */
public class ContrabandSmuggledEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int items;
    private final int confiscated;
    private final boolean bribed;

    public ContrabandSmuggledEvent(Player player, int items, int confiscated, boolean bribed) {
        this.player = player;
        this.items = items;
        this.confiscated = confiscated;
        this.bribed = bribed;
    }

    public Player player() { return player; }
    /** Contraband items carried out. */
    public int items() { return items; }
    /** Contraband items the guards took. */
    public int confiscated() { return confiscated; }
    /** True if the player bribed the guard. */
    public boolean bribed() { return bribed; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
