package dev.spacepunk.prison.guide;

import dev.spacepunk.prison.guide.GuideConfig.Chapter;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** /guide: one icon per chapter. Left-click reads it, right-click (re)starts its checklist. */
final class GuideMenu implements Listener {
    private static final int SIZE = 54;
    private static final int[] GRID = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final GuideManager guide;

    GuideMenu(GuideManager guide) { this.guide = guide; }

    static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    void open(Player p) {
        GuideData d = guide.data(p.getUniqueId());
        if (d == null) {
            Text.send(p, "<red>Your guide is still loading.");
            return;
        }
        Holder h = new Holder();
        h.inventory = Bukkit.createInventory(h, SIZE, Text.mm("<#3a3028>STATION GUIDE"));
        render(p, h, d);
        p.openInventory(h.inventory);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
    }

    private void render(Player p, Holder h, GuideData d) {
        h.inventory.clear();
        h.actions.clear();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", " ", List.of());
        filler.editMeta(m -> m.setHideTooltip(true));
        for (int i = 0; i < SIZE; i++) h.inventory.setItem(i, filler);

        String top = GuideManager.top(d);
        List<String> head = new ArrayList<>(List.of(
                "<gray>Everything on the station, explained.",
                "",
                "<white>Left-click</white> <gray>a chapter to read it.",
                "<white>Right-click</white> <gray>to run its checklist.",
                ""));
        if (top != null) {
            Chapter c = guide.cfg().chapters.get(top);
            head.add("<gray>Checklist running: " + (c == null ? top : c.color() + c.name()));
        }
        set(h, 4, icon(Material.WRITTEN_BOOK, "ui/info", "<#c8743a><bold>STATION GUIDE", head), t -> {});

        int i = 0;
        for (Chapter c : guide.cfg().chapters.values()) {
            if (i >= GRID.length) break;
            List<String> lore = new ArrayList<>();
            for (String s : c.summary()) lore.add("<gray>" + s);
            lore.add("");
            if (c.hasChecklist()) {
                if (d.active.contains(c.id())) {
                    int step = d.step.getOrDefault(c.id(), 0);
                    lore.add("<yellow>Checklist: step " + (step + 1) + "/" + c.steps().size());
                } else if (d.done.contains(c.id())) {
                    lore.add("<green>✔ Checklist done");
                } else {
                    lore.add("<dark_gray>Checklist: " + c.steps().size() + " steps");
                }
            }
            lore.add(d.read.contains(c.id()) ? "<dark_gray>Read" : "<#e0b354>New");
            lore.add("");
            lore.add("<white>Left-click</white> <gray>read");
            if (c.hasChecklist()) lore.add("<white>Right-click</white> <gray>" + (d.active.contains(c.id()) ? "show on the bar" : "start checklist"));
            ItemStack it = icon(c.icon(), c.model(), c.color() + "<bold>" + c.name(), lore);
            if (!d.read.contains(c.id())) it.editMeta(m -> m.setEnchantmentGlintOverride(true));
            set(h, GRID[i++], it, click -> {
                if (click.isRightClick() && c.hasChecklist()) {
                    p.closeInventory();
                    guide.startChecklist(p, d, c, true);
                } else {
                    p.closeInventory();
                    guide.openBook(p, c);
                }
            });
        }

        String url = guide.cfg().wikiUrl;
        if (!url.isEmpty()) {
            set(h, 46, icon(Material.MAP, null, "<#8fd3ff><bold>Station wiki", List.of(
                    "<gray>Every system with full tables",
                    "<gray>and numbers, on the web.", "", "<white>Click</white> <gray>for the link")), t -> {
                p.closeInventory();
                p.sendMessage(Text.mm("<#8fd3ff>Station wiki: <click:open_url:'" + url + "'><u>" + url + "</u></click>"));
            });
        }
        boolean popups = guide.popups(d);
        set(h, 48, icon(popups ? Material.BELL : Material.GRAY_DYE, null, "<white>First-visit tips: " + (popups ? "<green>on" : "<red>off"),
                List.of("<gray>A short briefing the first time", "<gray>you reach a new place or system.", "", "<white>Click</white> <gray>to toggle")), t -> {
            d.popups = !popups;
            guide.markDirty(p.getUniqueId());
            render(p, h, d);
        });
        set(h, 49, icon(Material.BARRIER, "ui/close", "<red>Close", List.of()), t -> p.closeInventory());
        boolean bar = guide.barOn(d);
        set(h, 50, icon(bar ? Material.LANTERN : Material.SOUL_LANTERN, null, "<white>Checklist bar: " + (bar ? "<green>shown" : "<red>hidden"),
                List.of("<gray>The step-by-step bar at the", "<gray>top of your screen.", "", "<white>Click</white> <gray>to toggle")), t -> {
            d.bar = !bar;
            guide.markDirty(p.getUniqueId());
            guide.updateBar(p);
            render(p, h, d);
        });
        if (top != null) {
            set(h, 52, icon(Material.SHEARS, null, "<gray>Skip current checklist", List.of(
                    "<dark_gray>Drops the checklist on the bar.", "<dark_gray>Restart it any time from here.")), t -> {
                guide.skip(p, d, top);
                render(p, h, d);
            });
        }
    }

    private static ItemStack icon(Material m, String model, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(Text.item(name));
            meta.lore(Text.lore(lore));
            meta.addItemFlags(ItemFlag.values());
            if (model != null && !model.isBlank()) {
                NamespacedKey key = model.contains(":") ? NamespacedKey.fromString(model) : new NamespacedKey("spacepunk", model);
                if (key != null) meta.setItemModel(key);
            }
        });
        return it;
    }

    private static void set(Holder h, int slot, ItemStack item, Consumer<ClickType> action) {
        h.inventory.setItem(slot, item);
        h.actions.put(slot, action);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        Consumer<ClickType> a = h.actions.get(e.getRawSlot());
        if (a != null) a.accept(e.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
