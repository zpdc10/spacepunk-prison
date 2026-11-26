package dev.spacepunk.prison.api.event;

import dev.spacepunk.prison.api.MineService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Fired by the mines module whenever a player breaks one or more blocks inside a mine,
 * by hand or through an enchant/module. The blocks are already gone when this fires.
 *
 * Listeners:
 *  - drill: proc enchants (only for cause HAND, to avoid chains), add heat, roll Tokens/rare drops
 *  - progression: put {@link #ores()} into the cargo hold (or auto-sell)
 *
 * {@link #ores()} is mutable so modifiers (Yield/Fortune) can multiply amounts before progression stores them.
 * Listen at EventPriority.NORMAL to modify, MONITOR to consume. Progression consumes at HIGHEST.
 */
public class MineBlocksBrokenEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final MineService.MineInfo mine;
    private final MineService.BreakCause cause;
    private final int blockCount;
    private final Map<Material, Long> ores;
    private final Block origin;

    public MineBlocksBrokenEvent(Player player, MineService.MineInfo mine, MineService.BreakCause cause,
                                 int blockCount, Map<Material, Long> ores) {
        this(player, mine, cause, blockCount, ores, null);
    }

    /**
     * @param origin the block the player broke by hand (cause HAND), so area enchants know where to center.
     *               May be null for bulk breaks.
     */
    public MineBlocksBrokenEvent(Player player, MineService.MineInfo mine, MineService.BreakCause cause,
                                 int blockCount, Map<Material, Long> ores, Block origin) {
        this.player = player;
        this.mine = mine;
        this.cause = cause;
        this.blockCount = blockCount;
        this.ores = ores;
        this.origin = origin;
    }

    public Player player() { return player; }
    public MineService.MineInfo mine() { return mine; }
    public MineService.BreakCause cause() { return cause; }
    /** Number of blocks physically removed. */
    public int blockCount() { return blockCount; }
    /** Ore yield keyed by the mined block's Material (mutable). */
    public Map<Material, Long> ores() { return ores; }
    /** The hand-broken block (now air) for cause HAND, or null if the firer did not supply it. */
    public Block origin() { return origin; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
