package dev.spacepunk.prison.api;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * The player's drill (custom pickaxe). Implemented by the drill module.
 * Other modules read drill effects that touch their systems through this, e.g. progression checks
 * {@link #autoSell} and {@link #sellMultiplier} when it stores or sells ore. Main thread, online players only.
 */
public interface DrillService {
    /** True when the drill has a module socketed that sells mined ore immediately (Magnet Coil). */
    boolean autoSell(UUID player);

    /** Extra sell-price multiplier from drill modules (1.0 = none; Scavenger Rig lowers it). Multiply into the sell price. */
    double sellMultiplier(UUID player);

    /** Multiplier for rare drop odds from Salvage Sense and modules (1.0 = none). For the rare drops system. */
    double rareDropMultiplier(UUID player);

    /** Current level of a drill enchant by id (e.g. "yield"), 0 if none. */
    int enchantLevel(UUID player, String enchantId);

    /** Frame tier id, e.g. "rusted", "salvaged", "industrial", "military", "prototype". */
    String frameId(UUID player);

    /** True if the item is a drill (anyone's). */
    boolean isDrill(ItemStack item);

    /**
     * Drill module ids of one rarity ("COMMON", "RARE", "EPIC", "LEGENDARY"; case-insensitive) mapped to their
     * find weight. Empty if none. Used by the rare drops system to assemble modules from blueprint fragments.
     */
    java.util.Map<String, Double> moduleWeights(String rarity);

    /** A new drill module item for this module id, or null if the id is unknown. */
    ItemStack createModuleItem(String moduleId);

    /** True if the item is a drill module item. */
    boolean isModule(ItemStack item);
}
