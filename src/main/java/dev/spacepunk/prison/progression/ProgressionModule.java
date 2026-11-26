package dev.spacepunk.prison.progression;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Grades 1-25, prestige, cargo hold and selling. Registers {@link ProgressionService}.
 * Config: progression.yml in the plugin folder (copied from the jar on first run).
 */
public final class ProgressionModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private volatile ProgressionConfig config;
    private CargoStore store;
    private BukkitTask flushTask;

    @Override public String id() { return "progression"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        reload();

        ProfileService profiles = Services.get(ProfileService.class);
        EconomyService economy = Services.get(EconomyService.class);
        store = new CargoStore(profiles, plugin.getLogger());
        ProgressionManager progression = new ProgressionManager(profiles, economy, store, () -> config);
        CargoHold cargo = new CargoHold(store, progression, economy);
        CargoMenu menu = new CargoMenu(cargo, progression, economy);
        Services.register(ProgressionService.class, progression);

        Bukkit.getPluginManager().registerEvents(new ProgressionListener(progression, cargo), plugin);
        Bukkit.getPluginManager().registerEvents(menu, plugin);

        ProgressionCommands cmd = new ProgressionCommands(progression, cargo, menu, economy, profiles, this::reload);
        plugin.addCommand("rankup", "Buy your next clearance grade", List.of("ru"), cmd.rankup());
        plugin.addCommand("prestige", "Reset to grade 1 for Shards and a permanent bonus", List.of(), cmd.prestige());
        plugin.addCommand("sell", "Sell everything in your cargo hold", List.of("sellall"), cmd.sell());
        plugin.addCommand("cargo", "Open your cargo hold", List.of("hold", "backpack", "bp"), cmd.cargo());
        plugin.addCommand("autosell", "Install or toggle the Auto-Seller", List.of(), cmd.autosell());
        plugin.addCommand("grades", "List clearance grades and their costs", List.of("ranks"), cmd.grades());
        plugin.addCommand("progression", "Admin progression controls", List.of(), cmd.admin());

        // Cargo changes are batched into the profile once a second (profile autosave writes them to disk).
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, store::flush, 20L, 20L);

        // /reload safety: players already online had their ProfileLoadedEvent before we registered.
        for (Player p : Bukkit.getOnlinePlayers()) {
            profiles.get(p.getUniqueId()).ifPresent(prof -> {
                store.load(prof);
                progression.refreshTab(p);
            });
        }
    }

    @Override
    public void disable() {
        if (flushTask != null) flushTask.cancel();
        if (store != null) store.flushAll();
    }

    private void reload() {
        File file = new File(plugin.getDataFolder(), "progression.yml");
        if (!file.exists()) plugin.saveResource("progression.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("progression.yml")) {
            if (in != null) y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read default progression.yml: " + e.getMessage());
        }
        config = new ProgressionConfig(y, plugin.getLogger());
    }
}
