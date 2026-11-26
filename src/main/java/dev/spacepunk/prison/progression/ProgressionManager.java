package dev.spacepunk.prison.progression;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.event.GradeChangeEvent;
import dev.spacepunk.prison.api.event.OreSellEvent;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Rules for grades, prestige and selling. Main thread only. */
final class ProgressionManager implements ProgressionService {
    private final ProfileService profiles;
    private final EconomyService economy;
    private final CargoStore cargo;
    private final Supplier<ProgressionConfig> config;

    ProgressionManager(ProfileService profiles, EconomyService economy, CargoStore cargo, Supplier<ProgressionConfig> config) {
        this.profiles = profiles;
        this.economy = economy;
        this.cargo = cargo;
        this.config = config;
    }

    ProgressionConfig cfg() { return config.get(); }

    // ---- ProgressionService ----

    @Override public int grade(UUID player) { return profiles.get(player).map(PlayerProfile::grade).orElse(1); }

    @Override public int prestige(UUID player) { return profiles.get(player).map(PlayerProfile::prestige).orElse(0); }

    @Override public long nextGradeCost(UUID player) { return cfg().gradeCost(grade(player), prestige(player)); }

    @Override public double sellMultiplier(UUID player) { return cfg().sellMultiplier(grade(player), prestige(player)); }

    @Override public long sellPrice(Material material) { return cfg().price(material); }

    @Override public long sellPrice(String itemId) { return cfg().price(itemId.toLowerCase(Locale.ROOT)); }

    @Override
    public long sell(Player player, Map<Material, Long> ores, double extraMultiplier, String reason) {
        Map<String, Long> items = new LinkedHashMap<>();
        ores.forEach((m, n) -> { if (n != null && n > 0) items.merge(ProgressionConfig.id(m), n, Long::sum); });
        return sellItems(player, items, extraMultiplier, reason);
    }

