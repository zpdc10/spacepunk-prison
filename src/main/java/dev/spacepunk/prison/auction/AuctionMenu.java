package dev.spacepunk.prison.auction;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.auction.AuctionManager.Filter;
import dev.spacepunk.prison.auction.AuctionManager.Sort;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Black Market menus: browse, your listings and bids, collection bin, buy confirm and bidding. */
final class AuctionMenu implements Listener {
    static final int PAGE = 45;

    enum View { BROWSE, MINE, CLAIMS, CONFIRM, BID }

    static final class Holder implements InventoryHolder {
        View view;
        int page;
        Sort sort = Sort.ENDING;
        Filter filter = Filter.ALL;
        long listing;
        /** Slot -> listing id (browse/mine) or claim index (claims). */
        final Map<Integer, Long> slots = new HashMap<>();
        /** Bid buttons: slot -> amount. */
        final Map<Integer, Long> bidButtons = new HashMap<>();
        Inventory inventory;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private final AuctionManager auctions;
    private boolean useModels = true;

    AuctionMenu(AuctionManager auctions) { this.auctions = auctions; }

    void useModels(boolean b) { this.useModels = b; }

    private static EconomyService eco() { return Services.get(EconomyService.class); }

    void open(Player p, View view) {
        Holder h = new Holder();
        h.view = view;
        show(p, h);
    }

    private void show(Player p, Holder h) {
        int size = switch (h.view) {
            case BROWSE, MINE, CLAIMS -> 54;
            case CONFIRM, BID -> 27;
        };
        String title = switch (h.view) {
            case BROWSE -> "<dark_gray>Black Market";
            case MINE -> "<dark_gray>Black Market · Your Stalls";
            case CLAIMS -> "<dark_gray>Black Market · Collection Bin";
            case CONFIRM -> "<dark_gray>Black Market · Buy";
            case BID -> "<dark_gray>Black Market · Bid";
        };
        h.inventory = Bukkit.createInventory(h, size, Text.mm(title));
        render(p, h);
        p.openInventory(h.inventory);
    }

