package dev.spacepunk.prison.auction;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;

/**
 * The Black Market (auction house): buy-it-now listings and timed auctions with escrowed bids, browsed in a chest
 * menu. Listing fees and a sales tax take Credits out of the economy.
 */
public final class AuctionModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private AuctionStore store;
    private AuctionManager manager;
    private AuctionMenu menu;
    private BukkitTask ticker;
    private BukkitTask saver;

    @Override public String id() { return "auction"; }

    @Override
    public void enable(SpacepunkPrison plugin) throws Exception {
        this.plugin = plugin;
        store = new AuctionStore(plugin);
        store.load();
        YamlConfiguration y = loadYaml();
        manager = new AuctionManager(store, AuctionConfig.load(y));
        menu = new AuctionMenu(manager);
        menu.useModels(y.getBoolean("use-item-models", true));
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(menu, plugin);
        pm.registerEvents(new AuctionListener(manager), plugin);
        plugin.addCommand("ah", "The Black Market (auction house)", List.of("auction", "auctionhouse", "blackmarket", "bm"),
                new AuctionCommand(this));

        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            manager.tick();
            menu.refreshAll();
        }, 20L, 20L);
        saver = Bukkit.getScheduler().runTaskTimer(plugin, store::flushAsync, 20L * 10, 20L * 10);

        ProfileService profiles = Services.get(ProfileService.class);
        for (Player p : Bukkit.getOnlinePlayers()) if (profiles.get(p.getUniqueId()).isPresent()) manager.onLoad(p);
    }

    @Override
    public void disable() {
        if (ticker != null) ticker.cancel();
        if (saver != null) saver.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof AuctionMenu.Holder) p.closeInventory();
        }
        if (store != null) store.flushSync();
    }

    void reload() {
        try {
            YamlConfiguration y = loadYaml();
            manager.config(AuctionConfig.load(y));
            menu.useModels(y.getBoolean("use-item-models", true));
        } catch (Exception e) {
            plugin.getLogger().severe("auction.yml reload failed, keeping the old config: " + e.getMessage());
        }
    }

    private YamlConfiguration loadYaml() {
        File file = new File(plugin.getDataFolder(), "auction.yml");
        if (!file.exists()) plugin.saveResource("auction.yml", false);
        return YamlConfiguration.loadConfiguration(file);
    }

    AuctionManager manager() { return manager; }

    AuctionMenu menu() { return menu; }
}
