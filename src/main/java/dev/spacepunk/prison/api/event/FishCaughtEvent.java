package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired by the fishing module after a player lands a catch (after the reel-in minigame, if any).
 * kind is "fish", "junk" or "treasure". For fish, id is the species id (e.g. "rust_minnow"), rarity is
 * "common".."legendary" and weightKg is the rolled weight; amount is 2 on a double catch. Read-only.
 */
public class FishCaughtEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String areaId, kind, id, rarity;
    private final double weightKg;
    private final int amount;

    public FishCaughtEvent(Player player, String areaId, String kind, String id, String rarity, double weightKg, int amount) {
        this.player = player;
        this.areaId = areaId;
        this.kind = kind;
        this.id = id;
        this.rarity = rarity;
        this.weightKg = weightKg;
        this.amount = amount;
    }

    public Player player() { return player; }
    /** Fishing area id, e.g. "scrapyard", "reactor", "cryo", "hull", "foundry". */
    public String areaId() { return areaId; }
    public String kind() { return kind; }
    public String id() { return id; }
    public String rarity() { return rarity; }
    public double weightKg() { return weightKg; }
    public int amount() { return amount; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
