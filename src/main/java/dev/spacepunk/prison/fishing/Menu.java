package dev.spacepunk.prison.fishing;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** A chest menu: every click is cancelled and routed to the slot's action (or bottom for the player inventory). */
final class Menu implements InventoryHolder {
    final UUID owner;
    final String kind;
    Inventory inventory;
    final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    Consumer<InventoryClickEvent> bottom;
    int page;

    Menu(UUID owner, String kind) {
        this.owner = owner;
        this.kind = kind;
    }

    void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    @Override public @NotNull Inventory getInventory() { return inventory; }
}
