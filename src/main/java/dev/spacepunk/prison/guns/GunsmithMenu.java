package dev.spacepunk.prison.guns;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.UndercroftService;
import dev.spacepunk.prison.guns.GunsConfig.AmmoDef;
import dev.spacepunk.prison.guns.GunsConfig.GunDef;
import dev.spacepunk.prison.util.Numbers;
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

/** The gunsmith: buy guns with Credits and gun parts, and ammo with Credits. Prices grow with your grade. */
final class GunsmithMenu implements Listener {
    private static final int SIZE = 45;
    private static final int[] GUN_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private static final int[] AMMO_SLOTS = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    private static final int SLOT_INFO = 4;
    private static final int SLOT_RIG = 8;
    private final GunManager guns;

    GunsmithMenu(GunManager guns) { this.guns = guns; }

    static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, String> gunAt = new HashMap<>();
        final Map<Integer, String> ammoAt = new HashMap<>();
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    void open(Player p) {
        UndercroftService uc = Services.find(UndercroftService.class).orElse(null);
        if (uc != null && uc.pvpAllowed(p.getLocation())) {
            Text.send(p, "<red>The gunsmith doesn't make house calls. Get back to the checkpoint.");
            return;
        }
        Holder h = new Holder();
        h.inventory = Bukkit.createInventory(h, SIZE, Text.mm("<#3a3028>GUNSMITH"));
        render(p, h);
        p.openInventory(h.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.7f, 0.7f);
    }

    private long scale(Player p, long base) {
        int grade = Services.find(ProgressionService.class).map(s -> s.grade(p.getUniqueId())).orElse(1);
        return Math.round(base * Math.pow(guns.cfg().priceGrowth, Math.max(0, grade - 1)));
    }

    private int prestige(Player p) {
        return Services.find(ProgressionService.class).map(s -> s.prestige(p.getUniqueId())).orElse(0);
    }

