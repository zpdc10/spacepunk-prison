package dev.spacepunk.prison.api;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Guns for PvP zones. Implemented by the guns module. Other modules use this to hand out guns, ammo and gun parts
 * (Undercroft supply drops, quests, crates) and to recognise them. Main thread only.
 */
public interface GunService {
    /** Gun ids from guns.yml, e.g. "rivet_pistol", "bolt_rifle". */
    Set<String> gunIds();

    /** Ammo type ids from guns.yml, e.g. "pistol", "rifle", "shell", "cell". */
    Set<String> ammoIds();

    /** A fresh gun with a full magazine, or empty if the id is unknown. */
    Optional<ItemStack> createGun(String gunId);

    /** A stack of ammo, or empty if the type is unknown. */
    Optional<ItemStack> createAmmo(String ammoId, int amount);

    /** A stack of gun parts (the crafting currency at the gunsmith). */
    ItemStack createParts(int amount);

    /** Gun id if the item is a gun, else empty. */
    Optional<String> gunId(ItemStack item);

    /** Gun id of the last shot that hit this player within a few seconds, so kill feeds can name the weapon. */
    Optional<String> lastHitBy(UUID victim);
}
