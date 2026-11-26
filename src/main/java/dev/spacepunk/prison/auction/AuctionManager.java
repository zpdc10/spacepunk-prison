package dev.spacepunk.prison.auction;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Black Market rules. Main thread only.
 *
 * Money flow: buyers and bidders pay with reason "auction:buy"/"auction:bid"; sellers receive "auction:sale"; refunds
 * use "auction:refund"; fees "auction:fee". None start with "sell:" or "income:", so auction money never counts toward
 * the prestige payout. Bids are held in escrow (taken from the bidder), and a player who is the top bidder anywhere
 * cannot prestige, so escrow can't carry Credits past the prestige wipe. From prestige-lock.spend-lock-grade on a
 * player can't buy or bid at all, so an inflated buy from a friend can't park Credits with them before prestige. Credits owed to offline players wait in the
 * store and are paid when their profile loads (they can't prestige before that).
 */
final class AuctionManager {
    enum ListResult { OK, NOTHING_IN_HAND, NOT_ALLOWED, BAD_PRICE, BAD_DURATION, TOO_MANY, CLAIMS_FULL, TOO_POOR, NOT_LOADED }
    enum BuyResult { OK, GONE, OWN, TOO_POOR, NOT_BIN, NOT_LOADED, LOCKED }
    enum BidResult { OK, GONE, OWN, TOO_LOW, TOO_POOR, NOT_AUCTION, NOT_LOADED, LOCKED }
    enum CancelResult { OK, GONE, NOT_YOURS, HAS_BIDS }

    private final AuctionStore store;
    private AuctionConfig cfg;

    AuctionManager(AuctionStore store, AuctionConfig cfg) {
        this.store = store;
        this.cfg = cfg;
    }

    AuctionConfig cfg() { return cfg; }

    void config(AuctionConfig cfg) { this.cfg = cfg; }

    AuctionStore store() { return store; }

    private static EconomyService eco() { return Services.get(EconomyService.class); }

    private static boolean loaded(UUID id) { return Services.get(ProfileService.class).get(id).isPresent(); }

    // ---- queries ----

    List<Listing> active() { return store.state().listings; }

    Listing get(long id) {
        for (Listing l : store.state().listings) if (l.id == id) return l;
        return null;
    }

    List<Listing> bySeller(UUID id) {
        String s = id.toString();
        return store.state().listings.stream().filter(l -> l.seller.equals(s)).toList();
    }

    List<Listing> leadingBids(UUID id) {
        String s = id.toString();
        return store.state().listings.stream().filter(l -> s.equals(l.topBidder)).toList();
    }

    long escrow(UUID id) { return leadingBids(id).stream().mapToLong(l -> l.topBid).sum(); }

    List<String> claims(UUID id) { return store.state().claims.getOrDefault(id.toString(), List.of()); }

    int maxListings(UUID id) {
        int prestige = Services.find(ProgressionService.class).map(p -> p.prestige(id)).orElse(0);
        return cfg.maxListings(prestige);
    }

    // ---- listing ----

    ListResult list(Player p, boolean auction, long price, int hours) {
        UUID id = p.getUniqueId();
        if (!loaded(id)) return ListResult.NOT_LOADED;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return ListResult.NOTHING_IN_HAND;
        if (!allowed(hand)) return ListResult.NOT_ALLOWED;
        if (price < cfg.minPrice || price > cfg.maxPrice) return ListResult.BAD_PRICE;
        if (!cfg.durations.containsKey(hours)) return ListResult.BAD_DURATION;
        if (bySeller(id).size() >= maxListings(id)) return ListResult.TOO_MANY;
        if (claims(id).size() >= cfg.maxClaims) return ListResult.CLAIMS_FULL;
        long fee = cfg.listingFee(price, hours);
        if (!eco().take(id, Currency.CREDITS, fee, "auction:fee")) return ListResult.TOO_POOR;

        ItemStack item = hand.clone();
        p.getInventory().setItemInMainHand(null);
        Listing l = new Listing();
        l.id = store.state().nextId++;
        l.seller = id.toString();
        l.sellerName = p.getName();
        l.item = Listing.encode(item);
        l.auction = auction;
        l.price = price;
        l.created = System.currentTimeMillis();
        l.ends = l.created + hours * 3_600_000L;
        l.fee = fee;
        store.state().listings.add(l);
        store.markDirty();
        p.playSound(Sound.sound(Key.key("entity.villager.work_cartographer"), Sound.Source.MASTER, 0.8f, 1f));
        return ListResult.OK;
    }

    /** Drills (soulbound) can't be sold, nor containers holding one, nor blocked materials. */
    boolean allowed(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (cfg.blockedMaterials.contains(item.getType())) return false;
        DrillService drill = Services.find(DrillService.class).orElse(null);
        if (drill != null && drill.isDrill(item)) return false;
        if (item.hasItemMeta()) {
            if (item.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
                for (ItemStack inner : box.getInventory().getContents()) if (inner != null && !allowed(inner)) return false;
            }
            if (item.getItemMeta() instanceof BundleMeta bundle) {
                for (ItemStack inner : bundle.getItems()) if (!allowed(inner)) return false;
            }
        }
        return true;
    }

    // ---- buying / bidding ----

    /**
     * Prestige lock: from cfg.spendLockGrade on a player can't buy or bid, so a friend's junk listing at an inflated
     * price can't carry Credits past the prestige wipe (same grade as the gang bank and /trade Credit locks).
     */
    boolean spendLocked(UUID id) {
        if (cfg.spendLockGrade <= 0) return false;
        int grade = Services.find(ProgressionService.class).map(s -> s.grade(id)).orElse(1);
        return grade >= cfg.spendLockGrade;
    }

    BuyResult buy(Player p, long listingId) {
        UUID id = p.getUniqueId();
        if (!loaded(id)) return BuyResult.NOT_LOADED;
        Listing l = get(listingId);
        if (l == null || l.ends <= System.currentTimeMillis()) return BuyResult.GONE;
        if (l.auction) return BuyResult.NOT_BIN;
        if (l.seller.equals(id.toString())) return BuyResult.OWN;
        if (spendLocked(id)) return BuyResult.LOCKED;
        ItemStack item = l.item();
        if (item == null) return BuyResult.GONE;
        if (!eco().take(id, Currency.CREDITS, l.price, "auction:buy")) return BuyResult.TOO_POOR;
        store.state().listings.remove(l);
        deliver(id, item, "<gray>You bought " + describe(item) + "<gray>.");
        paySeller(l, l.price, p.getName());
        store.markDirty();
        p.playSound(Sound.sound(Key.key("entity.villager.trade"), Sound.Source.MASTER, 0.8f, 1f));
        return BuyResult.OK;
    }

    BidResult bid(Player p, long listingId, long amount) {
        UUID id = p.getUniqueId();
        if (!loaded(id)) return BidResult.NOT_LOADED;
        Listing l = get(listingId);
        long now = System.currentTimeMillis();
        if (l == null || l.ends <= now) return BidResult.GONE;
        if (!l.auction) return BidResult.NOT_AUCTION;
        if (l.seller.equals(id.toString())) return BidResult.OWN;
        if (spendLocked(id)) return BidResult.LOCKED;
        if (amount < cfg.minNextBid(l) || amount > cfg.maxPrice) return BidResult.TOO_LOW;
        boolean raising = id.toString().equals(l.topBidder);
        long charge = raising ? amount - l.topBid : amount;
        if (!eco().take(id, Currency.CREDITS, charge, "auction:bid")) return BidResult.TOO_POOR;
        UUID previous = l.topBidderId();
        long previousBid = l.topBid;
        l.topBid = amount;
        l.topBidder = id.toString();
        l.topBidderName = p.getName();
        l.bids++;
        if (l.ends - now < cfg.snipeWindowSeconds * 1000L) l.ends = now + cfg.snipeExtendSeconds * 1000L;
        if (previous != null && !raising) {
            pay(previous, previousBid, "auction:refund", "<yellow>Outbid!</yellow> <gray>" + p.getName() + " bid "
                    + eco().format(Currency.CREDITS, amount) + " <gray>on " + describe(l.item())
                    + "<gray>. Your " + eco().format(Currency.CREDITS, previousBid) + " <gray>was refunded.");
            Player prev = Bukkit.getPlayer(previous);
            if (prev != null) prev.playSound(Sound.sound(Key.key("block.note_block.bass"), Sound.Source.MASTER, 1f, 0.6f));
        }
        notify(l.sellerId(), "<gray>New bid on your " + describe(l.item()) + "<gray>: "
                + eco().format(Currency.CREDITS, amount) + " <gray>by " + p.getName() + ".", false);
        store.markDirty();
        p.playSound(Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 0.8f, 1.4f));
        return BidResult.OK;
    }

    CancelResult cancel(Player p, long listingId, boolean admin) {
        Listing l = get(listingId);
        if (l == null) return CancelResult.GONE;
        if (!admin && !l.seller.equals(p.getUniqueId().toString())) return CancelResult.NOT_YOURS;
        if (!admin && l.hasBids()) return CancelResult.HAS_BIDS;
        store.state().listings.remove(l);
        if (l.hasBids()) {
            pay(l.topBidderId(), l.topBid, "auction:refund", "<gray>An auction you led was removed by staff. Your "
                    + eco().format(Currency.CREDITS, l.topBid) + " <gray>was refunded.");
        }
        ItemStack item = l.item();
        if (item != null) deliver(l.sellerId(), item, "<gray>Your listing of " + describe(item) + " <gray>was returned.");
        store.markDirty();
        return CancelResult.OK;
    }

    // ---- settlement ----

    /** Ends expired listings. Called every second. */
    void tick() {
        long now = System.currentTimeMillis();
        List<Listing> done = new ArrayList<>();
        for (Listing l : store.state().listings) if (l.ends <= now) done.add(l);
        if (done.isEmpty()) return;
        for (Listing l : done) {
            store.state().listings.remove(l);
            ItemStack item = l.item();
            if (l.hasBids()) {
                if (item != null) {
                    deliver(l.topBidderId(), item, "<green>You won the auction</green> <gray>for " + describe(item)
                            + " <gray>at " + eco().format(Currency.CREDITS, l.topBid) + "<gray>.");
                } else {
                    pay(l.topBidderId(), l.topBid, "auction:refund", "<red>An auction you won had a broken item; you were refunded.");
                }
                if (item != null) paySeller(l, l.topBid, l.topBidderName);
            } else if (item != null) {
                deliver(l.sellerId(), item, "<gray>Your listing of " + describe(item) + " <gray>expired"
                        + (l.auction ? " with no bids" : "") + ". It's in <white>/ah claims</white>.");
            }
        }
        store.markDirty();
    }

    private void paySeller(Listing l, long price, String buyer) {
        long tax = cfg.salesTax(price);
        long net = price - tax;
        ItemStack item = l.item();
        pay(l.sellerId(), net, "auction:sale", "<green>Sold!</green> <gray>" + buyer + " bought your " + describe(item)
                + " <gray>for " + eco().format(Currency.CREDITS, price) + "<gray>. You got "
                + eco().format(Currency.CREDITS, net) + " <dark_gray>(" + Numbers.shortFormat(tax) + " tax)");
        if (cfg.announceBigSales && price >= cfg.announceThreshold && item != null) {
            Component name = item.effectiveName().hoverEvent(item.asHoverEvent());
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<#c8743a>Black Market:</#c8743a> <white>" + buyer + "</white> <gray>paid "
                    + eco().format(Currency.CREDITS, price) + " <gray>for ").append(name));
        }
        Player seller = Bukkit.getPlayer(l.sellerId());
        if (seller != null) seller.playSound(Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1f, 1.2f));
    }

    // ---- delivery ----

    /** Pays Credits now if the profile is loaded, else holds them until it loads. Always sends/queues the message. */
    void pay(UUID id, long amount, String reason, String message) {
        if (amount > 0) {
            if (loaded(id)) eco().give(id, Currency.CREDITS, amount, reason);
            else store.state().pending.merge(id.toString(), amount, Long::sum);
        }
        notify(id, message, true);
        store.markDirty();
    }

    /** Puts the item in the player's inventory if they're online, and whatever doesn't fit in their collection bin. */
    void deliver(UUID id, ItemStack item, String message) {
        Player p = Bukkit.getPlayer(id);
        ItemStack rest = item;
        if (p != null && loaded(id)) {
            var left = p.getInventory().addItem(item.clone());
            if (left.isEmpty()) {
                notify(id, message, true);
                return;
            }
            rest = left.values().iterator().next();
        }
        store.state().claims.computeIfAbsent(id.toString(), k -> new ArrayList<>()).add(Listing.encode(rest));
        notify(id, message + (message.contains("/ah claims") ? "" : " <dark_gray>(waiting in /ah claims)"), true);
        store.markDirty();
    }

    private void notify(UUID id, String message, boolean queueIfOffline) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) Text.send(p, message);
        else if (queueIfOffline) {
            List<String> list = store.state().notices.computeIfAbsent(id.toString(), k -> new ArrayList<>());
            list.add(message);
            while (list.size() > 20) list.removeFirst();
        }
    }

    /** On profile load: pay owed Credits and show queued messages. */
    void onLoad(Player p) {
        String key = p.getUniqueId().toString();
        Long owed = store.state().pending.remove(key);
        List<String> notes = store.state().notices.remove(key);
        if (owed != null && owed > 0) eco().give(p.getUniqueId(), Currency.CREDITS, owed, "auction:sale");
        if (notes != null && !notes.isEmpty()) {
            Text.send(p, "<#c8743a>Black Market</#c8743a> <gray>while you were away:");
            for (String n : notes) p.sendMessage(Text.mm("<dark_gray> · </dark_gray>" + n));
        }
        if (owed != null && owed > 0) Text.send(p, "<gray>Paid out " + eco().format(Currency.CREDITS, owed) + " <gray>from the Black Market.");
        int c = claims(p.getUniqueId()).size();
        if (c > 0) Text.send(p, "<gray>You have <white>" + c + "</white> item" + (c == 1 ? "" : "s") + " waiting in <white>/ah claims</white>.");
        if (owed != null || notes != null) store.markDirty();
    }

    /** Moves claim index i into the inventory (as much as fits). Returns false if nothing fit. */
    boolean takeClaim(Player p, int index) {
        String key = p.getUniqueId().toString();
        List<String> list = store.state().claims.get(key);
        if (list == null || index < 0 || index >= list.size()) return false;
        ItemStack item = Listing.decode(list.get(index));
        store.markDirty();
        if (item == null) {
            list.remove(index);
            if (list.isEmpty()) store.state().claims.remove(key);
            return true;
        }
        int amount = item.getAmount();
        var left = p.getInventory().addItem(item.clone());
        if (left.isEmpty()) {
            list.remove(index);
            if (list.isEmpty()) store.state().claims.remove(key);
            return true;
        }
        ItemStack rest = left.values().iterator().next();
        if (rest.getAmount() >= amount) return false;
        list.set(index, Listing.encode(rest));
        return true;
    }

    // ---- helpers ----

    static String describe(ItemStack item) {
        if (item == null) return "<dark_gray>an item";
        String name = Text.MM.serialize(item.effectiveName());
        return (item.getAmount() > 1 ? "<white>" + item.getAmount() + "x</white> " : "") + name;
    }

    static String timeLeft(long ms) {
        if (ms <= 0) return "ending";
        long s = ms / 1000;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return (s % 60) + "s";
    }

    enum Sort {
        ENDING("Ending soon", Comparator.comparingLong(l -> l.ends)),
        NEWEST("Newest", Comparator.comparingLong((Listing l) -> l.created).reversed()),
        CHEAPEST("Lowest price", Comparator.comparingLong(Listing::currentPrice)),
        PRICIEST("Highest price", Comparator.comparingLong(Listing::currentPrice).reversed());

        final String label;
        final Comparator<Listing> order;

        Sort(String label, Comparator<Listing> order) {
            this.label = label;
            this.order = order;
        }

        Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }

    enum Filter {
        ALL("Everything"), BIN("Buy now only"), AUCTION("Auctions only");

        final String label;

        Filter(String label) { this.label = label; }

        boolean test(Listing l) {
            return this == ALL || (this == BIN) != l.auction;
        }

        Filter next() { return values()[(ordinal() + 1) % values().length]; }
    }
}
