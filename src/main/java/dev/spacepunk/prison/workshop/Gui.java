package dev.spacepunk.prison.workshop;

import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A chest menu with a click handler per slot. {@link #render} redraws it in place (no flicker, cursor kept);
 * live menus are redrawn every second by the module tick. One {@link Clicks} listener serves all of them.
 */
final class Gui implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private Consumer<Gui> renderer;
    /** Click in the player's own inventory while this menu is open (deposit goods). */
    Consumer<InventoryClickEvent> bottomClick;
    boolean live;

    Gui(int rows, String title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.mm(title));
    }

    @Override public @NotNull Inventory getInventory() { return inventory; }

    Gui renderer(Consumer<Gui> r) {
        this.renderer = r;
        return this;
    }

    /** While rendering, slots are drawn here first and only changed slots are written to the inventory. */
    private ItemStack[] staging;

    void render() {
        actions.clear();
        ItemStack filler = filler();
        staging = new ItemStack[inventory.getSize()];
        java.util.Arrays.fill(staging, filler);
        try {
            if (renderer != null) renderer.accept(this);
        } finally {
            ItemStack[] next = staging;
            staging = null;
            // Write only what changed, so live menus (redrawn every second) don't resend every slot.
            for (int i = 0; i < next.length; i++) {
                ItemStack cur = inventory.getItem(i);
                if (next[i] == null ? cur != null : !next[i].equals(cur)) inventory.setItem(i, next[i]);
            }
        }
    }

    void open(Player p) {
        render();
        p.openInventory(inventory);
    }

    void set(int slot, ItemStack item) { set(slot, item, null); }

    void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        if (staging != null) staging[slot] = item; else inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action); else actions.remove(slot);
    }

    int size() { return inventory.getSize(); }

    // ---- icons ----

    static ItemStack icon(Material base, String model, String name, List<String> lore) {
        ItemStack it = new ItemStack(base);
        it.editMeta(meta -> {
            if (model != null) meta.setItemModel(WorkshopItems.model(model));
            meta.displayName(Text.item(name));
            meta.lore(Text.lore(lore));
            meta.addItemFlags(ItemFlag.values());
        });
        return it;
    }

    static ItemStack withLore(ItemStack base, String name, List<String> lore) {
        ItemStack it = base.clone();
        it.editMeta(meta -> {
            if (name != null) meta.displayName(Text.item(name));
            meta.lore(Text.lore(lore));
            meta.addItemFlags(ItemFlag.values());
        });
        return it;
    }

    static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        it.editMeta(meta -> {
            meta.setItemModel(WorkshopItems.model("ui/panel"));
            meta.setHideTooltip(true);
        });
        return it;
    }

    static void click(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f); }

    static void deny(Player p) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f); }

    /** Routes clicks to the open Gui. */
    static final class Clicks implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder(false) instanceof Gui g)) return;
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player)) return;
            if (e.getClickedInventory() == null) return;
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                Consumer<InventoryClickEvent> a = g.actions.get(e.getSlot());
                if (a != null) a.accept(e);
            } else if (g.bottomClick != null) {
                g.bottomClick.accept(e);
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder(false) instanceof Gui) e.setCancelled(true);
        }
    }
}
