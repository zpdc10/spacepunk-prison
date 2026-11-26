package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.drill.DrillConfig.EnchantDef;
import dev.spacepunk.prison.drill.DrillConfig.FrameDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The drill bay: buy enchants, install the next frame, slot modules. One instance per open menu. */
public final class DrillMenu implements Listener {
    private static final int SIZE = 54;
    private static final int SLOT_DRILL = 4;
    private static final int SLOT_HEAT = 22;
    private static final int SLOT_MODULES_INFO = 40;
    private static final int SLOT_BALANCE = 45;
    private static final int SLOT_FRAME = 49;
    private static final int SLOT_CLOSE = 53;
    private static final int[] SOCKET_SLOTS = {38, 39, 41, 42};
    private static final Enchant[] ENCHANT_ORDER = {
            Enchant.BORE_SPEED, Enchant.YIELD, Enchant.TOKEN_SIPHON, Enchant.SHAPED_CHARGE,
            Enchant.JACKHAMMER, Enchant.CUTTING_LASER, Enchant.OVERCLOCK, Enchant.SALVAGE_SENSE, Enchant.COOLANT};
    private static final int[] ENCHANT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 21, 23};

    private final DrillManager drills;
    private final HeatManager heat;

    public DrillMenu(DrillManager drills, HeatManager heat) {
        this.drills = drills;
        this.heat = heat;
    }

    /** Inventory holder that marks our menu and remembers whose it is. */
    public static final class Holder implements InventoryHolder {
        final UUID owner;
        Inventory inventory;

        Holder(UUID owner) { this.owner = owner; }

        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    public void open(Player p) {
        if (drills.data(p.getUniqueId()) == null) {
            Text.send(p, "<red>Your drill is still loading.");
            return;
        }
        Holder h = new Holder(p.getUniqueId());
        h.inventory = Bukkit.createInventory(h, SIZE, Text.mm("<#3a3028>DRILL BAY"));
        render(p, h.inventory);
        p.openInventory(h.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.7f, 0.8f);
    }

    /** Re-renders the menu if the player has it open (e.g. after an admin change). */
    public void refreshIfOpen(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h && h.owner.equals(p.getUniqueId())) {
            render(p, h.inventory);
        }
    }

    private void render(Player p, Inventory inv) {
        DrillConfig cfg = drills.cfg();
        DrillItems items = drills.items();
        DrillData d = drills.data(p.getUniqueId());
        if (d == null) return;
        DrillMath.Effective eff = drills.effective(p.getUniqueId());
        FrameDef frame = eff.frame;
        EconomyService eco = Services.get(EconomyService.class);
        long tokens = eco.balance(p.getUniqueId(), Currency.TOKENS);

        ItemStack filler = items.icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", "<dark_gray> ", null);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        // Overview
        ItemStack overview = items.drill(p.getUniqueId(), d);
        inv.setItem(SLOT_DRILL, overview);

        // Enchants
        for (int i = 0; i < ENCHANT_ORDER.length; i++) {
            inv.setItem(ENCHANT_SLOTS[i], enchantIcon(p, cfg, d, frame, ENCHANT_ORDER[i], tokens));
        }

        // Heat
        List<String> heatLore = new ArrayList<>();
        heatLore.add("<gray>Heat <white>" + Math.round(heat.heat(p)) + " / " + Math.round(eff.heatCapacity));
        heatLore.add("<gray>Cooling <white>" + DrillMath.fmt(eff.decayPerSecond) + "/s</white> when you stop mining");
        heatLore.add("");
        heatLore.add("<gray>Heavy enchants heat the drill. At the limit it");
        heatLore.add("<gray>stalls for <white>" + DrillMath.fmt(cfg.heat.stallSeconds() * eff.stallMultiplier) + "s</white>.");
        heatLore.add("");
        heatLore.add("<#c8743a>Q</#c8743a> <gray>with the drill in hand vents all heat");
        heatLore.add("<gray>for a speed burst. Vent between <gold>" + Math.round(cfg.heat.perfectMin() * 100) + "%</gold><gray> and <gold>"
                + Math.round(cfg.heat.perfectMax() * 100) + "%</gold>");
        heatLore.add("<gray>for a <gold>perfect vent</gold><gray> (x" + DrillMath.fmt(cfg.heat.perfectMultiplier()) + " burst).");
        heatLore.add("");
        heatLore.add("<dark_gray>Overheats " + Numbers.shortFormat(d.overheats) + " · Perfect vents " + Numbers.shortFormat(d.perfectVents));
        ItemStack gauge = items.icon(Material.MAGMA_CREAM, "ui/heat_gauge", "<#ff8c1a>Heat Gauge", heatLore);
        // The pack's gauge picks one of 9 stages from custom_model_data float [heat 0.0-1.0].
        var gaugeMeta = gauge.getItemMeta();
        var cmd = gaugeMeta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) Math.min(1.0, heat.heat(p) / eff.heatCapacity)));
        gaugeMeta.setCustomModelDataComponent(cmd);
        gauge.setItemMeta(gaugeMeta);
        inv.setItem(SLOT_HEAT, gauge);

        // Modules
        List<String> modInfo = new ArrayList<>();
        modInfo.add("<gray>Modules change how the drill plays.");
        modInfo.add("<gray>Most come with a trade-off.");
        modInfo.add("");
        modInfo.add("<gray>Find them while mining (Salvage Sense helps).");
        modInfo.add("<gray>Click a module in your inventory to install it,");
        modInfo.add("<gray>click an installed module to take it out.");
        modInfo.add("");
        modInfo.add("<dark_gray>Modules found " + Numbers.shortFormat(d.modulesFound));
        inv.setItem(SLOT_MODULES_INFO, items.icon(Material.HOPPER, "ui/info", "<#c8743a>Module Sockets", modInfo));
        for (int i = 0; i < SOCKET_SLOTS.length; i++) {
            inv.setItem(SOCKET_SLOTS[i], socketIcon(cfg, d, frame, i));
        }

        // Balance
        List<String> bal = new ArrayList<>();
        bal.add(eco.format(Currency.TOKENS, tokens));
        bal.add(eco.format(Currency.SHARDS, eco.balance(p.getUniqueId(), Currency.SHARDS)));
        bal.add(eco.format(Currency.CREDITS, eco.balance(p.getUniqueId(), Currency.CREDITS)));
        bal.add("");
        bal.add("<dark_gray>Tokens found by this drill " + Numbers.shortFormat(d.tokensFound));
        inv.setItem(SLOT_BALANCE, items.icon(Material.PRISMARINE_CRYSTALS, "currency/token", "<aqua>Balances", bal));

        inv.setItem(SLOT_FRAME, frameIcon(p, cfg, frame));
        inv.setItem(SLOT_CLOSE, items.icon(Material.BARRIER, "ui/close", "<red>Close", null));
    }

    private ItemStack enchantIcon(Player p, DrillConfig cfg, DrillData d, FrameDef frame, Enchant e, long tokens) {
        EnchantDef def = cfg.enchant(e);
        int lvl = d.level(e);
        int cap = def.cap(frame);
        List<String> lore = new ArrayList<>();
        lore.add("<#8a8070>" + def.description());
        lore.add("");
        if (cap <= 0) {
            FrameDef unlock = firstFrameWith(cfg, def, frame);
            lore.add("<red>Locked on the " + frame.name() + " frame.");
            if (unlock != null) lore.add("<gray>Unlocks with the " + unlock.coloredName() + "<gray> frame.");
            return drills.items().icon(Material.GRAY_DYE, "ui/locked", "<dark_gray>" + def.name() + " <red>(locked)", lore);
        }
        lore.add("<gray>Level <white>" + lvl + "</white><dark_gray> / " + cap);
        lore.add("<gray>Now: " + (lvl > 0 ? DrillMath.describe(cfg, frame, e, lvl) : "<dark_gray>not installed"));
        if (lvl < cap) {
            lore.add("<gray>Next: " + DrillMath.describe(cfg, frame, e, lvl + 1));
            long next = def.cost(lvl + 1);
            lore.add("");
            lore.add("<gray>Cost " + (tokens >= next ? "<aqua>" : "<red>") + Numbers.shortFormat(next) + " Tokens");
            DrillManager.Quote ten = drills.quote(p, e, 10);
            DrillManager.Quote max = drills.quote(p, e, Integer.MAX_VALUE);
            lore.add("");
            lore.add("<#c8743a>Left-click</#c8743a> <gray>+1");
            lore.add("<#c8743a>Right-click</#c8743a> <gray>+" + Math.max(ten.levels(), 0) + (ten.levels() > 0 ? " for " + Numbers.shortFormat(ten.cost()) : " (can't afford)"));
            lore.add("<#c8743a>Shift-click</#c8743a> <gray>max: +" + max.levels() + (max.levels() > 0 ? " for " + Numbers.shortFormat(max.cost()) : ""));
        } else {
            lore.add("");
            FrameDef next = cfg.nextFrame(frame);
            int nextCap = next == null ? cap : def.cap(next);
            if (nextCap > cap) {
                lore.add("<gold>Frame limit reached.");
                lore.add("<gray>The " + next.coloredName() + "<gray> frame raises it to <white>" + nextCap + "</white>.");
            } else {
                lore.add("<gold><bold>MAXED");
            }
        }
        String title = def.coloredName() + (lvl > 0 ? " <white>" + lvl : "");
        ItemStack icon = drills.items().icon(def.icon(), null, title, lore);
        if (lvl >= cap && cap > 0) {
            var meta = icon.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static FrameDef firstFrameWith(DrillConfig cfg, EnchantDef def, FrameDef from) {
        for (FrameDef f : cfg.frames) if (f.index() > from.index() && def.cap(f) > 0) return f;
        return null;
    }

    private ItemStack socketIcon(DrillConfig cfg, DrillData d, FrameDef frame, int i) {
        DrillItems items = drills.items();
        if (i >= frame.sockets()) {
            FrameDef unlock = null;
            for (FrameDef f : cfg.frames) if (f.sockets() > i) { unlock = f; break; }
            List<String> lore = new ArrayList<>();
            lore.add(unlock == null ? "<dark_gray>No frame has this socket." : "<gray>Opens with the " + unlock.coloredName() + "<gray> frame.");
            return items.icon(Material.IRON_BARS, "ui/locked", "<dark_gray>Locked socket", lore);
        }
        ModuleDef m = cfg.module(d.socket(i));
        if (m == null) {
            return items.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "ui/socket_empty", "<gray>Empty socket",
                    List.of("<gray>Click a module in your inventory", "<gray>to install it here."));
        }
        List<String> lore = new ArrayList<>(items.moduleLore(m, false));
        lore.add("");
        lore.add("<#c8743a>Click</#c8743a> <gray>to take it out");
        ItemStack icon = items.module(m);
        var meta = icon.getItemMeta();
        meta.lore(Text.lore(lore));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack frameIcon(Player p, DrillConfig cfg, FrameDef frame) {
        FrameDef next = cfg.nextFrame(frame);
        DrillItems items = drills.items();
        if (next == null) {
            return items.icon(Material.NETHERITE_BLOCK, "ui/upgrade", frame.coloredName() + " <gold>frame",
                    List.of("<gold>Top frame installed.", "<gray>There's nothing left to bolt on."));
        }
        int grade = drills.grade(p);
        int prestige = drills.prestige(p);
        boolean unlocked = next.unlockedFor(grade, prestige);
        EconomyService eco = Services.get(EconomyService.class);
        boolean afford = next.cost() <= 0 || eco.balance(p.getUniqueId(), next.costCurrency()) >= next.cost();
        List<String> lore = new ArrayList<>();
        if (!next.lore().isEmpty()) lore.add("<#8a8070><i>" + next.lore());
        lore.add("");
        lore.add("<gray>Module sockets <white>" + frame.sockets() + " → " + next.sockets());
        lore.add("<gray>Base speed <white>x" + DrillMath.fmt(frame.baseSpeed()) + " → x" + DrillMath.fmt(next.baseSpeed()));
        lore.add("<gray>Heat limit <white>+" + Math.round(next.heatCapacity() - frame.heatCapacity()));
        lore.add("<gray>Raises the level cap of every enchant:");
        for (Enchant e : ENCHANT_ORDER) {
            EnchantDef def = cfg.enchant(e);
            int a = def.cap(frame), b = def.cap(next);
            if (b > a) lore.add("<dark_gray> · </dark_gray>" + def.coloredName() + " <white>" + (a == 0 ? "<green>unlocks</green> (" + b + ")" : a + " → " + b));
        }
        lore.add("");
        lore.add((unlocked ? "<green>✔ " : "<red>✘ ") + "<gray>Requires " + next.requirementText());
        if (next.cost() > 0) lore.add((afford ? "<green>✔ " : "<red>✘ ") + "<gray>Costs " + eco.format(next.costCurrency(), next.cost()));
        lore.add("");
        lore.add(unlocked && afford ? "<#c8743a>Click</#c8743a> <gray>to install" : "<dark_gray>Not available yet");
        return items.icon(unlocked && afford ? Material.ANVIL : Material.CHIPPED_ANVIL, "ui/upgrade",
                "<gray>Upgrade to " + next.coloredName() + " <gray>frame", lore);
    }

    // ---- clicks ----

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !h.owner.equals(p.getUniqueId())) return;
        Inventory top = e.getView().getTopInventory();
        if (e.getClickedInventory() == null) return;

        if (!e.getClickedInventory().equals(top)) {
            // Player inventory: install a module.
            ItemStack it = e.getCurrentItem();
            String id = drills.items().moduleId(it);
            if (id == null) return;
            ModuleDef m = drills.cfg().module(id);
            if (m == null) {
                Text.send(p, "<red>That module no longer exists.");
                return;
            }
            int slot = drills.socket(p, m);
            if (slot == -2) {
                deny(p, "That module is already installed.");
            } else if (slot < 0) {
                deny(p, drills.effective(p.getUniqueId()).frame.sockets() == 0
                        ? "Your frame has no module sockets yet." : "No free socket. Take a module out first.");
            } else {
                it.setAmount(it.getAmount() - 1);
                e.setCurrentItem(it.getAmount() <= 0 ? null : it);
                p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.4f);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.6f);
                Text.send(p, "<gray>Installed " + m.coloredName() + "<gray>.");
                render(p, top);
            }
            return;
        }

        int slot = e.getRawSlot();
        if (slot == SLOT_CLOSE) {
            p.closeInventory();
            return;
        }
        if (slot == SLOT_FRAME) {
            frameClick(p);
            render(p, top);
            return;
        }
        for (int i = 0; i < SOCKET_SLOTS.length; i++) {
            if (slot != SOCKET_SLOTS[i]) continue;
            DrillData d = drills.data(p.getUniqueId());
            if (d == null || drills.cfg().module(d.socket(i)) == null) return;
            if (p.getInventory().firstEmpty() < 0) {
                deny(p, "Make room in your inventory first.");
                return;
            }
            ItemStack mod = drills.unsocket(p, i);
            if (mod != null) {
                drills.give(p, mod);
                p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.2f);
            }
            render(p, top);
            return;
        }
        for (int i = 0; i < ENCHANT_SLOTS.length; i++) {
            if (slot != ENCHANT_SLOTS[i]) continue;
            Enchant ench = ENCHANT_ORDER[i];
            int want = e.isShiftClick() ? Integer.MAX_VALUE : e.getClick() == ClickType.RIGHT ? 10 : 1;
            buy(p, ench, want);
            render(p, top);
            return;
        }
    }

    private void buy(Player p, Enchant e, int want) {
        DrillConfig cfg = drills.cfg();
        DrillData d = drills.data(p.getUniqueId());
        if (d == null) return;
        EnchantDef def = cfg.enchant(e);
        int cap = def.cap(cfg.frame(d.frame));
        if (cap <= 0) {
            deny(p, def.name() + " is locked on this frame.");
            return;
        }
        if (d.level(e) >= cap) {
            deny(p, def.name() + " is at this frame's limit.");
            return;
        }
        int before = d.level(e);
        int n = drills.buy(p, e, want);
        if (n <= 0) {
            deny(p, "Not enough Tokens.");
            return;
        }
        int after = before + n;
        boolean milestone = after == cap || after / 10 > before / 10;
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.6f);
        if (milestone) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        Text.send(p, def.coloredName() + " <gray>→ level <white>" + after + "</white>" + (after == cap ? " <gold>(frame limit)" : ""));
    }

    private void frameClick(Player p) {
        DrillManager.FrameResult r = drills.upgradeFrame(p);
        switch (r) {
            case MAX -> deny(p, "You already have the top frame.");
            case LOCKED -> deny(p, "You don't meet the requirement for the next frame yet.");
            case POOR -> deny(p, "You can't afford the next frame yet.");
            case OK -> {
                FrameDef f = drills.effective(p.getUniqueId()).frame;
                p.showTitle(Title.title(Text.mm(f.coloredName() + " <white>FRAME"), Text.mm("<gray>Installed. Level caps raised.")));
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 0.7f);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.2);
                Bukkit.broadcast(Text.mm(Text.PREFIX + "<white>" + p.getName() + "</white> <gray>installed a " + f.coloredName() + " <gray>drill frame."));
            }
        }
    }

    private static void deny(Player p, String msg) {
        Text.send(p, "<red>" + msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
    }
}
