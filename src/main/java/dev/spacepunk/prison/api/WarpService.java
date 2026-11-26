package dev.spacepunk.prison.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Warps (shuttles, lifts, airlocks) between the station hub and every other area. Implemented by the station
 * module. Any module can register its own destinations (fishing spots, the Undercroft, private workshop bays),
 * and they show up on the hub's Shuttle Board for their category. Look it up with
 * {@code Services.find(WarpService.class)} in or after your module's enable() and skip if it's absent.
 */
public interface WarpService {
    /** Public places reached by shuttle from the hub's Shuttle Bay board. */
    String PLANETSIDE = "planetside";
    /** High-risk zones (the Undercroft). */
    String DANGER = "danger";
    /** Per-mine parkour starts. */
    String PARKOUR = "parkour";
    /** Per-player or per-gang places (workshop bay, gang base); destination depends on the player. */
    String PRIVATE = "private";
    /** Places on the station itself, including "hub". */
    String STATION = "station";

    /** Registers or replaces a warp by id. */
    void register(Warp warp);

    void unregister(String id);

    Optional<Warp> get(String id);

    /** All warps, in registration order. */
    Collection<Warp> all();

    /**
     * Sends a player through a warp: checks the lock, fires {@link dev.spacepunk.prison.api.event.PlayerWarpEvent},
     * teleports and tells the player. Returns false (and tells the player why) if the warp is missing, locked,
     * has no destination for them, or the event was cancelled.
     */
    boolean warp(Player player, String id);

    /** The hub spawn on the station concourse, or null while the hub has not been built. */
    Location hub();

    /** Teleports the player to the hub (no-op if there is none yet). */
    void sendToHub(Player player);

    /**
     * One destination.
     *
     * @param id          unique id, lower-case, e.g. "fishing-tidepools", "undercroft", "workshop"
     * @param displayName MiniMessage name shown on the board and in chat
     * @param category    one of the constants above (or your own)
     * @param icon        board icon
     * @param description MiniMessage lore lines on the board
     * @param destination where this player goes; null means "not available for you right now"
     * @param lockReason  null when the player may use it, otherwise a MiniMessage reason (e.g. "Needs Grade 6")
     */
    record Warp(String id, String displayName, String category, Material icon, List<String> description,
                Function<Player, Location> destination, Function<Player, String> lockReason) {
        public Warp {
            description = description == null ? List.of() : List.copyOf(description);
            if (icon == null) icon = Material.ENDER_PEARL;
            if (lockReason == null) lockReason = p -> null;
        }

        /** Unlocked warp to a per-player destination. */
        public Warp(String id, String displayName, String category, Function<Player, Location> destination) {
            this(id, displayName, category, Material.ENDER_PEARL, List.of(), destination, p -> null);
        }

        /** Unlocked warp to one fixed location. */
        public static Warp fixed(String id, String displayName, String category, Material icon, Location to) {
            Location copy = to.clone();
            return new Warp(id, displayName, category, icon, List.of(), p -> copy.clone(), p -> null);
        }
    }
}
