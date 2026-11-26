package dev.spacepunk.prison.core;

import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.UndercroftService;
import dev.spacepunk.prison.api.WarpService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

/**
 * Nobody dies outside a PvP zone. The Undercroft's PvP rings ({@link UndercroftService#pvpAllowed}) are the only
 * place where players take real damage and drop items; everywhere else (station, mines, parkour, fishing decks,
 * workshop bays, the Undercroft's Guard Checkpoint):
 * <ul>
 *   <li>environmental damage (falls, void, lava, fire, drowning, suffocation, starvation, ...) is cancelled,
 *       with void falls and suffocation moving the player somewhere safe;</li>
 *   <li>player-vs-player damage is cancelled;</li>
 *   <li>damage from mobs (fishing sea creatures) still lands but can never be lethal;</li>
 *   <li>hunger does not drain.</li>
 * </ul>
 * Safety net: any death outside a PvP zone (an admin /kill, a plugin bug) keeps the whole inventory and XP.
 * Parkour falls are untouched: the course resets a player to their checkpoint by height before they land.
 */
public final class SafeZones implements Listener {
    private final Plugin plugin;

    public SafeZones(Plugin plugin) { this.plugin = plugin; }

    public void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }

    static boolean pvpZone(Location l) {
        return l != null && Services.find(UndercroftService.class).map(u -> u.pvpAllowed(l)).orElse(false);
    }

    // Runs after every module's own rules so it is the final word on whether a hit lands.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || pvpZone(p.getLocation())) return;
        DamageCause cause = e.getCause();
        if (cause == DamageCause.KILL) return; // admin /kill; the death safety net below keeps their items

        Entity source = e.getDamageSource().getCausingEntity();
        if (source == null && e.getDamageSource().getDirectEntity() instanceof Projectile pr && pr.getShooter() instanceof Entity sh) {
            source = sh;
        }
        if (source != null && source != p && !(source instanceof Player)) {
            // A mob hit: it can hurt, never kill.
            if (e.getFinalDamage() >= p.getHealth() + p.getAbsorptionAmount()) e.setCancelled(true);
            return;
        }

        e.setCancelled(true);
        switch (cause) {
            case VOID -> rescueFromVoid(p);
            case SUFFOCATION -> escapeBlock(p);
            case FIRE, FIRE_TICK, LAVA, CAMPFIRE, HOT_FLOOR -> p.setFireTicks(0);
            case FALL -> p.setFallDistance(0);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && e.getFoodLevel() < p.getFoodLevel() && !pvpZone(p.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (pvpZone(p.getLocation())) return; // Undercroft rules: contraband and bounty tags drop as before
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    /** Falling out of a world puts the player back at the hub (or the world spawn if there is no hub yet). */
    private void rescueFromVoid(Player p) {
        p.setFallDistance(0);
        p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        Location hub = Services.find(WarpService.class).map(WarpService::hub).orElse(null);
        p.teleportAsync(hub != null ? hub : p.getWorld().getSpawnLocation());
    }

    /** Stuck inside blocks (e.g. a mine refilled around them): lift the player to the first open air above. */
    private void escapeBlock(Player p) {
        Location l = p.getLocation();
        World w = l.getWorld();
        for (int y = l.getBlockY() + 1; y < w.getMaxHeight() - 1; y++) {
            Block feet = w.getBlockAt(l.getBlockX(), y, l.getBlockZ());
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && !feet.getRelative(0, -1, 0).isPassable()) {
                Location to = feet.getLocation().add(0.5, 0, 0.5);
                to.setYaw(l.getYaw());
                to.setPitch(l.getPitch());
                p.teleportAsync(to);
                return;
            }
        }
        Location hub = Services.find(WarpService.class).map(WarpService::hub).orElse(null);
        if (hub != null) p.teleportAsync(hub);
    }
}
