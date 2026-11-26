package dev.spacepunk.prison.workshop;

import dev.spacepunk.prison.data.ProfileLoadedEvent;
import dev.spacepunk.prison.util.Text;
import dev.spacepunk.prison.workshop.BayData.MachineData;
import dev.spacepunk.prison.workshop.PlotRegistry.Plot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Bay protection (only owner and crew build, shell and machines can't be broken, no fluids, fire, mobs or PvP),
 * machine placement, machine and terminal clicks, conveyor bookkeeping, and profile load/save.
 */
final class WorkshopListener implements Listener {
    private static final java.util.Set<String> BLOCKED_SPAWNS = java.util.Set.of(
            "NATURAL", "JOCKEY", "CHUNK_GEN", "SPAWNER", "REINFORCEMENTS", "PATROL", "RAID", "VILLAGE_INVASION", "TRAP");
    private final Plugin plugin;
    private final WorkshopManager ws;
    private final WorkshopMenus menus;

    WorkshopListener(Plugin plugin, WorkshopManager ws, WorkshopMenus menus) {
        this.plugin = plugin;
        this.ws = ws;
        this.menus = menus;
    }

    private boolean inWorld(org.bukkit.World w) { return ws.world.isWorld(w); }

    // ---- profiles ----

    @EventHandler
    public void onLoaded(ProfileLoadedEvent e) {
        ws.load(e.profile());
        Player p = e.player();
        // Let progression load the cargo hold first, then catch up on time away.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) ws.catchUp(p);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) { ws.unload(e.getPlayer().getUniqueId()); }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (inWorld(e.getFrom())) ws.clearHud(e.getPlayer());
    }

    // ---- clicks ----

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        String machineType = ws.items.machineId(hand);
        Block clicked = e.getClickedBlock();

        if (!inWorld(p.getWorld())) {
            if (machineType != null && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                e.setCancelled(true);
                if (e.getHand() == EquipmentSlot.HAND) Text.send(p, "<gray>Machines go in your hull bay. <white>/ws");
            }
            return;
        }
        if (clicked == null || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Plot plot = ws.plotAt(clicked.getLocation());
        if (plot == null) return;

        // Terminal
        if (clicked.getType() == ws.cfg().shell.terminal() && clicked.equals(ws.world.terminal(plot))) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            if (plot.ownerId().equals(p.getUniqueId())) menus.main(p);
            else menus.visitorInfo(p, plot);
            return;
        }

        // Machine
        Bay bay = ws.bay(plot.ownerId());
        if (clicked.getType() == Material.BARRIER) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            if (bay == null) {
                Text.send(p, "<gray>The owner is offline, so these machines are powered down.");
                return;
            }
            MachineData m = bay.machineAt(clicked.getX(), clicked.getY(), clicked.getZ());
            if (m == null) return;
            if (!ws.canBuild(p, plot)) {
                Text.send(p, "<gray>Only " + plot.name + " and their crew can use these machines.");
                return;
            }
            menus.machine(p, bay, m);
            return;
        }

        // Placing a machine
        if (machineType != null) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            Block target = clicked.getRelative(e.getBlockFace());
            if (ws.place(p, target, machineType, ws.items.machineTier(hand), p.getFacing())) {
                if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            }
            return;
        }

        // Conveyors: no delay clicking.
        if (clicked.getType() == Material.REPEATER && (hand == null || !hand.getType().isBlock() || !p.isSneaking())) {
            e.setCancelled(true);
            return;
        }

        // Containers and other interactables in someone else's bay.
        if (!ws.canBuild(p, plot) && clicked.getType().isInteractable()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!inWorld(e.getPlayer().getWorld())) return;
        Plot plot = ws.plotAt(e.getRightClicked().getLocation());
        if (plot != null && !ws.canBuild(e.getPlayer(), plot)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!inWorld(e.getPlayer().getWorld())) return;
        Plot plot = ws.plotAt(e.getRightClicked().getLocation());
        if (plot != null && !ws.canBuild(e.getPlayer(), plot)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!inWorld(e.getEntity().getWorld())) return;
        Plot plot = ws.plotAt(e.getEntity().getLocation());
        if (plot == null || !(e.getRemover() instanceof Player p) || !ws.canBuild(p, plot)) e.setCancelled(true);
    }

    // ---- building ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        boolean conveyor = ws.items.isConveyor(e.getItemInHand());
        if (!inWorld(b.getWorld())) {
            if (conveyor) {
                e.setCancelled(true);
                Text.send(e.getPlayer(), "<gray>Conveyors only work in your hull bay. <white>/ws");
            }
            return;
        }
        Plot plot = ws.plotAt(b.getLocation());
        if (plot == null || !ws.canBuild(e.getPlayer(), plot) || !ws.world.inInterior(plot, b.getX(), b.getY(), b.getZ())) {
            e.setCancelled(true);
            return;
        }
        Material t = b.getType();
        if (t == Material.BARRIER || t == Material.WATER || t == Material.LAVA) {
            e.setCancelled(true);
            return;
        }
        if (t == Material.REPEATER) {
            if (!conveyor) {
                e.setCancelled(true);
                return;
            }
            // A repeater's facing points back toward its input; items flow the other way.
            BlockFace flow = b.getBlockData() instanceof Directional d ? d.getFacing().getOppositeFace() : e.getPlayer().getFacing();
            ws.conveyorPlaced(b, flow);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!inWorld(b.getWorld())) return;
        Plot plot = ws.plotAt(b.getLocation());
        if (plot == null || !ws.canBuild(e.getPlayer(), plot) || ws.world.inShell(plot, b.getX(), b.getY(), b.getZ())
                || !ws.world.inInterior(plot, b.getX(), b.getY(), b.getZ())) {
            e.setCancelled(true);
            return;
        }
        if (b.getType() == Material.BARRIER) {
            e.setCancelled(true);
            return;
        }
        if (b.getType() == Material.REPEATER) {
            e.setDropItems(false);
            ws.conveyorRemoved(b);
            if (e.getPlayer().getGameMode() != GameMode.CREATIVE) WorkshopManager.give(e.getPlayer(), ws.items.conveyor(1));
        }
    }

    /** A conveyor knocked off by physics drops a plain repeater: swap it for a conveyor item. */
    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        if (!inWorld(item.getWorld())) return;
        ItemStack s = item.getItemStack();
        if (s.getType() == Material.REPEATER && !ws.items.isConveyor(s)) {
            item.setItemStack(ws.items.conveyor(s.getAmount()));
            ws.conveyorRemoved(item.getLocation().getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) { if (inWorld(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) { if (inWorld(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (inWorld(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) { if (inWorld(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { if (inWorld(e.getLocation().getWorld())) e.blockList().clear(); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { if (inWorld(e.getBlock().getWorld())) e.blockList().clear(); }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!inWorld(e.getLocation().getWorld())) return;
        if (BLOCKED_SPAWNS.contains(e.getSpawnReason().name())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (inWorld(e.getEntity().getWorld()) && e.getEntity() instanceof Player && e.getDamager() instanceof Player) e.setCancelled(true);
    }

    /** Never fall into the void: back to a bay spawn. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getY() >= ws.cfg().floorY - 12 || !inWorld(e.getTo().getWorld())) return;
        Player p = e.getPlayer();
        Plot plot = ws.plotAt(p.getLocation());
        p.setFallDistance(0);
        p.teleportAsync(plot != null && plot.built && ws.canEnter(p, plot) ? ws.world.spawn(plot) : ws.fallbackLocation(p));
    }
}
