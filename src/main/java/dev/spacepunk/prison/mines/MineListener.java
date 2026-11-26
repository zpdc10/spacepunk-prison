package dev.spacepunk.prison.mines;

import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.util.Text;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mining, protection and PvP rules for mines and the mines world. */
final class MineListener implements Listener {
    private final MineManager manager;
    private final Map<UUID, Long> lastDenied = new HashMap<>();

    MineListener(MineManager manager) {
        this.manager = manager;
    }

    private boolean inMinesWorld(Entity e) {
        return e.getWorld().getName().equals(manager.config().settings().world());
    }

    private boolean inMinesWorld(Block b) {
        return b.getWorld().getName().equals(manager.config().settings().world());
    }

    // ignoreCancelled: drill cancels breaks at LOWEST while overheated, and that must stop mining here.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block block = event.getBlock();
        Mine mine = manager.mineFor(block);
        if (mine == null) {
            if (manager.config().settings().protectWorld() && inMinesWorld(block) && !p.hasPermission(MineManager.BUILD_PERMISSION)) {
                event.setCancelled(true);
            }
            return;
        }
        // We remove the block ourselves: no vanilla drops, xp, durability or physics.
        event.setCancelled(true);
        if (mine.refilling()) return;
        if (!manager.canAccess(p, mine)) {
            deny(p, "<red>You need <white>Grade " + mine.requiredGrade() + "</white> to mine in " + mine.displayName() + ".");
            return;
        }
        manager.breakBlocks(p, List.of(block), MineService.BreakCause.HAND);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        boolean inMine = manager.mineFor(block) != null;
        boolean protectedWorld = manager.config().settings().protectWorld() && inMinesWorld(block);
        if ((inMine || protectedWorld) && !event.getPlayer().hasPermission(MineManager.BUILD_PERMISSION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (manager.config().settings().pvp()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker == victim) return;
        if (inMinesWorld(victim) || inAnyMine(victim) || inAnyMine(attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (manager.config().settings().fallDamage()) return;
        if (event.getEntity() instanceof Player p && (inMinesWorld(p) || inAnyMine(p))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        boolean world = manager.config().settings().protectWorld() && event.getEntity().getWorld().getName().equals(manager.config().settings().world());
        if (world) event.blockList().clear();
        else event.blockList().removeIf(b -> manager.mineFor(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        boolean world = manager.config().settings().protectWorld() && inMinesWorld(event.getBlock());
        if (world) event.blockList().clear();
        else event.blockList().removeIf(b -> manager.mineFor(b) != null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer());
        lastDenied.remove(event.getPlayer().getUniqueId());
    }

    private boolean inAnyMine(Player p) {
        for (Mine m : manager.mines()) {
            if (m.region() != null && m.region().near(p.getLocation(), 0, 0, 2)) return true;
        }
        return false;
    }

    private void deny(Player p, String msg) {
        long now = System.currentTimeMillis();
        Long last = lastDenied.get(p.getUniqueId());
        if (last != null && now - last < 2000) return;
        lastDenied.put(p.getUniqueId(), now);
        Text.send(p, msg);
    }
}
