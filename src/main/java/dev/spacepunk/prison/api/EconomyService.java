package dev.spacepunk.prison.api;

import java.util.UUID;

/**
 * Balances for all three currencies. Implemented in core (economy package).
 * All methods are main-thread only and operate on the cached profile of an ONLINE player.
 * Every change fires a {@link dev.spacepunk.prison.api.event.CurrencyChangeEvent}.
 */
public interface EconomyService {
    long balance(UUID player, Currency currency);

    /** Adds amount (must be >= 0). Returns the new balance. */
    long give(UUID player, Currency currency, long amount, String reason);

    /** Removes amount if the player can afford it. Returns false and changes nothing otherwise. */
    boolean take(UUID player, Currency currency, long amount, String reason);

    /** Admin set. */
    void set(UUID player, Currency currency, long amount);

    /** Formats an amount with its currency, e.g. "1.2M Credits", as MiniMessage. */
    String format(Currency currency, long amount);
}
