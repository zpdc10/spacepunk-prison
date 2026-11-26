package dev.spacepunk.prison.data;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired on the main thread once a joining player's profile is ready. Use this instead of PlayerJoinEvent for anything that reads the profile. */
public class ProfileLoadedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final PlayerProfile profile;

    public ProfileLoadedEvent(Player player, PlayerProfile profile) {
        this.player = player;
        this.profile = profile;
    }

    public Player player() { return player; }
    public PlayerProfile profile() { return profile; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
