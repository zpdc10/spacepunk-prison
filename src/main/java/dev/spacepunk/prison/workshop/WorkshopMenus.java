package dev.spacepunk.prison.workshop;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ScrapService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import dev.spacepunk.prison.workshop.BayData.MachineData;
import dev.spacepunk.prison.workshop.PlotRegistry.Plot;
import dev.spacepunk.prison.workshop.WorkshopConfig.Assembly;
import dev.spacepunk.prison.workshop.WorkshopConfig.BayLevel;
import dev.spacepunk.prison.workshop.WorkshopConfig.Decor;
import dev.spacepunk.prison.workshop.WorkshopConfig.Good;
import dev.spacepunk.prison.workshop.WorkshopConfig.MachineDef;
import dev.spacepunk.prison.workshop.WorkshopConfig.Recipe;
import dev.spacepunk.prison.workshop.WorkshopConfig.Role;
import dev.spacepunk.prison.workshop.WorkshopConfig.Stage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/** Every workshop menu: terminal, machine panel, Bay Locker, Trade Terminal, Machine Shop, Assembly Bench. */
final class WorkshopMenus {
    private final WorkshopManager ws;

    WorkshopMenus(WorkshopManager ws) { this.ws = ws; }

    private static EconomyService eco() { return Services.get(EconomyService.class); }

    private WorkshopConfig cfg() { return ws.cfg(); }

    // =============================================================================================
    // Terminal (main menu)
    // =============================================================================================

    void main(Player p) {
        Bay bay = ws.bay(p.getUniqueId());
        if (bay == null) {
            ws.ensureBay(p);
            bay = ws.bay(p.getUniqueId());
            if (bay == null) {
                Text.send(p, "<red>Your profile is still loading.");
                return;
            }
        }
        Bay b = bay;
        Gui g = new Gui(5, "<dark_gray>Hull Bay Terminal");
        g.live = true;
        g.renderer(gui -> renderMain(p, gui, b)).open(p);
    }

    private void renderMain(Player p, Gui g, Bay bay) {
        WorkshopConfig c = cfg();
        Plot plot = bay.plot;
        BayLevel lv = c.level(plot.level);
        List<String> st = new ArrayList<>();
        st.add("<gray>Size: <white>" + lv.size() + "x" + lv.size() + "</white>, height " + lv.height() + " <dark_gray>(level " + plot.level + "/" + c.maxLevel() + ")");
        st.add("<gray>Machines: <white>" + bay.machineCount() + "</white> / " + lv.slots());
        st.add("<gray>Bay Locker: <white>" + Numbers.shortFormat(bay.data.lockerUsed()) + "</white> / " + Numbers.shortFormat(lv.locker()));
        st.add("");
        st.add(powerLine(bay));
        st.add(coolantLine(bay));
        st.add(bay.alerts > 0 ? "<red>⚠ " + bay.alerts + " machine(s) need attention" : "<green>All machines nominal");
        st.add("");
        st.add("<gray>Lifetime sales: " + eco().format(Currency.CREDITS, bay.data.lifetimeSales));
        st.add("<gray>Goods made: <white>" + Numbers.shortFormat(bay.data.lifetimeCrafted));
        g.set(4, Gui.icon(Material.LODESTONE, "workshop/ui/bay", "<#c8743a>" + plot.name + "'s Hull Bay", st));

        g.set(19, Gui.icon(Material.BARREL, "workshop/ui/locker", "<#e0a050>Bay Locker", List.of(
                "<gray>Finished goods, byproducts and parts.",
                "<gray>Take goods out as items, or store them.", "", "<yellow>Click to open")), e -> { Gui.click(p); locker(p, 0); });
        g.set(21, Gui.icon(Material.EMERALD, "workshop/ui/market", "<green>Trade Terminal", List.of(
                "<gray>Sell goods at today's prices.",
                "<gray>Priority orders: " + String.join("<gray>, ", ws.market.priorities().stream().map(id -> "<white>" + ws.items.name(id)).toList()),
                "", "<yellow>Click to open")), e -> { Gui.click(p); market(p, 0); });
        g.set(23, Gui.icon(Material.SMITHING_TABLE, "workshop/ui/shop", "<#c8743a>Machine Shop", List.of(
                "<gray>Buy machines, conveyors and decor.", "", "<yellow>Click to open")), e -> { Gui.click(p); shop(p); });
        g.set(25, Gui.icon(Material.CRAFTER, "workshop/ui/assembly", "<#6fd6a8>Assembly Bench", List.of(
                "<gray>Build drill, rod and weapon modules from schematics,", "<gray>scrap parts and workshop components.", "", "<yellow>Click to open")),
                e -> { Gui.click(p); assembly(p, 0); });
        g.set(30, Gui.icon(Material.GRINDSTONE, "workshop/ui/refurbisher", "<#c8a070>Refurbisher", List.of(
                "<gray>Repair fried scrap parts. Some come", "<gray>out pristine.", "", "<yellow>Click to open")),
                e -> { Gui.click(p); refurbisher(p); });

        g.set(28, Gui.icon(Material.COMPARATOR, "workshop/ui/machines", "<white>Machine Overview", List.of(
                "<gray>Every machine, its status and heat.", "<gray>Open any of them from here.", "", "<yellow>Click to open")),
                e -> { Gui.click(p); machineList(p, 0); });

        List<String> ex = new ArrayList<>();
        if (plot.level >= c.maxLevel()) {
            ex.add("<green>Fully expanded");
        } else {
            BayLevel next = c.level(plot.level + 1);
            ex.add("<gray>Next: <white>" + next.size() + "x" + next.size() + "</white>, height " + next.height());
            ex.add("<gray>Machine slots: <white>" + lv.slots() + "</white> -> <green>" + next.slots());
            ex.add("<gray>Locker: <white>" + Numbers.shortFormat(lv.locker()) + "</white> -> <green>" + Numbers.shortFormat(next.locker()));
            ex.add("<gray>Needs Grade " + next.grade());
            ex.add("<gray>Cost: " + ws.costText(next));
            ex.add("");
            ex.add("<yellow>Shift-click to expand");
        }
        g.set(32, Gui.icon(Material.PISTON, "workshop/ui/expand", "<aqua>Expand Bay", ex), e -> {
            if (!e.getClick().isShiftClick()) { Gui.deny(p); return; }
            ws.expand(p);
        });

        List<String> ac = new ArrayList<>();
        ac.add(plot.open ? "<green>Open to visitors" : "<gray>Sealed: crew only");
        ac.add("");
        ac.add("<gray>Crew (can enter and build):");
        if (plot.crew.isEmpty()) ac.add("<dark_gray>  nobody yet");
        for (String u : plot.crew) {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(u)).getName();
            ac.add("<dark_gray>  · <white>" + (name == null ? u.substring(0, 8) : name));
        }
        ac.add("");
        ac.add("<dark_gray>/ws crew add|remove <player>");
        ac.add("<yellow>Click to " + (plot.open ? "seal" : "open") + " the bay");
        g.set(34, Gui.icon(plot.open ? Material.IRON_DOOR : Material.IRON_BLOCK, "workshop/ui/access", "<white>Airlock Access", ac), e -> {
            Gui.click(p);
            ws.setOpen(p, !plot.open);
            g.render();
        });

