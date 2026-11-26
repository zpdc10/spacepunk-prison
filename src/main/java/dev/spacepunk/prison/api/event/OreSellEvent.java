package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Fired by progression just before ore is sold for Credits (from /sell, the cargo menu, auto-sell or
 * {@link dev.spacepunk.prison.api.ProgressionService#sell}). Listeners may change {@link #multiplier()}, for
 * example a booster (x1.5) or a Scavenger Rig module (x0.8). Payout = baseValue x multiplier.
 */
public class OreSellEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Map<String, Long> items;
    private final long baseValue;
    private final String reason;
    private double multiplier;

    public OreSellEvent(Player player, Map<String, Long> items, long baseValue, double multiplier, String reason) {
        this.player = player;
        this.items = Map.copyOf(items);
        this.baseValue = baseValue;
        this.multiplier = multiplier;
        this.reason = reason;
    }

    public Player player() { return player; }
    /** What is being sold, item id (e.g. "minecraft:copper_ore") -> amount. Read-only. */
    public Map<String, Long> items() { return items; }
    /** Sum of base prices, before any multiplier. */
    public long baseValue() { return baseValue; }
    /** Total multiplier so far (grade + prestige bonus included). */
    public double multiplier() { return multiplier; }
    public void multiplier(double m) { this.multiplier = Math.max(0, m); }
    /** "command", "menu", "auto-sell", or whatever another module passed to ProgressionService#sell. */
    public String reason() { return reason; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
