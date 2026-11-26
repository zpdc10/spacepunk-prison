package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after one player kills another in the Undercroft. For gang turf wars, quests and leaderboards. */
public class UndercroftKillEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player killer;
    private final Player victim;
    private final int ring;
    private final boolean tagDropped;
    private final String weapon;

    public UndercroftKillEvent(Player killer, Player victim, int ring, boolean tagDropped, String weapon) {
        this.killer = killer;
        this.victim = victim;
        this.ring = ring;
        this.tagDropped = tagDropped;
        this.weapon = weapon;
    }

    public Player killer() { return killer; }
    public Player victim() { return victim; }
    /** Ring the victim died in (1 = outer). */
    public int ring() { return ring; }
    /** False when the kill was a repeat on the same victim (anti-farm), so no bounty tag dropped. */
    public boolean tagDropped() { return tagDropped; }
    /** Gun id (e.g. "bolt_rifle") or "melee". */
    public String weapon() { return weapon; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
