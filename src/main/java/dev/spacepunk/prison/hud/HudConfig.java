package dev.spacepunk.prison.hud;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Locale;

/** Values from hud.yml. Immutable; a reload swaps the whole object. */
record HudConfig(
        boolean sidebar, String sidebarTitle, String sidebarFooter, int sidebarTicks,
        boolean tab, int tabTicks, String tabHeader, String tabFooter,
        long gainMillis, long flashMillis,
        double warnAt, double dangerAt,
        boolean sounds, boolean particles, List<String> valuableMatch, long fullWarningMillis,
        boolean joinHint) {

    static HudConfig load(YamlConfiguration y) {
        return new HudConfig(
                y.getBoolean("sidebar.enabled", true),
                y.getString("sidebar.title", "<#c8743a><bold>SPACEPUNK"),
                y.getString("sidebar.footer", ""),
                Math.max(2, y.getInt("sidebar.refresh-ticks", 10)),
                y.getBoolean("tab.enabled", true),
                Math.max(10, y.getInt("tab.refresh-ticks", 40)),
                y.getString("tab.header", ""),
                y.getString("tab.footer", ""),
                millis(y.getDouble("action-bar.gain-seconds", 2.5)),
                millis(y.getDouble("action-bar.flash-seconds", 2.0)),
                // Older hud.yml files kept these under cargo-bar (the boss bar is gone).
                y.getDouble("sidebar.cargo-warn-at", y.getDouble("cargo-bar.warn-at", 0.6)),
                y.getDouble("sidebar.cargo-danger-at", y.getDouble("cargo-bar.danger-at", 0.9)),
                y.getBoolean("mining.sounds", true),
                y.getBoolean("mining.particles", true),
                y.getStringList("mining.valuable-match").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList(),
                millis(y.getDouble("mining.full-warning-seconds", 4)),
                y.getBoolean("join-hint", true));
    }

    private static long millis(double seconds) { return Math.max(0, Math.round(seconds * 1000)); }

    boolean valuable(String itemId) {
        for (String m : valuableMatch) if (itemId.contains(m)) return true;
        return false;
    }
}
