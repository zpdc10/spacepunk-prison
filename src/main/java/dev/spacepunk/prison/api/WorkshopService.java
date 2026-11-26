package dev.spacepunk.prison.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Player workshops (private hull bays with processing machines). Implemented by the workshop module.
 *
 * Goods and components are stored in each player's Bay Locker, keyed by item id such as
 * "spacepunk:wiring_harness" (finished goods may carry a quality suffix: "#1" Refined, "#2" Pristine; the
 * methods below that take a plain id count or take every quality). Other modules use this to spend
 * components (gun parts, upgrades) or to hand out goods. Main thread only; locker methods work for online
 * players only (their profile must be loaded).
 */
public interface WorkshopService {
    /** Every workshop good id (spacepunk:...), in pipeline order. */
    java.util.List<String> goodIds();

    /** Display name of any item id (workshop good or mined ore), as MiniMessage without colour. */
    String itemName(String itemId);

    /** Base market value of one unit of a good (before daily demand and sell bonuses). 0 if unknown. */
    double baseValue(String itemId);

    /** Units in the player's Bay Locker (all qualities if itemId has no "#" suffix). */
    long lockerAmount(UUID player, String itemId);

    /** Read-only snapshot of the Bay Locker. Empty if not loaded. */
    Map<String, Long> lockerContents(UUID player);

    /** Takes up to amount from the Bay Locker (lowest quality first for plain ids). Returns how many were taken. */
    long lockerTake(UUID player, String itemId, long amount);

    /** Adds up to amount (limited by locker space). Returns how many were added. */
    long lockerGive(UUID player, String itemId, long amount);

    /** A physical item for a workshop good (for trading, auction, rewards), or empty for unknown ids. */
    Optional<ItemStack> createItem(String itemId, int amount);

    /** The item id of a physical workshop good item, or empty if it isn't one. */
    Optional<String> itemId(ItemStack item);

    /** True if this location is in the workshop world. */
    boolean isWorkshop(Location location);

    /** Owner of the bay containing this location, if any. */
    Optional<UUID> bayOwnerAt(Location location);

    /** Where a player arrives in their own bay (builds the bay first if needed). Null if not available yet. */
    Location bayLocation(Player player);

    /** Sends the player to their own bay, building it first if needed. */
    void sendToBay(Player player);
}
