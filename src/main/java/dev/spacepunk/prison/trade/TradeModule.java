package dev.spacepunk.prison.trade;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;

/**
 * Direct player-to-player trading: /trade <player>, a two-sided window for items and Credits, a countdown once both
 * accept, prestige locks so Credits can't be parked with a friend, and a log of every trade. Config: trade.yml.
 */
public final class TradeModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private TradeStore store;
    private TradeManager manager;
    private TradeMenu menu;
    private BukkitTask ticker;
    private BukkitTask saver;

    @Override public String id() { return "trade"; }

    @Override
    public void enable(SpacepunkPrison plugin) throws Exception {
        this.plugin = plugin;
        store = new TradeStore(plugin);
        store.load();
        YamlConfiguration y = loadYaml();
        manager = new TradeManager(plugin, store, TradeConfig.load(y));
        menu = new TradeMenu(plugin, manager);
        menu.useModels(y.getBoolean("use-item-models", true));
        manager.menu(menu);
        manager.recoverEscrow();
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(menu, plugin);
        pm.registerEvents(new TradeListener(manager), plugin);
        plugin.addCommand("trade", "Trade items and Credits with another player", List.of("tr"), new TradeCommand(this));

        ticker = Bukkit.getScheduler().runTaskTimer(plugin, manager::tick, 20L, 20L);
        saver = Bukkit.getScheduler().runTaskTimer(plugin, store::flushAsync, 20L * 3, 20L * 3);
        for (Player p : Bukkit.getOnlinePlayers()) manager.deliverReturns(p);
    }

    @Override
    public void disable() {
        if (ticker != null) ticker.cancel();
        if (saver != null) saver.cancel();
        if (manager != null) manager.cancelAll();
        if (store != null) store.flushSync();
    }

    void reload() {
        try {
            YamlConfiguration y = loadYaml();
            manager.config(TradeConfig.load(y));
            menu.useModels(y.getBoolean("use-item-models", true));
        } catch (Exception e) {
            plugin.getLogger().severe("trade.yml reload failed, keeping the old config: " + e.getMessage());
        }
    }

    private YamlConfiguration loadYaml() {
        File file = new File(plugin.getDataFolder(), "trade.yml");
        if (!file.exists()) plugin.saveResource("trade.yml", false);
        return YamlConfiguration.loadConfiguration(file);
    }

    TradeManager manager() { return manager; }
}
