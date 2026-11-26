package dev.spacepunk.prison.trade;

import java.util.ArrayList;
import java.util.List;

/** One completed trade as kept in trades.json for /trade history and /trade log (item names only). */
final class TradeRecord {
    long id;
    long time;
    String a, aName, b, bName;
    /** What each side gave, as MiniMessage descriptions ("3x Copper Ingot"). */
    List<String> aItems = new ArrayList<>();
    List<String> bItems = new ArrayList<>();
    long aCredits, bCredits;

    boolean involves(String uuid) { return uuid.equals(a) || uuid.equals(b); }

    /** The full version written to trades.log, with every item encoded so an admin can restore it. */
    static final class Full {
        long id;
        long time;
        String a, aName, b, bName;
        List<String> aItems, bItems;
        List<String> aItemData, bItemData;
        long aCredits, bCredits;
    }
}
