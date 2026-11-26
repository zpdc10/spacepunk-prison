package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a batch of stims finishes cooking at an Undercroft drug lab, before the stims are handed over.
 * {@link #amount()} is mutable (a gang lab upgrade could add doses). {@link #controllerCut()} is how many of
 * those doses the lab's controller is owed (already removed from the cook's batch); the gangs module decides
 * where they go (gang stash, Credits, points). Main thread.
 */
public class StimCookedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player cook;
    private final String labId;
    private final String stimId;
    private final String controllerId;
    private int amount;
    private final int controllerCut;

    public StimCookedEvent(Player cook, String labId, String stimId, int amount, String controllerId, int controllerCut) {
        this.cook = cook;
        this.labId = labId;
        this.stimId = stimId;
        this.amount = amount;
        this.controllerId = controllerId;
        this.controllerCut = controllerCut;
    }

    public Player cook() { return cook; }
    public String labId() { return labId; }
    /** Stim id, e.g. "overdrive". */
    public String stimId() { return stimId; }
    /** Doses the cook receives. */
    public int amount() { return amount; }
    public void amount(int amount) { this.amount = Math.max(0, amount); }
    /** Lab controller id (e.g. a gang id) or null if nobody holds the lab. */
    public String controllerId() { return controllerId; }
    /** Doses owed to the controller (0 when there is none, or the cook is the controller's member: gangs decides). */
    public int controllerCut() { return controllerCut; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
