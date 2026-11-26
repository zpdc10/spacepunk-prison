package dev.spacepunk.prison.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

/**
 * Mines: regions that refill. Implemented by the mines module.
 * Other modules (drill enchants) use this to break blocks in bulk and to ask where a block belongs.
 */
public interface MineService {
    /** The mine containing this block, if any. */
    Optional<MineInfo> mineAt(Block block);

    /**
     * Breaks the given blocks on behalf of a player as part of mining (e.g. Shaped Charge, Jackhammer).
     * Blocks outside any mine, or air, are skipped. Fires one {@link dev.spacepunk.prison.api.event.MineBlocksBrokenEvent}
     * with the drops; does NOT drop items in the world. Returns the number of blocks actually broken.
     */
    int breakBlocks(Player player, Collection<Block> blocks, BreakCause cause);

    /** Read-only view of a mine. */
    interface MineInfo {
        String id();
        String displayName();
        /** Minimum grade (1..25) needed to enter. */
        int requiredGrade();
        /** 0.0..1.0 share of blocks still present. */
        double fillRatio();
        /** True if the block is inside the mine's breakable region. */
        boolean contains(Block block);
    }

    enum BreakCause { HAND, ENCHANT, MODULE, ADMIN }
}
