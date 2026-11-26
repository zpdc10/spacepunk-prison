package dev.spacepunk.prison.workshop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.logging.Logger;

/** Parsed workshop.yml. Immutable; replaced as a whole on reload. */
final class WorkshopConfig {
    static final String NS = "spacepunk:";

    enum Role { SOURCE, PROCESSOR, GENERATOR, PUMP, RADIATOR, DEPOT, CHUTE }

    /** Pipeline step a good comes out of; used for sorting and menu colours. */
    enum Stage { RAW, CRUSHED, WASHED, REFINED, ALLOY, COMPONENT, BYPRODUCT }

    record BayLevel(int size, int height, int slots, long locker, long credits, long shards, int grade) {
        int half() { return size / 2; }
    }

    record Shell(Material floor, Material floorAccent, Material wall, Material pillar, Material window,
                 Material wallTop, Material ceiling, Material light, Material terminal) {
        boolean isShell(Material m) {
            return m == floor || m == floorAccent || m == wall || m == pillar || m == window || m == wallTop
                    || m == ceiling || m == light || m == terminal;
        }
    }

    record Tier(String name, double speed, double power, double heat, double refined, double pristine,
                double costMultiplier, Map<String, Long> components) {}

    record MachineDef(String id, String name, Role role, Material icon, long cost, int grade, double power,
                      double heat, double speed, double coolant, double markup, boolean quality, String lore) {
        String model() { return "workshop/machine/" + id; }
    }

    /** One craft: consumes inputs, produces outputs (fractional amounts are rolled), plus an optional byproduct. */
    record Recipe(String id, String machine, Map<String, Integer> inputs, String output, double outputAmount,
                  double time) {}

    record Good(String id, String name, double price, Stage stage) {
        /** Model key under spacepunk:, e.g. workshop/goods/copper_ingot. */
        String model() { return "workshop/goods/" + id.substring(NS.length()); }
    }

    record Byproduct(String id, double chance) {}

    record Quality(String prefix, double price) {}

    /** A drill module recipe; id = the drill module id. parts = scrap parts (working or better) from the inventory. */
    record Assembly(String id, String name, int grade, long credits, Map<String, Long> components, Map<String, Integer> parts) {}

    record RefurbLevel(String name, double pristine, long credits, int grade, Map<String, Long> components) {}

    record RefurbCost(long credits, Map<String, Long> components) {}

    record Decor(Material material, int amount, long price) {}

    // ---- values ----
    final String worldName;
    final int floorY;
    final int spacing;
    final int gridWidth;
    final List<BayLevel> levels;
    final Shell shell;
    final List<Tier> tiers;
    final Map<String, MachineDef> machines;
    final double costGrowth;
    final double heatMax, heatResume, baseCooling, neighbourHeat;
    final double transfer;
    final int maxConveyor, inputBuffer, outputBuffer, conveyorBundle;
    final long conveyorPrice;
    final Map<String, Double> fuels;
    final Map<String, Good> goods;
    /** machine id -> recipes in priority order. */
    final Map<String, List<Recipe>> recipes;
    final Map<String, Byproduct> byproducts;
    final List<Quality> qualities;
    final double demandMin, demandMax, priorityBonus, saturationCredits, minFactor, chuteRate;
    /** Token finds while the owner is online and the bay is processing: one roll per tokenRollSeconds. */
    final long tokenRollSeconds, tokenMin, tokenMax;
    final double tokenChance;
    final int priorityCount;
    final ZoneId zone;
    final double offlineHours, offlineEfficiency;
    final Map<String, Assembly> assembly;
    final int refurbGrade;
    final List<RefurbLevel> refurbLevels;
    /** Part tier id (common..legendary) -> cost to repair one fried part. */
    final Map<String, RefurbCost> refurbCosts;
    final List<Decor> decor;
    final float yawOffset;
    final boolean hud;

    private WorkshopConfig(YamlConfiguration y, ToLongFunction<String> rawPrice, Logger log) {
        worldName = y.getString("world.name", "workshop");
        floorY = y.getInt("world.floor-y", 64);
        spacing = Math.max(64, y.getInt("world.spacing", 128));
        gridWidth = Math.max(1, y.getInt("world.grid-width", 32));

        List<BayLevel> lv = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("bay-levels")) {
            int size = num(m, "size", 11).intValue();
            if (size % 2 == 0) size++;
            lv.add(new BayLevel(size, num(m, "height", 6).intValue(), num(m, "slots", 6).intValue(),
                    num(m, "locker", 2000).longValue(), num(m, "credits", 0).longValue(),
                    num(m, "shards", 0).longValue(), num(m, "grade", 1).intValue()));
        }
        if (lv.isEmpty()) lv.add(new BayLevel(11, 6, 6, 2000, 0, 0, 1));
        levels = List.copyOf(lv);

