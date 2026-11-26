package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.drill.DrillConfig.EnchantDef;
import dev.spacepunk.prison.drill.DrillConfig.FrameDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * All drill effect numbers in one place, so the listener, heat and menu agree.
 * Per-level values come from drill.yml; module multipliers are applied by {@link Effective}.
 */
public final class DrillMath {
    private DrillMath() {}

    // ---- raw per-enchant values at a level (no modules) ----

    public static double breakSpeed(DrillConfig c, FrameDef f, int bore) {
        return f.baseSpeed() + bore * c.enchant(Enchant.BORE_SPEED).p("per-level", 0.08);
    }

    public static double yieldMultiplier(DrillConfig c, int lvl) {
        return 1.0 + lvl * c.enchant(Enchant.YIELD).p("per-level", 0.025);
    }

    public static double chance(EnchantDef d, int lvl) {
        if (lvl <= 0) return 0;
        return Math.min(d.p("max-chance", 1.0), lvl * d.p("chance-per-level", 0));
    }

    public static double siphonAmountMultiplier(DrillConfig c, int lvl) {
        return 1.0 + lvl * c.enchant(Enchant.TOKEN_SIPHON).p("amount-per-level", 0.01);
    }

    public static int shapedRadius(DrillConfig c, int lvl) {
        EnchantDef d = c.enchant(Enchant.SHAPED_CHARGE);
        int every = Math.max(1, (int) d.p("radius-every", 15));
        return (int) Math.min(d.p("radius-max", 6), d.p("radius-base", 2) + lvl / every);
    }

    public static int laserLength(DrillConfig c, int lvl) {
        EnchantDef d = c.enchant(Enchant.CUTTING_LASER);
        return (int) Math.min(d.p("length-max", 40), d.p("length-base", 6) + lvl * d.p("length-per-level", 0.25));
    }

    public static double overclockSeconds(DrillConfig c, int lvl) {
        EnchantDef d = c.enchant(Enchant.OVERCLOCK);
        return d.p("duration-base", 3) + lvl * d.p("duration-per-level", 0.05);
    }

    public static double rareMultiplier(DrillConfig c, int lvl) {
        return 1.0 + lvl * c.enchant(Enchant.SALVAGE_SENSE).p("rare-per-level", 0.04);
    }

    public static double cacheChance(DrillConfig c, int lvl) {
        return lvl * c.enchant(Enchant.SALVAGE_SENSE).p("cache-chance-per-level", 0.000005);
    }

    public static double heatCapacity(DrillConfig c, FrameDef f, int coolant) {
        return c.heat.baseCapacity() + f.heatCapacity() + coolant * c.enchant(Enchant.COOLANT).p("capacity-per-level", 4);
    }

    public static double decayPerSecond(DrillConfig c, int coolant) {
        return c.heat.decayPerSecond() + coolant * c.enchant(Enchant.COOLANT).p("decay-per-level", 0.15);
    }

    /** One-line description of what the enchant does at a level, for menus. */
    public static String describe(DrillConfig c, FrameDef f, Enchant e, int lvl) {
        EnchantDef d = c.enchant(e);
        return switch (e) {
            case BORE_SPEED -> "Break speed <white>x" + fmt(breakSpeed(c, f, lvl)) + "</white>";
            case YIELD -> "Ore per block <white>x" + fmt(yieldMultiplier(c, lvl)) + "</white>";
            case TOKEN_SIPHON -> "<white>" + pct(chance(d, lvl)) + "</white> chance per block for <white>"
                    + Math.round(d.p("amount-min", 4) * siphonAmountMultiplier(c, lvl)) + "-"
                    + Math.round(d.p("amount-max", 7) * siphonAmountMultiplier(c, lvl)) + "</white> Tokens <dark_gray>(more in deeper mines)";
            case SHAPED_CHARGE -> "<white>" + pct(chance(d, lvl)) + "</white> chance, radius <white>" + shapedRadius(c, lvl) + "</white>";
            case JACKHAMMER -> "<white>" + pct(chance(d, lvl)) + "</white> chance to clear the layer";
            case CUTTING_LASER -> "<white>" + pct(chance(d, lvl)) + "</white> chance, <white>" + laserLength(c, lvl) + "</white> blocks long";
            case OVERCLOCK -> "<white>" + pct(chance(d, lvl)) + "</white> chance, <white>+" + Math.round(d.p("speed-bonus", 0.75) * 100)
                    + "%</white> speed for <white>" + fmt(overclockSeconds(c, lvl)) + "s</white>";
            case SALVAGE_SENSE -> "Rare odds <white>x" + fmt(rareMultiplier(c, lvl)) + "</white>, Token cache <white>"
                    + pct(cacheChance(c, lvl)) + "</white>";
            case COOLANT -> "Heat limit <white>" + Math.round(heatCapacity(c, f, lvl)) + "</white>, cooling <white>"
                    + fmt(decayPerSecond(c, lvl)) + "/s</white>";
        };
    }

