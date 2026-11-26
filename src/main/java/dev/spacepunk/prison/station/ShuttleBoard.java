package dev.spacepunk.prison.station;

import dev.spacepunk.prison.api.WarpService;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chest menu listing the warps of some categories; click one to go. */
final class ShuttleBoard implements Listener {
    private final WarpManager warps;

    ShuttleBoard(WarpManager warps) { this.warps = warps; }

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, String> slots = new HashMap<>();
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    void open(Player p, String title, List<String> categories) {
        List<WarpService.Warp> list = new ArrayList<>();
        for (WarpService.Warp w : warps.all()) if (categories.contains(w.category())) list.add(w);
        int rows = Math.max(1, Math.min(6, (list.size() + 8) / 9));
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, rows * 9, Text.mm("<dark_gray>" + title));
        h.inventory = inv;
        if (list.isEmpty()) {
            inv.setItem(4, icon(Material.BARRIER, "<gray>No shuttles in service yet",
                    List.of("<dark_gray>New routes open as the station grows.")));
        }
        int slot = 0;
        for (WarpService.Warp w : list) {
            if (slot >= rows * 9) break;
            String lock;
            try { lock = w.lockReason().apply(p); } catch (RuntimeException e) { lock = "offline"; }
            List<String> lore = new ArrayList<>(w.description());
            lore.add("");
            lore.add(lock == null ? "<green>Click to depart" : "<red>" + lock);
            inv.setItem(slot, icon(lock == null ? w.icon() : Material.BARRIER, w.displayName(), lore));
            h.slots.put(slot, w.id());
            slot++;
        }
        p.openInventory(inv);
    }

    private static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m.isItem() ? m : Material.ENDER_PEARL);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        meta.lore(Text.lore(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        String id = h.slots.get(e.getRawSlot());
        if (id == null) return;
        p.closeInventory();
        warps.warp(p, id);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
