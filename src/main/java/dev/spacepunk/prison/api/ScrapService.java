package dev.spacepunk.prison.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Scrap parts: salvaged electronics and machine parts (circuit boards, capacitors, servo motors...) that drop
 * from every activity. Each part comes in three states: FRIED (common, refurbish it in the workshop), WORKING and
 * PRISTINE (rare). Parts are physical, stackable, tradeable items used to craft drill modules (Assembly Bench),
 * to fuse modules to higher marks, and later for other gear. Implemented by the rare drops module. Main thread.
 */
public interface ScrapService {
    enum State {
        FRIED, WORKING, PRISTINE;

        public static State parse(String s) {
            if (s == null) return WORKING;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return WORKING;
            }
        }
    }

    /** One stack's worth of a part. */
    record Part(String partId, State state, String tier) {}

    /** Activity sources for {@link #roll}. Each has its own chance and signature parts in raredrops.yml. */
    String MINING = "mining", FISHING = "fishing", SEA_CREATURE = "sea_creature", PARKOUR = "parkour",
            WORKSHOP = "workshop", UNDERCROFT_KILL = "undercroft_kill", SMUGGLE = "smuggle", CONTRACT = "contract",
            STORY = "story";

    /**
     * Rolls scrap finds for an activity: trials chances at the source's base chance times chanceMultiplier
     * (drill modules and the like are applied inside). Found parts go to the player's inventory, overflow at their
     * feet, with an action bar flash. Returns how many parts were found.
     */
    int roll(Player player, String source, int trials, double chanceMultiplier);

    /** Every part id, in config order. */
    List<String> partIds();

    /** Coloured name (MiniMessage) of a part in a state, e.g. "Fried Circuit Board". */
    String partName(String partId, State state);

    /** The part an item is, if it is one. */
    Optional<Part> part(ItemStack item);

    /** A part item, or null for an unknown id. */
    ItemStack create(String partId, State state, int amount);

    /** Parts of this id at least this good in the player's inventory. */
    int count(Player player, String partId, State atLeast);

    /** Takes parts of this id at least this good (worst qualifying first). False (and nothing taken) if short. */
    boolean take(Player player, String partId, State atLeast, int amount);

    /** Gives an item: inventory first, the rest at the player's feet (owner-only pickup). True if it all fit. */
    boolean give(Player player, ItemStack item);
}
