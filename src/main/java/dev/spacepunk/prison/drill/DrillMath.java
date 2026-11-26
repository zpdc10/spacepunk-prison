package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.drill.DrillConfig.EnchantDef;
import dev.spacepunk.prison.drill.DrillConfig.FrameDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                    + Math.round(d.p("amount-min", 2) * siphonAmountMultiplier(c, lvl)) + "-"
                    + Math.round(d.p("amount-max", 5) * siphonAmountMultiplier(c, lvl)) + "</white> Tokens";
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

    /** Active modules: sockets the current frame actually has. */
    public static List<ModuleDef> activeModules(DrillConfig c, DrillData d) {
        FrameDef f = c.frame(d.frame);
        List<ModuleDef> out = new ArrayList<>();
        for (int i = 0; i < f.sockets(); i++) {
            ModuleDef m = c.module(d.socket(i));
            if (m != null) out.add(m);
        }
        return out;
    }

    /** Effective numbers for one player's drill, modules included. Cheap to build; rebuild after any change. */
    public static final class Effective {
        public final FrameDef frame;
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
        public final double cacheChance;
        public final double heatCapacity;
        public final double decayPerSecond;
        public final double handHeatMultiplier;
        public final double tokenMultiplier;
        public final double burstMultiplier;
        public final double stallMultiplier;
        public final double sellMultiplier;
        public final boolean autoSell;

        public Effective(DrillConfig c, DrillData d) {
            frame = c.frame(d.frame);
            List<ModuleDef> mods = activeModules(c, d);
            breakSpeed = DrillMath.breakSpeed(c, frame, d.level(Enchant.BORE_SPEED));
            yield = yieldMultiplier(c, d.level(Enchant.YIELD));
            siphonChance = chance(c.enchant(Enchant.TOKEN_SIPHON), d.level(Enchant.TOKEN_SIPHON));
            siphonAmount = siphonAmountMultiplier(c, d.level(Enchant.TOKEN_SIPHON));

            double radiusMul = 1, shapedHeatMul = 1, rareMul = 1, capMul = 1, handHeatMul = 1, tokenMul = 1,
                    jackMul = 1, burstMul = 1, stallMul = 1, sellMul = 1;
            boolean noOverclock = false, noLaser = false, magnet = false;
            for (ModuleDef m : mods) {
                radiusMul *= m.p("radius-multiplier", 1);
                shapedHeatMul *= m.p("heat-multiplier", 1);
                rareMul *= m.p("rare-multiplier", 1);
                capMul *= m.p("capacity-multiplier", 1);
                handHeatMul *= m.p("hand-heat-multiplier", 1);
                tokenMul *= m.p("token-multiplier", 1);
                jackMul *= m.p("jackhammer-multiplier", 1);
                burstMul *= m.p("burst-multiplier", 1);
                stallMul *= m.p("stall-multiplier", 1);
                sellMul *= m.p("sell-multiplier", 1);
                switch (m.id()) {
                    case "coolant_loop" -> noOverclock = true;
                    case "seismic_dampener" -> noLaser = true;
                    case "magnet_coil" -> magnet = true;
                    default -> { }
                }
            }
            if (magnet) tokenMul = 0;

            shapedChance = chance(c.enchant(Enchant.SHAPED_CHARGE), d.level(Enchant.SHAPED_CHARGE));
            shapedRadius = (int) Math.round(DrillMath.shapedRadius(c, d.level(Enchant.SHAPED_CHARGE)) * radiusMul);
            shapedHeat = c.enchant(Enchant.SHAPED_CHARGE).p("heat", 6) * shapedHeatMul;
            jackChance = chance(c.enchant(Enchant.JACKHAMMER), d.level(Enchant.JACKHAMMER)) * jackMul;
            laserChance = noLaser ? 0 : chance(c.enchant(Enchant.CUTTING_LASER), d.level(Enchant.CUTTING_LASER));
            laserLength = DrillMath.laserLength(c, d.level(Enchant.CUTTING_LASER));
            overclockChance = noOverclock ? 0 : chance(c.enchant(Enchant.OVERCLOCK), d.level(Enchant.OVERCLOCK));
            overclockSeconds = DrillMath.overclockSeconds(c, d.level(Enchant.OVERCLOCK)) * burstMul;
            rareMultiplier = DrillMath.rareMultiplier(c, d.level(Enchant.SALVAGE_SENSE)) * rareMul;
            cacheChance = DrillMath.cacheChance(c, d.level(Enchant.SALVAGE_SENSE));
            heatCapacity = DrillMath.heatCapacity(c, frame, d.level(Enchant.COOLANT)) * capMul;
            decayPerSecond = DrillMath.decayPerSecond(c, d.level(Enchant.COOLANT));
            handHeatMultiplier = handHeatMul;
            tokenMultiplier = tokenMul;
            burstMultiplier = burstMul;
            stallMultiplier = stallMul;
            sellMultiplier = sellMul;
            autoSell = magnet;
        }
    }
}
