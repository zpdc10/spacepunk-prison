package dev.spacepunk.prison.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * The Undercroft: the lawless lower hull and the only PvP zone on the station. Implemented by the undercroft
 * module. Gangs use the lab methods to hold drug-lab territory; guns ask {@link #pvpAllowed} before hurting
 * players. Main thread only.
 */
public interface UndercroftService {
    /** True if the location is in the Undercroft world (checkpoint included). */
    boolean inUndercroft(Location location);

    /** True if players may hurt each other at this location (Undercroft rings, not the checkpoint). */
    boolean pvpAllowed(Location location);

    /** Ring number at this location: 0 = checkpoint (safe), 1..n = rings (deeper = riskier), -1 = not in the Undercroft. */
    int ringAt(Location location);

    /** True while the player is combat tagged (cannot log out, leave or use commands without penalty). */
    boolean combatTagged(UUID player);

    /** True if the item is contraband (stims, ingredients, hot ore, bounty tags, smuggled tech). */
    boolean isContraband(ItemStack item);

    /** Drug labs where stims are cooked. Gangs can contest them. */
    Collection<LabInfo> labs();

    Optional<LabInfo> lab(String labId);

    /**
     * Sets (or clears, with null) who controls a lab, for example a gang id. The controller gets the cut set in
     * undercroft.yml of every batch cooked there, delivered through {@link dev.spacepunk.prison.api.event.StimCookedEvent}
     * (the gangs module pays it out). Announces the change server-wide when announce is true.
     */
    void setLabController(String labId, String controllerId, String controllerName, boolean announce);

    /** Stim currently active on the player (e.g. "overdrive"), or empty. Comedowns are not stims. */
    Optional<String> activeStim(Player player);

    /** Read-only view of a lab. */
    interface LabInfo {
        String id();
        String displayName();
        Location location();
        int ring();
        /** Controller id (e.g. a gang id), or empty when nobody holds the lab. */
        Optional<String> controllerId();
        Optional<String> controllerName();
        /** True while someone is cooking at this lab. */
        boolean cooking();
    }
}
