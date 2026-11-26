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
    enum Type { RELIC, CACHE, FRAGMENT, PART }

    record Tier(String id, int index, String name, String color, double weight, boolean title, boolean broadcast,
                boolean globalSound, String sound, float pitch) {
        String colored(String text) { return color + text + "</" + color.substring(1); }
        String label() { return colored(name); }
    }

    record Drop(String id, String name, Tier tier, Type type, Material material, String model, double weight,
                List<Integer> decks, String lore, String set, String moduleRarity,
                long creditsMin, long creditsMax, long tokensMin, long tokensMax, String partId, String state) {
        boolean allowedOn(int deck) { return decks.isEmpty() || decks.contains(deck); }
        String coloredName() { return tier.colored(name); }
    }

    /** A scrap part. Its three states (fried / working / pristine) are separate drop entries (and items). */
    record PartDef(String id, String name, Tier tier, Material material, double weight, List<String> sources, String lore) {
        boolean from(String source) { return sources.contains("all") || sources.contains(source); }
    }

    record Source(String id, double chance, int maxPerEvent) {}

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
    final Map<String, PartDef> parts = new LinkedHashMap<>();
    final Map<String, Source> sources = new LinkedHashMap<>();
    /** Weights of the three part states for a scrap find: fried, working, pristine. */
    double friedWeight, workingWeight, pristineWeight;
    /** Mining: blocks broken by enchants roll scrap at this share of the hand chance. */
    double scrapEnchantRate;
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
                        credits.get(0), credits.get(1), tokens.get(0), tokens.get(1), null, null));
            }
        }

        ConfigurationSection sc = y.getConfigurationSection("scrap");
        c.friedWeight = sc == null ? 80 : Math.max(0, sc.getDouble("states.fried", 80));
        c.workingWeight = sc == null ? 14 : Math.max(0, sc.getDouble("states.working", 14));
        c.pristineWeight = sc == null ? 6 : Math.max(0, sc.getDouble("states.pristine", 6));
        c.scrapEnchantRate = sc == null ? 0.02 : sc.getDouble("enchant-rate", 0.02);
        ConfigurationSection srcs = sc == null ? null : sc.getConfigurationSection("sources");
        if (srcs != null) {
            for (String id : srcs.getKeys(false)) {
                ConfigurationSection s = srcs.getConfigurationSection(id);
                if (s == null) continue;
                c.sources.put(id.toLowerCase(Locale.ROOT), new Source(id.toLowerCase(Locale.ROOT), Math.max(0, s.getDouble("chance", 0)),
                        Math.max(1, s.getInt("max-per-event", 1))));
            }
        }
        ConfigurationSection ps = y.getConfigurationSection("parts");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection d = ps.getConfigurationSection(id);
                if (d == null) continue;
                Tier tier = c.tiers.get(d.getString("tier", "common").toLowerCase(Locale.ROOT));
                if (tier == null) {
                    log.warning("raredrops.yml: part " + id + " has an unknown tier, skipped");
                    continue;
                }
                Material mat = Material.matchMaterial(d.getString("material", "PAPER"));
                if (mat == null || !mat.isItem()) mat = Material.PAPER;
                List<String> from = new ArrayList<>();
                for (String x : d.getStringList("sources")) from.add(x.toLowerCase(Locale.ROOT));
                if (from.isEmpty()) from.add("all");
                PartDef part = new PartDef(id, d.getString("name", id), tier, mat, Math.max(0, d.getDouble("weight", 10)),
                        List.copyOf(from), d.getString("lore", ""));
                c.parts.put(id, part);
                for (String state : List.of("FRIED", "WORKING", "PRISTINE")) {
                    String did = partDropId(id, state);
                    String name = switch (state) {
                        case "FRIED" -> "Fried " + part.name();
                        case "PRISTINE" -> "Pristine " + part.name();
                        default -> part.name();
                    };
                    String model = "part/" + id + (state.equals("WORKING") ? "" : "_" + state.toLowerCase(Locale.ROOT));
                    // weight 0: parts never come from the mining rare-drop table, only from scrap rolls.
                    c.drops.put(did, new Drop(did, name, tier, Type.PART, mat, model, 0, List.of(), part.lore(), null, "COMMON",
                            0, 0, 0, 0, id, state));
                }
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

    static String partDropId(String partId, String state) {
        return switch (state) {
            case "FRIED" -> "part_" + partId + "_fried";
            case "PRISTINE" -> "part_" + partId + "_pristine";
            default -> "part_" + partId;
        };
    }

    Drop partDrop(String partId, String state) { return drops.get(partDropId(partId, state)); }

    Tier tier(String id) { return id == null ? null : tiers.get(id.toLowerCase(Locale.ROOT)); }

    int fragmentsNeeded(String rarity) { return fragmentsNeeded.getOrDefault(rarity.toUpperCase(Locale.ROOT), 5); }

    /** The fragment drop for a drill module rarity, or null. */
    Drop fragmentFor(String rarity) {
        for (Drop d : drops.values()) if (d.type == Type.FRAGMENT && d.moduleRarity.equalsIgnoreCase(rarity)) return d;
        return null;
    }
}
