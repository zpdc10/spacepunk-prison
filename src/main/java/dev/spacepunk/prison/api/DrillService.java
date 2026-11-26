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

    /** A new Mk I drill module item for this module id, or null if the id is unknown. */
    ItemStack createModuleItem(String moduleId);

    /** A new drill module item at a mark (1 = Mk I), or null if the id is unknown. */
    ItemStack createModuleItem(String moduleId, int mark);

    /** True if the item is a drill module item. */
    boolean isModule(ItemStack item);

    /** Module id of a module item, or null. */
    String moduleId(ItemStack item);

    /** Mark of a module item (1 = Mk I), 0 if it isn't one. */
    int moduleMark(ItemStack item);

    /** Every drill module id, in config order. */
    java.util.List<String> moduleIds();

    /** Coloured display name (MiniMessage) of a module id. */
    String moduleName(String moduleId);

    /** Rarity name of a module ("COMMON".."LEGENDARY"), or null if unknown. */
    String moduleRarity(String moduleId);

    /** True if the player has learned this module's schematic (Assembly Bench recipe). Online players only. */
    boolean knowsSchematic(UUID player, String moduleId);

    /** Teaches a schematic. False if already known, unknown module, or the player isn't loaded. */
    boolean learnSchematic(UUID player, String moduleId);

    /**
     * Teaches a random schematic of a rarity that the player doesn't know yet (weighted like module finds).
     * Returns the module id, or null if they already know every one of that rarity.
     */
    String learnRandomSchematic(UUID player, String rarity);

    /** Multiplier on scrap part find odds from drill modules (1.0 = none). */
    double scrapMultiplier(UUID player);

    /**
     * Stat multipliers from the modules on one of the player's other rigs: rigKey "rod" (Rod Rig) or
     * "gun:<gun id>" (that gun's Weapon Rig). Stat -> multiplier (1.0 or missing = no change). Empty if nothing is
     * fitted. Rod stats are fishing effect names (bite-speed, rare-luck, ...): add (multiplier - 1) to the effect.
     * Gun stats: damage, fire-rate, spread, recoil, magazine, reload-time, range, headshot.
     */
    java.util.Map<String, Double> gearMultipliers(UUID player, String rigKey);

    /**
     * Rolls a module find for an activity: kind "drill" / "rod" / "gun" / "exo", source a drill.yml module-drops key
     * (e.g. "parkour", "contract"). Gives and announces the module on a hit.
     */
    void rollModuleFind(org.bukkit.entity.Player player, String kind, String source);

    /** Opens a module rig menu: "drill", "rod", "exo" or "gun:<gun id>". */
    void openRig(org.bukkit.entity.Player player, String rigKey);
}
