package dev.spacepunk.prison.api.event;

import dev.spacepunk.prison.api.Currency;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Fired after a balance changes. Informational (not cancellable). */
public class CurrencyChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private final Currency currency;
    private final long oldBalance;
    private final long newBalance;
    private final String reason;

    public CurrencyChangeEvent(UUID player, Currency currency, long oldBalance, long newBalance, String reason) {
        this.player = player;
        this.currency = currency;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.reason = reason;
    }

    public UUID player() { return player; }
    public Currency currency() { return currency; }
    public long oldBalance() { return oldBalance; }
    public long newBalance() { return newBalance; }
    public String reason() { return reason; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
