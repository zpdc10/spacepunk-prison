package dev.spacepunk.prison.parkour;

import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /parkour: every mine's run, one row per deck, with today's reward status and best times. Click to go. */
final class ParkourMenu implements Listener {
    private final ParkourManager parkour;
    private final ParkourCommands commands;

    ParkourMenu(ParkourManager parkour, ParkourCommands commands) {
        this.parkour = parkour;
        this.commands = commands;
    }

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, String> slots = new HashMap<>();
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inventory = Bukkit.createInventory(h, 54, Text.mm("<#3a3028>MAINTENANCE RUNS"));
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setItemModel(new NamespacedKey("spacepunk", "ui/panel"));
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        for (int i = 0; i < 54; i++) h.inventory.setItem(i, filler);

        List<MineService.MineInfo> mines = new ArrayList<>();
        Services.find(MineService.class).ifPresent(ms -> mines.addAll(ms.mines()));
        int[] perDeck = new int[6];
        for (MineService.MineInfo m : mines) {
            int deck = ParkourConfig.deckOf(m.requiredGrade());
            int col = perDeck[deck]++;
            if (col > 6) continue;
            int slot = (deck - 1) * 9 + 1 + col;
            Course c = parkour.courses().get(m.id());
            h.inventory.setItem(slot, icon(p, m, c));
            if (c != null) h.slots.put(slot, m.id());
        }
        for (int d = 1; d <= 5; d++) {
            ParkourConfig.Tier t = parkour.cfg().tier(d);
            h.inventory.setItem((d - 1) * 9, item(Material.LADDER,
                    ParkourManager.deckColor(d * 5) + "<bold>" + t.name(), List.of("<gray>Gaps " + t.gapMin() + "-" + t.gapMax()
                            + ", checkpoint every " + t.checkpointEvery() + " jumps"), false));
        }
        h.inventory.setItem(49, item(Material.CLOCK, "<#e0c040>Daily rewards", List.of(
                "<gray>Each run pays one reward roll a day,", "<gray>with a small chance at something rare.",
                "<gray>Rolls reset in <white>" + parkour.untilReset()), false));
        p.openInventory(h.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.6f, 1f);
    }

    private ItemStack icon(Player p, MineService.MineInfo m, Course c) {
        String color = ParkourManager.deckColor(m.requiredGrade());
        if (c == null) return item(Material.GRAY_DYE, "<gray>" + m.displayName(), List.of("<dark_gray>No run built yet"), false);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Grade " + c.grade + " · " + c.jumps + " jumps · " + c.checkpoints.size() + " checkpoints");
        boolean open = parkour.canRun(p, c);
        boolean ready = parkour.rewardReady(p.getUniqueId(), c.mineId);
        ParkourManager.Stats s = parkour.stats(p.getUniqueId(), c.mineId);
        lore.add(s != null && s.best > 0 ? "<gray>Your best: <white>" + ParkourManager.time(s.best) + " <dark_gray>(" + s.completions + " runs)"
                : "<gray>Your best: <dark_gray>none yet");
        parkour.record(c.mineId).ifPresent(r -> lore.add("<gray>Record: <gold>" + ParkourManager.time(r.millis()) + " <gray>by " + r.name()));
        if (!open) lore.add("<red>Needs Grade " + c.grade);
        else lore.add(ready ? "<green>Reward roll ready today" : "<dark_gray>Reward claimed, resets in " + parkour.untilReset());
        if (open) lore.add("<yellow>Click to go to the start");
        Material mat = !open ? Material.IRON_BARS : ready ? Material.LIME_CONCRETE : Material.CLOCK;
        return item(mat, color + m.displayName(), lore, open && ready);
    }

    private static ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        meta.lore(Text.lore(lore));
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        String id = h.slots.get(e.getRawSlot());
        if (id == null) return;
        p.closeInventory();
        commands.go(p, id);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
