package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;

/** The drill: soulbound custom pickaxe with frames, Token enchants, modules and heat. */
public final class DrillModule implements FeatureModule {
    private static final int TICK_PERIOD = 4; // ticks between heat updates

    private SpacepunkPrison plugin;
    private DrillManager drills;
    private HeatManager heat;
    private DrillMenu menu;
    private RigMenu rig;
    private BukkitTask ticker;
    private BukkitTask flusher;

    @Override public String id() { return "drill"; }

    @Override
    public void enable(SpacepunkPrison plugin) throws Exception {
        this.plugin = plugin;
        DrillConfig cfg = loadConfig();
        DrillItems items = new DrillItems(plugin, cfg);
        drills = new DrillManager(plugin.getLogger(), items, cfg);
        heat = new HeatManager(drills);
        menu = new DrillMenu(drills, heat);
        rig = new RigMenu(drills, menu);
        menu.rigMenu(rig);
        drills.rigMenu(rig);
        MiningListener mining = new MiningListener(drills, heat);

        Services.register(DrillService.class, drills);
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(mining, plugin);
        pm.registerEvents(menu, plugin);
        pm.registerEvents(rig, plugin);
        pm.registerEvents(new SellListener(drills, heat), plugin);
        pm.registerEvents(new GearFindListener(drills), plugin);
        pm.registerEvents(new GadgetListener(drills), plugin);
        pm.registerEvents(new DrillListener(plugin, drills, heat, mining, menu), plugin);

        plugin.addCommand("drill", "Open your drill bay", List.of("pickaxe", "pick"), new DrillCommand(this));
        plugin.addCommand("rig", "Fit modules on your drill, rod and guns", List.of("modules"), new RigCommand(drills));

        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> heat.tick(TICK_PERIOD / 20.0), TICK_PERIOD, TICK_PERIOD);
        flusher = Bukkit.getScheduler().runTaskTimer(plugin, drills::flushAll, 20L * 30, 20L * 30);

        // /reload safety: players already online with loaded profiles.
        ProfileService profiles = Services.get(ProfileService.class);
        for (Player p : Bukkit.getOnlinePlayers()) {
            profiles.get(p.getUniqueId()).ifPresent(prof -> {
                drills.load(prof);
                drills.ensureDrill(p);
                drills.applyExo(p);
            });
        }
    }

    @Override
    public void disable() {
        if (ticker != null) ticker.cancel();
        if (flusher != null) flusher.cancel();
        if (drills == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            var holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof DrillMenu.Holder || holder instanceof RigMenu.Holder) p.closeInventory();
            HeatManager.clearModifiers(p);
            DrillManager.clearExo(p);
        }
        drills.flushAll();
    }

    public void reload() {
        try {
            DrillConfig cfg = loadConfig();
            drills.config(cfg);
            for (Player p : Bukkit.getOnlinePlayers()) {
                drills.refreshItem(p);
                drills.ejectMisfits(p);
                drills.applyExo(p);
                menu.refreshIfOpen(p);
                rig.refreshIfOpen(p);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("drill.yml reload failed, keeping the old config: " + e.getMessage());
        }
    }

    private DrillConfig loadConfig() throws Exception {
        File file = new File(plugin.getDataFolder(), "drill.yml");
        if (!file.exists()) plugin.saveResource("drill.yml", false);
        // No jar defaults merged in: removing a frame or module from drill.yml should really remove it.
        return DrillConfig.load(YamlConfiguration.loadConfiguration(file), plugin.getLogger());
    }

    public DrillManager drills() { return drills; }

    public HeatManager heat() { return heat; }

    public DrillMenu menu() { return menu; }
}
