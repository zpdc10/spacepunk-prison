package dev.spacepunk.prison.trade;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A live trade between two online players. Main thread only. */
final class TradeSession {
    static final int MAX_ITEMS = 16;

    /** One player's half of the table. */
    static final class Side implements InventoryHolder {
        final TradeSession session;
        final UUID id;
        final String name;
        final List<ItemStack> items = new ArrayList<>();
        long credits;
        boolean accepted;
        /** Window closed while they type a Credits amount in chat (closing then does not cancel). */
        boolean typing;
        long typingUntil;
        Inventory inventory;

        Side(TradeSession session, UUID id, String name) {
            this.session = session;
            this.id = id;
            this.name = name;
        }

        Side other() { return session.a == this ? session.b : session.a; }

        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    final long id;
    final Side a;
    final Side b;
    /** Seconds left after both accepted, or -1. */
    int countdown = -1;
    /** Set once the trade completed or was cancelled; closing windows after that does nothing. */
    boolean over;

    TradeSession(long id, UUID a, String aName, UUID b, String bName) {
        this.id = id;
        this.a = new Side(this, a, aName);
        this.b = new Side(this, b, bName);
    }

    Side side(UUID player) {
        if (a.id.equals(player)) return a;
        if (b.id.equals(player)) return b;
        return null;
    }

    /** Any change to either offer: both have to accept again. */
    void changed() {
        a.accepted = false;
        b.accepted = false;
        countdown = -1;
    }
}
