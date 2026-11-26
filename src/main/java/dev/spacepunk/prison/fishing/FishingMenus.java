package dev.spacepunk.prison.fishing;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.fishing.FishingConfig.Area;
import dev.spacepunk.prison.fishing.FishingConfig.BaitDef;
import dev.spacepunk.prison.fishing.FishingConfig.EnchantDef;
import dev.spacepunk.prison.fishing.FishingConfig.FishDef;
import dev.spacepunk.prison.fishing.FishingConfig.LureDef;
import dev.spacepunk.prison.fishing.FishingConfig.Rarity;
import dev.spacepunk.prison.fishing.FishingConfig.RodTier;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** All fishing menus: hub, rod bay, bait shop, Fish Market, collection, leaderboards and the waters list. */
final class FishingMenus implements Listener {
    private static final int[] ENCHANT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 21, 23};
    private static final int[] LURE_SLOTS = {38, 39, 40};
    private static final int[] AREA_SLOTS = {20, 21, 22, 23, 24};
    private static final int[] FISH_SLOTS = {19, 20, 21, 22, 23, 24, 25, 31};

    private final FishingModule module;

    FishingMenus(FishingModule module) { this.module = module; }

    private FishingManager m() { return module.manager(); }

    private FishingItems items() { return module.manager().items(); }

    private FishingConfig cfg() { return module.cfg(); }

    // ------------------------------------------------------------------ open / render

    void open(Player p, String kind) { open(p, kind, 0); }

    void open(Player p, String kind, int page) {
        if (m().data(p.getUniqueId()) == null) {
            Text.send(p, "<red>Your fishing data is still loading.");
            return;
        }
        Menu menu = new Menu(p.getUniqueId(), kind);
        String title = switch (kind) {
            case "rod" -> "ROD BAY";
            case "bait" -> "BAIT SHOP";
            case "market" -> "FISH MARKET";
            case "collection" -> "COLLECTION";
            case "top" -> "LEADERBOARDS";
            case "waters" -> "STATION WATERS";
            default -> "FISHING";
        };
        menu.inventory = Bukkit.createInventory(menu, 54, Text.mm("<#2a3a40>" + title));
        menu.page = page;
        render(p, menu);
        p.openInventory(menu.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.6f, 1.2f);
    }

    private void render(Player p, Menu menu) {
        menu.inventory.clear();
        menu.actions.clear();
        menu.bottom = null;
        ItemStack filler = items().icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", " ", null);
        hideTooltip(filler);
        for (int i = 0; i < 54; i++) menu.inventory.setItem(i, filler);
        switch (menu.kind) {
            case "rod" -> rod(p, menu);
            case "bait" -> bait(p, menu);
            case "market" -> market(p, menu);
            case "collection" -> collection(p, menu);
            case "top" -> top(p, menu);
            case "waters" -> waters(p, menu);
            default -> main(p, menu);
        }
        if (!menu.kind.equals("main")) {
            menu.set(45, items().icon(Material.ARROW, "ui/back", "<gray>Back", null), e -> open(p, "main"));
        }
        menu.set(49, items().icon(Material.BARRIER, "ui/close", "<red>Close", null), e -> p.closeInventory());
    }

    private static void hideTooltip(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
    }

    private void rerender(Player p, Menu menu) {
        if (p.getOpenInventory().getTopInventory().getHolder() == menu) render(p, menu);
    }

    // ------------------------------------------------------------------ main

    private void main(Player p, Menu menu) {
        FishingData d = m().data(p.getUniqueId());
        menu.set(4, profileIcon(p, d), null);
        menu.set(20, items().icon(Material.FISHING_ROD, "rod/" + cfg().tier(d.rodTier).id(), "<#5fb4c8>Rod Bay",
                List.of("<gray>Enchants, lures, bait and rod tiers.", "", "<#c8743a>Click to open")), e -> open(p, "rod"));
        menu.set(21, items().icon(Material.WHEAT_SEEDS, "fishing/bait/" + firstBait(), "<#6fd35a>Bait Shop",
                List.of("<gray>Bait boosts your next catches.", "<gray>One is used per catch.", "", "<#c8743a>Click to open")),
                e -> open(p, "bait"));
        menu.set(22, items().icon(Material.EMERALD, "ui/sell", "<#ffd23f>Fish Market",
                List.of("<gray>Sell fish for Credits or", "<gray>gut them for Scales.", "", "<#c8743a>Click to open")),
                e -> open(p, "market"));
        menu.set(23, items().icon(Material.BOOK, "fishing/collection", "<#4fa8ff>Collection",
                List.of("<gray>Every species you've landed,", "<gray>your records and collection tiers.", "", "<#c8743a>Click to open")),
                e -> open(p, "collection"));
        menu.set(24, items().icon(Material.GOLD_INGOT, "fishing/leaderboard", "<#ffb43a>Leaderboards",
                List.of("<gray>This week's biggest catches", "<gray>and the top anglers.", "", "<#c8743a>Click to open")),
                e -> open(p, "top"));
        menu.set(31, items().icon(Material.HEART_OF_THE_SEA, "fishing/waters", "<#8fd3ff>Station Waters",
                List.of("<gray>Travel to a fishing pool.", "", "<#c8743a>Click to open")), e -> open(p, "waters"));
    }

    private String firstBait() { return cfg().baits.keySet().stream().findFirst().orElse("grub_paste"); }

    private ItemStack profileIcon(Player p, FishingData d) {
        List<String> lore = new ArrayList<>();
        long need = cfg().xpFor(d.level);
        lore.add("<gray>Fishing level <white>" + d.level + "</white><dark_gray>/" + cfg().maxLevel);
        if (d.level < cfg().maxLevel) lore.add(bar(d.xp / (double) need) + " <gray>" + Numbers.shortFormat(d.xp) + "/" + Numbers.shortFormat(need) + " XP");
        lore.add("<gray>Fish value bonus <white>" + Effects.pct(cfg().levelBonus * (d.level - 1)));
        lore.add("");
        lore.add("<gray>Scales " + m().formatScales(d.scales));
        lore.add("<gray>Catches <white>" + Numbers.shortFormat(d.catches));
        lore.add("<gray>Sea creatures slain <white>" + Numbers.shortFormat(d.creaturesKilled));
        lore.add("<gray>Credits earned fishing <gold>" + Numbers.shortFormat(d.creditsEarned));
        long species = d.species.values().stream().filter(s -> s.caught > 0).count();
        lore.add("<gray>Species found <white>" + species + "/" + cfg().fish.size());
        return items().icon(Material.COD, "fishing/scale", "<#5fb4c8>" + p.getName() + "'s fishing log", lore);
    }

    static String bar(double frac) {
        int n = 20, full = (int) Math.round(Math.max(0, Math.min(1, frac)) * n);
        return "<#5fb4c8>" + "|".repeat(full) + "<dark_gray>" + "|".repeat(n - full);
    }

    // ------------------------------------------------------------------ rod bay

    private void rod(Player p, Menu menu) {
        FishingData d = m().data(p.getUniqueId());
        RodTier t = cfg().tier(d.rodTier);
        menu.set(4, items().rod(p.getUniqueId(), d), null);
        int i = 0;
        for (EnchantDef e : cfg().enchants.values()) {
            if (i >= ENCHANT_SLOTS.length) break;
            menu.set(ENCHANT_SLOTS[i++], enchantIcon(d, t, e), click -> {
                int want = click.getClick() == ClickType.RIGHT ? 1000 : click.isShiftClick() ? 10 : 1;
                FishingManager.Buy r = m().buyEnchant(p, e, want);
                switch (r) {
                    case OK -> p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);
                    case POOR -> deny(p, "Not enough Scales. Gut fish at the Fish Market or fight sea creatures.");
                    case CAPPED -> deny(p, "Your " + t.name() + " caps enchants at " + t.maxEnchant() + ". Upgrade the rod tier.");
                    case MAX -> deny(p, e.name() + " is maxed.");
                }
                rerender(p, menu);
            });
        }
        // Lure slots
        for (int s = 0; s < LURE_SLOTS.length; s++) {
            int slot = s;
            if (s >= t.lureSlots()) {
                menu.set(LURE_SLOTS[s], items().icon(Material.GRAY_DYE, "ui/locked", "<dark_gray>Locked lure slot",
                        List.of("<gray>A better rod tier has more lure slots.")), null);
                continue;
            }
            LureDef l = cfg().lures.get(d.lure(s));
            if (l == null) {
                menu.set(LURE_SLOTS[s], items().icon(Material.LIGHT_GRAY_DYE, "ui/socket_empty", "<gray>Empty lure slot",
                        List.of("<gray>Click a lure in your inventory", "<gray>to slot it here.")), null);
            } else {
                List<String> lore = new ArrayList<>(items().lureLore(l, false));
                lore.add("");
                lore.add("<#c8743a>Click to take it out");
                ItemStack icon = items().lure(l);
                ItemMeta meta = icon.getItemMeta();
                meta.lore(Text.lore(lore));
                icon.setItemMeta(meta);
                menu.set(LURE_SLOTS[s], icon, click -> {
                    m().unsocketLure(p, slot);
                    p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.6f, 1.4f);
                    rerender(p, menu);
                });
            }
        }
        // Bait
        BaitDef active = m().activeBait(d);
        List<String> bl = new ArrayList<>();
        if (active == null) bl.add("<gray>No bait loaded.");
        else {
            bl.add("<gray>Loaded: <white>" + active.name() + " <dark_gray>x" + d.baitCount(active.id()));
            for (String s : active.effects().describe()) bl.add("<green>" + s);
        }
        bl.add("");
        for (BaitDef b : cfg().baits.values()) {
            int n = d.baitCount(b.id());
            if (n > 0) bl.add((active != null && active.id().equals(b.id()) ? "<#5fb4c8>▶ " : "<dark_gray>  ") + "<gray>" + b.name() + " <dark_gray>x" + n);
        }
        bl.add("");
        bl.add("<#c8743a>Click to switch bait · Bait Shop in /fish");
        menu.set(42, items().icon(Material.WHEAT_SEEDS, "fishing/bait/" + (active == null ? firstBait() : active.id()),
                "<#6fd35a>Bait", bl), click -> {
            List<String> owned = cfg().baits.keySet().stream().filter(id -> d.baitCount(id) > 0).toList();
            if (owned.isEmpty()) {
                open(p, "bait");
                return;
            }
            int idx = active == null ? -1 : owned.indexOf(active.id());
            m().selectBait(p, owned.get((idx + 1) % owned.size()));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            rerender(p, menu);
        });
        // Tier upgrade
        menu.set(31, tierIcon(d), click -> {
            switch (m().upgradeRod(p)) {
                case OK -> { }
                case MAX -> deny(p, "Your rod is already the best on the station.");
                case LEVEL -> deny(p, "You need fishing level " + cfg().tier(d.rodTier + 1).level() + " for the next rod.");
                case POOR -> deny(p, "Not enough Scales for the next rod.");
            }
            rerender(p, menu);
        });
        menu.set(46, items().icon(Material.PRISMARINE_SHARD, "fishing/scale", "<#9cf27a>Scales: " + Numbers.shortFormat(d.scales),
                List.of("<gray>Gut fish at the Fish Market, fight sea", "<gray>creatures and find treasure for more.")), null);
        menu.bottom = click -> {
            ItemStack it = click.getCurrentItem();
            if (!"lure".equals(items().kind(items().tag(it)))) return;
            if (m().socketLure(p, it)) {
                click.setCurrentItem(it.getAmount() > 0 ? it : null);
                p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 1.4f);
            } else deny(p, "No free lure slot. Take one out first.");
            rerender(p, menu);
        };
    }

    private ItemStack enchantIcon(FishingData d, RodTier t, EnchantDef e) {
        int lvl = d.enchant(e.id());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + e.description());
        lore.add("");
        lore.add("<gray>Level <white>" + lvl + "</white><dark_gray>/" + t.maxEnchant() + " (this rod)");
        Effects now = new Effects().add(e.effect(), e.perLevel() * lvl);
        Effects next = new Effects().add(e.effect(), e.perLevel() * (lvl + 1));
        List<String> nd = now.describe(), xd = next.describe();
        if (!nd.isEmpty()) lore.add("<gray>Now: <white>" + nd.get(0));
        if (lvl < t.maxEnchant()) {
            if (!xd.isEmpty()) lore.add("<gray>Next: <green>" + xd.get(0));
            lore.add("<gray>Cost: " + m().formatScales(e.cost(lvl)));
            lore.add("");
            lore.add("<#c8743a>Click +1 · Shift +10 · Right-click max");
        } else {
            lore.add("");
            lore.add("<dark_gray>Capped. A better rod tier raises the cap.");
        }
        Material mat = lvl > 0 ? Material.ENCHANTED_BOOK : Material.BOOK;
        return items().icon(mat, "ui/rod_enchant/" + e.id(), e.colored() + (lvl > 0 ? " <white>" + lvl : ""), lore);
    }

    private ItemStack tierIcon(FishingData d) {
        RodTier cur = cfg().tier(d.rodTier);
        if (d.rodTier >= cfg().tiers.size() - 1) {
            return items().icon(Material.NETHER_STAR, "ui/upgrade", cur.colored() + " <gray>(top tier)",
                    List.of("<gray>Enchant cap " + cur.maxEnchant() + " · " + cur.lureSlots() + " lure slots"));
        }
        RodTier next = cfg().tier(d.rodTier + 1);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Current: " + cur.colored());
        lore.add("<gray>Next: " + next.colored());
        if (!next.lore().isEmpty()) lore.add("<#8a8070><i>" + next.lore());
        lore.add("");
        lore.add("<gray>Enchant cap <white>" + cur.maxEnchant() + " → " + next.maxEnchant());
        lore.add("<gray>Lure slots <white>" + cur.lureSlots() + " → " + next.lureSlots());
        lore.add("");
        lore.add((d.level >= next.level() ? "<green>✔" : "<red>✘") + " <gray>Fishing level " + next.level());
        lore.add((d.scales >= next.scales() ? "<green>✔" : "<red>✘") + " <gray>" + m().formatScales(next.scales()));
        lore.add("");
        lore.add("<#c8743a>Click to upgrade");
        return items().icon(Material.ANVIL, "ui/upgrade", "<#ffd23f>Upgrade rod", lore);
    }

    private void deny(Player p, String msg) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        Text.send(p, "<red>" + msg);
    }

    // ------------------------------------------------------------------ bait shop

    private void bait(Player p, Menu menu) {
        FishingData d = m().data(p.getUniqueId());
        int i = 0;
        for (BaitDef b : cfg().baits.values()) {
            if (i >= AREA_SLOTS.length) break;
            long price = m().baitPrice(p.getUniqueId(), b);
            List<String> lore = new ArrayList<>();
            if (!b.lore().isEmpty()) lore.add("<#8a8070><i>" + b.lore());
            lore.add("");
            for (String s : b.effects().describe()) lore.add("<green>" + s);
            lore.add("");
            lore.add("<gray>You have <white>" + d.baitCount(b.id()) + (b.id().equals(d.activeBait) ? " <#5fb4c8>(loaded)" : ""));
            lore.add("<gray>" + b.amount() + " for " + m().economy().map(e -> e.format(Currency.CREDITS, price)).orElse(price + " Credits"));
            lore.add("");
            lore.add("<#c8743a>Click buy " + b.amount() + " · Shift buy " + (b.amount() * 4) + " · Right-click load");
            menu.set(AREA_SLOTS[i++], items().icon(Material.WHEAT_SEEDS, "fishing/bait/" + b.id(), "<#6fd35a>" + b.name(), lore), click -> {
                if (click.getClick() == ClickType.RIGHT) {
                    if (d.baitCount(b.id()) <= 0) deny(p, "You have no " + b.name() + ".");
                    else m().selectBait(p, b.id());
                } else if (m().buyBait(p, b, click.isShiftClick() ? 4 : 1)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.6f, 1.2f);
                } else {
                    deny(p, "Not enough Credits.");
                }
                rerender(p, menu);
            });
        }
        menu.set(4, items().icon(Material.PAPER, "ui/info", "<#6fd35a>Bait", List.of(
                "<gray>Loaded bait is used up one per catch,", "<gray>and its effect stacks with enchants", "<gray>and lures. Prices follow your grade.")), null);
    }

    // ------------------------------------------------------------------ market

    private void market(Player p, Menu menu) {
        FishingData d = m().data(p.getUniqueId());
        long value = 0, scales = 0, units = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (!items().isSellable(it) || "trophy".equals(items().kind(items().tag(it)))) continue;
            value += m().itemValue(p, it) * it.getAmount();
            scales += m().gutValue(it) * it.getAmount();
            units += it.getAmount();
        }
        for (Map.Entry<String, Long> e : d.cooler.entrySet()) {
            if (e.getKey().startsWith("trophy:") || e.getKey().startsWith("lure:")) continue;
            ItemStack sample = m().coolerItem(e.getKey(), p.getName());
            if (sample == null) continue;
            value += m().itemValue(p, sample) * e.getValue();
            scales += m().gutValue(sample) * e.getValue();
            units += e.getValue();
        }
        long fv = value, fs = scales;
        String money = m().economy().map(e -> e.format(Currency.CREDITS, fv)).orElse(fv + " Credits");
        menu.set(4, items().icon(Material.PAPER, "ui/info", "<#ffd23f>Fish Market", List.of(
                "<gray>Click a fish in your inventory to sell", "<gray>that stack, right-click it to gut it.",
                "<gray>Legendary trophies only sell that way.", "", "<gray>Fish money counts as income for", "<gray>your prestige payout.")), null);
        menu.set(20, items().icon(Material.EMERALD, "ui/sell", "<#ffd23f>Sell all fish and junk", List.of(
                "<gray>" + units + " items (inventory + cooler)", "<gray>Worth " + money, "", "<#c8743a>Click to sell")), click -> {
            FishingManager.Sale s = m().sellAll(p, false, false);
            if (s.units() == 0) deny(p, "Nothing to sell.");
            else {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                Text.send(p, "<gray>Sold " + s.units() + " items for " + m().economy().map(e -> e.format(Currency.CREDITS, s.credits())).orElse(s.credits() + " Credits"));
            }
            rerender(p, menu);
        });
        menu.set(22, items().icon(Material.SHEARS, "fishing/scale", "<#9cf27a>Gut all fish", List.of(
                "<gray>Turns fish into Scales for rod upgrades.", "<gray>Gives " + m().formatScales(fs), "<dark_gray>Junk and trophies are kept.",
                "", "<#c8743a>Click to gut")), click -> {
            FishingManager.Sale s = m().sellAll(p, true, false);
            if (s.units() == 0) deny(p, "No fish to gut.");
            else {
                p.playSound(p.getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.8f, 1.0f);
                Text.send(p, "<gray>Gutted " + s.units() + " fish for " + m().formatScales(s.scales()));
            }
            rerender(p, menu);
        });
        long cooler = m().coolerUnits(p.getUniqueId());
        menu.set(24, items().icon(Material.BARREL, "fishing/cooler", "<#8fd3ff>Cooler", List.of(
                "<gray>Catches that didn't fit in your", "<gray>inventory: <white>" + cooler + " items",
                "<dark_gray>Sell all and gut all include the cooler.", "", "<#c8743a>Click to move them to your inventory")), click -> {
            long moved = m().claimCooler(p);
            if (moved == 0) deny(p, cooler == 0 ? "Your cooler is empty." : "No room in your inventory.");
            else Text.send(p, "<gray>Moved " + moved + " items from your cooler.");
            rerender(p, menu);
        });
        menu.bottom = click -> {
            ItemStack it = click.getCurrentItem();
            if (!items().isSellable(it)) return;
            boolean gut = click.getClick() == ClickType.RIGHT;
            FishingManager.Sale s = m().sellStack(p, it, gut);
            if (s.units() == 0) {
                deny(p, gut ? "That can't be gutted." : "The market won't buy that.");
                return;
            }
            click.setCurrentItem(null);
            if (gut) Text.send(p, "<gray>Gutted for " + m().formatScales(s.scales()));
            else Text.send(p, "<gray>Sold for " + m().economy().map(e -> e.format(Currency.CREDITS, s.credits())).orElse(s.credits() + " Credits"));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            rerender(p, menu);
        };
    }

    // ------------------------------------------------------------------ collection

    private void collection(Player p, Menu menu) {
        FishingData d = m().data(p.getUniqueId());
        List<Area> areas = new ArrayList<>(cfg().areas.values());
        int page = Math.max(0, Math.min(areas.size() - 1, menu.page));
        for (int i = 0; i < areas.size() && i < 5; i++) {
            Area a = areas.get(i);
            int idx = i;
            List<FishDef> fish = cfg().fishIn(a.id());
            long found = fish.stream().filter(f -> d.species.containsKey(f.id()) && d.species.get(f.id()).caught > 0).count();
            ItemStack tab = items().icon(i == page ? Material.LIME_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    null, a.colored(), List.of("<gray>" + found + "/" + fish.size() + " species", i == page ? "<#5fb4c8>Showing" : "<#c8743a>Click to view"));
            menu.set(2 + i, tab, click -> {
                menu.page = idx;
                rerender(p, menu);
            });
        }
        if (areas.isEmpty()) return;
        Area a = areas.get(page);
        List<FishDef> fish = cfg().fishIn(a.id());
        for (int i = 0; i < fish.size() && i < FISH_SLOTS.length; i++) {
            FishDef f = fish.get(i);
            FishingData.Species s = d.species.get(f.id());
            Rarity r = cfg().rarity(f.rarity());
            if (s == null || s.caught == 0) {
                menu.set(FISH_SLOTS[i], items().icon(Material.GRAY_DYE, "ui/locked", "<dark_gray>???",
                        List.of(r.color() + r.name() + " fish", "<gray>Not caught yet.")), null);
                continue;
            }
            int tier = cfg().collectionTier(f, s.caught);
            long next = cfg().nextCollectionAt(f, s.caught);
            List<String> lore = new ArrayList<>();
            lore.add(r.color() + r.name() + " fish");
            if (!f.lore().isEmpty()) lore.add("<#8a8070><i>" + f.lore());
            lore.add("");
            lore.add("<gray>Caught <white>" + Numbers.shortFormat(s.caught));
            lore.add("<gray>Your heaviest <white>" + FishingItems.kg(s.heaviest));
            Leaderboard.Catch rec = module.leaderboard().speciesRecord(f.id());
            if (rec != null) lore.add("<gray>Station record <white>" + FishingItems.kg(rec.kg) + " <dark_gray>by " + rec.name);
            lore.add("");
            lore.add("<gray>Collection tier <white>" + tier + (tier > 1 ? " <green>(" + Effects.pct(cfg().collectionBonus * (tier - 1)) + " value)" : ""));
            if (next > 0) lore.add(bar(s.caught / (double) next) + " <gray>" + s.caught + "/" + next);
            else lore.add("<#ffd23f>Collection complete");
            ItemStack icon = items().fish(f, 1);
            ItemMeta meta = icon.getItemMeta();
            meta.lore(Text.lore(lore));
            icon.setItemMeta(meta);
            menu.set(FISH_SLOTS[i], icon, null);
        }
        menu.set(40, items().icon(Material.PAPER, "ui/info", a.colored(), List.of(
                "<#8a8070><i>" + a.description(), "", "<gray>Clearance: Grade " + a.grade() + "+",
                "<gray>Collection tiers add value to that", "<gray>species and pay out Scales.")), null);
    }

    // ------------------------------------------------------------------ leaderboards

    private void top(Player p, Menu menu) {
        menu.set(20, items().icon(Material.COD, "fishing/leaderboard", "<#ffb43a>Biggest catch this week", weeklyLines()), null);
        menu.set(24, items().icon(Material.EXPERIENCE_BOTTLE, "fishing/leaderboard", "<#5fb4c8>Top anglers", levelLines()), null);
        List<String> last = new ArrayList<>(module.leaderboard().lastWinners());
        if (last.isEmpty()) last.add("<gray>No results yet.");
        else last.replaceAll(s -> "<gray>" + s);
        last.add("");
        List<Integer> rw = cfg().weeklyRewards;
        last.add("<gray>Top " + rw.size() + " each week win Scales: <white>" + String.join(" / ", rw.stream().map(String::valueOf).toList()));
        last.add("<gray>Resets Monday 00:00 UTC.");
        menu.set(22, items().icon(Material.CLOCK, "ui/info", "<#ffd23f>Last week's winners", last), null);
    }

    List<String> weeklyLines() {
        List<String> out = new ArrayList<>();
        var top = module.leaderboard().weeklyTop(cfg().leaderboardSize);
        if (top.isEmpty()) out.add("<gray>Nothing caught yet this week.");
        for (int i = 0; i < top.size(); i++) {
            Leaderboard.Catch c = top.get(i).getValue();
            FishDef f = cfg().fish.get(c.fish);
            String fn = f == null ? c.fish : cfg().rarity(f.rarity()).color() + f.name();
            out.add(place(i) + " <white>" + c.name + " <dark_gray>· " + fn + " <gray>" + FishingItems.kg(c.kg));
        }
        return out;
    }

    List<String> levelLines() {
        List<String> out = new ArrayList<>();
        var top = module.leaderboard().levelTop(cfg().leaderboardSize);
        if (top.isEmpty()) out.add("<gray>No anglers yet.");
        for (int i = 0; i < top.size(); i++) {
            Leaderboard.LevelRow r = top.get(i).getValue();
            out.add(place(i) + " <white>" + r.name + " <dark_gray>· <gray>level <white>" + r.level);
        }
        return out;
    }

    private static String place(int i) {
        return switch (i) {
            case 0 -> "<#ffd23f>#1";
            case 1 -> "<#d8d0c0>#2";
            case 2 -> "<#c8743a>#3";
            default -> "<gray>#" + (i + 1);
        };
    }

    // ------------------------------------------------------------------ waters

    private void waters(Player p, Menu menu) {
        int grade = m().grade(p.getUniqueId());
        int i = 0;
        for (Area a : cfg().areas.values()) {
            if (i >= AREA_SLOTS.length) break;
            boolean open = grade >= a.grade();
            FishDef show = cfg().fishIn(a.id()).stream().filter(f -> f.rarity().equals("rare")).findFirst()
                    .orElse(cfg().fishIn(a.id()).stream().findFirst().orElse(null));
            List<String> lore = new ArrayList<>();
            lore.add("<#8a8070><i>" + a.description());
            lore.add("");
            lore.add("<gray>Clearance: Grade " + a.grade() + "+");
            lore.add("<gray>Fish value: <white>x" + a.valueMult() + "<gray>, scales up to grade " + a.maxGrade());
            long creatures = cfg().creatures.values().stream().filter(c -> c.area().equals(a.id())).count();
            if (creatures > 0) lore.add("<#ff5a3c>" + creatures + " kinds of sea creature");
            lore.add("");
            lore.add(open ? "<#c8743a>Click to travel" : "<red>Locked: reach Grade " + a.grade());
            ItemStack icon = show != null && open ? items().fish(show, 1) : items().icon(Material.GRAY_DYE, "ui/locked", "", null);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Text.item(a.colored()));
            meta.lore(Text.lore(lore));
            icon.setItemMeta(meta);
            menu.set(AREA_SLOTS[i++], icon, click -> {
                if (!open) {
                    deny(p, "Your clearance is too low for the " + a.name() + ".");
                    return;
                }
                p.closeInventory();
                module.travel(p, a);
            });
        }
        menu.set(31, items().icon(Material.LANTERN, "fishing/waters", cfg().hubName,
                List.of("<gray>The Fish Market and leaderboards.", "", "<#c8743a>Click to travel")), click -> {
            p.closeInventory();
            module.travelHub(p);
        });
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(menu.owner)) return;
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory().equals(e.getView().getTopInventory())) {
            Consumer<InventoryClickEvent> a = menu.actions.get(e.getRawSlot());
            if (a != null) a.accept(e);
        } else if (menu.bottom != null && e.getCurrentItem() != null) {
            menu.bottom.accept(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
        }
    }
}
