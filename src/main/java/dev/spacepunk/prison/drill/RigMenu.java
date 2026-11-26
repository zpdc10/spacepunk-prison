package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ScrapService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.drill.DrillConfig.ComboDef;
import dev.spacepunk.prison.drill.DrillConfig.FamilyDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;
import dev.spacepunk.prison.drill.DrillConfig.Rarity;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The module rig: fit modules into the frame's grid like puzzle pieces, rotate them, fuse duplicates.
 * Board cells are slots 10..16, 19..25, 28..34, 37..43 (up to 7 x 4). One holder per open menu.
 */
public final class RigMenu implements Listener {
    private static final int SIZE = 54;
    private static final int MAX_W = 7;
    private static final int MAX_H = 4;
    private static final int SLOT_BACK = 0;
    private static final int SLOT_HELP = 2;
    private static final int SLOT_HELD = 4;
    private static final int SLOT_ROTATE = 5;
    private static final int SLOT_FUSE = 7;
    private static final int SLOT_CLOSE = 8;
    private static final int SLOT_SCHEMATICS = 9;
    private static final int SLOT_FAMILIES = 17;
    private static final int SLOT_COMBOS = 26;
    private static final int SLOT_EXPAND = 35;
    private static final int SLOT_STATS = 44;
    private static final int SLOT_EMPTY_ALL = 53;
    /** Rig tabs along the bottom row: drill, rod, then one per gun. */
    private static final int SLOT_TAB_DRILL = 45;
    private static final int SLOT_TAB_ROD = 46;
    private static final int SLOT_TAB_GUN0 = 47;
    private static final int MAX_GUN_TABS = 5;

    private final DrillManager drills;
    private final DrillMenu bay;

    public RigMenu(DrillManager drills, DrillMenu bay) {
        this.drills = drills;
        this.bay = bay;
    }

    enum Mode { RIG, FUSE }

    public static final class Holder implements InventoryHolder {
        final UUID owner;
        /** Which rig: "drill", "rod" or "gun:<id>". */
        String rigKey = DrillManager.RIG_DRILL;
        Inventory inventory;
        Mode mode = Mode.RIG;
        /** Player inventory slot of the module being fitted, or -1. */
        int held = -1;
        int rot;

        Holder(UUID owner) { this.owner = owner; }

        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    /** Opens the rig that fits the module in heldSlot (or the drill rig), with that module picked up. heldSlot -1 = none. */
    public void open(Player p, int heldSlot) {
        String key = DrillManager.RIG_DRILL;
        if (heldSlot >= 0) {
            ModuleDef m = drills.cfg().module(drills.items().moduleId(p.getInventory().getItem(heldSlot)));
            if (m != null) key = rigFor(m.kind(), key);
        }
        open(p, key, heldSlot);
    }

    /** The rig key for a module kind (stays on current if it already fits). */
    private String rigFor(String kind, String current) {
        if (DrillManager.kindOf(current).equals(kind)) return current;
        if (DrillConfig.KIND_ROD.equals(kind)) return DrillManager.RIG_ROD;
        if (DrillConfig.KIND_GUN.equals(kind) && !drills.cfg().rigGuns().isEmpty()) return DrillManager.gunRigKey(drills.cfg().rigGuns().getFirst());
        return DrillManager.RIG_DRILL;
    }

    /** Opens a rig ("drill", "rod", "gun:<id>"). heldSlot = player inventory slot of a module to pick up, or -1. */
    public void open(Player p, String rigKey, int heldSlot) {
        if (drills.data(p.getUniqueId()) == null) {
            Text.send(p, "<red>Your drill is still loading.");
            return;
        }
        Holder h = new Holder(p.getUniqueId());
        h.rigKey = drills.validRig(rigKey) ? rigKey : DrillManager.RIG_DRILL;
        h.inventory = Bukkit.createInventory(h, SIZE, Text.mm("<#3a3028>" + drills.rigTitle(h.rigKey)));
        if (heldSlot >= 0 && drills.items().moduleId(p.getInventory().getItem(heldSlot)) != null) h.held = heldSlot;
        render(p, h);
        p.openInventory(h.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.7f, 1.1f);
    }

    public void refreshIfOpen(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h && h.owner.equals(p.getUniqueId())) render(p, h);
    }

    private static int boardSlot(int x, int y) { return (y + 1) * 9 + 1 + x; }

