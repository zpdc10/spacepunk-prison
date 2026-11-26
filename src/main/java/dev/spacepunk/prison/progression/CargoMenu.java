package dev.spacepunk.prison.progression;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.util.Numbers;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** The /cargo menu: contents, sell all, capacity upgrade, Auto-Seller. Vanilla items only. */
final class CargoMenu implements Listener {
    private static final int SLOT_CONTENTS = 10;
    private static final int SLOT_SELL = 12;
    private static final int SLOT_UPGRADE = 14;
    private static final int SLOT_AUTOSELL = 16;
    private static final int SLOT_GRADE = 22;

    private final CargoHold cargo;
    private final ProgressionManager progression;
    private final EconomyService economy;

    CargoMenu(CargoHold cargo, ProgressionManager progression, EconomyService economy) {
        this.cargo = cargo;
        this.progression = progression;
        this.economy = economy;
    }

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    void open(Player player) {
        CargoStore.Data d = cargo.store().get(player.getUniqueId());
        if (d == null) {
            Text.send(player, "<red>Your profile is still loading.");
            return;
        }
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Text.mm("<dark_gray>Cargo Hold"));
        h.inventory = inv;
        render(player, inv, d);
        player.openInventory(inv);
    }

    private void render(Player player, Inventory inv, CargoStore.Data d) {
        ProgressionConfig c = progression.cfg();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        long cap = cargo.capacity(d);
        long value = cargo.estimatedValue(player, d);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Stored: <white>" + Numbers.shortFormat(d.used) + "</white> / " + Numbers.shortFormat(cap));
        lore.add(bar(d.used, cap));
        lore.add("");
        d.items.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .forEach(e -> lore.add("<dark_gray>· <gray>" + progression.cfg().itemName(e.getKey()) + " <white>x" + Numbers.shortFormat(e.getValue())));
        if (d.items.size() > 8) lore.add("<dark_gray>  and " + (d.items.size() - 8) + " more");
        if (d.items.isEmpty()) lore.add("<dark_gray>Empty. Go mine something.");
        inv.setItem(SLOT_CONTENTS, icon(Material.BARREL, "<#c8743a>Cargo Contents", lore));

        inv.setItem(SLOT_SELL, icon(Material.GOLD_INGOT, "<gold>Sell Everything", List.of(
                "<gray>Worth about " + economy.format(Currency.CREDITS, value),
                "<gray>Sell multiplier: <white>x" + String.format("%.2f", progression.sellMultiplier(player.getUniqueId())),
                "",
                "<yellow>Click to sell")));

        long upCost = c.cargoUpgradeCost(d.level);
        List<String> up = new ArrayList<>();
        up.add("<gray>Level <white>" + d.level + "</white> / " + c.cargoMaxLevel);
        if (upCost < 0) {
            up.add("<green>Fully upgraded");
        } else {
            up.add("<gray>Capacity: <white>" + Numbers.shortFormat(cap) + "</white> -> <green>" + Numbers.shortFormat(c.capacity(d.level + 1)));
            up.add("<gray>Cost: " + economy.format(Currency.CREDITS, upCost));
            up.add("");
            up.add("<yellow>Click to upgrade");
        }
        inv.setItem(SLOT_UPGRADE, icon(Material.HOPPER, "<aqua>Expand Hold", up));

        List<String> as = new ArrayList<>();
        Material asIcon;
        if (!d.autoSellUnlocked) {
            asIcon = Material.REDSTONE_TORCH;
            as.add("<gray>Sells ore the moment you mine it,");
            as.add("<gray>at <white>" + Math.round(c.autoSellRate * 100) + "%</white> of the normal price.");
            as.add("");
            as.add("<gray>Needs " + progression.gradeLabel(c.autoSellGrade));
            as.add("<gray>Cost: " + economy.format(Currency.CREDITS, c.autoSellCost));
            as.add("");
            as.add("<yellow>Click to install");
        } else {
            asIcon = d.autoSell ? Material.LIME_DYE : Material.GRAY_DYE;
            as.add(d.autoSell ? "<green>ON" : "<red>OFF");
            as.add("<gray>Pays <white>" + Math.round(c.autoSellRate * 100) + "%</white> of the normal price.");
            as.add("");
            as.add("<yellow>Click to toggle");
        }
        inv.setItem(SLOT_AUTOSELL, icon(asIcon, "<#e0562e>Auto-Seller", as));

        int grade = progression.grade(player.getUniqueId());
        long next = progression.nextGradeCost(player.getUniqueId());
        List<String> gl = new ArrayList<>();
        gl.add(progression.gradeLabel(grade));
        if (progression.prestige(player.getUniqueId()) > 0) gl.add("<light_purple>Prestige " + progression.prestige(player.getUniqueId()));
        gl.add(next < 0 ? "<light_purple>Max grade: /prestige" : "<gray>Next grade: " + economy.format(Currency.CREDITS, next));
        gl.add("<gray>Balance: " + economy.format(Currency.CREDITS, economy.balance(player.getUniqueId(), Currency.CREDITS)));
        inv.setItem(SLOT_GRADE, icon(Material.NAME_TAG, "<white>Clearance", gl));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        CargoStore.Data d = cargo.store().get(player.getUniqueId());
        if (d == null) return;
        switch (e.getSlot()) {
            case SLOT_SELL -> ProgressionCommands.reportSell(player, cargo.sellAll(player, "menu"), economy);
            case SLOT_UPGRADE -> ProgressionCommands.reportUpgrade(player, cargo.upgrade(player), cargo, progression, economy);
            case SLOT_AUTOSELL -> ProgressionCommands.reportAutoSell(player, cargo.autoSell(player), progression, economy);
            default -> { return; }
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
        render(player, e.getInventory(), d);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Holder) e.setCancelled(true);
    }

    private static String bar(long used, long cap) {
        int cells = 20;
        int filled = cap <= 0 ? cells : (int) Math.min(cells, Math.round((double) used / cap * cells));
        String color = filled >= cells ? "<red>" : filled >= cells * 3 / 4 ? "<yellow>" : "<green>";
        return color + "|".repeat(filled) + "<dark_gray>" + "|".repeat(cells - filled);
    }


    private static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(Text.item(name));
            meta.lore(Text.lore(lore));
            meta.addItemFlags(ItemFlag.values());
        });
        return it;
    }
}
