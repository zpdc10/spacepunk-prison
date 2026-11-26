package dev.spacepunk.prison.progression;

import dev.spacepunk.prison.api.ProgressionService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Parsed progression.yml. Immutable; a reload builds a new one. */
final class ProgressionConfig {
    final long baseCost;
    final double growth;
    final Map<Integer, Long> costOverrides;
    final String nameFormat;
    final int gradesPerDeck;
    final List<String> decks;

    final int maxPrestige;
    final double costPerPrestige;
    final long shardsBase;
    final long shardsPerLevel;
    final long tokensBase;
    final double tokensGrowth;
    final List<String> prestigeCommands;
    final int confirmSeconds;

    final double bonusPerGrade;
    final double bonusPerPrestige;
    final long defaultPrice;
    /** Item id ("minecraft:copper_ore", "spacepunk:copper_ingot") -> base price. */
    final Map<String, Long> prices;

    final long cargoBaseCapacity;
    final double cargoCapacityGrowth;
    final int cargoMaxLevel;
    final long cargoUpgradeBaseCost;
    final double cargoUpgradeGrowth;
    final int fullWarningSeconds;

    final int autoSellGrade;
    final long autoSellCost;
    final double autoSellRate;

    final boolean tabPrefix;
    final String tabFormat;
    final String prestigeFormat;

    ProgressionConfig(YamlConfiguration y, Logger log) {
        baseCost = Math.max(1, y.getLong("grades.base-cost", 4000));
        growth = Math.max(1.0, y.getDouble("grades.growth", 1.45));
        costOverrides = new HashMap<>();
        ConfigurationSection ov = y.getConfigurationSection("grades.cost-overrides");
        if (ov != null) {
            for (String k : ov.getKeys(false)) {
                try {
                    costOverrides.put(Integer.parseInt(k), ov.getLong(k));
                } catch (NumberFormatException e) {
                    log.warning("progression.yml: bad grade in cost-overrides: " + k);
                }
            }
        }
        nameFormat = y.getString("grades.name-format", "Grade {grade}");
        gradesPerDeck = Math.max(1, y.getInt("grades.grades-per-deck", 5));
        decks = List.copyOf(y.getStringList("grades.decks"));

        maxPrestige = Math.max(0, y.getInt("prestige.max", 100));
        costPerPrestige = Math.max(0, y.getDouble("prestige.cost-per-prestige", 0.5));
        shardsBase = Math.max(0, y.getLong("prestige.rewards.shards-base", 10));
        shardsPerLevel = Math.max(0, y.getLong("prestige.rewards.shards-per-level", 2));
        tokensBase = Math.max(0, y.getLong("prestige.rewards.tokens-base", 25000));
        tokensGrowth = Math.max(1.0, y.getDouble("prestige.rewards.tokens-growth", 1.35));
        prestigeCommands = List.copyOf(y.getStringList("prestige.rewards.commands"));
        confirmSeconds = Math.max(5, y.getInt("prestige.confirm-seconds", 30));

        bonusPerGrade = y.getDouble("selling.bonus-per-grade", 0.02);
        bonusPerPrestige = y.getDouble("selling.bonus-per-prestige", 0.10);
        defaultPrice = Math.max(0, y.getLong("selling.default-price", 1));
        prices = new HashMap<>();
        ConfigurationSection ps = y.getConfigurationSection("selling.prices");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                String id;
                if (k.contains(":")) {
                    id = k.toLowerCase(Locale.ROOT);
                } else {
                    Material m = Material.matchMaterial(k.toUpperCase(Locale.ROOT));
                    if (m == null) {
                        log.warning("progression.yml: unknown material in selling.prices: " + k);
                        continue;
                    }
                    id = id(m);
                }
                prices.put(id, Math.max(0, ps.getLong(k)));
            }
        }

        cargoBaseCapacity = Math.max(1, y.getLong("cargo.base-capacity", 500));
        cargoCapacityGrowth = Math.max(1.0, y.getDouble("cargo.capacity-growth", 1.5));
        cargoMaxLevel = Math.max(0, y.getInt("cargo.max-level", 12));
        cargoUpgradeBaseCost = Math.max(0, y.getLong("cargo.upgrade-base-cost", 1500));
        cargoUpgradeGrowth = Math.max(1.0, y.getDouble("cargo.upgrade-growth", 2.4));
        fullWarningSeconds = Math.max(1, y.getInt("cargo.full-warning-seconds", 4));

        autoSellGrade = Math.max(1, y.getInt("auto-sell.required-grade", 8));
        autoSellCost = Math.max(0, y.getLong("auto-sell.cost", 50000));
        autoSellRate = Math.max(0, y.getDouble("auto-sell.rate", 0.9));

        tabPrefix = y.getBoolean("display.tab-prefix", true);
        tabFormat = y.getString("display.tab-format", "<dark_gray>[</dark_gray>{prestige}<gray>G{grade}</gray><dark_gray>]</dark_gray> ");
        prestigeFormat = y.getString("display.prestige-format", "<light_purple>P{prestige}</light_purple> ");
    }

    /** Credits to go from {@code grade} to {@code grade + 1} at the given prestige. -1 at max grade. */
    long gradeCost(int grade, int prestige) {
        if (grade >= ProgressionService.MAX_GRADE) return -1;
        double base = costOverrides.containsKey(grade)
                ? costOverrides.get(grade)
                : baseCost * Math.pow(growth, grade - 1);
        return clamp(base * (1 + costPerPrestige * prestige));
    }

    long price(String itemId) {
        Long p = prices.get(itemId);
        if (p != null) return p;
        // Unlisted vanilla blocks get the default price; unlisted custom items are not sellable.
        return itemId.startsWith("minecraft:") ? defaultPrice : 0;
    }

    long price(Material m) { return price(id(m)); }

    /** Item id for a vanilla material, e.g. "minecraft:copper_ore". */
    static String id(Material m) { return m.getKey().toString(); }

    long capacity(int level) { return clamp(cargoBaseCapacity * Math.pow(cargoCapacityGrowth, level)); }

    /** Credits for the next cargo level, or -1 if already maxed. */
    long cargoUpgradeCost(int level) {
        if (level >= cargoMaxLevel) return -1;
        return clamp(cargoUpgradeBaseCost * Math.pow(cargoUpgradeGrowth, level));
    }

    long shardsFor(int newPrestige) { return shardsBase + shardsPerLevel * newPrestige; }

    long tokensFor(int newPrestige) { return clamp(tokensBase * Math.pow(tokensGrowth, Math.max(0, newPrestige - 1))); }

    double sellMultiplier(int grade, int prestige) {
        return Math.max(0, 1 + bonusPerGrade * (grade - 1) + bonusPerPrestige * prestige);
    }

    String gradeName(int grade) { return nameFormat.replace("{grade}", Integer.toString(grade)); }

    int deckIndex(int grade) { return (grade - 1) / gradesPerDeck; }

    String deckName(int grade) {
        int i = deckIndex(grade);
        return i < decks.size() ? decks.get(i) : "Deck " + (i + 1);
    }

    static long clamp(double v) {
        if (Double.isNaN(v) || v <= 0) return 0;
        return v >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(v);
    }
}
