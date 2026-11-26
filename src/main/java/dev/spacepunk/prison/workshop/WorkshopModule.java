package dev.spacepunk.prison.workshop;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.WarpService;
import dev.spacepunk.prison.api.WorkshopService;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Player workshops: a private hull bay per player (own void world, plot grid) with machines that crush, wash,
 * smelt, alloy and fabricate mined ore into goods worth more than raw ore. Registers {@link WorkshopService}
 * and the "workshop" warp. Config: workshop.yml. Design notes: docs/WORKSHOP.md.
 */
public final class WorkshopModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private volatile WorkshopConfig config;
    private WorkshopManager manager;
    private BukkitTask task;
    private int ticks;

    @Override public String id() { return "workshop"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        reload();
        manager = new WorkshopManager(plugin, () -> config);
        manager.world.world();
        WorkshopMenus menus = new WorkshopMenus(manager);
        Services.register(WorkshopService.class, manager);

        Bukkit.getPluginManager().registerEvents(new Gui.Clicks(), plugin);
        Bukkit.getPluginManager().registerEvents(new WorkshopListener(plugin, manager, menus), plugin);
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onTeleport(PlayerTeleportEvent e) {
                if (!manager.world.isWorld(e.getTo().getWorld())) return;
                Player p = e.getPlayer();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Bay bay = manager.bayAt(p.getLocation());
                    if (bay != null) manager.reconcile(bay);
                }, 3L);
            }
        }, plugin);

        WorkshopCommands cmd = new WorkshopCommands(manager, menus, this::reloadAll);
        plugin.addCommand("workshop", "Go to your hull bay workshop", List.of("ws", "bay", "hullbay"), cmd.workshop());
        plugin.addCommand("wsadmin", "Admin workshop controls", List.of(), cmd.admin());

        Services.find(WarpService.class).ifPresent(w -> w.register(new WarpService.Warp("workshop", "<#c8743a>Your Hull Bay",
                WarpService.PRIVATE, Material.LODESTONE,
                List.of("<gray>Your private workshop: machines that", "<gray>turn ore into goods worth far more."),
                manager::bayLocation, p -> null)));
        Services.find(GangService.class).ifPresent(g -> g.registerContractObjective(WorkshopManager.GANG_OBJECTIVE, "Goods processed in workshops"));

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);

        // /reload safety: players already online had their ProfileLoadedEvent before we registered.
        ProfileService profiles = Services.get(ProfileService.class);
        for (Player p : Bukkit.getOnlinePlayers()) profiles.get(p.getUniqueId()).ifPresent(manager::load);
    }

    private void tick() {
        ticks++;
        manager.tickAll();
        manager.world.keepNight();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Gui g && g.live) g.render();
            if (manager.world.isWorld(p.getWorld())) manager.hud(p);
        }
        if (ticks % 30 == 0) {
            manager.saveAll();
            manager.market.flush();
        }
    }

    @Override
    public void disable() {
        if (task != null) task.cancel();
        if (manager != null) {
            manager.saveAll();
            manager.plots.saveNow();
            manager.market.flushNow();
        }
    }

    private void reloadAll() { reload(); }

    private void reload() {
        File file = new File(plugin.getDataFolder(), "workshop.yml");
        if (!file.exists()) plugin.saveResource("workshop.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("workshop.yml")) {
            if (in != null) y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read default workshop.yml: " + e.getMessage());
        }
        config = WorkshopConfig.load(y, id -> Services.find(ProgressionService.class).map(s -> s.sellPrice(id)).orElse(0L),
                plugin.getLogger());
        plugin.getLogger().info("Workshop: " + config.machines.size() + " machines, " + config.goods.size() + " goods, "
                + config.recipes.values().stream().mapToInt(List::size).sum() + " recipes");
    }
}