    @Override
    public long sellItems(Player player, Map<String, Long> items, double extraMultiplier, String reason) {
        if (items.isEmpty() || profiles.get(player.getUniqueId()).isEmpty()) return 0;
        ProgressionConfig c = cfg();
        double base = 0;
        for (Map.Entry<String, Long> e : items.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) base += (double) c.price(e.getKey()) * e.getValue();
        }
        long baseValue = ProgressionConfig.clamp(base);
        if (baseValue <= 0) return 0;
        OreSellEvent ev = new OreSellEvent(player, items, baseValue,
                sellMultiplier(player.getUniqueId()) * Math.max(0, extraMultiplier), reason);
        Bukkit.getPluginManager().callEvent(ev);
        long paid = ProgressionConfig.clamp(baseValue * ev.multiplier());
        if (paid > 0) economy.give(player.getUniqueId(), Currency.CREDITS, paid, "sell:" + reason);
        return paid;
    }

    // ---- Cargo hold API ----

    @Override public Map<String, Long> cargoContents(UUID player) {
        CargoStore.Data d = cargo.get(player);
        return d == null ? Map.of() : Map.copyOf(d.items);
    }

    @Override public long cargoAmount(UUID player, String itemId) {
        CargoStore.Data d = cargo.get(player);
        return d == null ? 0 : d.items.getOrDefault(itemId.toLowerCase(Locale.ROOT), 0L);
    }

    @Override public long cargoSpace(UUID player) {
        CargoStore.Data d = cargo.get(player);
        return d == null ? 0 : Math.max(0, cfg().capacity(d.level) - d.used);
    }

    @Override public long cargoDeposit(UUID player, String itemId, long amount) {
        CargoStore.Data d = cargo.get(player);
        if (d == null || amount <= 0) return 0;
        return cargo.add(player, d, itemId.toLowerCase(Locale.ROOT), amount, cfg().capacity(d.level));
    }

    @Override public long cargoWithdraw(UUID player, String itemId, long amount) {
        CargoStore.Data d = cargo.get(player);
        if (d == null) return 0;
        return cargo.remove(player, d, itemId.toLowerCase(Locale.ROOT), amount);
    }

    /**
     * Counts a Credits gain toward this run's earnings if its reason is server income (see
     * prestige.rewards.income-reasons). Player-to-player money (pay, trades, auctions, gang bank) never matches.
     */
    void recordIncome(UUID player, long gained, String reason) {
        if (gained <= 0 || reason == null || !cfg().isServerIncome(reason)) return;
        CargoStore.Data d = cargo.get(player);
        if (d == null) return;
        d.runEarnings = ProgressionConfig.clamp((double) d.runEarnings + gained);
        cargo.markDirty(player);
    }

    /** Credits earned from the server (not other players) since the last prestige. */
    long runEarnings(UUID player) {
        CargoStore.Data d = cargo.get(player);
        return d == null ? 0 : d.runEarnings;
    }

    // ---- Grades ----

    enum RankUpResult { OK, MAX_GRADE, TOO_POOR, NOT_LOADED }

    RankUpResult rankUp(Player player) {
        UUID id = player.getUniqueId();
        PlayerProfile p = profiles.get(id).orElse(null);
        if (p == null) return RankUpResult.NOT_LOADED;
        if (p.grade() >= MAX_GRADE) return RankUpResult.MAX_GRADE;
        long cost = cfg().gradeCost(p.grade(), p.prestige());
        if (!economy.take(id, Currency.CREDITS, cost, "rankup")) return RankUpResult.TOO_POOR;
        setGrade(player, p, p.grade() + 1, p.prestige());
        return RankUpResult.OK;
    }

    /** Ranks up as many times as the player can afford. Returns grades gained. */
    int rankUpMax(Player player) {
        UUID id = player.getUniqueId();
        PlayerProfile p = profiles.get(id).orElse(null);
        if (p == null) return 0;
        int start = p.grade();
        int g = start;
        while (g < MAX_GRADE) {
            long cost = cfg().gradeCost(g, p.prestige());
            if (!economy.take(id, Currency.CREDITS, cost, "rankup")) break;
            g++;
        }
        if (g > start) setGrade(player, p, g, p.prestige());
        return g - start;
    }

    // ---- Prestige ----

    enum PrestigeResult { OK, NOT_MAX_GRADE, MAX_PRESTIGE, NOT_LOADED }

    PrestigeResult canPrestige(Player player) {
        PlayerProfile p = profiles.get(player.getUniqueId()).orElse(null);
        if (p == null) return PrestigeResult.NOT_LOADED;
        if (p.prestige() >= cfg().maxPrestige) return PrestigeResult.MAX_PRESTIGE;
        if (p.grade() < MAX_GRADE) return PrestigeResult.NOT_MAX_GRADE;
        return PrestigeResult.OK;
    }

    /** Grade back to 1, Credits and cargo contents cleared, Tokens and Shards granted. Drill, Tokens, Shards and cargo upgrades are kept. */
    PrestigeResult prestige(Player player) {
        PrestigeResult r = canPrestige(player);
        if (r != PrestigeResult.OK) return r;
        UUID id = player.getUniqueId();
        PlayerProfile p = profiles.require(id);
        int newPrestige = p.prestige() + 1;
        long credits = economy.balance(id, Currency.CREDITS);
        if (credits > 0) economy.take(id, Currency.CREDITS, credits, "prestige");
        CargoStore.Data d = cargo.get(id);
        long earned = d == null ? 0 : d.runEarnings;
        if (d != null) {
            cargo.drain(id, d);
            d.runEarnings = 0;
        }
        ProgressionConfig c = cfg();
        long shards = c.shardsFor(newPrestige);
        if (shards > 0) economy.give(id, Currency.SHARDS, shards, "prestige");
        long tokens = c.tokensFor(newPrestige, earned);
        if (tokens > 0) economy.give(id, Currency.TOKENS, tokens, "prestige");
        setGrade(player, p, 1, newPrestige);
        for (String cmd : c.prestigeCommands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("{player}", player.getName()).replace("{prestige}", Integer.toString(newPrestige)));
        }
        return PrestigeResult.OK;
    }

    // ---- Shared ----

    /** Sets grade and prestige, saves, and fires GradeChangeEvent. Also used by admin commands. */
    void setGrade(Player player, PlayerProfile p, int grade, int prestige) {
        int og = p.grade(), op = p.prestige();
        int ng = Math.clamp(grade, 1, MAX_GRADE);
        int np = Math.max(0, prestige);
        if (og == ng && op == np) return;
        p.grade(ng);
        p.prestige(np);
        profiles.markDirty(p.uuid());
        Bukkit.getPluginManager().callEvent(new GradeChangeEvent(player, og, ng, op, np));
    }

    String gradeLabel(int grade) {
        return "<white>" + cfg().gradeName(grade) + "</white> <dark_gray>·</dark_gray> " + cfg().deckName(grade) + "<reset>";
    }

    String tabPrefix(PlayerProfile p) {
        ProgressionConfig c = cfg();
        String pres = p.prestige() > 0 ? c.prestigeFormat.replace("{prestige}", Integer.toString(p.prestige())) : "";
        return c.tabFormat.replace("{prestige}", pres).replace("{grade}", Integer.toString(p.grade()));
    }

    void refreshTab(Player player) {
        ProgressionConfig c = cfg();
        if (!c.tabPrefix) {
            player.playerListName(null);
            return;
        }
        profiles.get(player.getUniqueId()).ifPresent(p ->
                player.playerListName(Text.mm(tabPrefix(p)).append(net.kyori.adventure.text.Component.text(player.getName()))));
    }
}