    private void render(Player p, Holder h) {
        Inventory inv = h.inventory;
        inv.clear();
        h.gunAt.clear();
        h.ammoAt.clear();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", " ", List.of(), true);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);
        int parts = countParts(p);
        inv.setItem(SLOT_RIG, icon(Material.CRAFTER, "ui/fuse", "<#ff8a5c>Weapon Rigs", List.of(
                "<gray>Fit shaped <white>weapon modules</white> on each gun",
                "<gray>model's own rig: more damage, faster fire,",
                "<gray>bigger mags... each with a downside.",
                "<gray>Find them in the Undercroft.", "",
                "<#c8743a>Click</#c8743a> <gray>to open <dark_gray>(/rig gun)"), false));
        inv.setItem(SLOT_INFO, icon(Material.PAPER, "ui/info", "<#c8743a>GUNSMITH", List.of(
                "<gray>Guns only fire in PvP zones,", "<gray>and drop when you die there.", "",
                "<gray>Your gun parts: <white>" + parts,
                "<gray>Prices grow with your grade."), false));
        GunsConfig cfg = guns.cfg();
        int i = 0;
        for (GunDef g : cfg.guns.values()) {
            if (i >= GUN_SLOTS.length) break;
            ItemStack shown = guns.items().gun(g, g.magazine());
            ItemMeta meta = shown.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.item(""));
            long price = scale(p, g.price());
            lore.add(Text.item("<gray>Price: <gold>" + Numbers.shortFormat(price) + " Credits"
                    + (g.parts() > 0 ? "</gold> <gray>+ <#d6a640>" + g.parts() + " gun parts" : "")));
            if (g.prestige() > prestige(p)) lore.add(Text.item("<red>Needs Prestige " + g.prestige()));
            else lore.add(Text.item("<#c8743a>Click to buy"));
            meta.lore(lore);
            shown.setItemMeta(meta);
            inv.setItem(GUN_SLOTS[i], shown);
            h.gunAt.put(GUN_SLOTS[i], g.id());
            i++;
        }
        i = 0;
        for (AmmoDef a : cfg.ammo.values()) {
            if (i >= AMMO_SLOTS.length) break;
            ItemStack shown = guns.items().ammo(a, a.pack());
            ItemMeta meta = shown.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.item(""));
            lore.add(Text.item("<gray>" + a.pack() + " rounds: <gold>" + Numbers.shortFormat(scale(p, a.price())) + " Credits"));
            lore.add(Text.item("<#c8743a>Click</#c8743a> <gray>for 1 pack, <#c8743a>shift-click</#c8743a> <gray>for 5"));
            meta.lore(lore);
            shown.setItemMeta(meta);
            inv.setItem(AMMO_SLOTS[i], shown);
            h.ammoAt.put(AMMO_SLOTS[i], a.id());
            i++;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != h.inventory) return;
        int slot = e.getRawSlot();
        EconomyService eco = Services.find(EconomyService.class).orElse(null);
        if (eco == null) return;
        if (slot == SLOT_RIG) {
            Services.find(dev.spacepunk.prison.api.DrillService.class).ifPresent(d -> {
                String held = guns.items().gunId(p.getInventory().getItemInMainHand());
                String first = guns.cfg().guns.keySet().stream().findFirst().orElse("rivet_pistol");
                d.openRig(p, "gun:" + (held != null ? held : first));
            });
            return;
        }
        String gunId = h.gunAt.get(slot);
        if (gunId != null) {
            GunDef g = guns.cfg().guns.get(gunId);
            if (g == null) return;
            if (g.prestige() > prestige(p)) { deny(p, "Needs Prestige " + g.prestige() + "."); return; }
            long price = scale(p, g.price());
            if (countParts(p) < g.parts()) { deny(p, "You need " + g.parts() + " gun parts."); return; }
            if (eco.balance(p.getUniqueId(), Currency.CREDITS) < price) { deny(p, "Not enough Credits."); return; }
            if (!eco.take(p.getUniqueId(), Currency.CREDITS, price, "gunsmith:" + g.id())) { deny(p, "Not enough Credits."); return; }
            takeParts(p, g.parts());
            give(p, guns.items().gun(g, g.magazine()));
            Text.send(p, "<gray>Bought a " + g.name() + "<gray>. Loaded and ready.");
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 0.9f);
            render(p, h);
            return;
        }
        String ammoId = h.ammoAt.get(slot);
        if (ammoId != null) {
            AmmoDef a = guns.cfg().ammo.get(ammoId);
            if (a == null) return;
            int packs = e.isShiftClick() ? 5 : 1;
            long price = scale(p, a.price()) * packs;
            if (!eco.take(p.getUniqueId(), Currency.CREDITS, price, "gunsmith:ammo")) { deny(p, "Not enough Credits."); return; }
            int total = a.pack() * packs;
            while (total > 0) {
                int n = Math.min(99, total);
                give(p, guns.items().ammo(a, n));
                total -= n;
            }
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 1.2f);
            render(p, h);
        }
    }

    private void deny(Player p, String why) {
        Text.send(p, "<red>" + why);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
    }

    private static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    private int countParts(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (guns.items().isParts(it)) n += it.getAmount();
        return n;
    }

    private void takeParts(Player p, int want) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length && want > 0; i++) {
            if (!guns.items().isParts(c[i])) continue;
            int use = Math.min(want, c[i].getAmount());
            c[i].setAmount(c[i].getAmount() - use);
            p.getInventory().setItem(i, c[i].getAmount() <= 0 ? null : c[i]);
            want -= use;
        }
    }

    private ItemStack icon(Material mat, String model, String name, List<String> lore, boolean hide) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        if (!lore.isEmpty()) meta.lore(Text.lore(lore));
        if (guns.cfg().itemModels) meta.setItemModel(new NamespacedKey(GunItems.NS, model));
        if (hide) meta.setHideTooltip(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }
}
