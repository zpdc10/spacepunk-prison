package dev.spacepunk.prison.auction;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.UUID;

/** One Black Market listing: buy-it-now or a timed auction. Plain fields so Gson can store it. */
final class Listing {
    long id;
    String seller;
    String sellerName;
    /** The item, serialized with ItemStack#serializeAsBytes, Base64. */
    String item;
    boolean auction;
    /** Buy-it-now price, or the starting bid for an auction. */
    long price;
    long topBid;
    String topBidder;
    String topBidderName;
    int bids;
    long created;
    long ends;
    long fee;

    private transient ItemStack cached;

    UUID sellerId() { return UUID.fromString(seller); }

    UUID topBidderId() { return topBidder == null ? null : UUID.fromString(topBidder); }

    boolean hasBids() { return auction && topBidder != null; }

    /** What a buyer pays now (BIN) or the current top bid / start price (auction). */
    long currentPrice() { return auction && hasBids() ? topBid : price; }

    ItemStack item() {
        if (cached == null) cached = decode(item);
        return cached == null ? null : cached.clone();
    }

    static String encode(ItemStack stack) { return Base64.getEncoder().encodeToString(stack.serializeAsBytes()); }

    static ItemStack decode(String data) {
        if (data == null) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