    public static String fmt(double v) {
        if (Math.abs(v - Math.round(v)) < 0.005) return Long.toString(Math.round(v));
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0$", "");
    }

    public static String pct(double chance) {
        double p = chance * 100;
        if (p == 0) return "0%";
        if (p < 0.01) return String.format(Locale.ROOT, "%.4f%%", p);
        if (p < 1) return String.format(Locale.ROOT, "%.2f%%", p);
        return String.format(Locale.ROOT, "%.1f%%", p).replace(".0%", "%");
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "Mk I";
            case 2 -> "Mk II";
            case 3 -> "Mk III";
            case 4 -> "Mk IV";
            case 5 -> "Mk V";
            case 6 -> "Mk VI";
            default -> "Mk " + n;
        };
    }

    /** Stats a module, family bonus or combo can change, with their menu labels. */
    public static final Map<String, String> STATS = new LinkedHashMap<>();
    /** On/off effects. */
    public static final Map<String, String> FLAGS = new LinkedHashMap<>();

    static {
        STATS.put("break-speed", "break speed");
        STATS.put("yield", "ore per block");
        STATS.put("tokens", "Token finds");
        STATS.put("rare", "rare drop odds");
        STATS.put("scrap", "scrap part finds");
        STATS.put("sell", "sell price");
        STATS.put("heat-capacity", "heat limit");
        STATS.put("hand-heat", "heat from hand mining");
        STATS.put("cooling", "cooling");
        STATS.put("proc-heat", "heat from enchants");
        STATS.put("shaped-chance", "Shaped Charge chance");
        STATS.put("shaped-radius", "Shaped Charge radius");
        STATS.put("shaped-heat", "Shaped Charge heat");
        STATS.put("jack-chance", "Jackhammer chance");
        STATS.put("laser-chance", "Cutting Laser chance");
        STATS.put("laser-length", "Cutting Laser length");
        STATS.put("overclock-chance", "Overclock chance");
        STATS.put("burst", "Overclock and vent bursts");
        STATS.put("stall", "overheat stall time");
        STATS.put("cache-chance", "Token cache chance");
        FLAGS.put("auto-sell", "Auto-sells everything you mine");
    }

    /** "+50% Token finds" for a multiplier, coloured by whether it's an upside. */
    public static String effectLine(String stat, double mult, boolean pro) {
        String label = STATS.getOrDefault(stat, stat);
        double pct = (mult - 1) * 100;
        String num = (pct >= 0 ? "+" : "") + (Math.abs(pct) < 10 ? String.format(Locale.ROOT, "%.1f", pct).replace(".0", "") : Long.toString(Math.round(pct))) + "%";
        return (pro ? "<green>+ " : "<red>- ") + "<white>" + num + "</white> " + label;
    }

    /** Lore lines for a module's effects at a mark (drain = extra power on cons from neighbours). */
    public static List<String> effectLines(DrillConfig c, ModuleDef m, int mk, double drain) {
        List<String> out = new ArrayList<>();
        for (String f : m.flags()) out.add((f.startsWith("no-") ? "<red>- " : "<green>+ ") + FLAGS.getOrDefault(f, f));
        for (Map.Entry<String, Double> e : m.pros().entrySet()) out.add(effectLine(e.getKey(), Math.pow(e.getValue(), c.proPower(mk)), true));
        for (Map.Entry<String, Double> e : m.cons().entrySet()) out.add(effectLine(e.getKey(), Math.pow(e.getValue(), c.conPower(mk) * drain), false));
        return out;
    }

    /** Effective numbers for one player's drill, modules included. Cheap to build; rebuild after any change. */
    public static final class Effective {
        public final FrameDef frame;
        public final ModuleRig rig;
        public final double breakSpeed;
        public final double yield;
        public final double siphonChance;
        public final double siphonAmount;
        public final double shapedChance;
        public final int shapedRadius;
        public final double shapedHeat;
        public final double jackChance;
        public final double laserChance;
        public final int laserLength;
        public final double overclockChance;
        public final double overclockSeconds;
        public final double rareMultiplier;
        public final double scrapMultiplier;
        public final double cacheChance;
        public final double heatCapacity;
        public final double decayPerSecond;
        public final double handHeatMultiplier;
        public final double procHeatMultiplier;
        public final double tokenMultiplier;
        public final double burstMultiplier;
        public final double stallMultiplier;
        public final double sellMultiplier;
        public final boolean autoSell;
        private final Map<String, Double> mul;

        public Effective(DrillConfig c, DrillData d) {
            frame = c.frame(d.frame);
            rig = ModuleRig.build(c, d);
            mul = new HashMap<>();
            Set<String> flags = new HashSet<>();
            for (ModuleRig.Entry e : rig.entries) {
                if (!e.online) continue;
                int mk = Math.max(1, Math.min(c.maxMark, e.placed.mk));
                for (Map.Entry<String, Double> x : e.def.pros().entrySet()) apply(x.getKey(), Math.pow(x.getValue(), c.proPower(mk)));
                for (Map.Entry<String, Double> x : e.def.cons().entrySet()) apply(x.getKey(), Math.pow(x.getValue(), c.conPower(mk) * e.drain));
                flags.addAll(e.def.flags());
            }
            for (Map.Entry<String, Integer> fc : rig.familyCounts.entrySet()) {
                DrillConfig.FamilyDef fam = c.family(fc.getKey());
                if (fam == null) continue;
                for (Map.Entry<Integer, Map<String, Double>> b : fam.bonuses().entrySet()) {
                    if (fc.getValue() >= b.getKey()) b.getValue().forEach(this::apply);
                }
            }
            for (DrillConfig.ComboDef combo : rig.activeCombos) {
                combo.effects().forEach(this::apply);
                flags.removeAll(combo.clears());
            }

            breakSpeed = DrillMath.breakSpeed(c, frame, d.level(Enchant.BORE_SPEED)) * m("break-speed");
            yield = yieldMultiplier(c, d.level(Enchant.YIELD)) * m("yield");
            siphonChance = chance(c.enchant(Enchant.TOKEN_SIPHON), d.level(Enchant.TOKEN_SIPHON));
            siphonAmount = siphonAmountMultiplier(c, d.level(Enchant.TOKEN_SIPHON));
            shapedChance = Math.min(1, chance(c.enchant(Enchant.SHAPED_CHARGE), d.level(Enchant.SHAPED_CHARGE)) * m("shaped-chance"));
            shapedRadius = (int) Math.max(1, Math.min(12, Math.round(DrillMath.shapedRadius(c, d.level(Enchant.SHAPED_CHARGE)) * m("shaped-radius"))));
            procHeatMultiplier = m("proc-heat");
            shapedHeat = c.enchant(Enchant.SHAPED_CHARGE).p("heat", 6) * m("shaped-heat") * procHeatMultiplier;
            jackChance = Math.min(1, chance(c.enchant(Enchant.JACKHAMMER), d.level(Enchant.JACKHAMMER)) * m("jack-chance"));
            laserChance = Math.min(1, chance(c.enchant(Enchant.CUTTING_LASER), d.level(Enchant.CUTTING_LASER)) * m("laser-chance"));
            laserLength = (int) Math.max(1, Math.min(80, Math.round(DrillMath.laserLength(c, d.level(Enchant.CUTTING_LASER)) * m("laser-length"))));
            overclockChance = Math.min(1, chance(c.enchant(Enchant.OVERCLOCK), d.level(Enchant.OVERCLOCK)) * m("overclock-chance"));
            burstMultiplier = m("burst");
            overclockSeconds = DrillMath.overclockSeconds(c, d.level(Enchant.OVERCLOCK)) * burstMultiplier;
            rareMultiplier = DrillMath.rareMultiplier(c, d.level(Enchant.SALVAGE_SENSE)) * m("rare");
            scrapMultiplier = m("scrap");
            cacheChance = DrillMath.cacheChance(c, d.level(Enchant.SALVAGE_SENSE)) * m("cache-chance");
            heatCapacity = Math.max(10, DrillMath.heatCapacity(c, frame, d.level(Enchant.COOLANT)) * m("heat-capacity"));
            decayPerSecond = DrillMath.decayPerSecond(c, d.level(Enchant.COOLANT)) * m("cooling");
            handHeatMultiplier = m("hand-heat");
            tokenMultiplier = m("tokens");
            stallMultiplier = m("stall");
            sellMultiplier = m("sell");
            autoSell = flags.contains("auto-sell");
        }

        private void apply(String stat, double v) { mul.merge(stat, v, (a, b) -> a * b); }

        /** Combined multiplier on a stat from modules, family bonuses and combos (1.0 = none). */
        public double m(String stat) { return mul.getOrDefault(stat, 1.0); }

        /** Every changed stat, for the rig summary. */
        public Map<String, Double> multipliers() { return java.util.Collections.unmodifiableMap(mul); }
    }
}
