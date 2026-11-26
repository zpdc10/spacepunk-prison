package dev.spacepunk.prison.api;

import org.bukkit.entity.Player;

/**
 * Quests, tutorials and daily/weekly contracts. Implemented by the quests module.
 *
 * Any module can feed quest progress: call {@link #progress} (or the null-safe {@link Objectives#report}) whenever
 * the player does something a quest could ask for. A quest objective has a type (see {@link ObjectiveTypes}) and
 * an optional set of targets; a report counts toward it when the type matches and one of the reported targets is
 * one of the objective's targets (an objective without targets accepts any report of its type).
 *
 * Modules that add a new activity (fishing, workshop, gangs...) call {@link #registerObjectiveType} from their
 * enable(): quests.yml entries that use a type nobody registered stay hidden, so tutorials and contracts for a
 * system only appear once that system is installed. Main thread only.
 */
public interface QuestService {
    /** Makes an objective type available to quests and contracts. displayName is shown in menus, e.g. "Catch fish". */
    void registerObjectiveType(String type, String displayName);

    /** True once a module registered this type (the quests module registers every type it reports itself). */
    boolean hasObjectiveType(String type);

    /**
     * Adds progress for an online player. targets describe what was done, most specific first, for example
     * ("minecraft:cod", "rarity:common") for a fish, or ("mine:cryo-11", "deck:3") for mined blocks.
     */
    void progress(Player player, String type, long amount, String... targets);
}
