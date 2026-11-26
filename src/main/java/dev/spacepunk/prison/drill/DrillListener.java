package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.data.ProfileLoadedEvent;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Drill lifecycle (first join, respawn), soulbinding, the menu hotkey and venting. */
public final class DrillListener implements Listener {
    private final Plugin plugin;
    private final DrillManager drills;
    private final HeatManager heat;
    private final MiningListener mining;
    private final DrillMenu menu;

    public DrillListener(Plugin plugin, DrillManager drills, HeatManager heat, MiningListener mining, DrillMenu menu) {
        this.plugin = plugin;
        this.drills = drills;
        this.heat = heat;
        this.mining = mining;
        this.menu = menu;
    }

    // ---- lifecycle ----

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Leftover speed modifiers from a crash or restart mid-burst.
        HeatManager.clearModifiers(e.getPlayer());
    }

    @EventHandler
    public void onLoaded(ProfileLoadedEvent e) {
        Player p = e.player();
        boolean fresh = drills.load(e.profile());
        boolean given = drills.ensureDrill(p);
        int returned = drills.migrateSockets(p);
        if (returned > 0) {
            Text.send(p, "<#c8743a>Drill refit:</#c8743a> <gray>modules now fit on a <white>module rig</white> by shape. Your "
                    + returned + " module" + (returned == 1 ? " is" : "s are") + " back in your inventory: shift + right-click the drill to fit them.");
        }
        drills.ejectMisfits(p);
        drills.applyExo(p);
        if (fresh) {
            Text.send(p, "<gray>Station issue: one <#8a6a55>Rusted Drill</#8a6a55>. It's bound to you. Don't lose it (you can't).");
            Text.send(p, "<gray>Shift + right-click it to open the drill bay. Press <white>Q</white> to vent heat.");
        } else if (given) {
            Text.send(p, "<gray>Your drill was returned to your inventory.");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        heat.remove(p);
        mining.forget(p.getUniqueId());
        drills.unload(p.getUniqueId()); // LOW: before the profile is saved at MONITOR
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UuidMatch own = new UuidMatch(e.getEntity());
        e.getDrops().removeIf(own::isDrill);
        e.getItemsToKeep().removeIf(own::isForeignDrill);
        // Keep the drill in place rather than dropping it.
        for (ItemStack it : e.getEntity().getInventory().getContents()) {
            if (own.isDrill(it) && !e.getItemsToKeep().contains(it)) e.getItemsToKeep().add(it);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) drills.ensureDrill(p);
        });
    }

    private final class UuidMatch {
        private final Player p;

        UuidMatch(Player p) { this.p = p; }

        boolean isDrill(ItemStack it) { return drills.items().isDrill(it); }

        boolean isForeignDrill(ItemStack it) {
            return isDrill(it) && !p.getUniqueId().equals(drills.items().owner(it));
        }
    }

    // ---- hotkeys ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        if (drills.items().moduleId(hand) != null && e.getAction().isRightClick()) {
            // Modules are items, not tools: no placing or using them.
            e.setCancelled(true);
            if (p.isSneaking() || e.getAction() == Action.RIGHT_CLICK_AIR) {
                Text.send(p, "<gray>Open your drill bay (shift + right-click the drill) and click the module to install it.");
            }
            return;
        }
        if (!drills.items().isDrill(hand)) return;
        if (e.getAction().isRightClick() && p.isSneaking()) {
            e.setCancelled(true);
            if (p.getUniqueId().equals(drills.items().owner(hand))) menu.open(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack it = e.getItemDrop().getItemStack();
        if (!drills.items().isDrill(it)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        // Drop key on the drill = vent. Only from the hotbar (no inventory screen open).
        if (p.getOpenInventory().getType() == InventoryType.CRAFTING) heat.vent(p);
    }

    // ---- soulbound ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof DrillMenu.Holder) return; // the menu handles its own clicks
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        ItemStack hotbar = e.getClick() == ClickType.NUMBER_KEY ? e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) : null;
        ItemStack offhand = e.getClick() == ClickType.SWAP_OFFHAND ? e.getWhoClicked().getInventory().getItemInOffHand() : null;
        boolean drillInvolved = drills.items().isDrill(current) || drills.items().isDrill(cursor)
                || drills.items().isDrill(hotbar) || drills.items().isDrill(offhand);
        if (!drillInvolved) return;

        InventoryAction a = e.getAction();
        if (a == InventoryAction.DROP_ALL_CURSOR || a == InventoryAction.DROP_ONE_CURSOR
                || a == InventoryAction.DROP_ALL_SLOT || a == InventoryAction.DROP_ONE_SLOT) {
            e.setCancelled(true);
            return;
        }
        // Bundles could carry the drill off.
        if (isBundle(current) || isBundle(cursor)) {
            e.setCancelled(true);
            return;
        }
        Inventory top = e.getView().getTopInventory();
        boolean topIsOwn = top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE;
        if (topIsOwn) return;
        // Another container is open: the drill may only move within the player's own inventory.
        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        if (clickedTop || a == InventoryAction.MOVE_TO_OTHER_INVENTORY || a == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!drills.items().isDrill(e.getOldCursor())) return;
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE) return;
        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItem(e.getHand());
        if ((drills.items().isDrill(it) || drills.items().moduleId(it) != null) && e.getRightClicked() instanceof ItemFrame) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (drills.items().isDrill(e.getPlayerItem())) e.setCancelled(true);
    }

    private static boolean isBundle(ItemStack it) {
        return it != null && it.getType().name().endsWith("BUNDLE") && it.getType() != Material.AIR;
    }
}
