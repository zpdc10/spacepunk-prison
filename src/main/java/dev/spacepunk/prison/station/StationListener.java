package dev.spacepunk.prison.station;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Hub behaviour: join and respawn at the hub, terminals, and a protected, damage-free station world. */
final class StationListener implements Listener {
    static final String BUILD_PERMISSION = "spacepunk.station.build";

    private final StationModule module;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    StationListener(StationModule module) { this.module = module; }

    private boolean inStation(org.bukkit.World w) { return w != null && w.getName().equals(module.worldName()); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        if (!module.config().getBoolean("join.send-to-hub", true)) return;
        Location hub = module.warps().hub();
        if (hub != null) e.getPlayer().teleportAsync(hub);
    }

    /** LOW so zone modules (e.g. the Undercroft) can choose their own respawn at a higher priority. */
    @EventHandler(priority = EventPriority.LOW)
    public void onRespawn(PlayerRespawnEvent e) {
        if (!module.config().getBoolean("join.respawn-at-hub", true)) return;
        Location hub = module.warps().hub();
        if (hub != null) e.setRespawnLocation(hub);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !inStation(b.getWorld())) return;
        Player p = e.getPlayer();
        String key = e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK
                ? module.terminalAt(b.getX(), b.getY(), b.getZ()) : null;
        if (key != null) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            if (e.getAction() == Action.LEFT_CLICK_BLOCK && p.hasPermission(BUILD_PERMISSION) && p.isSneaking()) return;
            long now = System.currentTimeMillis();
            Long last = lastUse.get(p.getUniqueId());
            if (last != null && now - last < 600) return;
            lastUse.put(p.getUniqueId(), now);
            p.playSound(b.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
            module.runTerminal(p, key);
            return;
        }
        // Decorations (barrels, brewing stands, buttons) are props for everyone but builders.
        if (!p.hasPermission(BUILD_PERMISSION) && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
        if (!p.hasPermission(BUILD_PERMISSION) && e.getAction() == Action.PHYSICAL) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (inStation(e.getBlock().getWorld()) && (module.building() || !e.getPlayer().hasPermission(BUILD_PERMISSION))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (inStation(e.getBlock().getWorld()) && (module.building() || !e.getPlayer().hasPermission(BUILD_PERMISSION))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent e) {
        if (inStation(e.getEntity().getWorld()) && !(e.getRemover() instanceof Player p && p.hasPermission(BUILD_PERMISSION))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && inStation(e.getEntity().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (inStation(e.getEntity().getWorld()) && e.getFoodLevel() < e.getEntity().getFoodLevel()) e.setCancelled(true);
    }

    /** Anyone who falls out of the hub is caught and put back on the concourse. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getY() >= 40 || !inStation(e.getTo().getWorld())) return;
        Location hub = module.warps().hub();
        if (hub != null) {
            e.getPlayer().setFallDistance(0);
            e.getPlayer().teleportAsync(hub);
        }
    }
}