    /** Re-renders every open Black Market menu (time left, new listings, outbids). */
    void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) {
                if ((h.view == View.CONFIRM || h.view == View.BID) && auctions.get(h.listing) == null) {
                    h.view = View.BROWSE;
                    show(p, h);
                    continue;
                }
                render(p, h);
            }
        }
    }

    private void render(Player p, Holder h) {
        Inventory inv = h.inventory;
        inv.clear();
        h.slots.clear();
        h.bidButtons.clear();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", " ", List.of());
        ItemMeta fm = filler.getItemMeta();
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        switch (h.view) {
            case BROWSE, MINE -> {
                List<Listing> list = new ArrayList<>();
                if (h.view == View.BROWSE) {
                    for (Listing l : auctions.active()) if (h.filter.test(l)) list.add(l);
                    list.sort(h.sort.order);
                } else {
                    list.addAll(auctions.bySeller(p.getUniqueId()));
                    for (Listing l : auctions.leadingBids(p.getUniqueId())) if (!list.contains(l)) list.add(l);
                    list.sort(Sort.ENDING.order);
                }
                int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
                h.page = Math.max(0, Math.min(h.page, pages - 1));
                for (int i = 0; i < PAGE; i++) {
                    int idx = h.page * PAGE + i;
                    if (idx >= list.size()) break;
                    Listing l = list.get(idx);
                    inv.setItem(i, display(p, l));
                    h.slots.put(i, l.id);
                }
                for (int i = 45; i < 54; i++) inv.setItem(i, filler);
                if (h.page > 0) inv.setItem(45, icon(Material.ARROW, "ui/previous", "<gray>Previous page", List.of()));
                if (h.page < pages - 1) inv.setItem(53, icon(Material.ARROW, "ui/next", "<gray>Next page", List.of()));
                if (h.view == View.BROWSE) {
                    inv.setItem(46, icon(Material.HOPPER, "ui/info", "<#c8743a>Sort: <white>" + h.sort.label,
                            List.of("<gray>Click to change")));
                    inv.setItem(47, icon(Material.COMPARATOR, "ui/info", "<#c8743a>Show: <white>" + h.filter.label,
                            List.of("<gray>Click to change")));
                    int mine = auctions.bySeller(p.getUniqueId()).size();
                    inv.setItem(48, icon(Material.CHEST_MINECART, "ui/sell", "<#c8743a>Your Stalls", List.of(
                            "<gray>Listings: <white>" + mine + "</white> / " + auctions.maxListings(p.getUniqueId()),
                            "<gray>Leading bids: <white>" + auctions.leadingBids(p.getUniqueId()).size(),
                            "", "<#c8743a>Click to open")));
                } else {
                    inv.setItem(48, icon(Material.ARROW, "ui/back", "<gray>Back to the market", List.of()));
                }
                inv.setItem(49, infoIcon(p));
                int claims = auctions.claims(p.getUniqueId()).size();
                inv.setItem(50, icon(claims > 0 ? Material.CHEST : Material.ENDER_CHEST, "ui/cargo", "<#c8743a>Collection Bin",
                        List.of(claims > 0 ? "<white>" + claims + "</white> <gray>item" + (claims == 1 ? "" : "s") + " waiting" : "<dark_gray>Empty",
                                "", "<#c8743a>Click to open")));
                inv.setItem(52, icon(Material.BARRIER, "ui/close", "<red>Close", List.of()));
                if (list.isEmpty()) {
                    inv.setItem(22, icon(Material.COBWEB, "ui/info", "<gray>Nothing here",
                            h.view == View.BROWSE ? List.of("<gray>Hold an item and use", "<white>/ah sell <price></white> <gray>or <white>/ah auction <start bid>")
                                    : List.of("<gray>You have no listings or leading bids.")));
                }
            }
            case CLAIMS -> {
                List<String> claims = auctions.claims(p.getUniqueId());
                for (int i = 0; i < Math.min(45, claims.size()); i++) {
                    ItemStack it = Listing.decode(claims.get(i));
                    if (it == null) it = icon(Material.BARRIER, null, "<red>Broken item", List.of("<gray>Click to discard"));
                    inv.setItem(i, it);
                    h.slots.put(i, (long) i);
                }
                for (int i = 45; i < 54; i++) inv.setItem(i, filler);
                inv.setItem(45, icon(Material.ARROW, "ui/back", "<gray>Back to the market", List.of()));
                inv.setItem(49, icon(Material.HOPPER, "ui/add", "<#c8743a>Collect All", List.of("<gray>Moves everything that fits", "<gray>into your inventory.")));
                inv.setItem(52, icon(Material.BARRIER, "ui/close", "<red>Close", List.of()));
            }
            case CONFIRM -> {
                for (int i = 0; i < 27; i++) inv.setItem(i, filler);
                Listing l = auctions.get(h.listing);
                if (l == null) return;
                inv.setItem(13, display(p, l));
                long bal = eco().balance(p.getUniqueId(), Currency.CREDITS);
                inv.setItem(11, icon(Material.LIME_STAINED_GLASS_PANE, "ui/confirm", "<green>Buy for " + eco().format(Currency.CREDITS, l.price),
                        List.of("<gray>Your balance: " + eco().format(Currency.CREDITS, bal),
                                bal >= l.price ? "<#c8743a>Click to buy" : "<red>You can't afford this")));
                inv.setItem(15, icon(Material.RED_STAINED_GLASS_PANE, "ui/cancel", "<red>Cancel", List.of()));
            }
            case BID -> {
                for (int i = 0; i < 27; i++) inv.setItem(i, filler);
                Listing l = auctions.get(h.listing);
                if (l == null) return;
                inv.setItem(4, display(p, l));
                long min = auctions.cfg().minNextBid(l);
                long bal = eco().balance(p.getUniqueId(), Currency.CREDITS);
                boolean leading = p.getUniqueId().toString().equals(l.topBidder);
                long[] amounts = {min, roundUp(Math.max(min + 1, (long) Math.ceil(min * 1.25))), roundUp(Math.max(min + 2, (long) Math.ceil(min * 1.5)))};
                int[] slots = {11, 13, 15};
                for (int i = 0; i < 3; i++) {
                    long a = amounts[i];
                    long cost = leading ? a - l.topBid : a;
                    List<String> lore = new ArrayList<>();
                    if (leading) lore.add("<gray>You lead. Raising costs " + eco().format(Currency.CREDITS, cost) + "<gray> more.");
                    lore.add("<gray>Your balance: " + eco().format(Currency.CREDITS, bal));
                    lore.add("<dark_gray>Held in escrow until you're outbid");
                    lore.add("<dark_gray>(refunded) or the auction ends.");
                    lore.add(bal >= cost ? "<#c8743a>Click to bid" : "<red>You can't afford this");
                    inv.setItem(slots[i], icon(Material.GOLD_NUGGET, "currency/credit",
                            "<gold>Bid " + eco().format(Currency.CREDITS, a), lore));
                    h.bidButtons.put(slots[i], a);
                }
                inv.setItem(22, icon(Material.ARROW, "ui/back", "<gray>Back", List.of(
                        "<gray>Custom amount: <white>/ah bid " + l.id + " <amount>")));
            }
        }
    }

    private static long roundUp(long v) {
        if (v < 100) return v;
        long step = (long) Math.pow(10, Math.max(0, (long) Math.floor(Math.log10(v)) - 2));
        return ((v + step - 1) / step) * step;
    }

    private ItemStack display(Player viewer, Listing l) {
        ItemStack it = l.item();
        if (it == null) return icon(Material.BARRIER, null, "<red>Broken listing #" + l.id, List.of());
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        List<String> add = new ArrayList<>();
        add.add("<dark_gray>───────────────");
        add.add("<gray>Seller: <white>" + l.sellerName);
        boolean own = l.seller.equals(viewer.getUniqueId().toString());
        if (l.auction) {
            if (l.hasBids()) {
                add.add("<gray>Top bid: " + eco().format(Currency.CREDITS, l.topBid) + " <gray>by <white>" + l.topBidderName);
                add.add("<gray>Bids: <white>" + l.bids);
            } else {
                add.add("<gray>Starting bid: " + eco().format(Currency.CREDITS, l.price));
            }
        } else {
            add.add("<gray>Buy now: " + eco().format(Currency.CREDITS, l.price));
        }
        add.add("<gray>Ends in: <white>" + AuctionManager.timeLeft(l.ends - System.currentTimeMillis()));
        add.add("<dark_gray>" + (l.auction ? "Auction" : "Buy it now") + " · #" + l.id);
        add.add("");
        if (own) add.add(l.hasBids() ? "<dark_gray>Has bids, can't be cancelled" : "<#c8743a>Shift-click</#c8743a> <gray>to cancel (fee not refunded)");
        else if (viewer.getUniqueId().toString().equals(l.topBidder)) add.add("<green>You're the top bidder. <#c8743a>Click</#c8743a> <gray>to raise");
        else add.add("<#c8743a>Click</#c8743a> <gray>to " + (l.auction ? "bid" : "buy"));
        lore.addAll(Text.lore(add));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack infoIcon(Player p) {
        AuctionConfig c = auctions.cfg();
        return icon(Material.BOOK, "ui/info", "<#c8743a>How the Black Market works", List.of(
                "<gray>Balance: " + eco().format(Currency.CREDITS, eco().balance(p.getUniqueId(), Currency.CREDITS)),
                "<gray>In escrow (your top bids): " + eco().format(Currency.CREDITS, auctions.escrow(p.getUniqueId())),
                "",
                "<white>/ah sell <price> [hours]</white> <gray>buy-it-now",
                "<white>/ah auction <start bid> [hours]</white> <gray>timed auction",
                "<gray>Hours: <white>" + String.join(", ", c.durations.keySet().stream().map(String::valueOf).toList()),
                "",
                "<gray>Listing fee: <white>" + pct(c.listingFeePercent) + "</white> of the price (min " + c.listingFeeMin + "),",
                "<gray>more for longer listings. Sales tax: <white>" + pct(c.salesTaxPercent),
                "<gray>Bids must beat the top bid by <white>" + pct(c.bidIncrementPercent),
                "<dark_gray>Late bids extend the auction to " + c.snipeExtendSeconds + "s.",
                "",
                "<dark_gray>Money from sales doesn't count toward your",
                "<dark_gray>prestige payout, and you can't prestige while",
                "<dark_gray>you're the top bidder on an auction."));
    }

    private static String pct(double v) { return (v == Math.rint(v) ? Long.toString(Math.round(v)) : String.valueOf(v)) + "%"; }

    private ItemStack icon(Material mat, String model, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore != null && !lore.isEmpty()) meta.lore(Text.lore(lore));
        if (model != null && useModels) meta.setItemModel(new NamespacedKey("spacepunk", model));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        switch (h.view) {
            case BROWSE, MINE -> {
                if (slot < PAGE) {
                    Long id = h.slots.get(slot);
                    Listing l = id == null ? null : auctions.get(id);
                    if (l == null) return;
                    if (l.seller.equals(p.getUniqueId().toString())) {
                        if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
                            switch (auctions.cancel(p, l.id, false)) {
                                case HAS_BIDS -> Text.send(p, "<red>That auction has bids and can't be cancelled.");
                                case OK -> Text.send(p, "<gray>Listing cancelled.");
                                default -> {}
                            }
                            render(p, h);
                        }
                        return;
                    }
                    Holder next = new Holder();
                    next.view = l.auction ? View.BID : View.CONFIRM;
                    next.listing = l.id;
                    next.sort = h.sort;
                    next.filter = h.filter;
                    show(p, next);
                    return;
                }
                switch (slot) {
                    case 45 -> { h.page--; render(p, h); }
                    case 53 -> { h.page++; render(p, h); }
                    case 46 -> { if (h.view == View.BROWSE) { h.sort = h.sort.next(); h.page = 0; render(p, h); } }
                    case 47 -> { if (h.view == View.BROWSE) { h.filter = h.filter.next(); h.page = 0; render(p, h); } }
                    case 48 -> { h.view = h.view == View.BROWSE ? View.MINE : View.BROWSE; h.page = 0; show(p, h); }
                    case 50 -> { h.view = View.CLAIMS; show(p, h); }
                    case 52 -> p.closeInventory();
                    default -> {}
                }
            }
            case CLAIMS -> {
                if (slot < 45) {
                    Long idx = h.slots.get(slot);
                    if (idx != null && !auctions.takeClaim(p, idx.intValue())) Text.send(p, "<red>Your inventory is full.");
                    render(p, h);
                    return;
                }
                switch (slot) {
                    case 45 -> { h.view = View.BROWSE; show(p, h); }
                    case 49 -> {
                        int moved = 0;
                        for (int i = auctions.claims(p.getUniqueId()).size() - 1; i >= 0; i--) if (auctions.takeClaim(p, i)) moved++;
                        if (auctions.claims(p.getUniqueId()).isEmpty()) Text.send(p, "<gray>Collected everything.");
                        else Text.send(p, "<yellow>Collected " + moved + "; your inventory is full.");
                        render(p, h);
                    }
                    case 52 -> p.closeInventory();
                    default -> {}
                }
            }
            case CONFIRM -> {
                if (slot == 15) back(p, h);
                else if (slot == 11) {
                    switch (auctions.buy(p, h.listing)) {
                        case OK -> { back(p, h); return; }
                        case TOO_POOR -> Text.send(p, "<red>You can't afford that.");
                        case GONE -> { Text.send(p, "<red>That listing is gone."); back(p, h); return; }
                        case OWN -> Text.send(p, "<red>That's your own listing.");
                        case NOT_LOADED -> Text.send(p, "<red>Your profile is still loading.");
                        case LOCKED -> Text.send(p, "<red>Prestige lock: you can't buy or bid from Grade " + auctions.cfg().spendLockGrade + " on.</red> <gray>Prestige wipes Credits, so they can't be passed to a seller before it. You can still sell.");
                        case NOT_BIN -> {}
                    }
                    render(p, h);
                }
            }
            case BID -> {
                if (slot == 22) { back(p, h); return; }
                Long amount = h.bidButtons.get(slot);
                if (amount == null) return;
                report(p, auctions.bid(p, h.listing, amount), amount);
                if (auctions.get(h.listing) == null) back(p, h);
                else render(p, h);
            }
        }
    }

    void report(Player p, AuctionManager.BidResult r, long amount) {
        switch (r) {
            case OK -> Text.send(p, "<green>Bid placed:</green> " + eco().format(Currency.CREDITS, amount) + "<gray>. It's held in escrow.");
            case TOO_LOW -> Text.send(p, "<red>Someone bid first. The minimum bid has gone up.");
            case TOO_POOR -> Text.send(p, "<red>You can't afford that bid.");
            case GONE -> Text.send(p, "<red>That auction has ended.");
            case OWN -> Text.send(p, "<red>You can't bid on your own auction.");
            case NOT_AUCTION -> Text.send(p, "<red>That listing is buy-it-now.");
            case NOT_LOADED -> Text.send(p, "<red>Your profile is still loading.");
            case LOCKED -> Text.send(p, "<red>Prestige lock: you can't buy or bid from Grade " + auctions.cfg().spendLockGrade + " on.</red> <gray>Prestige wipes Credits, so they can't be passed to a seller before it. You can still sell.");
        }
    }

    private void back(Player p, Holder from) {
        Holder h = new Holder();
        h.view = View.BROWSE;
        h.sort = from.sort;
        h.filter = from.filter;
        show(p, h);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