    /** Board (x, y) for a slot, or null if it isn't a board slot. */
    private static int[] boardCell(int slot) {
        int row = slot / 9 - 1, col = slot % 9 - 1;
        if (row < 0 || row >= MAX_H || col < 0 || col >= MAX_W) return null;
        return new int[]{col, row};
    }

    // ---- rendering ----

    private void render(Player p, Holder h) {
        if (h.mode == Mode.FUSE) renderFuse(p, h);
        else renderRig(p, h);
    }

    private void renderRig(Player p, Holder h) {
        Inventory inv = h.inventory;
        DrillConfig cfg = drills.cfg();
        DrillItems items = drills.items();
        DrillData d = drills.data(p.getUniqueId());
        DrillMath.Effective eff = drills.effective(p.getUniqueId());
        ModuleRig rig = drills.rig(p.getUniqueId(), h.rigKey);
        if (d == null || eff == null || rig == null) return;
        boolean drillRig = DrillManager.RIG_DRILL.equals(h.rigKey);
        ItemStack filler = items.icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", "<dark_gray> ", null);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        ItemStack heldItem = heldItem(p, h);
        ModuleDef heldDef = heldItem == null ? null : cfg.module(items.moduleId(heldItem));
        if (heldDef == null) h.held = -1;

        // Board
        ItemStack none = items.icon(Material.BLACK_STAINED_GLASS_PANE, "ui/cell_none", "<dark_gray> ", null);
        for (int y = 0; y < MAX_H; y++) {
            for (int x = 0; x < MAX_W; x++) {
                int slot = boardSlot(x, y);
                if (!rig.inside(x, y)) {
                    inv.setItem(slot, none);
                    continue;
                }
                ModuleRig.Entry e = rig.entryAt(x, y);
                inv.setItem(slot, e == null ? emptyCell(rig, x, y, heldDef) : moduleCell(e, x, y, heldDef != null));
            }
        }
        if (rig.cellCount() == 0 && drillRig) {
            FrameDef0 info = firstRigFrame(cfg);
            inv.setItem(boardSlot(3, 1), items.icon(Material.IRON_BARS, "ui/locked", "<red>No module rig",
                    List.of("<gray>Your " + eff.frame.coloredName() + "<gray> frame has no module rig.",
                            info == null ? "" : "<gray>Install the " + info.name + "<gray> frame to get one.")));
        }

        inv.setItem(SLOT_BACK, items.icon(Material.ARROW, "ui/back", "<gray>Back to the drill bay", null));
        inv.setItem(SLOT_CLOSE, items.icon(Material.BARRIER, "ui/close", "<red>Close", null));
        inv.setItem(SLOT_HELP, helpIcon(cfg, h.rigKey));
        inv.setItem(SLOT_HELD, heldIcon(cfg, h, heldItem, heldDef));
        inv.setItem(SLOT_ROTATE, items.icon(Material.COMPASS, "workshop/ui/rotate", "<#c8743a>Rotate",
                List.of("<gray>Turns the held module 90°.", "<gray>Right-clicking a rig cell does the same.")));
        inv.setItem(SLOT_FUSE, items.icon(Material.SMITHING_TABLE, "ui/fuse", "<#c070ff>Fuse Modules",
                List.of("<gray>Fuse two identical modules of the same", "<gray>mark into one of the next mark.",
                        "<gray>Higher marks: bigger upsides, smaller", "<gray>downsides. Mk IV and V also need parts.", "",
                        "<#c8743a>Click</#c8743a> <gray>to open the fuser")));
        inv.setItem(SLOT_SCHEMATICS, schematicsIcon(p, cfg, rig.kind));
        inv.setItem(SLOT_FAMILIES, familiesIcon(cfg, rig));
        inv.setItem(SLOT_COMBOS, combosIcon(cfg, d, rig));
        if (drillRig) inv.setItem(SLOT_EXPAND, expandIcon(cfg, d, eff, p.getUniqueId()));
        else inv.setItem(SLOT_EXPAND, gearSizeIcon(p, h.rigKey, rig));
        inv.setItem(SLOT_STATS, statsIcon(rig.totals(cfg), h.rigKey));
        renderTabs(p, h);
        if (!rig.entries.isEmpty()) {
            inv.setItem(SLOT_EMPTY_ALL, items.icon(Material.HOPPER_MINECART, "ui/cancel", "<gray>Take every module out",
                    List.of("<gray>Moves all fitted modules", "<gray>back to your inventory.", "", "<#c8743a>Shift-click</#c8743a> <gray>to confirm")));
        }
    }

