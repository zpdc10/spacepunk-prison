package dev.spacepunk.prison.economy;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.event.CurrencyChangeEvent;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.util.Numbers;
import org.bukkit.Bukkit;

import java.util.UUID;

public final class EconomyManager implements EconomyService {
    private final ProfileService profiles;

    public EconomyManager(ProfileService profiles) { this.profiles = profiles; }

    @Override public long balance(UUID player, Currency currency) {
        return profiles.get(player).map(p -> p.balance(currency)).orElse(0L);
    }

    @Override public long give(UUID player, Currency currency, long amount, String reason) {
        if (amount < 0) throw new IllegalArgumentException("amount < 0");
        PlayerProfile p = profiles.require(player);
        long old = p.balance(currency);
        long now = saturatedAdd(old, amount);
        apply(p, currency, old, now, reason);
        return now;
    }

    @Override public boolean take(UUID player, Currency currency, long amount, String reason) {
        if (amount < 0) throw new IllegalArgumentException("amount < 0");
        PlayerProfile p = profiles.require(player);
        long old = p.balance(currency);
        if (old < amount) return false;
        apply(p, currency, old, old - amount, reason);
        return true;
    }

    @Override public void set(UUID player, Currency currency, long amount) {
        PlayerProfile p = profiles.require(player);
        apply(p, currency, p.balance(currency), Math.max(0, amount), "admin-set");
    }

    @Override public String format(Currency currency, long amount) {
        return currency.color() + Numbers.shortFormat(amount) + " " + currency.displayName() + "<reset>";
    }

    private void apply(PlayerProfile p, Currency c, long old, long now, String reason) {
        p.balance(c, now);
        profiles.markDirty(p.uuid());
        if (old != now) Bukkit.getPluginManager().callEvent(new CurrencyChangeEvent(p.uuid(), c, old, now, reason));
    }

    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }
}
