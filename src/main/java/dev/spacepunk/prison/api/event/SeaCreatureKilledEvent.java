package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired by the fishing module when a player kills a sea creature they hooked. Read-only. */
public class SeaCreatureKilledEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String areaId, creatureId;

    public SeaCreatureKilledEvent(Player player, String areaId, String creatureId) {
        this.player = player;
        this.areaId = areaId;
        this.creatureId = creatureId;
    }

    public Player player() { return player; }
    public String areaId() { return areaId; }
    /** Creature id from fishing.yml, e.g. "sludge_blob". */
    public String creatureId() { return creatureId; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
