package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Tiny chest-menu helper: fixed buttons with click handlers. All clicks in these menus are cancelled. */
final class Menu implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    Menu(int rows, String title) {
        inventory = Bukkit.createInventory(this, rows * 9, Text.mm(title));
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", " ", List.of());
        ItemMeta m = filler.getItemMeta();
        m.setHideTooltip(true);
        filler.setItemMeta(m);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    Menu set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        return this;
    }

    void open(Player p) { p.openInventory(inventory); }

    @Override public @NotNull Inventory getInventory() { return inventory; }

    static ItemStack icon(Material mat, String model, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore != null && !lore.isEmpty()) meta.lore(Text.lore(lore));
        if (model != null) meta.setItemModel(new NamespacedKey("spacepunk", model));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    /** Copy of an item with extra lore lines appended (for showing real items as buttons). */
    static ItemStack withLore(ItemStack base, List<String> extra) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
        lore.addAll(Text.lore(extra));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    static final class Listen implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
            e.setCancelled(true);
            if (e.getClickedInventory() != m.inventory) return;
            Consumer<InventoryClickEvent> a = m.actions.get(e.getRawSlot());
            if (a != null) a.accept(e);
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
        }
    }
}
