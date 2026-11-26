package dev.spacepunk.prison.fishing;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** Additive fishing effects from enchants, lures, bait and level. See the effects comment in fishing.yml. */
final class Effects {
    double biteSpeed, doubleCatch, rareLuck, treasure, creature, zone, strength, harpoon, value;

    static Effects parse(ConfigurationSection s) {
        Effects e = new Effects();
        if (s == null) return e;
        for (String k : s.getKeys(false)) e.add(k, s.getDouble(k));
        return e;
    }

    /** Adds amount to the named effect (the yaml name, e.g. "bite-speed"). Unknown names are ignored. */
    Effects add(String effect, double amount) {
        switch (effect.toLowerCase(Locale.ROOT)) {
            case "bite-speed" -> biteSpeed += amount;
            case "double-catch" -> doubleCatch += amount;
            case "rare-luck" -> rareLuck += amount;
            case "treasure" -> treasure += amount;
            case "creature" -> creature += amount;
            case "zone" -> zone += amount;
            case "strength" -> strength += amount;
            case "harpoon" -> harpoon += amount;
            case "value" -> value += amount;
            default -> { }
        }
        return this;
    }

    Effects add(Effects o) {
        if (o == null) return this;
        biteSpeed += o.biteSpeed;
        doubleCatch += o.doubleCatch;
        rareLuck += o.rareLuck;
        treasure += o.treasure;
        creature += o.creature;
        zone += o.zone;
        strength += o.strength;
        harpoon += o.harpoon;
        value += o.value;
        return this;
    }

    static boolean known(String effect) {
        return switch (effect.toLowerCase(Locale.ROOT)) {
            case "bite-speed", "double-catch", "rare-luck", "treasure", "creature", "zone", "strength", "harpoon", "value" -> true;
            default -> false;
        };
    }

    /** Human lines, e.g. "+12% bite speed". */
    java.util.List<String> describe() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (biteSpeed != 0) out.add(pct(biteSpeed) + " bite speed");
        if (doubleCatch != 0) out.add(pct(doubleCatch) + " double catch chance");
        if (rareLuck != 0) out.add(pct(rareLuck) + " rare fish odds");
        if (treasure != 0) out.add(pct(treasure) + " treasure odds");
        if (creature != 0) out.add(pct(creature) + " sea creature odds");
        if (zone != 0) out.add(pct(zone) + " catch zone");
        if (strength != 0) out.add(pct(strength) + " line strength");
        if (harpoon != 0) out.add("+" + fmt(harpoon) + " damage to sea creatures");
        if (value != 0) out.add(pct(value) + " fish value");
        return out;
    }

    static String pct(double v) {
        double p = v * 100;
        String s = Math.abs(p - Math.round(p)) < 0.05 ? Long.toString(Math.round(p)) : String.format(Locale.ROOT, "%.1f", p);
        return (v >= 0 ? "+" : "") + s + "%";
    }

    static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 0.05 ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
