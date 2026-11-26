package dev.spacepunk.prison.guns;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.GunService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;

/** Guns for PvP zones: resource-pack models, plugin-side ammo, reloads, recoil, spread and hit detection. */
public final class GunsModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private GunManager guns;
    private GunsmithMenu menu;
    private GunListener listener;
    private BukkitTask hud;

    @Override public String id() { return "guns"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        GunsConfig cfg = loadConfig();
        guns = new GunManager(plugin, cfg, new GunItems(cfg));
        Services.register(GunService.class, guns);
        listener = new GunListener(guns);
        menu = new GunsmithMenu(guns);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getServer().getPluginManager().registerEvents(menu, plugin);
        GunCommands commands = new GunCommands(this);
        plugin.addCommand("gunsmith", "Buy guns and ammo", List.of("guns"), commands.gunsmith());
        plugin.addCommand("gun", "How guns work (and admin tools)", List.of(), commands.gun());
        hud = plugin.getServer().getScheduler().runTaskTimer(plugin, guns::tickHud, 5L, 4L);
        plugin.getLogger().info("[guns] " + cfg.guns.size() + " guns, " + cfg.ammo.size() + " ammo types");
    }

    @Override
    public void disable() {
        if (hud != null) hud.cancel();
        if (listener != null) HandlerList.unregisterAll(listener);
        if (menu != null) HandlerList.unregisterAll(menu);
        if (guns != null) for (var p : plugin.getServer().getOnlinePlayers()) guns.forget(p);
    }

    private GunsConfig loadConfig() {
        File f = new File(plugin.getDataFolder(), "guns.yml");
        if (!f.exists()) plugin.saveResource("guns.yml", false);
        return GunsConfig.load(f, plugin.getLogger());
    }

    void reload() { guns.config(loadConfig()); }

    GunManager guns() { return guns; }
    GunsmithMenu menu() { return menu; }
}
