package dev.spacepunk.prison.raredrops;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;

/**
 * Rare drops while mining: tiered finds (Common to Mythic) with announcements, collectible relics with
 * per-deck collections, salvage caches, and blueprint fragments that assemble into drill modules.
 */
public final class RareDropsModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private RareDropsManager manager;
    private SalvageMenu menu;

    @Override public String id() { return "raredrops"; }

    @Override
    public void enable(SpacepunkPrison plugin) throws Exception {
        this.plugin = plugin;
        RareConfig cfg = loadConfig();
        manager = new RareDropsManager(plugin.getLogger(), new RareItems(plugin, cfg), cfg);
        menu = new SalvageMenu(manager);
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new RareListener(manager), plugin);
        pm.registerEvents(menu, plugin);
        plugin.addCommand("salvage", "Rare drops: collections, blueprint assembly, stash",
                List.of("relics", "collections", "raredrops"), new SalvageCommand(this));

        ProfileService profiles = Services.get(ProfileService.class);
        for (Player p : Bukkit.getOnlinePlayers()) profiles.get(p.getUniqueId()).ifPresent(manager::load);
    }

    @Override
    public void disable() {
        if (manager == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof SalvageMenu.Holder) p.closeInventory();
            manager.save(p.getUniqueId());
        }
    }

    void reload() {
        try {
            manager.config(loadConfig());
        } catch (Exception e) {
            plugin.getLogger().severe("raredrops.yml reload failed, keeping the old config: " + e.getMessage());
        }
    }

    private RareConfig loadConfig() throws Exception {
        File file = new File(plugin.getDataFolder(), "raredrops.yml");
        if (!file.exists()) plugin.saveResource("raredrops.yml", false);
        return RareConfig.load(YamlConfiguration.loadConfiguration(file), plugin.getLogger());
    }

    RareDropsManager manager() { return manager; }

    SalvageMenu menu() { return menu; }
}
