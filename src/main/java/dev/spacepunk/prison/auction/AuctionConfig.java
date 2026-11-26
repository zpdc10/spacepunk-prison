package dev.spacepunk.prison.auction;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumSet;
import java.util.Set;
import java.util.TreeMap;

/** auction.yml */
final class AuctionConfig {
    double listingFeePercent;
    long listingFeeMin;
    double salesTaxPercent;
    double bidIncrementPercent;
    long bidIncrementMin;
    long minPrice;
    long maxPrice;
    int maxListingsBase;
    int maxListingsPerPrestige;
    int maxListingsCap;
    int defaultBinHours;
    int defaultAuctionHours;
    /** Listing hours -> fee multiplier. */
    final TreeMap<Integer, Double> durations = new TreeMap<>();
    int snipeWindowSeconds;
    int snipeExtendSeconds;
    int maxClaims;
    boolean announceBigSales;
    long announceThreshold;
    /** From this grade on a player can't buy or bid (0 = off). */
    int spendLockGrade;
    final Set<Material> blockedMaterials = EnumSet.noneOf(Material.class);

    static AuctionConfig load(YamlConfiguration y) {
        AuctionConfig c = new AuctionConfig();
        c.listingFeePercent = y.getDouble("fees.listing-percent", 1.0);
        c.listingFeeMin = y.getLong("fees.listing-min", 25);
        c.salesTaxPercent = y.getDouble("fees.sales-tax-percent", 5.0);
        c.bidIncrementPercent = y.getDouble("bids.increment-percent", 5.0);
        c.bidIncrementMin = Math.max(1, y.getLong("bids.increment-min", 10));
        c.snipeWindowSeconds = y.getInt("bids.anti-snipe-window-seconds", 60);
        c.snipeExtendSeconds = y.getInt("bids.anti-snipe-extend-seconds", 60);
        c.minPrice = Math.max(1, y.getLong("limits.min-price", 10));
        c.maxPrice = y.getLong("limits.max-price", 1_000_000_000_000_000L);
        c.maxListingsBase = y.getInt("limits.listings.base", 3);
        c.maxListingsPerPrestige = y.getInt("limits.listings.per-prestige", 1);
        c.maxListingsCap = y.getInt("limits.listings.max", 14);
        c.maxClaims = y.getInt("limits.max-claims", 54);
        c.defaultBinHours = y.getInt("durations.default-bin", 24);
        c.defaultAuctionHours = y.getInt("durations.default-auction", 12);
        ConfigurationSection d = y.getConfigurationSection("durations.fee-multiplier");
        if (d != null) {
            for (String k : d.getKeys(false)) {
                try {
                    c.durations.put(Integer.parseInt(k), d.getDouble(k));
                } catch (NumberFormatException ignored) { }
            }
        }
        if (c.durations.isEmpty()) {
            c.durations.put(6, 0.75);
            c.durations.put(12, 1.0);
            c.durations.put(24, 1.5);
            c.durations.put(48, 2.5);
        }
        c.announceBigSales = y.getBoolean("announce.big-sales", true);
        c.announceThreshold = y.getLong("announce.threshold", 1_000_000);
        c.spendLockGrade = y.getInt("prestige-lock.spend-lock-grade", 21);
        for (String m : y.getStringList("blocked-materials")) {
            Material mat = Material.matchMaterial(m);
            if (mat != null) c.blockedMaterials.add(mat);
        }
        return c;
    }

    /** Fee to list at this price for this many hours. */
    long listingFee(long price, int hours) {
        double mul = durations.getOrDefault(hours, 1.0);
        return Math.max(listingFeeMin, (long) Math.ceil(price * listingFeePercent / 100.0 * mul));
    }

    long salesTax(long price) { return (long) Math.floor(price * salesTaxPercent / 100.0); }

    long minNextBid(Listing l) {
        if (!l.hasBids()) return l.price;
        long inc = Math.max(bidIncrementMin, (long) Math.ceil(l.topBid * bidIncrementPercent / 100.0));
        return l.topBid + inc;
    }

    int maxListings(int prestige) { return Math.min(maxListingsCap, maxListingsBase + maxListingsPerPrestige * prestige); }
}
