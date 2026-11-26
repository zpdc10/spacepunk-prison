package dev.spacepunk.prison.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after a player's grade or prestige changes (rank-up, prestige, admin). */
public class GradeChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int oldGrade, newGrade, oldPrestige, newPrestige;

    public GradeChangeEvent(Player player, int oldGrade, int newGrade, int oldPrestige, int newPrestige) {
        this.player = player;
        this.oldGrade = oldGrade;
        this.newGrade = newGrade;
        this.oldPrestige = oldPrestige;
        this.newPrestige = newPrestige;
    }

    public Player player() { return player; }
    public int oldGrade() { return oldGrade; }
    public int newGrade() { return newGrade; }
    public int oldPrestige() { return oldPrestige; }
    public int newPrestige() { return newPrestige; }
    public boolean isPrestige() { return newPrestige > oldPrestige; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
