package dev.spacepunk.prison.core;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Server-wide world rules from config.yml (worlds.*). Applied to every loaded world and every world loaded later.
 * Weather: the space station has no sky, so rain and thunder are switched off permanently.
 */
public final class WorldRules implements Listener {
    private final boolean disableWeather;

    public WorldRules(boolean disableWeather) { this.disableWeather = disableWeather; }

    public void start(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getWorlds().forEach(this::apply);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) { apply(e.getWorld()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (disableWeather && e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent e) {
        if (disableWeather && e.toThunderState()) e.setCancelled(true);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private void apply(World w) {
        if (!disableWeather) return;
        // The weather-cycle rule was renamed across versions (doWeatherCycle / advance_weather); match it by name.
        for (GameRule<?> rule : GameRule.values()) {
            String n = rule.getName().toLowerCase(java.util.Locale.ROOT);
            if (rule.getType() == Boolean.class && n.contains("weather")) {
                w.setGameRule((GameRule<Boolean>) rule, false);
            }
        }
        w.setStorm(false);
        w.setThundering(false);
        w.setClearWeatherDuration(Integer.MAX_VALUE);
    }
}
