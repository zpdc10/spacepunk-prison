package dev.spacepunk.prison.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired by the guns module when a shot hits a living entity, before damage is applied. {@link #damage()} is mutable
 * (stims, armor perks, gang upgrades). Cancel to block the hit (safe zones, grace periods). Main thread.
 */
public class GunHitEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player shooter;
    private final LivingEntity target;
    private final String gunId;
    private final boolean headshot;
    private double damage;
    private boolean cancelled;

    public GunHitEvent(Player shooter, LivingEntity target, String gunId, boolean headshot, double damage) {
        this.shooter = shooter;
        this.target = target;
        this.gunId = gunId;
        this.headshot = headshot;
        this.damage = damage;
    }

    public Player shooter() { return shooter; }
    public LivingEntity target() { return target; }
    public String gunId() { return gunId; }
    public boolean headshot() { return headshot; }
    public double damage() { return damage; }
    public void damage(double damage) { this.damage = Math.max(0, damage); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
