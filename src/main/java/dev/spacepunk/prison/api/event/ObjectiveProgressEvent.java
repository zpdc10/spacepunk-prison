package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Fired on the main thread for every quest objective report ({@link dev.spacepunk.prison.api.QuestService#progress}),
 * whether or not a quest was waiting for it. Lets other modules (the in-game guide's checklists) follow what players
 * do without each system firing its own event. Read only.
 */
public class ObjectiveProgressEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String type;
    private final long amount;
    private final List<String> targets;

    public ObjectiveProgressEvent(Player player, String type, long amount, List<String> targets) {
        this.player = player;
        this.type = type;
        this.amount = amount;
        this.targets = List.copyOf(targets);
    }

    public Player player() { return player; }
    /** Objective type id, lower case (see {@link dev.spacepunk.prison.api.ObjectiveTypes}). */
    public String type() { return type; }
    public long amount() { return amount; }
    public List<String> targets() { return targets; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
