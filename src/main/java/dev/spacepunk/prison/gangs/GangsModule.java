package dev.spacepunk.prison.gangs;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.ProfileService;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Gangs (crews): membership and ranks, gang bank with a prestige lock, crew chat, upgrades, weekly contracts and
 * leaderboard, and turf hooks for the Undercroft. Registers {@link GangService}. Config: gangs.yml.
 * State: gangs-data.json in the plugin folder.
 */
public final class GangsModule implements FeatureModule {
    private static volatile GangsConfig CONFIG;

    private SpacepunkPrison plugin;
    private GangStore store;
    private GangManager gangs;
    private final List<BukkitTask> tasks = new ArrayList<>();

    static GangsConfig config() { return CONFIG; }

    @Override public String id() { return "gangs"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        reload();
        store = new GangStore(plugin);
        gangs = new GangManager(() -> CONFIG, store);
        Services.register(GangService.class, gangs);

        GangMenu menu = new GangMenu(gangs);
        Bukkit.getPluginManager().registerEvents(new GangListener(plugin, gangs), plugin);
        Bukkit.getPluginManager().registerEvents(menu, plugin);
        TurfManager turf = new TurfManager(gangs);
        Bukkit.getPluginManager().registerEvents(turf, plugin);

        GangCommands cmd = new GangCommands(gangs, menu, this::reload);
        plugin.addCommand("gang", "Crews: bank, upgrades, contracts, chat", List.of("crew", "gangs"), cmd.gang());
        plugin.addCommand("gc", "Crew chat", List.of("crewchat"), cmd.gc());

        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, gangs::tick, 20L * 5, 20L * 60));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, gangs::save, 20L * 30, 20L * 30));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, turf::tick, 40L, 20L));

        // /reload safety: refresh grades of players already online.
        ProfileService profiles = Services.get(ProfileService.class);
        for (Player p : Bukkit.getOnlinePlayers()) {
            profiles.get(p.getUniqueId()).ifPresent(prof -> gangs.updateGrade(p.getUniqueId(), p.getName(), prof.grade()));
        }
    }

    @Override
    public void disable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (gangs != null) gangs.shutdown();
    }

    private void reload() {
        File file = new File(plugin.getDataFolder(), "gangs.yml");
        if (!file.exists()) plugin.saveResource("gangs.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("gangs.yml")) {
            if (in != null) y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read default gangs.yml: " + e.getMessage());
        }
        CONFIG = new GangsConfig(y, plugin.getLogger());
    }
}
