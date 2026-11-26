package dev.spacepunk.prison.quests;

import org.bukkit.configuration.ConfigurationSection;

/**
 * A number in quests.yml that can scale with the player. Written either as a plain number, or as a section:
 * <pre>
 * amount: {base: 2000, per-grade: 0.05, growth: 1.0, grade-cost: 0.0, min: 0, max: 0}
 * </pre>
 * value = base x (1 + per-grade x (grade-1)) x growth^(grade-1) + grade-cost x (Credits for the next rank-up),
 * then clamped to [min, max] (max 0 = no cap) and rounded to two significant figures so it reads cleanly.
 */
record Amount(double base, double perGrade, double growth, double gradeCost, long min, long max) {
    static final Amount ZERO = new Amount(0, 0, 1, 0, 0, 0);

    static Amount fixed(long v) { return new Amount(v, 0, 1, 0, 0, 0); }

    static Amount parse(ConfigurationSection parent, String key) {
        if (parent == null || !parent.contains(key)) return ZERO;
        if (parent.isConfigurationSection(key)) {
            ConfigurationSection s = parent.getConfigurationSection(key);
            return new Amount(s.getDouble("base", 0), s.getDouble("per-grade", 0), s.getDouble("growth", 1.0),
                    s.getDouble("grade-cost", 0), s.getLong("min", 0), s.getLong("max", 0));
        }
        return fixed(Math.max(0, Math.round(parent.getDouble(key, 0))));
    }

    boolean isZero() { return base == 0 && gradeCost == 0 && min == 0; }

    long resolve(int grade, long rankCost) {
        int g = Math.max(1, grade);
        double v = base * (1 + perGrade * (g - 1)) * Math.pow(growth, g - 1) + gradeCost * Math.max(0, rankCost);
        long out = nice(v);
        if (out < min) out = min;
        if (max > 0 && out > max) out = max;
        return out;
    }

    /** Rounds to two significant figures (1234 -> 1200, 87 -> 87, 5 -> 5). */
    static long nice(double v) {
        if (v <= 0 || Double.isNaN(v)) return 0;
        if (v < 100) return Math.round(v);
        double mag = Math.pow(10, Math.floor(Math.log10(v)) - 1);
        double r = Math.round(v / mag) * mag;
        return r >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) r;
    }
}
