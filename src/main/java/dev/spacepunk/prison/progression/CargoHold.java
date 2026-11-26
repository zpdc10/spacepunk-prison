package dev.spacepunk.prison.progression;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Player-facing cargo hold actions: deposit mined ore, sell, upgrade, auto-sell. Main thread only. */
final class CargoHold {
    private final CargoStore store;
    private final ProgressionManager progression;
    private final EconomyService economy;
    private final Map<UUID, Long> lastFullWarning = new HashMap<>();

    CargoHold(CargoStore store, ProgressionManager progression, EconomyService economy) {
        this.store = store;
        this.progression = progression;
        this.economy = economy;
    }

    CargoStore store() { return store; }

    long capacity(CargoStore.Data d) { return progression.cfg().capacity(d.level); }

    /** Mining income. Auto-sells if enabled, otherwise stores what fits and warns when full. */
    void deposit(Player player, Map<Material, Long> ores) {
        UUID id = player.getUniqueId();
        CargoStore.Data d = store.get(id);
        if (d == null || ores.isEmpty()) return;
        ProgressionConfig c = progression.cfg();
        Map<String, Long> sellable = new LinkedHashMap<>();
        for (Map.Entry<Material, Long> e : ores.entrySet()) {
            String itemId = ProgressionConfig.id(e.getKey());
            if (e.getValue() != null && e.getValue() > 0 && c.price(itemId) > 0) sellable.merge(itemId, e.getValue(), Long::sum);
        }
        if (sellable.isEmpty()) return;

        if (d.autoSellUnlocked && d.autoSell) {
            progression.sellItems(player, sellable, c.autoSellRate, "auto-sell");
            return;
        }
        long cap = capacity(d);
        boolean overflow = false;
        for (Map.Entry<String, Long> e : sellable.entrySet()) {
            if (store.add(id, d, e.getKey(), e.getValue(), cap) < e.getValue()) overflow = true;
        }
        if (overflow) warnFull(player, cap);
    }

    private void warnFull(Player player, long cap) {
        long now = System.currentTimeMillis();
        Long last = lastFullWarning.get(player.getUniqueId());
        if (last != null && now - last < progression.cfg().fullWarningSeconds * 1000L) return;
        lastFullWarning.put(player.getUniqueId(), now);
        player.sendActionBar(Text.mm("<red><bold>CARGO HOLD FULL</bold></red> <gray>(" + Numbers.shortFormat(cap)
                + ") · <white>/sell</white> or upgrade with <white>/cargo</white>"));
    }

    /** Value of the hold's contents right now, with the player's multiplier (before booster events). */
    long estimatedValue(Player player, CargoStore.Data d) {
        ProgressionConfig c = progression.cfg();
        double base = 0;
        for (Map.Entry<String, Long> e : d.items.entrySet()) base += (double) c.price(e.getKey()) * e.getValue();
        return ProgressionConfig.clamp(base * progression.sellMultiplier(player.getUniqueId()));
    }

    record SellResult(long units, long credits) {}

    SellResult sellAll(Player player, String reason) {
        UUID id = player.getUniqueId();
        CargoStore.Data d = store.get(id);
        if (d == null || d.used <= 0) return new SellResult(0, 0);
        long units = d.used;
        Map<String, Long> items = store.drain(id, d);
        return new SellResult(units, progression.sellItems(player, items, 1.0, reason));
    }

    enum UpgradeResult { OK, MAXED, TOO_POOR, NOT_LOADED }

    UpgradeResult upgrade(Player player) {
        UUID id = player.getUniqueId();
        CargoStore.Data d = store.get(id);
        if (d == null) return UpgradeResult.NOT_LOADED;
        long cost = progression.cfg().cargoUpgradeCost(d.level);
        if (cost < 0) return UpgradeResult.MAXED;
        if (!economy.take(id, Currency.CREDITS, cost, "cargo-upgrade")) return UpgradeResult.TOO_POOR;
        d.level++;
        store.markDirty(id);
        return UpgradeResult.OK;
    }

    enum AutoSellResult { ENABLED, DISABLED, UNLOCKED, LOW_GRADE, TOO_POOR, NOT_LOADED }

    /** Unlocks the Auto-Seller if needed (paying for it), otherwise toggles it. */
    AutoSellResult autoSell(Player player) {
        UUID id = player.getUniqueId();
        CargoStore.Data d = store.get(id);
        if (d == null) return AutoSellResult.NOT_LOADED;
        ProgressionConfig c = progression.cfg();
        if (!d.autoSellUnlocked) {
            if (progression.grade(id) < c.autoSellGrade) return AutoSellResult.LOW_GRADE;
            if (!economy.take(id, Currency.CREDITS, c.autoSellCost, "auto-sell-unlock")) return AutoSellResult.TOO_POOR;
            d.autoSellUnlocked = true;
            d.autoSell = true;
            store.markDirty(id);
            return AutoSellResult.UNLOCKED;
        }
        d.autoSell = !d.autoSell;
        store.markDirty(id);
        return d.autoSell ? AutoSellResult.ENABLED : AutoSellResult.DISABLED;
    }

    void forget(UUID id) { lastFullWarning.remove(id); }
}