    private void renderTabs(Player p, Holder h) {
        DrillItems items = drills.items();
        UUID id = p.getUniqueId();
        h.inventory.setItem(SLOT_TAB_DRILL, tab(Material.NETHERITE_PICKAXE, null, "<#7fd65a>Drill rig", DrillManager.RIG_DRILL, h, id,
                "<gray>Mining upgrades."));
        h.inventory.setItem(SLOT_TAB_ROD, tab(Material.FISHING_ROD, null, "<#5fb4c8>Rod rig", DrillManager.RIG_ROD, h, id,
                "<gray>Fishing upgrades. Grows with your rod tier."));
        List<String> guns = drills.cfg().rigGuns();
        for (int i = 0; i < Math.min(MAX_GUN_TABS, guns.size()); i++) {
            String gun = guns.get(i);
            h.inventory.setItem(SLOT_TAB_GUN0 + i, tab(Material.CARROT_ON_A_STICK, "gun/" + gun, "<#ff8a5c>" + DrillManager.gunName(gun) + " rig",
                    DrillManager.gunRigKey(gun), h, id, "<gray>Weapon upgrades for every " + DrillManager.gunName(gun) + " you carry."));
        }
    }

    private ItemStack tab(Material mat, String model, String name, String key, Holder h, UUID id, String line) {
        boolean current = key.equals(h.rigKey);
        int n = drills.placedCount(id, key);
        List<String> lore = new ArrayList<>();
        lore.add(line);
        lore.add("<gray>Modules fitted: <white>" + n);
        lore.add("");
        lore.add(current ? "<green>Showing this rig" : "<#c8743a>Click</#c8743a> <gray>to switch");
        ItemStack icon = drills.items().icon(mat, model, name, lore);
        if (current) {
            ItemMeta meta = icon.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /** For the rod and gun rigs: what sets the grid size. */
    private ItemStack gearSizeIcon(Player p, String key, ModuleRig rig) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>This rig is <white>" + rig.width + "x" + rig.height + "</white>.");
        if (DrillManager.RIG_ROD.equals(key)) {
            lore.add("<gray>Upgrade your rod (rod bay at the waters)");
            lore.add("<gray>for a bigger rig and core cells.");
        } else {
            lore.add("<gray>Each gun model has its own rig size.");
            lore.add("<gray>The modules work on every one of that");
            lore.add("<gray>gun you carry.");
        }
        return drills.items().icon(Material.IRON_BARS, "ui/info", "<#c8743a>Rig size", lore);
    }

    private record FrameDef0(String name) {}

    private static FrameDef0 firstRigFrame(DrillConfig cfg) {
        for (DrillConfig.FrameDef f : cfg.frames) if (f.hasRig()) return new FrameDef0(f.coloredName());
        return null;
    }

    private ItemStack heldItem(Player p, Holder h) {
        if (h.held < 0) return null;
        ItemStack it = p.getInventory().getItem(h.held);
        return drills.items().moduleId(it) == null ? null : it;
    }

    private ItemStack emptyCell(ModuleRig rig, int x, int y, ModuleDef held) {
        boolean core = rig.isCore(x, y);
        List<String> lore = new ArrayList<>();
        if (core) lore.add("<gold>Core cell: Legendary modules only.");
        if (held != null) {
            lore.add("<#c8743a>Left-click</#c8743a> <gray>to fit " + held.coloredName() + "<gray> here");
            lore.add("<dark_gray>(its <white>◆</white><dark_gray> cell goes on this one)");
            lore.add("<#c8743a>Right-click</#c8743a> <gray>to rotate it");
        } else {
            lore.add("<gray>Click a module in your inventory");
            lore.add("<gray>to pick it up, then click here.");
        }
        return drills.items().icon(core ? Material.ORANGE_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                core ? "ui/cell_core" : "ui/cell_empty", core ? "<gold>Core cell" : "<gray>Empty cell", lore);
    }

    private ItemStack moduleCell(ModuleRig.Entry e, int x, int y, boolean holding) {
        DrillItems items = drills.items();
        boolean pivot = e.cells.getFirst()[0] == x && e.cells.getFirst()[1] == y;
        List<String> lore = new ArrayList<>();
        if (!e.online) {
            lore.add("<red><bold>OFFLINE</bold></red> <red>" + e.problem);
            lore.add("<dark_gray>It does nothing until you fix that.");
            lore.add("");
        }
        lore.addAll(items.moduleLore(e.def, e.placed.mk, e.drain, false));
        lore.add("");
        if (holding) lore.add("<dark_gray>Put the held module down first (click it in your inventory).");
        lore.add("<#c8743a>Click</#c8743a> <gray>to pick it up and move it");
        lore.add("<#c8743a>Shift-click</#c8743a> <gray>to take it out");
        String name = e.def.coloredName(e.placed.mk) + (e.online ? "" : " <red>(offline)");
        ItemStack icon;
        if (pivot) {
            icon = items.module(e.def, e.placed.mk);
        } else {
            icon = items.icon(cellMaterial(e), "ui/cell_" + (e.online ? e.def.rarity().name().toLowerCase(java.util.Locale.ROOT) : "offline"), name, null);
        }
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Text.item(name));
        meta.lore(Text.lore(lore));
        icon.setItemMeta(meta);
        return icon;
    }

