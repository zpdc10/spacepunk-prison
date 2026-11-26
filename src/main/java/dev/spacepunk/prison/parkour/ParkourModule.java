package dev.spacepunk.prison.parkour;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.ObjectiveTypes;
import dev.spacepunk.prison.api.Objectives;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily parkour: one maintenance run per mine, built next to it by /parkouradmin generate, finishable once a day
 * for a reward roll with a small chance at something rare. Personal bests and course records are kept.
 * Config: parkour.yml; built courses: parkour-data.yml.
 */
public final class ParkourModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private volatile ParkourConfig config;
    private ParkourManager manager;
    private ParkourMenu menu;
    private final List<Listener> listeners = new ArrayList<>();
    private BukkitTask ticker;

    @Override public String id() { return "parkour"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "parkour.yml");
        if (!file.exists()) plugin.saveResource("parkour.yml", false);
        config = ParkourConfig.load(file, plugin.getLogger());
        manager = new ParkourManager(plugin, () -> config);
        ParkourCommands commands = new ParkourCommands(this);
        menu = new ParkourMenu(manager, commands);
        listeners.add(new ParkourListener(manager));
        listeners.add(menu);
        for (Listener l : listeners) plugin.getServer().getPluginManager().registerEvents(l, plugin);
        plugin.addCommand("parkour", "Daily maintenance runs (parkour)", List.of("pk", "runs"), commands.player());
        plugin.addCommand("parkouradmin", "Build and manage parkour runs", List.of("pkadmin"), commands.admin());
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, manager::tick, 2L, 2L);
        Objectives.register(ObjectiveTypes.PARKOUR_COMPLETE, "Finish maintenance runs");

        // One tick later every module is up: station warps and gang contract objectives.
        Bukkit.getScheduler().runTask(plugin, () -> {
            manager.registerWarps();
            Services.find(GangService.class).ifPresent(g -> g.registerContractObjective("parkour_runs", "Maintenance runs finished"));
        });

        ProfileService profiles = Services.get(ProfileService.class);
        for (Player p : Bukkit.getOnlinePlayers()) profiles.get(p.getUniqueId()).ifPresent(prof -> manager.loadPlayer(p.getUniqueId(), prof));
        plugin.getLogger().info("[parkour] " + manager.courses().size() + " runs built"
                + (manager.courses().isEmpty() ? ". Run /parkouradmin generate after /mineadmin generate." : ""));
    }

    @Override
    public void disable() {
        if (ticker != null) ticker.cancel();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        if (manager != null) manager.endAll();
    }

    void reload() { config = ParkourConfig.load(new File(plugin.getDataFolder(), "parkour.yml"), plugin.getLogger()); }

    ParkourManager manager() { return manager; }
    ParkourMenu menu() { return menu; }
}
