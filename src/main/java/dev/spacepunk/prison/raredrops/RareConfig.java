package dev.spacepunk.prison.raredrops;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** raredrops.yml: tiers, drop table, relic collections, blueprint fragments, cache rewards. */
final class RareConfig {
    enum Type { RELIC, CACHE, FRAGMENT }

    record Tier(String id, int index, String name, String color, double weight, boolean title, boolean broadcast,
                boolean globalSound, String sound, float pitch) {
        String colored(String text) { return color + text + "</" + color.substring(1); }
        String label() { return colored(name); }
    }

    record Drop(String id, String name, Tier tier, Type type, Material material, String model, double weight,
                List<Integer> decks, String lore, String set, String moduleRarity,
                long creditsMin, long creditsMax, long tokensMin, long tokensMax) {
        boolean allowedOn(int deck) { return decks.isEmpty() || decks.contains(deck); }
        String coloredName() { return tier.colored(name); }
    }

    record RelicSet(String id, String name, String color, double sellBonus, long tokens, List<String> relics) {
        String coloredName() { return color + name + "</" + color.substring(1); }
    }

    double baseChance;
    double enchantRate;
    int maxPerEvent;
    boolean useItemModels;
    double cachePerGrade;
    double cachePerPrestige;
    final Map<String, Tier> tiers = new LinkedHashMap<>();
    final Map<String, Drop> drops = new LinkedHashMap<>();
    final Map<String, RelicSet> sets = new LinkedHashMap<>();
    /** Drill module rarity (upper case) -> fragments needed to assemble one module. */
    final Map<String, Integer> fragmentsNeeded = new LinkedHashMap<>();

    static RareConfig load(YamlConfiguration y, Logger log) {
        RareConfig c = new RareConfig();
        c.baseChance = y.getDouble("roll.base-chance", 0.002);
        c.enchantRate = y.getDouble("roll.enchant-rate", 0.15);
        c.maxPerEvent = Math.max(1, y.getInt("roll.max-per-event", 3));
        c.useItemModels = y.getBoolean("use-item-models", true);
        c.cachePerGrade = y.getDouble("caches.per-grade", 1.26);
        c.cachePerPrestige = y.getDouble("caches.per-prestige", 0.5);

        ConfigurationSection ts = y.getConfigurationSection("tiers");
        if (ts != null) {
            int i = 0;
            for (String id : ts.getKeys(false)) {
                ConfigurationSection t = ts.getConfigurationSection(id);
                if (t == null) continue;
                c.tiers.put(id.toLowerCase(Locale.ROOT), new Tier(id.toLowerCase(Locale.ROOT), i++, t.getString("name", id),
                        t.getString("color", "<gray>"), t.getDouble("weight", 1), t.getBoolean("title", false),
                        t.getBoolean("broadcast", false), t.getBoolean("global-sound", false),
                        t.getString("sound", "entity.item.pickup"), (float) t.getDouble("pitch", 1.0)));
            }
        }
        if (c.tiers.isEmpty()) throw new IllegalStateException("raredrops.yml has no tiers");

        ConfigurationSection fs = y.getConfigurationSection("fragments");
        if (fs != null) for (String k : fs.getKeys(false)) c.fragmentsNeeded.put(k.toUpperCase(Locale.ROOT), Math.max(1, fs.getInt(k)));

        ConfigurationSection ds = y.getConfigurationSection("drops");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection d = ds.getConfigurationSection(id);
                if (d == null) continue;
                Tier tier = c.tiers.get(d.getString("tier", "").toLowerCase(Locale.ROOT));
                if (tier == null) {
                    log.warning("raredrops.yml: drop " + id + " has an unknown tier, skipped");
                    continue;
                }
                Type type;
                try {
                    type = Type.valueOf(d.getString("type", "RELIC").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    log.warning("raredrops.yml: drop " + id + " has an unknown type, skipped");
                    continue;
                }
                Material mat = Material.matchMaterial(d.getString("material", "PAPER"));
                if (mat == null || !mat.isItem()) mat = Material.PAPER;
                List<Long> credits = longs(d.getLongList("credits"));
                List<Long> tokens = longs(d.getLongList("tokens"));
                c.drops.put(id, new Drop(id, d.getString("name", id), tier, type, mat, d.getString("model", id),
                        d.getDouble("weight", 1), List.copyOf(d.getIntegerList("decks")), d.getString("lore", ""),
                        d.getString("set", null), d.getString("module-rarity", "COMMON").toUpperCase(Locale.ROOT),
                        credits.get(0), credits.get(1), tokens.get(0), tokens.get(1)));
            }
        }

        ConfigurationSection ss = y.getConfigurationSection("sets");
        if (ss != null) {
            for (String id : ss.getKeys(false)) {
                ConfigurationSection s = ss.getConfigurationSection(id);
                if (s == null) continue;
                List<String> relics = new ArrayList<>();
                for (Drop d : c.drops.values()) if (d.type == Type.RELIC && id.equals(d.set)) relics.add(d.id);
                c.sets.put(id, new RelicSet(id, s.getString("name", id), s.getString("color", "<gray>"),
                        s.getDouble("sell-bonus", 0), s.getLong("tokens", 0), Collections.unmodifiableList(relics)));
            }
        }
        return c;
    }

    private static List<Long> longs(List<Long> in) {
        long a = in.isEmpty() ? 0 : Math.max(0, in.get(0));
        long b = in.size() < 2 ? a : Math.max(a, in.get(1));
        return List.of(a, b);
    }

    Drop drop(String id) { return id == null ? null : drops.get(id); }

    Tier tier(String id) { return id == null ? null : tiers.get(id.toLowerCase(Locale.ROOT)); }

    int fragmentsNeeded(String rarity) { return fragmentsNeeded.getOrDefault(rarity.toUpperCase(Locale.ROOT), 5); }

    /** The fragment drop for a drill module rarity, or null. */
    Drop fragmentFor(String rarity) {
        for (Drop d : drops.values()) if (d.type == Type.FRAGMENT && d.moduleRarity.equalsIgnoreCase(rarity)) return d;
        return null;
    }
}
