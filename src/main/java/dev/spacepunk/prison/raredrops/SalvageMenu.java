package dev.spacepunk.prison.raredrops;

import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.raredrops.RareConfig.Drop;
import dev.spacepunk.prison.raredrops.RareConfig.RelicSet;
import dev.spacepunk.prison.raredrops.RareConfig.Tier;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /salvage: collection log, blueprint assembly bay, overflow stash and drop odds. */
final class SalvageMenu implements Listener {
    private static final String[] RARITIES = {"COMMON", "RARE", "EPIC", "LEGENDARY"};
    private static final int[] ASSEMBLY_SLOTS = {10, 12, 14, 16};

    enum View { MAIN, COLLECTIONS, ASSEMBLY }

    static final class Holder implements InventoryHolder {
        final View view;
        Inventory inventory;
        Holder(View view) { this.view = view; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private final RareDropsManager rare;

    SalvageMenu(RareDropsManager rare) { this.rare = rare; }

    void open(Player p, View view) {
        if (rare.data(p.getUniqueId()) == null) {
            Text.send(p, "<red>Your profile is still loading.");
            return;
        }
        Holder h = new Holder(view);
        int size = view == View.COLLECTIONS ? 54 : 27;
        String title = switch (view) {
            case MAIN -> "<dark_gray>Salvage Bay";
            case COLLECTIONS -> "<dark_gray>Salvage Bay · Collections";
            case ASSEMBLY -> "<dark_gray>Salvage Bay · Assembly";
        };
        h.inventory = Bukkit.createInventory(h, size, Text.mm(title));
        render(p, h);
        p.openInventory(h.inventory);
    }

    private void render(Player p, Holder h) {
        Inventory inv = h.inventory;
        ItemStack filler = rare.items().icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", "<dark_gray> ", List.of());
        ItemMeta fm = filler.getItemMeta();
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        RareData d = rare.data(p.getUniqueId());
        RareConfig cfg = rare.cfg();
        switch (h.view) {
            case MAIN -> {
                int done = 0, total = 0;
                for (RelicSet s : cfg.sets.values()) {
                    total += s.relics().size();
                    for (String r : s.relics()) if (d.catalogued.contains(r)) done++;
                }
                inv.setItem(4, rare.items().icon(Material.SPYGLASS, "ui/info", "<#c8743a>Salvage Record", List.of(
                        "<gray>Rare finds: <white>" + RareDropsManager.fmt(d.totalFinds),
                        "<gray>Caches cracked: <white>" + RareDropsManager.fmt(d.cachesOpened),
                        "<gray>Modules assembled: <white>" + RareDropsManager.fmt(d.modulesAssembled),
                        "<gray>Collection sell bonus: <green>+" + RareDropsManager.pct(rare.sellMultiplier(p.getUniqueId()) - 1))));
                inv.setItem(10, rare.items().icon(Material.BOOK, "ui/rank", "<#c8743a>Collections", List.of(
                        "<gray>Relics catalogued: <white>" + done + "</white> / " + total,
                        "<gray>Finish a deck's set for a", "<gray>permanent sell bonus.", "", "<#c8743a>Click to open")));
                List<String> asm = new ArrayList<>();
                asm.add("<gray>Turn blueprint fragments into");
                asm.add("<gray>random drill modules.");
                asm.add("");
                for (String r : RARITIES) {
                    Drop f = cfg.fragmentFor(r);
                    if (f != null) asm.add(f.tier().colored(RareItems.titleCase(r)) + " <gray>" + rare.count(p, f) + " / " + cfg.fragmentsNeeded(r));
                }
                asm.add("");
                asm.add("<#c8743a>Click to open");
                inv.setItem(12, rare.items().icon(Material.SMITHING_TABLE, "ui/upgrade", "<#c8743a>Assembly Bay", asm));
                long stashed = d.stashed();
                inv.setItem(14, rare.items().icon(Material.CHEST, "ui/cargo", "<#c8743a>Overflow Stash", List.of(
                        "<gray>Drops that didn't fit in your", "<gray>inventory wait here.", "",
                        stashed > 0 ? "<white>" + stashed + "</white> <gray>waiting. <#c8743a>Click to collect" : "<dark_gray>Empty")));
                inv.setItem(16, oddsIcon(p));
                inv.setItem(22, rare.items().icon(Material.BARRIER, "ui/close", "<red>Close", List.of()));
            }
            case COLLECTIONS -> {
                int row = 0;
                for (RelicSet s : cfg.sets.values()) {
                    if (row >= 6) break;
                    int have = (int) s.relics().stream().filter(d.catalogued::contains).count();
                    boolean complete = d.setsClaimed.contains(s.id());
                    List<String> lore = new ArrayList<>();
                    lore.add("<gray>Catalogued: <white>" + have + "</white> / " + s.relics().size());
                    lore.add("");
                    lore.add("<gray>Reward:");
                    if (s.sellBonus() > 0) lore.add("<dark_gray>· <green>+" + RareDropsManager.pct(s.sellBonus()) + " sell price forever");
                    if (s.tokens() > 0) lore.add("<dark_gray>· <aqua>" + RareDropsManager.fmt(s.tokens()) + " Tokens");
                    lore.add("");
                    lore.add(complete ? "<green>✔ Complete" : "<dark_gray>Incomplete");
                    ItemStack icon = rare.items().icon(complete ? Material.ENCHANTED_BOOK : Material.BOOK, "ui/rank",
                            s.coloredName(), lore);
                    inv.setItem(row * 9, icon);
                    int col = 1;
                    for (String rid : s.relics()) {
                        if (col > 7) break;
                        Drop r = cfg.drop(rid);
                        inv.setItem(row * 9 + col++, relicIcon(r, d.catalogued.contains(rid), d.found.getOrDefault(rid, 0L)));
                    }
                    row++;
                }
                inv.setItem(45, rare.items().icon(Material.ARROW, "ui/back", "<gray>Back", List.of()));
                inv.setItem(53, rare.items().icon(Material.WRITABLE_BOOK, "ui/add", "<#c8743a>Catalog All", List.of(
                        "<gray>Catalogs every new relic", "<gray>in your inventory.")));
            }
            case ASSEMBLY -> {
                boolean drill = Services.find(DrillService.class).isPresent();
                for (int i = 0; i < RARITIES.length; i++) {
                    String r = RARITIES[i];
                    Drop f = cfg.fragmentFor(r);
                    if (f == null) continue;
                    int have = rare.count(p, f), need = cfg.fragmentsNeeded(r);
                    int pool = drill ? Services.get(DrillService.class).moduleWeights(r).size() : 0;
                    ItemStack icon = rare.items().create(f, Math.max(1, Math.min(64, have)));
                    ItemMeta m = icon.getItemMeta();
                    List<String> lore = new ArrayList<>();
                    lore.add("<gray>Fragments: " + (have >= need ? "<green>" : "<red>") + have + "</green></red><gray> / " + need);
                    lore.add("<gray>Possible modules: <white>" + pool);
                    lore.add("");
                    lore.add(pool == 0 ? "<dark_gray>No " + RareItems.titleCase(r) + " modules exist yet"
                            : have >= need ? "<#c8743a>Click to assemble" : "<dark_gray>Find more while mining");
                    m.lore(Text.lore(lore));
                    m.displayName(Text.item(f.tier().colored(RareItems.titleCase(r) + " Module Blueprint")));
                    icon.setItemMeta(m);
                    inv.setItem(ASSEMBLY_SLOTS[i], icon);
                }
                inv.setItem(18, rare.items().icon(Material.ARROW, "ui/back", "<gray>Back", List.of()));
            }
        }
    }

    private ItemStack relicIcon(Drop r, boolean catalogued, long found) {
        if (r == null) return null;
        if (catalogued) {
            ItemStack it = rare.items().create(r, 1);
            ItemMeta m = it.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(r.tier().label() + " <gray>relic");
            if (!r.lore().isEmpty()) lore.add("<#8a8070><i>" + r.lore());
            lore.add("");
            lore.add("<green>✔ Catalogued");
            lore.add("<gray>Found <white>" + found + "</white> time" + (found == 1 ? "" : "s"));
            m.lore(Text.lore(lore));
            it.setItemMeta(m);
            return it;
        }
        String where = r.decks().isEmpty() ? "any deck" : "deck " + r.decks().stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("?");
        return rare.items().icon(Material.GRAY_DYE, "ui/locked", "<dark_gray>??? " + r.tier().label() + " <dark_gray>relic", List.of(
                "<gray>Found while mining on " + where + ".",
                found > 0 ? "<yellow>You've found one: right-click it to catalog." : "<dark_gray>Not found yet"));
    }

    private ItemStack oddsIcon(Player p) {
        RareConfig cfg = rare.cfg();
        double mul = Services.find(DrillService.class).map(s -> s.rareDropMultiplier(p.getUniqueId())).orElse(1.0);
        double total = cfg.tiers.values().stream().mapToDouble(t -> Math.max(0, t.weight())).sum();
        List<String> lore = new ArrayList<>();
        double per = cfg.baseChance * mul;
        lore.add("<gray>Chance per block: <white>1 in " + (per > 0 ? Math.round(1 / per) : "∞"));
        lore.add("<gray>Your drill's rare odds: <white>x" + String.format("%.2f", mul));
        lore.add("<dark_gray>(Salvage Sense and Scavenger Rig raise it)");
        lore.add("");
        for (Tier t : cfg.tiers.values()) {
            double share = total > 0 ? t.weight() / total : 0;
            lore.add(t.label() + " <gray>" + (share >= 0.01 ? String.format("%.0f%%", share * 100) : String.format("%.2f%%", share * 100)));
        }
        return rare.items().icon(Material.COMPASS, "ui/info", "<#c8743a>Drop Odds", lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        switch (h.view) {
            case MAIN -> {
                switch (slot) {
                    case 10 -> open(p, View.COLLECTIONS);
                    case 12 -> open(p, View.ASSEMBLY);
                    case 14 -> {
                        long n = rare.claimStash(p);
                        if (n > 0) Text.send(p, "<gray>Collected <white>" + n + "</white> stashed drops.");
                        else if (rare.data(p.getUniqueId()).stashed() > 0) Text.send(p, "<red>Your inventory is full.");
                        render(p, h);
                    }
                    case 22 -> p.closeInventory();
                    default -> {}
                }
            }
            case COLLECTIONS -> {
                if (slot == 45) open(p, View.MAIN);
                else if (slot == 53) {
                    int n = rare.catalogAll(p);
                    if (n == 0) Text.send(p, "<gray>No new relics in your inventory.");
                    render(p, h);
                }
            }
            case ASSEMBLY -> {
                if (slot == 18) { open(p, View.MAIN); return; }
                for (int i = 0; i < ASSEMBLY_SLOTS.length; i++) {
                    if (slot != ASSEMBLY_SLOTS[i]) continue;
                    switch (rare.assemble(p, RARITIES[i])) {
                        case NOT_ENOUGH -> Text.send(p, "<red>Not enough fragments.");
                        case NO_MODULES -> Text.send(p, "<red>No drill modules of that rarity exist yet.");
                        case NO_DRILL -> Text.send(p, "<red>The drill system is offline.");
                        case NOT_LOADED -> Text.send(p, "<red>Your profile is still loading.");
                        case OK -> {}
                    }
                    render(p, h);
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
