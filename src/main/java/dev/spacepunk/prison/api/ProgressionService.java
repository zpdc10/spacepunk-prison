package dev.spacepunk.prison.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Grades (clearance ranks 1..25) and prestige. Implemented by the progression module.
 * Values live on PlayerProfile; this service owns the rules (costs, rank-up, prestige).
 */
public interface ProgressionService {
    int MAX_GRADE = 25;

    int grade(UUID player);

    int prestige(UUID player);

    /** Credits needed to go from current grade to the next. -1 at max grade. */
    long nextGradeCost(UUID player);

    /** Permanent sell-price multiplier from grade + prestige (1.0 = no bonus). */
    double sellMultiplier(UUID player);

    /** Base sell price of one unit of this ore, before any multiplier. 0 means it cannot be sold. */
    long sellPrice(Material material);

    /**
     * Base sell price by item id: a vanilla key such as "minecraft:copper_ore" or a custom one such as
     * "spacepunk:copper_ingot" (for processed goods). 0 means it cannot be sold.
     */
    long sellPrice(String itemId);

    /**
     * Sells ores straight to the station for an online player (does not touch the cargo hold): price x
     * {@link #sellMultiplier} x extraMultiplier, then any {@link dev.spacepunk.prison.api.event.OreSellEvent}
     * listeners. Pays Credits and returns the amount paid. For example, a Magnet Coil drill module can call
     * this from a MineBlocksBrokenEvent listener at HIGH priority and then clear the event's ores.
     */
    long sell(Player player, Map<Material, Long> ores, double extraMultiplier, String reason);

    /** Same as {@link #sell} but keyed by item id (see {@link #sellPrice(String)}). */
    long sellItems(Player player, Map<String, Long> items, double extraMultiplier, String reason);

    // ---- Cargo hold (per online player, keyed by item id) ----

    /** Read-only snapshot of the cargo hold, item id -> amount. Empty if not loaded. */
    Map<String, Long> cargoContents(UUID player);

    /** Amount of one item in the cargo hold. */
    long cargoAmount(UUID player, String itemId);

    /** Free space left in the cargo hold (units). */
    long cargoSpace(UUID player);

    /** Adds up to amount (limited by free space). Returns how many were added. */
    long cargoDeposit(UUID player, String itemId, long amount);

    /** Removes up to amount. Returns how many were removed (for example, to feed a processing machine). */
    long cargoWithdraw(UUID player, String itemId, long amount);
}