    private static Material cellMaterial(ModuleRig.Entry e) {
        if (!e.online) return Material.RED_STAINED_GLASS_PANE;
        return switch (e.def.rarity()) {
            case COMMON -> Material.WHITE_STAINED_GLASS_PANE;
            case RARE -> Material.BLUE_STAINED_GLASS_PANE;
            case EPIC -> Material.PURPLE_STAINED_GLASS_PANE;
            case LEGENDARY -> Material.YELLOW_STAINED_GLASS_PANE;
        };
    }

    private ItemStack heldIcon(DrillConfig cfg, Holder h, ItemStack held, ModuleDef def) {
        DrillItems items = drills.items();
        if (def == null) {
            return items.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "ui/socket_empty", "<gray>No module picked up",
                    List.of("<gray>Click a module in your inventory", "<gray>to pick it up, then click a rig cell."));
        }
        int mk = items.moduleMark(held);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Picked up. Click a rig cell to fit it.");
        lore.add("<gray>Rotation <white>" + (h.rot * 90) + "°</white>:");
        lore.addAll(def.shape().picture(h.rot, def.rarity().color, true));
        lore.add("<dark_gray>The <white>◆</white><dark_gray> cell goes on the cell you click.");
        lore.add("");
        lore.addAll(DrillMath.effectLines(cfg, def, mk, 1.0));
        lore.add("");
        lore.add("<#c8743a>Click</#c8743a> <gray>to rotate · click the module in your inventory to put it down");
        ItemStack icon = items.module(def, mk);
        ItemMeta meta = icon.getItemMeta();
        meta.lore(Text.lore(lore));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack helpIcon(DrillConfig cfg, String rigKey) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Every module has a shape. Fit them on the");
        lore.add("<gray>rig without overlapping. Pick one up from your");
        lore.add("<gray>inventory, rotate it, click where it goes.");
        lore.add("");
        lore.add("<gold>■</gold> <gray>Core cells only take Legendary modules,");
        lore.add("<gray>  and every Legendary needs one.");
        lore.add("<yellow>⚠</yellow> <gray>Some modules must touch, or avoid,");
        lore.add("<gray>  a family, or sit on the rig's edge. If they");
        lore.add("<gray>  don't, they go <red>offline</red><gray>.");
        lore.add("<gray>Families give set bonuses when you run");
        lore.add("<gray>  several of them. Some modules touching");
        lore.add("<gray>  each other unlock <gold>hidden combos</gold><gray>.");
        lore.add("");
        lore.add("<gray>Fuse two identical modules for the next mark.");
        lore.add("<gray>New modules: blueprint fragments teach");
        lore.add("<gray>schematics (<white>/salvage</white>), then build them at");
        lore.add("<gray>your workshop's Assembly Bench.");
        lore.add("");
        lore.add("<gray>The tabs along the bottom switch between your");
        lore.add("<gray>drill, rod and weapon rigs. Each takes its own");
        lore.add("<gray>kind of module.");
        return drills.items().icon(Material.BOOK, "ui/info", "<#c8743a>How the rig works", lore);
    }

    private ItemStack schematicsIcon(Player p, DrillConfig cfg, String kind) {
        List<String> lore = new ArrayList<>();
        int known = 0, total = 0;
        for (ModuleDef m : cfg.modules.values()) {
            if (!m.kind().equals(kind)) continue;
            total++;
            boolean k = drills.knows(p.getUniqueId(), m.id());
            if (k) known++;
            lore.add((k ? "<green>✔ " : "<dark_gray>✘ ") + (k ? m.coloredName() : "<dark_gray>" + m.rarity().label + " schematic"));
        }
        lore.add(0, "<gray>Known " + DrillConfig.kindLabel(kind) + "s <white>" + known + "/" + total);
        lore.add(1, "");
        lore.add("");
        lore.add("<gray>Decode blueprint fragments in <white>/salvage</white>");
        lore.add("<gray>to learn new ones. Build them at the");
        lore.add("<gray>Assembly Bench in your workshop.");
        return drills.items().icon(Material.MAP, "ui/info", "<#7fb8d6>Schematics", lore);
    }

    private ItemStack familiesIcon(DrillConfig cfg, ModuleRig rig) {
        List<String> lore = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (ModuleDef m : cfg.modules.values()) if (m.kind().equals(rig.kind) && !m.family().isEmpty()) used.add(m.family());
        for (FamilyDef f : cfg.families.values()) {
            if (!used.contains(f.id())) continue;
            int n = rig.familyCounts.getOrDefault(f.id(), 0);
            lore.add(f.coloredName() + " <white>" + n + "</white>" + (f.max() < 99 ? "<dark_gray>/" + f.max() + " max" : ""));
            for (Map.Entry<Integer, Map<String, Double>> b : f.bonuses().entrySet()) {
                boolean on = n >= b.getKey();
                StringBuilder sb = new StringBuilder(on ? "<green>  " : "<dark_gray>  ").append(b.getKey()).append(": ");
                List<String> parts = new ArrayList<>();
                for (Map.Entry<String, Double> x : b.getValue().entrySet()) {
                    double pct = (x.getValue() - 1) * 100;
                    parts.add((pct >= 0 ? "+" : "") + Math.round(pct) + "% " + DrillMath.STATS.getOrDefault(x.getKey(), x.getKey()));
                }
                sb.append(String.join(", ", parts));
                lore.add(sb.toString());
            }
            if (!f.lore().isEmpty()) lore.add("<#8a8070>  <i>" + f.lore());
        }
        if (used.isEmpty()) lore.add("<dark_gray>No families for this rig.");
        return drills.items().icon(Material.KNOWLEDGE_BOOK, "ui/info", "<#c8743a>Family set bonuses", lore);
    }

    private ItemStack combosIcon(DrillConfig cfg, DrillData d, ModuleRig rig) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Fit the right two modules side by side");
        lore.add("<gray>and something new happens.");
        lore.add("");
        int shown = 0, found = 0;
        for (ComboDef c : cfg.combos.values()) {
            ModuleDef first = cfg.module(c.a());
            if (first == null || !first.kind().equals(rig.kind)) continue;
            shown++;
            if (d.combos.contains(c.id())) found++;
            boolean active = rig.activeCombos.contains(c);
            if (d.combos.contains(c.id())) {
                lore.add((active ? "<gold>✦ " : "<gray>✧ ") + c.name() + (active ? " <green>(online)" : ""));
                lore.add("<dark_gray>  " + moduleName(cfg, c.a()) + " + " + moduleName(cfg, c.b()));
                lore.add("<#8a8070>  <i>" + c.lore());
            } else {
                lore.add("<dark_gray>✧ ??? <i>(undiscovered)");
            }
        }
        return drills.items().icon(Material.AMETHYST_CLUSTER, "ui/info", "<gold>Hidden combos <gray>(" + found + "/" + shown + ")", lore);
    }

    private static String moduleName(DrillConfig cfg, String id) {
        ModuleDef m = cfg.module(id);
        return m == null ? id : m.name();
    }

    private ItemStack expandIcon(DrillConfig cfg, DrillData d, DrillMath.Effective eff, UUID owner) {
        DrillItems items = drills.items();
        DrillConfig.ExpansionDef next = drills.nextExpansion(d);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Bolt extra columns of cells onto the");
        lore.add("<gray>rig, for Shards. Kept through prestige.");
        lore.add("<gray>Bought <white>" + d.expansion + "/" + cfg.expansions.size());
        lore.add("");
        if (next == null) {
            lore.add("<gold>Fully expanded.");
            return items.icon(Material.NETHERITE_BLOCK, "ui/upgrade", "<#c8743a>Rig Expansion", lore);
        }
        EconomyService eco = Services.get(EconomyService.class);
        DrillConfig.FrameDef min = next.frame().isEmpty() ? null : cfg.frame(next.frame());
        boolean frameOk = eff.frame.hasRig() && (min == null || eff.frame.index() >= min.index());
        boolean afford = eco.balance(owner, Currency.SHARDS) >= next.shards();
        lore.add("<gray>Next: <white>+" + next.columns() + " column" + (next.columns() == 1 ? "" : "s"));
        if (min != null) lore.add((frameOk ? "<green>✔ " : "<red>✘ ") + "<gray>Needs the " + min.coloredName() + "<gray> frame or better");
        lore.add((afford ? "<green>✔ " : "<red>✘ ") + "<gray>Costs " + eco.format(Currency.SHARDS, next.shards()));
        lore.add("");
        lore.add(frameOk && afford ? "<#c8743a>Click</#c8743a> <gray>to buy" : "<dark_gray>Not available yet");
        return items.icon(frameOk && afford ? Material.ANVIL : Material.CHIPPED_ANVIL, "ui/upgrade", "<#c8743a>Rig Expansion", lore);
    }

    private ItemStack statsIcon(ModuleRig.Totals totals, String rigKey) {
        List<String> lore = new ArrayList<>();
        Map<String, Double> mul = new LinkedHashMap<>();
        for (String stat : DrillMath.STATS.keySet()) {
            double v = totals.m(stat);
            if (Math.abs(v - 1) > 0.0005) mul.put(stat, v);
        }
        if (mul.isEmpty()) lore.add("<dark_gray>No module effects running.");
        for (Map.Entry<String, Double> e : mul.entrySet()) {
            boolean good = !Bad.isBadWhenHigher(e.getKey()) == (e.getValue() >= 1);
            lore.add(DrillMath.effectLine(e.getKey(), e.getValue(), good));
        }
        for (String f : totals.flags()) lore.add("<green>+ " + DrillMath.FLAGS.getOrDefault(f, f));
        if (DrillManager.RIG_ROD.equals(rigKey) && !mul.isEmpty()) lore.add("<dark_gray>Adds to your rod's enchants and lures.");
        return drills.items().icon(Material.CLOCK, "ui/info", "<#c8743a>Rig total", lore);
    }

    /** Stats where a bigger number is worse for the player. */
    static final class Bad {
        static boolean isBadWhenHigher(String stat) {
            return switch (stat) {
                case "hand-heat", "proc-heat", "shaped-heat", "stall", "spread", "recoil", "reload-time" -> true;
                default -> false;
            };
        }
    }

    // ---- fuse page ----

    private void renderFuse(Player p, Holder h) {
        Inventory inv = h.inventory;
        DrillConfig cfg = drills.cfg();
        DrillItems items = drills.items();
        ItemStack filler = items.icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", "<dark_gray> ", null);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);
        inv.setItem(SLOT_BACK, items.icon(Material.ARROW, "ui/back", "<gray>Back to the rig", null));
        inv.setItem(SLOT_CLOSE, items.icon(Material.BARRIER, "ui/close", "<red>Close", null));
        inv.setItem(SLOT_HELD, items.icon(Material.SMITHING_TABLE, "ui/fuse", "<#c070ff>Module Fuser",
                List.of("<gray>Two identical modules of the same mark", "<gray>fuse into one of the next mark.",
                        "<gray>Modules must be in your inventory", "<gray>(take them off the rig first).")));
        // Group inventory modules by (id, mk).
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            String id = items.moduleId(it);
            if (id == null || cfg.module(id) == null) continue;
            counts.merge(id + "#" + items.moduleMark(it), 1, Integer::sum);
        }
        ScrapService scrap = Services.find(ScrapService.class).orElse(null);
        int slot = 18;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (slot >= 45) break;
            String[] k = e.getKey().split("#");
            ModuleDef m = cfg.module(k[0]);
            int mk = Integer.parseInt(k[1]);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>In your inventory: <white>" + e.getValue());
            if (mk >= cfg.maxMark) {
                lore.add("<gold>Top mark.");
            } else {
                lore.add("<gray>Fuse 2 → <white>" + m.coloredName(mk + 1));
                lore.add("");
                lore.addAll(DrillMath.effectLines(cfg, m, mk + 1, 1.0));
                DrillManager.FuseCost cost = drills.fuseCost(m, mk);
                if (cost.amount() > 0) {
                    lore.add("");
                    ScrapService.State st = ScrapService.State.parse(cost.state());
                    int have = scrap == null ? 0 : scrap.count(p, cost.partId(), st);
                    String name = scrap == null ? cost.partId() : scrap.partName(cost.partId(), st);
                    lore.add((have >= cost.amount() ? "<green>✔ " : "<red>✘ ") + "<gray>Needs <white>" + cost.amount() + "x</white> " + name
                            + (st == ScrapService.State.PRISTINE ? "" : " <dark_gray>(or better)") + " <dark_gray>(" + have + ")");
                }
                lore.add((e.getValue() >= 2 ? "<green>✔ " : "<red>✘ ") + "<gray>Needs 2 of this module");
                lore.add("");
                lore.add(e.getValue() >= 2 ? "<#c8743a>Click</#c8743a> <gray>to fuse" : "<dark_gray>Find or build another one");
            }
            ItemStack icon = items.module(m, mk);
            ItemMeta meta = icon.getItemMeta();
            meta.lore(Text.lore(lore));
            icon.setItemMeta(meta);
            inv.setItem(slot++, icon);
        }
        if (counts.isEmpty()) {
            inv.setItem(31, items.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "ui/socket_empty", "<gray>No modules in your inventory",
                    List.of("<gray>Take modules off the rig, or find", "<gray>and build more, to fuse them.")));
        }
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
        if (e.getClickedInventory() == null) return;
        Inventory top = e.getView().getTopInventory();
        DrillConfig cfg = drills.cfg();
        DrillItems items = drills.items();

        if (!e.getClickedInventory().equals(top)) {
            if (h.mode != Mode.RIG) return;
            String id = items.moduleId(e.getCurrentItem());
            if (id == null) return;
            if (h.held == e.getSlot()) {
                h.held = -1;
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
            } else {
                h.held = e.getSlot();
                h.rot = 0;
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.7f, 1.3f);
                ModuleDef m = cfg.module(id);
                if (m != null && !DrillManager.kindOf(h.rigKey).equals(m.kind())) {
                    // It's for another rig: reopen on that one (the title changes with it).
                    int held = h.held;
                    open(p, rigFor(m.kind(), h.rigKey), held);
                    return;
                }
            }
            render(p, h);
            return;
        }

        int slot = e.getRawSlot();
        if (slot == SLOT_CLOSE) {
            p.closeInventory();
            return;
        }
        if (h.mode == Mode.FUSE) {
            if (slot == SLOT_BACK) {
                h.mode = Mode.RIG;
                render(p, h);
                return;
            }
            fuseClick(p, h, slot);
            return;
        }
        if (slot == SLOT_BACK) {
            bay.open(p);
            return;
        }
        if (slot == SLOT_HELD || slot == SLOT_ROTATE) {
            rotate(p, h);
            return;
        }
        if (slot == SLOT_FUSE) {
            h.mode = Mode.FUSE;
            h.held = -1;
            render(p, h);
            return;
        }
        if (slot == SLOT_TAB_DRILL || slot == SLOT_TAB_ROD || (slot >= SLOT_TAB_GUN0 && slot < SLOT_TAB_GUN0 + MAX_GUN_TABS)) {
            String key;
            if (slot == SLOT_TAB_DRILL) key = DrillManager.RIG_DRILL;
            else if (slot == SLOT_TAB_ROD) key = DrillManager.RIG_ROD;
            else {
                List<String> guns = cfg.rigGuns();
                int i = slot - SLOT_TAB_GUN0;
                if (i >= guns.size()) return;
                key = DrillManager.gunRigKey(guns.get(i));
            }
            if (key.equals(h.rigKey)) return;
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            open(p, key, h.held);
            return;
        }
        if (slot == SLOT_EXPAND) {
            if (!DrillManager.RIG_DRILL.equals(h.rigKey)) return;
            switch (drills.expand(p)) {
                case OK -> {
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
                    Text.send(p, "<green>Rig expanded.");
                }
                case MAX -> deny(p, "Your rig is fully expanded.");
                case FRAME -> deny(p, "Your frame can't take that expansion yet.");
                case POOR -> deny(p, "Not enough Shards.");
            }
            render(p, h);
            return;
        }
        if (slot == SLOT_EMPTY_ALL && e.isShiftClick()) {
            DrillData d = drills.data(p.getUniqueId());
            if (d == null) return;
            int n = 0;
            while (drills.placedCount(p.getUniqueId(), h.rigKey) > 0) {
                ItemStack it = drills.takeOut(p, h.rigKey, 0);
                if (it == null) break;
                drills.give(p, it);
                n++;
            }
            h.held = -1;
            Text.send(p, "<gray>Took " + n + " module" + (n == 1 ? "" : "s") + " off the rig.");
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.2f);
            render(p, h);
            return;
        }
        int[] cell = boardCell(slot);
        if (cell == null) return;
        ModuleRig rig = drills.rig(p.getUniqueId(), h.rigKey);
        if (rig == null || !rig.inside(cell[0], cell[1])) return;
        ItemStack held = heldItem(p, h);
        if (held != null && e.isRightClick()) {
            rotate(p, h);
            return;
        }
        if (held != null) {
            ModuleDef m = cfg.module(items.moduleId(held));
            if (m == null) {
                deny(p, "That module no longer exists.");
                return;
            }
            ModuleRig.Entry there = rig.entryAt(cell[0], cell[1]);
            String why = drills.place(p, h.rigKey, m, items.moduleMark(held), cell[0], cell[1], h.rot);
            if (why != null) {
                deny(p, there != null && why.startsWith("It overlaps") ? why + " Pick that one up first." : why);
                return;
            }
            p.getInventory().setItem(h.held, null);
            h.held = -1;
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.4f);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.6f);
            ModuleRig after = drills.rig(p.getUniqueId(), h.rigKey);
            ModuleRig.Entry placed = after == null ? null : after.entryAt(cell[0], cell[1]);
            if (placed != null && !placed.online) Text.send(p, "<yellow>" + m.name() + " is fitted but offline: " + placed.problem + ".");
            render(p, h);
            return;
        }
        ModuleRig.Entry entry = rig.entryAt(cell[0], cell[1]);
        if (entry == null) return;
        if (p.getInventory().firstEmpty() < 0) {
            deny(p, "Make room in your inventory first.");
            return;
        }
        ItemStack out = drills.takeOut(p, h.rigKey, entry.index);
        if (out == null) return;
        int free = p.getInventory().firstEmpty();
        p.getInventory().setItem(free, out);
        if (!e.isShiftClick()) {
            h.held = free;
            h.rot = entry.placed.rot;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.2f);
        render(p, h);
    }

    private void rotate(Player p, Holder h) {
        ItemStack held = heldItem(p, h);
        if (held == null) {
            deny(p, "Pick a module up from your inventory first.");
            return;
        }
        ModuleDef m = drills.cfg().module(drills.items().moduleId(held));
        int n = m == null ? 4 : m.shape().rotations();
        h.rot = (h.rot + 1) % Math.max(1, n);
        p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f, 1.4f);
        render(p, h);
    }

    private void fuseClick(Player p, Holder h, int slot) {
        ItemStack icon = h.inventory.getItem(slot);
        String id = drills.items().moduleId(icon);
        if (id == null || slot < 18 || slot >= 45) return;
        ModuleDef m = drills.cfg().module(id);
        int mk = drills.items().moduleMark(icon);
        if (m == null) return;
        String why = drills.fuse(p, m, mk);
        if (why != null) {
            deny(p, why);
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 0.9f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);
        Text.send(p, "<gray>Fused into " + m.coloredName(mk + 1) + "<gray>.");
        if (mk + 1 >= drills.cfg().maxMark && m.rarity().ordinal() >= Rarity.EPIC.ordinal()) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<white>" + p.getName() + "</white> <gray>fused a " + m.coloredName(mk + 1) + "<gray>."));
        }
        render(p, h);
    }

    private static void deny(Player p, String msg) {
        Text.send(p, "<red>" + msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
    }
}
