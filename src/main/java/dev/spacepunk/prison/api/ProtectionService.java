package dev.spacepunk.prison.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Map protection, server-wide and default-deny. Nobody (ops included) can break, place or alter blocks
 * anywhere unless a rule allows it: the block is inside a mine pit, a module registered a rule that allows it
 * (e.g. the owner's private workshop bay), or the player is an admin with build mode switched on (/buildmode,
 * off by default and off again on rejoin). Explosions, fire, liquids, pistons and other world changes are
 * blocked the same way unless an environment rule allows that block.
 *
 * Provided by core (always registered). Register rules from your module's enable(). Rules must be cheap.
 * A rule only lets the vanilla event through: your own listeners still decide what actually happens.
 */
public interface ProtectionService {
    /** Lets players break a block when the rule returns true. */
    void allowBreak(String owner, BiPredicate<Player, Block> rule);

    /** Lets players place a block (and empty buckets, use hoes/axes, edit signs...) when the rule returns true. */
    void allowPlace(String owner, BiPredicate<Player, Block> rule);

    /**
     * Lets players right-click a block that would otherwise be locked (containers, pots, cauldrons, beds...)
     * or use shaping tools on it. Map containers are locked by default so they can't become shared storage.
     */
    void allowUse(String owner, BiPredicate<Player, Block> rule);

    /** Lets the world change this block on its own (liquid flow, pistons, explosions, fire, growth). */
    void allowEnvironment(String owner, Predicate<Block> rule);

    /** Removes every rule registered under this owner. */
    void clearRules(String owner);

    boolean canBreak(Player player, Block block);

    boolean canPlace(Player player, Block block);

    boolean canUse(Player player, Block block);

    /** True while an admin has /buildmode on. */
    boolean buildMode(UUID player);
}