        shell = new Shell(mat(y, "shell.floor", Material.POLISHED_DEEPSLATE, log),
                mat(y, "shell.floor-accent", Material.DEEPSLATE, log),
                mat(y, "shell.wall", Material.WAXED_WEATHERED_CUT_COPPER, log),
                mat(y, "shell.pillar", Material.POLISHED_BLACKSTONE_BRICKS, log),
                mat(y, "shell.window", Material.TINTED_GLASS, log),
                mat(y, "shell.wall-top", Material.POLISHED_BLACKSTONE, log),
                mat(y, "shell.ceiling", Material.DEEPSLATE, log),
                mat(y, "shell.light", Material.SEA_LANTERN, log),
                mat(y, "shell.terminal", Material.LODESTONE, log));

        List<Tier> tl = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("tiers")) {
            Map<String, Long> comps = new LinkedHashMap<>();
            if (m.get("components") instanceof Map<?, ?> cm) {
                cm.forEach((k, v) -> comps.put(goodId(String.valueOf(k)), ((Number) v).longValue()));
            }
            tl.add(new Tier(String.valueOf(m.get("name") == null ? "Mk " + (tl.size() + 1) : m.get("name")),
                    num(m, "speed", 1).doubleValue(), num(m, "power", 1).doubleValue(), num(m, "heat", 1).doubleValue(),
                    num(m, "refined", 0).doubleValue(), num(m, "pristine", 0).doubleValue(),
                    num(m, "cost", 0).doubleValue(), Map.copyOf(comps)));
        }
        if (tl.isEmpty()) tl.add(new Tier("Mk I", 1, 1, 1, 0, 0, 0, Map.of()));
        tiers = List.copyOf(tl);

        Map<String, MachineDef> md = new LinkedHashMap<>();
        ConfigurationSection ms = y.getConfigurationSection("machines");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(id);
                if (s == null) continue;
                Role role;
                try {
                    role = Role.valueOf(s.getString("role", "processor").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    log.warning("workshop.yml: machine " + id + " has unknown role " + s.getString("role"));
                    continue;
                }
                md.put(id, new MachineDef(id, s.getString("name", id), role, mat(s, "icon", Material.IRON_BLOCK, log),
                        s.getLong("cost", 1000), s.getInt("grade", 1), s.getDouble("power", 0), s.getDouble("heat", 0),
                        s.getDouble("speed", 1), s.getDouble("coolant", 0), s.getDouble("markup", 1.0),
                        s.getBoolean("quality", false), s.getString("lore", "")));
            }
        }
        machines = Collections.unmodifiableMap(md);
        costGrowth = y.getDouble("cost-growth", 1.35);

        heatMax = y.getDouble("heat.max", 100);
        heatResume = y.getDouble("heat.resume", 25);
        baseCooling = y.getDouble("heat.base-cooling", 3);
        neighbourHeat = y.getDouble("heat.neighbour-heat", 0.35);

        transfer = y.getDouble("logistics.transfer", 4);
        maxConveyor = y.getInt("logistics.max-conveyor", 48);
        inputBuffer = y.getInt("logistics.input-buffer", 32);
        outputBuffer = y.getInt("logistics.output-buffer", 64);
        conveyorBundle = Math.max(1, Math.min(64, y.getInt("logistics.conveyor-bundle", 16)));
        conveyorPrice = y.getLong("logistics.conveyor-price", 800);

        Map<String, Double> fu = new LinkedHashMap<>();
        ConfigurationSection fs = y.getConfigurationSection("fuels");
        if (fs != null) for (String k : fs.getKeys(false)) fu.put(anyId(k), fs.getDouble(k));
        fuels = Collections.unmodifiableMap(fu);

        // ---- goods and recipes ----
        Map<String, Good> gd = new LinkedHashMap<>();
        Map<String, List<Recipe>> rc = new LinkedHashMap<>();
        ConfigurationSection goodSec = y.getConfigurationSection("goods");
        Map<String, Double> fixedPrice = new LinkedHashMap<>();
        Map<String, String> fixedName = new LinkedHashMap<>();
        if (goodSec != null) {
            for (String k : goodSec.getKeys(false)) {
                String id = goodId(k);
                if (goodSec.isSet(k + ".price")) fixedPrice.put(id, goodSec.getDouble(k + ".price"));
                fixedName.put(id, goodSec.getString(k + ".name", k));
            }
        }
        Map<String, Byproduct> bp = new LinkedHashMap<>();
        ConfigurationSection bs = y.getConfigurationSection("byproducts");
        if (bs != null) {
            for (String mach : bs.getKeys(false)) {
                String id = goodId(bs.getString(mach + ".id", "slag_dust"));
                bp.put(mach, new Byproduct(id, bs.getDouble(mach + ".chance", 0)));
                gd.putIfAbsent(id, new Good(id, fixedName.getOrDefault(id, id), fixedPrice.getOrDefault(id, 1.0), Stage.BYPRODUCT));
            }
        }
        byproducts = Collections.unmodifiableMap(bp);

        double crushMk = markup(md, "crusher"), washMk = markup(md, "washer"), smeltMk = markup(md, "furnace");
        double crushT = y.getDouble("step-time.crusher", 1.0), washT = y.getDouble("step-time.washer", 1.25),
                smeltT = y.getDouble("step-time.furnace", 1.5);
        ConfigurationSection fam = y.getConfigurationSection("families");
        if (fam != null) {
            for (String f : fam.getKeys(false)) {
                ConfigurationSection s = fam.getConfigurationSection(f);
                if (s == null) continue;
                double value = Math.max(0.01, s.getDouble("value", 1));
                String crushed = s.isSet("crushed.id") ? goodId(s.getString("crushed.id")) : null;
                String washed = s.isSet("washed.id") ? goodId(s.getString("washed.id")) : null;
                String refined = s.isSet("refined.id") ? goodId(s.getString("refined.id")) : null;
                if (crushed == null) continue;
                double pCrushed = value * crushMk;
                putGood(gd, crushed, s.getString("crushed.name", crushed), fixedPrice.getOrDefault(crushed, pCrushed), Stage.CRUSHED);
                for (String ore : s.getStringList("ores")) {
                    String oreId = anyId(ore);
                    long price = rawPrice.applyAsLong(oreId);
                    if (price <= 0) {
                        log.warning("workshop.yml: family " + f + " ore " + oreId + " has no sell price; skipped");
                        continue;
                    }
                    add(rc, new Recipe("crush:" + oreId, "crusher", Map.of(oreId, 1), crushed, price / value, crushT));
                }
                if (washed != null) {
                    double pWashed = pCrushed * washMk;
                    putGood(gd, washed, s.getString("washed.name", washed), fixedPrice.getOrDefault(washed, pWashed), Stage.WASHED);
                    add(rc, new Recipe("wash:" + crushed, "washer", Map.of(crushed, 1), washed, 1, washT));
                    if (refined != null) {
                        double pRefined = pWashed * smeltMk;
                        putGood(gd, refined, s.getString("refined.name", refined), fixedPrice.getOrDefault(refined, pRefined), Stage.REFINED);
                        add(rc, new Recipe("smelt:" + washed, "furnace", Map.of(washed, 1), refined, 1, smeltT));
                    }
                }
            }
        }
        // Explicit forge / fabricator recipes, priced from their inputs.
        ConfigurationSection rs = y.getConfigurationSection("recipes");
        if (rs != null) {
            for (String mach : rs.getKeys(false)) {
                ConfigurationSection msec = rs.getConfigurationSection(mach);
                if (msec == null) continue;
                Stage stage = "fabricator".equals(mach) ? Stage.COMPONENT : Stage.ALLOY;
                for (String out : msec.getKeys(false)) {
                    ConfigurationSection r = msec.getConfigurationSection(out);
                    if (r == null) continue;
                    Map<String, Integer> in = new LinkedHashMap<>();
                    ConfigurationSection is = r.getConfigurationSection("inputs");
                    if (is != null) for (String k : is.getKeys(false)) in.put(goodId(k), Math.max(1, is.getInt(k)));
                    if (in.isEmpty()) continue;
                    String outId = goodId(out);
                    int count = Math.max(1, r.getInt("count", 1));
                    double inputValue = 0;
                    boolean known = true;
                    for (Map.Entry<String, Integer> e : in.entrySet()) {
                        Good g = gd.get(e.getKey());
                        if (g == null) {
                            known = false;
                            log.warning("workshop.yml: recipe " + out + " needs unknown good " + e.getKey() + " (define it earlier)");
                            break;
                        }
                        inputValue += g.price() * e.getValue();
                    }
                    if (!known) continue;
                    double price = fixedPrice.getOrDefault(outId, inputValue * markup(md, mach) / count);
                    putGood(gd, outId, r.getString("name", fixedName.getOrDefault(outId, out)), price, stage);
                    add(rc, new Recipe(mach + ":" + out, mach, Map.copyOf(in), outId, count, r.getDouble("time", 4)));
                }
            }
        }
        // Remaining fixed goods (names/prices only).
        fixedName.forEach((id, name) -> gd.putIfAbsent(id, new Good(id, name, fixedPrice.getOrDefault(id, 1.0), Stage.BYPRODUCT)));
        goods = Collections.unmodifiableMap(gd);
        rc.replaceAll((k, v) -> List.copyOf(v));
        recipes = Collections.unmodifiableMap(rc);

        List<Quality> q = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("quality")) {
            q.add(new Quality(String.valueOf(m.get("name") == null ? "" : m.get("name")), num(m, "price", 1).doubleValue()));
        }
        if (q.isEmpty()) q.add(new Quality("", 1));
        qualities = List.copyOf(q);

        demandMin = y.getDouble("market.demand-min", 0.7);
        demandMax = Math.max(demandMin, y.getDouble("market.demand-max", 1.4));
        priorityCount = y.getInt("market.priority-count", 3);
        priorityBonus = y.getDouble("market.priority-bonus", 1.5);
        saturationCredits = Math.max(1, y.getDouble("market.saturation-credits", 3_000_000));
        minFactor = y.getDouble("market.min-factor", 0.4);
        chuteRate = y.getDouble("market.chute-rate", 0.9);
        tokenRollSeconds = Math.max(5, y.getLong("tokens.roll-seconds", 60));
        tokenChance = Math.max(0, y.getDouble("tokens.chance", 0.06));
        tokenMin = Math.max(0, y.getLong("tokens.min", 4));
        tokenMax = Math.max(tokenMin, y.getLong("tokens.max", 7));
        ZoneId z;
        try {
            z = ZoneId.of(y.getString("market.timezone", "UTC"));
        } catch (Exception e) {
            log.warning("workshop.yml: bad market.timezone, using UTC");
            z = ZoneId.of("UTC");
        }
        zone = z;

        offlineHours = Math.max(0, y.getDouble("offline.max-hours", 4));
        offlineEfficiency = Math.max(0, Math.min(1, y.getDouble("offline.efficiency", 0.5)));

        Map<String, Assembly> as = new LinkedHashMap<>();
        ConfigurationSection asec = y.getConfigurationSection("assembly");
        if (asec != null) {
            for (String id : asec.getKeys(false)) {
                ConfigurationSection s = asec.getConfigurationSection(id);
                if (s == null) continue;
                Map<String, Long> comps = new LinkedHashMap<>();
                ConfigurationSection cs = s.getConfigurationSection("components");
                if (cs != null) for (String k : cs.getKeys(false)) comps.put(goodId(k), cs.getLong(k));
                Map<String, Integer> parts = new LinkedHashMap<>();
                ConfigurationSection pcs = s.getConfigurationSection("parts");
                if (pcs != null) for (String k : pcs.getKeys(false)) parts.put(k, Math.max(0, pcs.getInt(k)));
                as.put(id, new Assembly(id, s.getString("name", id), s.getInt("grade", 1), s.getLong("credits", 0),
                        Collections.unmodifiableMap(comps), Collections.unmodifiableMap(parts)));
            }
        }
        assembly = Collections.unmodifiableMap(as);

        refurbGrade = Math.max(1, y.getInt("refurbisher.grade", 1));
        List<RefurbLevel> rl = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("refurbisher.levels")) {
            rl.add(new RefurbLevel(String.valueOf(m.get("name") == null ? "Mk " + (rl.size() + 1) : m.get("name")),
                    num(m, "pristine", 0.05).doubleValue(), num(m, "credits", 0).longValue(), num(m, "grade", 1).intValue(), goods(m.get("components"))));
        }
        if (rl.isEmpty()) rl.add(new RefurbLevel("Mk I", 0.05, 0, 1, Map.of()));
        refurbLevels = List.copyOf(rl);
        Map<String, RefurbCost> rc = new LinkedHashMap<>();
        ConfigurationSection rcs = y.getConfigurationSection("refurbisher.costs");
        if (rcs != null) {
            for (String tier : rcs.getKeys(false)) {
                ConfigurationSection t = rcs.getConfigurationSection(tier);
                if (t == null) continue;
                Map<String, Long> comps = new LinkedHashMap<>();
                ConfigurationSection cs = t.getConfigurationSection("components");
                if (cs != null) for (String k : cs.getKeys(false)) comps.put(goodId(k), cs.getLong(k));
                rc.put(tier.toLowerCase(Locale.ROOT), new RefurbCost(t.getLong("credits", 0), Collections.unmodifiableMap(comps)));
            }
        }
        refurbCosts = Collections.unmodifiableMap(rc);

        List<Decor> dl = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("decor")) {
            Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
            if (mat == null || !mat.isItem()) {
                log.warning("workshop.yml: unknown decor material " + m.get("material"));
                continue;
            }
            dl.add(new Decor(mat, Math.max(1, Math.min(64, num(m, "amount", 16).intValue())), num(m, "price", 100).longValue()));
        }
        decor = List.copyOf(dl);

        yawOffset = (float) y.getDouble("display.yaw-offset", 180);
        hud = y.getBoolean("display.hud", true);
    }

    static WorkshopConfig load(YamlConfiguration y, ToLongFunction<String> rawPrice, Logger log) {
        return new WorkshopConfig(y, rawPrice, log);
    }

    // ---- lookups ----

    BayLevel level(int level) { return levels.get(Math.max(0, Math.min(levels.size() - 1, level))); }

    int maxLevel() { return levels.size() - 1; }

    Tier tier(int tier) { return tiers.get(Math.max(0, Math.min(tiers.size() - 1, tier))); }

    MachineDef machine(String id) { return machines.get(id); }

    List<Recipe> recipesFor(String machine) { return recipes.getOrDefault(machine, List.of()); }

    Quality quality(int q) { return qualities.get(Math.max(0, Math.min(qualities.size() - 1, q))); }

    boolean isFuel(String baseId) { return fuels.containsKey(baseId); }

    /** True if any recipe of this machine consumes the item (base id, quality stripped). */
    boolean consumes(String machine, String baseId) {
        for (Recipe r : recipesFor(machine)) if (r.inputs().containsKey(baseId)) return true;
        return false;
    }

    // ---- id helpers ----

    /** "copper_ingot" -> "spacepunk:copper_ingot"; namespaced ids stay. */
    private static Map<String, Long> goods(Object o) {
        Map<String, Long> comps = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> cm) {
            cm.forEach((k, v) -> { if (v instanceof Number n) comps.put(goodId(String.valueOf(k)), n.longValue()); });
        }
        return Collections.unmodifiableMap(comps);
    }

    static String goodId(String s) {
        String k = s.trim().toLowerCase(Locale.ROOT);
        return k.contains(":") ? k : NS + k;
    }

    /** "copper_ore" -> "minecraft:copper_ore"; namespaced ids stay. */
    static String anyId(String s) {
        String k = s.trim().toLowerCase(Locale.ROOT);
        return k.contains(":") ? k : "minecraft:" + k;
    }

    // ---- parsing helpers ----

    private static void putGood(Map<String, Good> gd, String id, String name, double price, Stage stage) {
        gd.put(id, new Good(id, name, price, stage));
    }

    private static void add(Map<String, List<Recipe>> rc, Recipe r) {
        rc.computeIfAbsent(r.machine(), k -> new ArrayList<>()).add(r);
    }

    private static double markup(Map<String, MachineDef> md, String id) {
        MachineDef d = md.get(id);
        return d == null ? 1.0 : d.markup();
    }

    private static Number num(Map<?, ?> m, String key, Number def) {
        Object o = m.get(key);
        return o instanceof Number n ? n : def;
    }

    private static Material mat(ConfigurationSection s, String path, Material def, Logger log) {
        String name = s.getString(path);
        if (name == null) return def;
        Material m = Material.matchMaterial(name);
        if (m == null) {
            log.warning("workshop.yml: unknown material " + name + " at " + path + ", using " + def);
            return def;
        }
        return m;
    }
}