        g.set(40, Gui.icon(Material.BARRIER, "ui/close", "<red>Close", List.of()), e -> p.closeInventory());
    }

    private String powerLine(Bay bay) {
        if (bay.powerDemand <= 0) return "<yellow>⚡ <gray>Power: idle <dark_gray>(" + WorkshopItems.fmt(bay.powerSupply) + " made)";
        String col = bay.powerSupply >= bay.powerDemand ? "<green>" : "<red>";
        return "<yellow>⚡ <gray>Power: " + col + WorkshopItems.fmt(bay.powerSupply) + "</gray> / " + WorkshopItems.fmt(bay.powerDemand) + " needed";
    }

    private String coolantLine(Bay bay) {
        if (bay.coolantDemand <= 0) return "<aqua>❄ <gray>Coolant: not needed";
        String col = bay.coolantSupply >= bay.coolantDemand ? "<green>" : "<red>";
        return "<aqua>❄ <gray>Coolant: " + col + WorkshopItems.fmt(bay.coolantSupply) + "</gray> / " + WorkshopItems.fmt(bay.coolantDemand) + " needed";
    }

    /** Read-only info for visitors at someone else's terminal. */
    void visitorInfo(Player p, Plot plot) {
        Gui g = new Gui(3, "<dark_gray>" + plot.name + "'s Hull Bay");
        g.renderer(gui -> {
            BayLevel lv = cfg().level(plot.level);
            Bay bay = ws.bay(plot.ownerId());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Size: <white>" + lv.size() + "x" + lv.size());
            if (bay != null) {
                lore.add("<gray>Machines: <white>" + bay.machineCount());
                lore.add(powerLine(bay));
            } else {
                lore.add("<dark_gray>Owner offline: machines are powered down.");
            }
            lore.add("");
            lore.add("<dark_gray>Your own bay: /ws");
            gui.set(13, Gui.icon(Material.LODESTONE, "workshop/ui/bay", "<#c8743a>" + plot.name + "'s Hull Bay", lore));
        }).open(p);
    }

    // =============================================================================================
    // Machine panel
    // =============================================================================================

    void machine(Player p, Bay bay, MachineData m) {
        Gui g = new Gui(5, "<dark_gray>" + nameOf(m));
        g.live = true;
        MachineDef md = cfg().machine(m.type);
        if (md != null && md.role() == Role.GENERATOR) {
            g.bottomClick = e -> { if (ws.fuelIn(p, bay, m, e.getCurrentItem())) g.render(); };
        }
        g.renderer(gui -> renderMachine(p, gui, bay, m)).open(p);
        ws.showOutput(p, m);
    }

    private String nameOf(MachineData m) {
        MachineDef def = cfg().machine(m.type);
        return (def == null ? m.type : def.name()) + " " + cfg().tier(m.tier).name();
    }

    private void renderMachine(Player p, Gui g, Bay bay, MachineData m) {
        WorkshopConfig c = cfg();
        MachineDef def = c.machine(m.type);
        if (def == null || !bay.data.machines.contains(m)) {
            p.closeInventory();
            return;
        }
        boolean owner = bay.owner.equals(p.getUniqueId());
        List<String> head = new ArrayList<>(ws.items.machineLore(def, m.tier, false));
        head.add("");
        head.add("<gray>Status: " + (m.status == null || m.status.isEmpty() ? "<gray>Starting up" : m.status));
        g.set(4, Gui.withLore(ws.items.machine(def, m.tier, 1), "<#c8743a>" + nameOf(m), head));

        // Heat gauge
        double heat = m.heat / c.heatMax;
        List<String> hl = new ArrayList<>();
        hl.add(heatBar(heat));
        hl.add("<gray>Heat: <white>" + Math.round(m.heat) + "</white> / " + Math.round(c.heatMax) + (m.overheated ? " <red>OVERHEATED" : ""));
        hl.add("");
        hl.add("<gray>Machines shed " + WorkshopItems.fmt(c.baseCooling) + " heat/s on their own.");
        hl.add("<gray>Each running machine touching this one");
        hl.add("<gray>adds " + Math.round(c.neighbourHeat * 100) + "% to its heat. Heat Sinks cool it.");
        hl.add("<gray>At " + Math.round(c.heatMax) + " it jams until it cools to " + Math.round(c.heatResume) + ".");
        ItemStack gauge = Gui.icon(Material.MAGMA_CREAM, "ui/heat_gauge", "<#ff8c1a>Heat", hl);
        ItemMeta gm = gauge.getItemMeta();
        var cmd = gm.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) Math.max(0, Math.min(1, heat))));
        gm.setCustomModelDataComponent(cmd);
        gauge.setItemMeta(gm);
        g.set(20, gauge);

        // Buffers
        g.set(21, Gui.icon(Material.HOPPER, "workshop/ui/input", "<white>Input", bufferLore(m.input, def.role() == Role.GENERATOR
                ? List.of("<gray>Fuel left: <white>" + Math.round(m.fuel) + "s", "",
                        "<gray>Burns fuel from, in order:",
                        "<dark_gray>1 <gray>this hopper: <yellow>click fuel in your inventory",
                        "<dark_gray>2 <gray>your <white>Bay Locker</white> (slag dust, fuel cells)",
                        "<dark_gray>3 <white>Slag</white> / <white>Slag Rubble</white> <gray>in your cargo hold",
                        "<dark_gray>Only burns while machines need power.") : List.of())));
        g.set(23, Gui.icon(Material.CHEST, "workshop/ui/output", "<white>Output", bufferLore(m.output, List.of(
                "<gray>Pushes out the front: <white>" + arrow(m.facing) + " " + m.facing.toLowerCase(),
                frontLine(bay, m)))));

        // What it does
        g.set(24, Gui.icon(Material.BOOK, "ui/info", "<white>What it does", roleLore(def)));

        if (!owner) {
            g.set(40, Gui.icon(Material.BARRIER, "ui/close", "<red>Close", List.of("<dark_gray>Only the owner can change machines.")), e -> p.closeInventory());
            return;
        }

        g.set(37, Gui.icon(Material.COMPASS, "workshop/ui/rotate", "<white>Rotate Output", List.of(
                "<gray>Now: <white>" + arrow(m.facing) + " " + m.facing.toLowerCase(), "", "<yellow>Click to turn clockwise")),
                e -> { Gui.click(p); ws.rotate(p, bay, m); g.render(); });
        g.set(38, Gui.icon(m.enabled ? Material.LIME_DYE : Material.GRAY_DYE, m.enabled ? "workshop/ui/toggle_on" : "workshop/ui/toggle_off",
                m.enabled ? "<green>Running" : "<red>Switched off", List.of("<yellow>Click to " + (m.enabled ? "switch off" : "switch on"))),
                e -> { Gui.click(p); m.enabled = !m.enabled; ws.save(bay.owner); g.render(); });

        if (def.role() == Role.SOURCE) {
            boolean locker = "locker".equals(m.source);
            g.set(39, Gui.icon(locker ? Material.BARREL : Material.MINECART, "workshop/ui/source", "<white>Pull From", List.of(
                    locker ? "<gray>The <white>Bay Locker</white>" : "<gray>Your <white>cargo hold</white>",
                    "", "<yellow>Click to switch")), e -> {
                Gui.click(p);
                m.source = locker ? "cargo" : "locker";
                m.filter = "any";
                ws.save(bay.owner);
                g.render();
            });
            List<String> options = filterOptions(bay, m);
            List<String> fl = new ArrayList<>();
            fl.add("<gray>Pulling: <white>" + ("any".equals(m.filter) ? "anything the next machine takes" : ws.items.name(m.filter)));
            fl.add("");
            fl.add("<yellow>Left-click: next <dark_gray>| <yellow>Right-click: previous");
            g.set(40, Gui.icon(Material.NAME_TAG, "workshop/ui/filter", "<white>Filter", fl), e -> {
                Gui.click(p);
                int i = Math.max(0, options.indexOf(m.filter));
                int n = options.size();
                m.filter = options.get(Math.floorMod(i + (e.getClick() == ClickType.RIGHT ? -1 : 1), n));
                ws.save(bay.owner);
                g.render();
            });
        }

        long up = ws.upgradeCost(m);
        List<String> ul = new ArrayList<>();
        if (up < 0) {
            ul.add("<green>Top mark");
        } else {
            WorkshopConfig.Tier next = c.tier(m.tier + 1);
            ul.add("<gray>To <white>" + next.name() + "</white>: speed x" + WorkshopItems.fmt(next.speed()) + ", heat x" + WorkshopItems.fmt(next.heat()));
            if (def.quality()) ul.add("<gray>Quality: <#7fb8d6>Refined " + WorkshopItems.pct(next.refined()) + "<gray>, <light_purple>Pristine " + WorkshopItems.pct(next.pristine()));
            ul.add("<gray>Cost: " + eco().format(Currency.CREDITS, up));
            for (Map.Entry<String, Long> e : next.components().entrySet()) {
                long have = BayData.countBase(bay.data.locker, e.getKey());
                ul.add((have >= e.getValue() ? "<green>" : "<red>") + e.getValue() + "x " + ws.items.name(e.getKey()) + " <dark_gray>(" + have + " in locker)");
            }
            ul.add("");
            ul.add("<yellow>Click to upgrade");
        }
        g.set(41, Gui.icon(Material.ANVIL, "ui/upgrade", "<aqua>Upgrade", ul), e -> {
            if (up < 0) return;
            ws.upgrade(p, bay, m);
            g.render();
        });

        g.set(43, Gui.icon(Material.SHEARS, "workshop/ui/pickup", "<#e0562e>Pick Up", List.of(
                "<gray>Returns the machine (with its mark) to you.",
                "<gray>Buffered items go to the Bay Locker.", "<dark_gray>Or sneak + right-click the machine in the bay.", "", "<yellow>Shift-click to pick up")), e -> {
            if (!e.getClick().isShiftClick()) { Gui.deny(p); return; }
            ws.pickup(p, bay, m);
        });
        g.set(44, Gui.icon(Material.ARROW, "ui/back", "<gray>Terminal", List.of()), e -> { Gui.click(p); main(p); });
    }

    private List<String> filterOptions(Bay bay, MachineData m) {
        TreeSet<String> ids = new TreeSet<>();
        Map<String, Long> from = "locker".equals(m.source) ? bay.data.locker : ws.cargo(bay.owner);
        for (String k : from.keySet()) ids.add(BayData.base(k));
        if (!"any".equals(m.filter)) ids.add(m.filter);
        List<String> out = new ArrayList<>();
        out.add("any");
        out.addAll(ids);
        return out;
    }

    private String frontLine(Bay bay, MachineData m) {
        MachineData t = ws.sim.frontTarget(bay, m);
        if (t == null) return "<red>Nothing connected in front";
        MachineDef td = cfg().machine(t.type);
        return "<gray>Feeds: <white>" + (td == null ? t.type : td.name());
    }

    private List<String> bufferLore(Map<String, Long> buf, List<String> extra) {
        List<String> l = new ArrayList<>(extra);
        if (!extra.isEmpty()) l.add("");
        if (buf.isEmpty()) l.add("<dark_gray>Empty");
        buf.entrySet().stream().limit(10).forEach(e ->
                l.add("<dark_gray>· " + ws.items.color(e.getKey()) + ws.items.name(e.getKey()) + " <white>x" + Numbers.shortFormat(e.getValue())));
        if (buf.size() > 10) l.add("<dark_gray>  and " + (buf.size() - 10) + " more");
        return l;
    }

    private List<String> roleLore(MachineDef def) {
        WorkshopConfig c = cfg();
        List<String> l = new ArrayList<>();
        switch (def.role()) {
            case PROCESSOR -> {
                List<Recipe> rs = c.recipesFor(def.id());
                l.add("<gray>Recipes (" + rs.size() + "):");
                int shown = 0;
                for (Recipe r : rs) {
                    if (shown++ >= 12) {
                        l.add("<dark_gray>  and " + (rs.size() - 12) + " more");
                        break;
                    }
                    StringBuilder sb = new StringBuilder("<dark_gray>· <gray>");
                    int i = 0;
                    for (Map.Entry<String, Integer> e : r.inputs().entrySet()) {
                        if (i++ > 0) sb.append(" + ");
                        sb.append(e.getValue()).append(" ").append(ws.items.name(e.getKey()));
                    }
                    sb.append(" <dark_gray>-></dark_gray> <white>").append(WorkshopItems.fmt(r.outputAmount())).append(" ").append(ws.items.name(r.output()));
                    l.add(sb.toString());
                }
                WorkshopConfig.Byproduct bp = c.byproducts.get(def.id());
                if (bp != null) l.add("<dark_gray>Side product: " + ws.items.name(bp.id()) + " (" + WorkshopItems.pct(bp.chance()) + ", to the locker)");
            }
            case GENERATOR -> {
                l.add("<gray>Fuels (seconds per unit):");
                c.fuels.forEach((id, s) -> l.add("<dark_gray>· <gray>" + ws.items.name(id) + " <white>" + WorkshopItems.fmt(s) + "s"));
                l.add("<dark_gray>Burns only while machines need power.");
            }
            case SOURCE -> {
                l.add("<gray>Pulls items into the machine in front.");
                l.add("<gray>Set the filter to feed one ore, or");
                l.add("<gray>\"any\" to send whatever the next machine takes.");
            }
            case PUMP -> l.add("<gray>Shares coolant with every washer and forge.");
            case RADIATOR -> l.add("<gray>Cools every machine touching it.");
            case DEPOT -> l.add("<gray>Stores what it receives in the Bay Locker.");
            case CHUTE -> l.add("<gray>Sells goods at " + WorkshopItems.pct(c.chuteRate) + " of the Trade Terminal price.");
        }
        return l;
    }

    private static String heatBar(double ratio) {
        int cells = 20;
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * cells);
        String col = ratio >= 0.9 ? "<red>" : ratio >= 0.6 ? "<gold>" : "<yellow>";
        return col + "|".repeat(filled) + "<dark_gray>" + "|".repeat(cells - filled);
    }

    static String arrow(String facing) {
        return switch (facing) {
            case "EAST" -> "→";
            case "SOUTH" -> "↓";
            case "WEST" -> "←";
            default -> "↑";
        };
    }

    // =============================================================================================
    // Machine overview
    // =============================================================================================

    void machineList(Player p, int page) {
        Bay bay = ws.bay(p.getUniqueId());
        if (bay == null) return;
        Gui g = new Gui(6, "<dark_gray>Machine Overview");
        g.live = true;
        g.renderer(gui -> {
            List<MachineData> ms = bay.data.machines;
            int pages = Math.max(1, (ms.size() + 44) / 45);
            int pg = Math.min(page, pages - 1);
            for (int i = 0; i < 45; i++) {
                int idx = pg * 45 + i;
                if (idx >= ms.size()) break;
                MachineData m = ms.get(idx);
                MachineDef def = cfg().machine(m.type);
                if (def == null) continue;
                List<String> l = new ArrayList<>();
                l.add("<gray>Status: " + (m.status == null || m.status.isEmpty() ? "<gray>Starting up" : m.status));
                l.add(heatBar(m.heat / cfg().heatMax) + " <gray>" + Math.round(m.heat));
                l.add("<dark_gray>At " + m.x + ", " + m.y + ", " + m.z + " · output " + arrow(m.facing));
                l.add("");
                l.add("<yellow>Click to open");
                gui.set(i, Gui.withLore(ws.items.machine(def, m.tier, 1), "<#c8743a>" + nameOf(m), l), e -> { Gui.click(p); machine(p, bay, m); });
            }
            pager(p, gui, pg, pages, np -> machineList(p, np));
            gui.set(49, Gui.icon(Material.ARROW, "ui/back", "<gray>Terminal", List.of()), e -> { Gui.click(p); main(p); });
        }).open(p);
    }

    // =============================================================================================
    // Bay Locker
    // =============================================================================================

    void locker(Player p, int page) {
        Bay bay = ws.bay(p.getUniqueId());
        if (bay == null) {
            Text.send(p, "<red>You don't have a bay yet. /ws");
            return;
        }
        Gui g = new Gui(6, "<dark_gray>Bay Locker");
        g.live = true;
        g.bottomClick = e -> {
            ItemStack it = e.getCurrentItem();
            if (it != null && ws.items.goodKey(it) != null && ws.deposit(p, it)) g.render();
        };
        g.renderer(gui -> {
            List<Map.Entry<String, Long>> entries = new ArrayList<>(bay.data.locker.entrySet());
            entries.sort(Comparator.comparingInt((Map.Entry<String, Long> e) -> stageOrder(e.getKey())).thenComparing(Map.Entry::getKey));
            int pages = Math.max(1, (entries.size() + 44) / 45);
            int pg = Math.min(page, pages - 1);
            double mult = ws.sellMultiplier(p.getUniqueId());
            for (int i = 0; i < 45; i++) {
                int idx = pg * 45 + i;
                if (idx >= entries.size()) break;
                String key = entries.get(idx).getKey();
                long n = entries.get(idx).getValue();
                List<String> l = new ArrayList<>();
                l.add("<gray>Stored: <white>" + Numbers.shortFormat(n));
                boolean good = ws.market.buys(key);
                if (good) {
                    long unit = Math.round(ws.market.unitPrice(key) * mult);
                    l.add("<gray>Sells for <gold>" + Numbers.shortFormat(unit) + "</gold> each today (" + eco().format(Currency.CREDITS, Math.round(ws.market.unitPrice(key) * mult * n)) + " all)");
                    l.add("");
                    l.add("<yellow>Left-click: take a stack");
                    l.add("<yellow>Shift-left: take one");
                    l.add("<yellow>Right-click: sell all of these");
                } else {
                    l.add("<dark_gray>Raw ore. Feed it to machines with a Cargo Intake set to the locker.");
                }
                gui.set(i, stacked(iconFor(key, l), n), e -> {
                    if (!good) return;
                    if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                        long paid = ws.sell(p, key, Long.MAX_VALUE);
                        if (paid > 0) {
                            Gui.click(p);
                            Text.send(p, "<gray>Sold for " + eco().format(Currency.CREDITS, paid) + ".");
                        }
                    } else {
                        ws.withdraw(p, key, e.getClick() == ClickType.SHIFT_LEFT ? 1 : 64);
                    }
                    gui.render();
                });
            }
            pager(p, gui, pg, pages, np -> locker(p, np));
            long cap = ws.lockerCapacityOf(bay.plot);
            gui.set(48, Gui.icon(Material.BOOK, "ui/info", "<white>Locker", List.of(
                    "<gray>Used: <white>" + Numbers.shortFormat(bay.data.lockerUsed()) + "</white> / " + Numbers.shortFormat(cap),
                    "", "<gray>Click goods in your inventory to store them.")));
            gui.set(50, Gui.icon(Material.GOLD_INGOT, "ui/sell", "<gold>Sell All Goods", List.of(
                    "<gray>Sells every workshop good in the locker", "<gray>at today's prices.", "", "<yellow>Shift-click to sell")), e -> {
                if (!e.getClick().isShiftClick()) { Gui.deny(p); return; }
                long paid = ws.sellAll(p);
                Text.send(p, paid > 0 ? "<gray>Sold everything for " + eco().format(Currency.CREDITS, paid) + "." : "<gray>Nothing to sell.");
                gui.render();
            });
            gui.set(45, Gui.icon(Material.ARROW, "ui/back", "<gray>Terminal", List.of()), e -> { Gui.click(p); main(p); });
        }).open(p);
    }

    private int stageOrder(String key) {
        Good g = cfg().goods.get(BayData.base(key));
        return g == null ? -1 : g.stage().ordinal();
    }

    /**
     * Shows a stored amount as the icon's stack number, like a normal item. Vanilla draws at most 99 (the
     * max_stack_size limit), so bigger amounts show 99 with the compact total ("x1.2K") after the name.
     */
    private static ItemStack stacked(ItemStack icon, long n) {
        int shown = (int) Math.max(1, Math.min(99, n));
        icon.editMeta(meta -> {
            meta.setMaxStackSize(99);
            if (n > 99 && meta.hasDisplayName()) {
                meta.displayName(meta.displayName().append(Text.item(" <gray>x" + Numbers.shortFormat(n))));
            }
        });
        icon.setAmount(shown);
        return icon;
    }

    private ItemStack iconFor(String key, List<String> lore) {
        ItemStack good = ws.items.good(key, 1);
        if (good != null) return Gui.withLore(good, null, lore);
        Material m = Material.matchMaterial(BayData.base(key));
        if (m == null || !m.isItem()) m = Material.COBBLESTONE;
        return Gui.icon(m, null, "<#9c8a6e>" + ws.items.name(key), lore);
    }

    // =============================================================================================
    // Trade Terminal
    // =============================================================================================

    void market(Player p, int page) {
        Bay bay = ws.bay(p.getUniqueId());
        if (bay == null) {
            Text.send(p, "<red>You don't have a bay yet. /ws");
            return;
        }
        Gui g = new Gui(6, "<dark_gray>Trade Terminal · " + ws.market.today());
        g.renderer(gui -> {
            List<Good> goods = new ArrayList<>(cfg().goods.values());
            List<String> prio = ws.market.priorities();
            goods.sort(Comparator.comparing((Good x) -> !prio.contains(x.id())).thenComparing(x -> x.stage().ordinal()).thenComparing(Good::price));
            int pages = Math.max(1, (goods.size() + 44) / 45);
            int pg = Math.min(page, pages - 1);
            double mult = ws.sellMultiplier(p.getUniqueId());
            for (int i = 0; i < 45; i++) {
                int idx = pg * 45 + i;
                if (idx >= goods.size()) break;
                Good good = goods.get(idx);
                double demand = ws.market.demand(good.id());
                double sat = ws.market.saturation(good.id());
                long have = BayData.countBase(bay.data.locker, good.id());
                List<String> l = new ArrayList<>();
                if (prio.contains(good.id())) l.add("<gold>★ Priority order: x" + WorkshopItems.fmt(cfg().priorityBonus) + " today");
                l.add("<dark_gray>" + WorkshopItems.stageName(good.stage()));
                l.add("<gray>Base value: <white>" + Numbers.shortFormat(Math.round(good.price())));
                l.add("<gray>Demand today: " + (demand >= 1 ? "<green>" : "<red>") + "x" + String.format(java.util.Locale.ROOT, "%.2f", demand));
                if (sat < 0.999) l.add("<gray>Market flooded: <red>" + WorkshopItems.pct(sat) + "</red> of price");
                l.add("<gray>You get: <gold>" + Numbers.shortFormat(Math.round(ws.market.unitPrice(good.id()) * mult)) + "</gold> each <dark_gray>(x" + String.format(java.util.Locale.ROOT, "%.2f", mult) + " sell bonus)");
                l.add("<gray>In your locker: <white>" + Numbers.shortFormat(have));
                if (have > 0) {
                    l.add("");
                    l.add("<yellow>Click to sell all of these");
                }
                ItemStack icon = ws.items.good(good.id(), 1);
                if (icon == null) continue;
                gui.set(i, Gui.withLore(icon, null, l), e -> {
                    if (have <= 0) return;
                    long paid = 0;
                    for (String k : new ArrayList<>(bay.data.locker.keySet())) {
                        if (BayData.base(k).equals(good.id())) paid += ws.sell(p, k, Long.MAX_VALUE);
                    }
                    if (paid > 0) {
                        Gui.click(p);
                        Text.send(p, "<gray>Sold " + Numbers.shortFormat(have) + "x " + good.name() + " for " + eco().format(Currency.CREDITS, paid) + ".");
                    }
                    gui.render();
                });
            }
            pager(p, gui, pg, pages, np -> market(p, np));
            gui.set(49, Gui.icon(Material.CLOCK, "ui/info", "<white>How prices work", List.of(
                    "<gray>Demand for every good changes daily.",
                    "<gray>Priority orders pay x" + WorkshopItems.fmt(cfg().priorityBonus) + " on top.",
                    "<gray>The more the station sells of one good",
                    "<gray>today, the lower its price, for everyone.",
                    "<gray>Your grade and prestige sell bonus applies.",
                    "<gray>Refined goods pay x" + WorkshopItems.fmt(cfg().quality(1).price()) + ", Pristine x" + WorkshopItems.fmt(cfg().quality(Math.min(2, cfg().qualities.size() - 1)).price()) + ".")));
            gui.set(45, Gui.icon(Material.ARROW, "ui/back", "<gray>Terminal", List.of()), e -> { Gui.click(p); main(p); });
        }).open(p);
    }

    // =============================================================================================
    // Machine Shop + Decor
    // =============================================================================================

    void shop(Player p) {
        Gui g = new Gui(5, "<dark_gray>Machine Shop");
        g.renderer(gui -> {
            int grade = ws.grade(p.getUniqueId());
            int slot = 10;
            for (MachineDef def : cfg().machines.values()) {
                if (slot == 17) slot = 19;
                if (slot == 26) slot = 28;
                if (slot >= 35) break;
                long price = ws.machinePrice(p.getUniqueId(), def);
                List<String> l = new ArrayList<>(ws.items.machineLore(def, 0, false));
                l.add("");
                boolean locked = grade < def.grade();
                if (locked) l.add("<red>Needs Grade " + def.grade());
                l.add("<gray>Price: " + eco().format(Currency.CREDITS, price));
                if (!locked) {
                    l.add("");
                    l.add("<yellow>Click to buy");
                }
                ItemStack icon = locked ? Gui.icon(Material.IRON_BARS, "ui/locked", "<dark_gray>" + def.name(), l)
                        : Gui.withLore(ws.items.machine(def, 0, 1), "<#c8743a>" + def.name(), l);
                gui.set(slot++, icon, e -> { ws.buyMachine(p, def); gui.render(); });
            }
            WorkshopConfig c = cfg();
            gui.set(38, Gui.withLore(ws.items.conveyor(c.conveyorBundle), "<#c8743a>Conveyors x" + c.conveyorBundle, List.of(
                    "<gray>Link machines that aren't side by side.",
                    "<gray>Price: " + eco().format(Currency.CREDITS, c.conveyorPrice), "", "<yellow>Click to buy")),
                    e -> ws.buyConveyors(p));
            gui.set(42, Gui.icon(Material.WAXED_WEATHERED_CUT_COPPER, "workshop/ui/decor", "<#9c8a6e>Decor Locker", List.of(
                    "<gray>Building blocks to dress up your bay.", "", "<yellow>Click to open")), e -> { Gui.click(p); decor(p); });
            gui.set(40, Gui.icon(Material.ARROW, "ui/back", "<gray>Terminal", List.of()), e -> { Gui.click(p); main(p); });
        }).open(p);
    }

    void decor(Player p) {
        Gui g = new Gui(5, "<dark_gray>Decor Locker");
        g.renderer(gui -> {
            List<Decor> ds = cfg().decor;
            for (int i = 0; i < Math.min(36, ds.size()); i++) {
                Decor d = ds.get(i);
                gui.set(i, Gui.withLore(new ItemStack(d.material(), d.amount()), null, List.of(
                        "<gray>" + d.amount() + " for " + eco().format(Currency.CREDITS, d.price()), "", "<yellow>Click to buy")),
                        e -> ws.buyDecor(p, d));
            }
            gui.set(40, Gui.icon(Material.ARROW, "ui/back", "<gray>Machine Shop", List.of()), e -> { Gui.click(p); shop(p); });
        }).open(p);
    }

    // =============================================================================================
    // Assembly Bench
    // =============================================================================================

    void assembly(Player p, int page) {
        Bay bay = ws.bay(p.getUniqueId());
        if (bay == null) return;
        DrillService drill = Services.find(DrillService.class).orElse(null);
        ScrapService scrap = Services.find(ScrapService.class).orElse(null);
        List<Assembly> all = new ArrayList<>(cfg().assembly.values());
        int perPage = 28;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(pages - 1, page));
        Gui g = new Gui(6, "<dark_gray>Assembly Bench");
        g.renderer(gui -> {
            int grade = ws.grade(p.getUniqueId());
            gui.set(4, Gui.icon(Material.CRAFTER, "workshop/ui/assembly", "<#6fd6a8>Assembly Bench", List.of(
                    "<gray>Build drill, rod and weapon modules you know the", "<gray>schematic for. Components come from",
                    "<gray>your Bay Locker, scrap parts from your", "<gray>inventory (working or pristine).", "",
                    "<gray>Learn schematics by decoding blueprint", "<gray>fragments in <white>/salvage</white><gray>.")));
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
            int i = 0;
            for (Assembly a : all.subList(pg * perPage, Math.min(all.size(), (pg + 1) * perPage))) {
                if (i >= slots.length) break;
                ItemStack module = drill == null ? null : drill.createModuleItem(a.id());
                if (module == null) continue;
                boolean known = drill.knowsSchematic(p.getUniqueId(), a.id());
                List<String> l = new ArrayList<>();
                if (!known) {
                    String rarity = drill.moduleRarity(a.id());
                    l.add("<red>Schematic not known.");
                    l.add("<gray>Decode " + (rarity == null ? "" : rarity.charAt(0) + rarity.substring(1).toLowerCase(java.util.Locale.ROOT) + " ")
                            + "blueprint fragments in <white>/salvage</white><gray>.");
                    gui.set(slots[i++], Gui.icon(Material.MAP, "ui/locked", "<dark_gray>??? " + (rarity == null ? "" : rarity.toLowerCase(java.util.Locale.ROOT) + " ") + "schematic", l));
                    continue;
                }
                var meta = module.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                l.add("");
                l.add("<#6fd6a8>Build cost:");
                for (Map.Entry<String, Long> e : a.components().entrySet()) {
                    long have = BayData.countBase(bay.data.locker, e.getKey());
                    l.add((have >= e.getValue() ? "<green>✔ " : "<red>✘ ") + "<white>" + e.getValue() + "x</white> <gray>" + ws.items.name(e.getKey()) + " <dark_gray>(locker " + Numbers.shortFormat(have) + ")");
                }
                for (Map.Entry<String, Integer> e : a.parts().entrySet()) {
                    int have = scrap == null ? 0 : scrap.count(p, e.getKey(), ScrapService.State.WORKING);
                    String name = scrap == null ? e.getKey() : scrap.partName(e.getKey(), ScrapService.State.WORKING);
                    l.add((have >= e.getValue() ? "<green>✔ " : "<red>✘ ") + "<white>" + e.getValue() + "x</white> " + name + " <dark_gray>(" + have + ")");
                }
                if (a.credits() > 0) {
                    boolean ok = eco().balance(p.getUniqueId(), Currency.CREDITS) >= a.credits();
                    l.add((ok ? "<green>✔ " : "<red>✘ ") + "<gray>" + eco().format(Currency.CREDITS, a.credits()));
                }
                l.add((grade >= a.grade() ? "<green>✔ " : "<red>✘ ") + "<gray>Grade " + a.grade());
                l.add("");
                l.add("<yellow>Click to assemble");
                for (String line : l) lore.add(Text.item(line));
                meta.lore(lore);
                module.setItemMeta(meta);
                gui.set(slots[i++], module, e -> { ws.assemble(p, a); gui.render(); });
            }
            pager(p, gui, pg, pages, n -> assembly(p, n));
            gui.set(49, Gui.icon(Material.ARROW, "ui/back", "<gray>Terminal", List.of()), e -> { Gui.click(p); main(p); });
        }).open(p);
    }

    // =============================================================================================
    // Refurbisher
    // =============================================================================================

    void refurbisher(Player p) {
        Bay bay = ws.bay(p.getUniqueId());
        if (bay == null) return;
        ScrapService scrap = Services.find(ScrapService.class).orElse(null);
        Gui g = new Gui(6, "<dark_gray>Refurbisher");
        g.renderer(gui -> {
            WorkshopConfig c = cfg();
            WorkshopConfig.RefurbLevel lv = ws.refurbLevel(bay.data);
            List<String> info = new ArrayList<>();
            info.add("<gray>Repairs fried scrap parts into working");
            info.add("<gray>ones, for Credits and goods from your");
            info.add("<gray>Bay Locker. Some come out <gold>pristine</gold><gray>.");
            info.add("");
            info.add("<gray>Mark <white>" + lv.name() + "</white> · pristine chance <gold>" + Math.round(lv.pristine() * 100) + "%");
            info.add("<gray>Parts repaired: <white>" + Numbers.shortFormat(bay.data.refurbished));
            if (ws.grade(p.getUniqueId()) < c.refurbGrade) info.add("<red>Needs Grade " + c.refurbGrade);
            gui.set(4, Gui.icon(Material.GRINDSTONE, "workshop/ui/refurbisher", "<#c8a070>Refurbisher", info));

            // Upgrade
            List<String> up = new ArrayList<>();
            if (bay.data.refurbLevel + 1 >= c.refurbLevels.size()) {
                up.add("<gold>Top mark.");
            } else {
                WorkshopConfig.RefurbLevel next = c.refurbLevels.get(bay.data.refurbLevel + 1);
                up.add("<gray>Next: <white>" + next.name() + "</white> · pristine <gold>" + Math.round(next.pristine() * 100) + "%");
                up.add("");
                for (Map.Entry<String, Long> e : next.components().entrySet()) {
                    long have = BayData.countBase(bay.data.locker, e.getKey());
                    up.add((have >= e.getValue() ? "<green>✔ " : "<red>✘ ") + "<white>" + e.getValue() + "x</white> <gray>" + ws.items.name(e.getKey()));
                }
                if (next.credits() > 0) up.add("<gray>" + eco().format(Currency.CREDITS, next.credits()));
                up.add("<gray>Grade " + next.grade());
                up.add("");
                up.add("<yellow>Click to upgrade");
            }
            gui.set(8, Gui.icon(Material.ANVIL, "ui/upgrade", "<aqua>Upgrade Refurbisher", up), e -> { ws.upgradeRefurbisher(p); gui.render(); });

            if (scrap == null) {
                gui.set(22, Gui.icon(Material.BARRIER, null, "<red>Scrap parts are offline", List.of()));
            } else {
                Map<String, Integer> fried = new LinkedHashMap<>();
                for (ItemStack it : p.getInventory().getStorageContents()) {
                    scrap.part(it).ifPresent(part -> {
                        if (part.state() == ScrapService.State.FRIED) fried.merge(part.partId(), it.getAmount(), Integer::sum);
                    });
                }
                int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
                int i = 0;
                for (Map.Entry<String, Integer> e : fried.entrySet()) {
                    if (i >= slots.length) break;
                    String partId = e.getKey();
                    ItemStack icon = scrap.create(partId, ScrapService.State.FRIED, Math.min(64, e.getValue()));
                    if (icon == null) continue;
                    String tier = scrap.part(icon).map(ScrapService.Part::tier).orElse("common");
                    WorkshopConfig.RefurbCost cost = c.refurbCosts.getOrDefault(tier, new WorkshopConfig.RefurbCost(0, Map.of()));
                    List<String> l = new ArrayList<>();
                    l.add("<gray>In your inventory: <white>" + e.getValue());
                    l.add("");
                    l.add("<gray>Repair cost, each:");
                    for (Map.Entry<String, Long> x : cost.components().entrySet()) {
                        long have = BayData.countBase(bay.data.locker, x.getKey());
                        l.add((have >= x.getValue() ? "<green>✔ " : "<red>✘ ") + "<white>" + x.getValue() + "x</white> <gray>" + ws.items.name(x.getKey()) + " <dark_gray>(locker " + Numbers.shortFormat(have) + ")");
                    }
                    if (cost.credits() > 0) l.add("<gray>" + eco().format(Currency.CREDITS, cost.credits()));
                    l.add("");
                    l.add("<yellow>Click</yellow> <gray>to repair one");
                    l.add("<yellow>Shift-click</yellow> <gray>to repair them all");
                    var meta = icon.getItemMeta();
                    meta.lore(Text.lore(l));
                    icon.setItemMeta(meta);
                    gui.set(slots[i++], icon, ev -> { ws.refurbish(p, partId, ev.getClick().isShiftClick()); gui.render(); });
                }
                if (fried.isEmpty()) {
                    gui.set(31, Gui.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "ui/socket_empty", "<gray>No fried parts in your inventory", List.of(
                            "<gray>Scrap parts drop from mining, fishing,", "<gray>parkour, your workshop, the Undercroft",
                            "<gray>and contracts. Most come out fried.")));
                }
            }
            gui.set(49, Gui.icon(Material.ARROW, "ui/back", "<gray>Terminal", List.of()), e -> { Gui.click(p); main(p); });
        }).open(p);
    }

    // =============================================================================================

    private void pager(Player p, Gui gui, int page, int pages, java.util.function.IntConsumer open) {
        if (page > 0) gui.set(46, Gui.icon(Material.ARROW, "ui/previous", "<gray>Previous page", List.of()), e -> { Gui.click(p); open.accept(page - 1); });
        if (page < pages - 1) gui.set(52, Gui.icon(Material.ARROW, "ui/next", "<gray>Next page", List.of("<dark_gray>" + (page + 1) + " / " + pages)), e -> { Gui.click(p); open.accept(page + 1); });
    }

    @SuppressWarnings("unused")
    private static Map<String, Long> copy(Map<String, Long> m) { return new LinkedHashMap<>(m); }

    @SuppressWarnings("unused")
    private static boolean refinedOrBetter(Good g) { return g.stage().ordinal() >= Stage.REFINED.ordinal(); }
}
