package dev.spacepunk.prison.raredrops;

import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.event.MineBlocksBrokenEvent;
import dev.spacepunk.prison.api.event.OreSellEvent;
import dev.spacepunk.prison.data.ProfileLoadedEvent;
import dev.spacepunk.prison.raredrops.RareConfig.Drop;
import dev.spacepunk.prison.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Mining rolls, right-click use of rare items, crafting protection, collection sell bonus, profile load. */
final class RareListener implements Listener {
    private final RareDropsManager rare;

    RareListener(RareDropsManager rare) { this.rare = rare; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMine(MineBlocksBrokenEvent e) {
        if (e.cause() == MineService.BreakCause.ADMIN) return;
        int deck = Math.max(1, (e.mine().requiredGrade() - 1) / 5 + 1);
        rare.roll(e.player(), e.blockCount(), e.cause() == MineService.BreakCause.HAND, deck);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSell(OreSellEvent e) {
        double m = rare.sellMultiplier(e.player().getUniqueId());
        if (m != 1.0) e.multiplier(e.multiplier() * m);
    }

    @EventHandler
    public void onLoad(ProfileLoadedEvent e) {
        rare.load(e.profile());
        RareData d = rare.data(e.player().getUniqueId());
        if (d != null && !d.stash.isEmpty()) {
            Text.send(e.player(), "<gray>You have <white>" + d.stashed() + "</white> rare drops waiting in <white>/salvage</white>.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        rare.save(e.getPlayer().getUniqueId());
        rare.unload(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        Drop drop = rare.items().drop(item);
        if (drop == null) return;
        e.setCancelled(true); // rare items are never placed, eaten, thrown or used as their base item
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        switch (drop.type()) {
            case CACHE -> rare.openCaches(p, item, p.isSneaking());
            case RELIC -> {
                switch (rare.catalog(p, item)) {
                    case DUPLICATE -> Text.send(p, "<gray>" + drop.coloredName()
                            + " <gray>is already catalogued. Sell the duplicate on the Black Market (<white>/ah</white>).");
                    case NOT_LOADED -> Text.send(p, "<red>Your profile is still loading.");
                    default -> {}
                }
            }
            case FRAGMENT -> Text.send(p, "<gray>Assemble fragments into a drill module in <white>/salvage</white>.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (rare.items().dropId(e.getPlayer().getInventory().getItem(e.getHand())) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (rare.items().dropId(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (rare.items().dropId(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        for (ItemStack it : e.getInventory().getContents()) {
            if (rare.items().dropId(it) != null) {
                e.setResult(null);
                return;
            }
        }
    }
}
