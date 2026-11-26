package dev.spacepunk.prison.guns;

import dev.spacepunk.prison.guns.GunsConfig.GunDef;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Trigger, reload keys, scope and cleanup. */
final class GunListener implements Listener {
    private final GunManager guns;

    GunListener(GunManager guns) { this.guns = guns; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            // Never let a gun in the off hand do anything.
            if (guns.items().gunId(e.getItem()) != null) e.setCancelled(true);
            return;
        }
        ItemStack item = e.getItem();
        if (guns.items().gunId(item) == null) {
            if (guns.items().isOurs(item) && e.getAction().isRightClick()) e.setCancelled(true);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            guns.trigger(e.getPlayer(), item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        if (guns.items().gunId(item) == null) return;
        e.setCancelled(true);
        guns.trigger(e.getPlayer(), item);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        // Q on the held gun reloads instead of dropping it (only from the hotbar, not from an open inventory).
        if (guns.items().gunId(e.getItemDrop().getItemStack()) == null) return;
        if (p.getOpenInventory().getType() != InventoryType.CRAFTING) return;
        e.setCancelled(true);
        guns.startReload(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (guns.items().gunId(e.getPlayer().getInventory().getItemInMainHand()) == null
                && guns.items().gunId(e.getOffHandItem()) == null) return;
        e.setCancelled(true);
        guns.startReload(e.getPlayer());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        guns.cancelReload(e.getPlayer(), true);
        guns.unscope(e.getPlayer());
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking()) {
            guns.unscope(p);
            return;
        }
        GunDef g = guns.items().gun(p.getInventory().getItemInMainHand());
        if (g != null) guns.scope(p, g);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { guns.forget(e.getPlayer()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) { guns.forget(e.getEntity()); }

    /** Guns, ammo and parts are not crafting ingredients. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (guns.items().isOurs(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
