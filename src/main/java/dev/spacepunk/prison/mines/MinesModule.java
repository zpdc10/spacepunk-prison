package dev.spacepunk.prison.mines;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Shared mines: 25 refilling mine rooms, block breaking for the mining loop, /mine and /mineadmin. */
public final class MinesModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private MineManager manager;
    private MineListener listener;
    private BukkitTask ticker;

    @Override public String id() { return "mines"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        File cfgFile = new File(plugin.getDataFolder(), "mines.yml");
        if (!cfgFile.exists()) plugin.saveResource("mines.yml", false);
        MineStore store = new MineStore(new File(plugin.getDataFolder(), "mines-data.yml"), plugin.getLogger());
        MinesConfig config = MinesConfig.load(cfgFile, store.custom(), plugin.getLogger());
        loadWorld(config, false);

        manager = new MineManager(plugin, config, store, createRefiller(config));
        Services.register(MineService.class, manager);

        listener = new MineListener(manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        if (org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfgFile)
                .getBoolean("world.send-players-on-join", true)) {
            plugin.getServer().getPluginManager().registerEvents(new JoinToMines(), plugin);
        }

        MineCommands commands = new MineCommands(this);
        plugin.addCommand("mine", "Teleport to a mine", List.of(), commands.mine());
        plugin.addCommand("mines", "List the mines", List.of(), commands.list());
        plugin.addCommand("mineadmin", "Create, place and reset mines", List.of("madmin"), commands.admin());

        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, manager::tick, 20L, 20L);
        // One tick later so every world is fully loaded: bring generated mines up to the current layout, then refill.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            upgradeLayout();
            if (manager.config().settings().refillOnStartup()) manager.refillAll();
        });
        long placed = manager.mines().stream().filter(Mine::placed).count();
        plugin.getLogger().info("[mines] " + manager.mines().size() + " mines configured, " + placed
                + " placed, refiller: " + manager.refiller().name());
        if (placed == 0) plugin.getLogger().info("[mines] No mines placed yet. Run /mineadmin generate in game to build them.");
    }

    @Override
    public void disable() {
        if (ticker != null) ticker.cancel();
        if (listener != null) HandlerList.unregisterAll(listener);
        if (manager != null) {
            manager.hideAllBars();
            manager.refiller().shutdown();
        }
    }

    /** Until the station hub is built, everyone who joins outside the mines world is sent to the mines world spawn. */
    private final class JoinToMines implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler
        public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
            String name = manager.config().settings().world();
            World w = plugin.getServer().getWorld(name);
            if (w == null || e.getPlayer().getWorld().equals(w)) return;
            // The station hub takes over once it exists.
            if (dev.spacepunk.prison.api.Services.find(dev.spacepunk.prison.api.WarpService.class)
                    .map(dev.spacepunk.prison.api.WarpService::hub).isPresent()) return;
            e.getPlayer().teleportAsync(w.getSpawnLocation().add(0.5, 0, 0.5));
        }
    }

    MineManager manager() { return manager; }
    SpacepunkPrison plugin() { return plugin; }

    /** Re-reads mines.yml and mines-data.yml, then refills everything. */
    void reload() {
        File cfgFile = new File(plugin.getDataFolder(), "mines.yml");
        manager.store().reload();
        MinesConfig config = MinesConfig.load(cfgFile, manager.store().custom(), plugin.getLogger());
        loadWorld(config, false);
        manager.refiller().shutdown();
        manager.refiller(createRefiller(config));
        manager.apply(config);
        manager.refillAll();
    }

    /**
     * Builds shells for the given mines in the mines world using the auto layout and places them.
     * Returns the mines that were generated.
     */
    List<Mine> generate(List<Mine> targets) {
        World world = loadWorld(manager.config(), true);
        List<Mine> done = new ArrayList<>();
        if (world == null) return done;
        for (Mine m : targets) {
            Region r = MineLayout.regionFor(manager.config(), m, world);
            MineLayout.buildShell(manager.config(), m, r, world);
            Location spawn = MineLayout.spawnFor(manager.config(), r, world);
            manager.place(m, r, spawn);
            done.add(m);
        }
        manager.store().layoutVersion(MineLayout.VERSION);
        if (manager.config().settings().setWorldSpawn()) {
            manager.mines().stream().filter(Mine::placed).findFirst().ifPresent(first -> {
                Location s = first.spawn();
                if (s != null && s.getWorld() == world) world.setSpawnLocation(s);
            });
        }
        return done;
    }

    /**
     * Rebuilds the shells of mines placed by the auto layout when they were built with an older layout version,
     * e.g. open pits from before mines became enclosed rooms. Mines placed by hand (define/create) are left alone.
     */
    private void upgradeLayout() {
        MineStore store = manager.store();
        if (store.layoutVersion() >= MineLayout.VERSION) return;
        World world = plugin.getServer().getWorld(manager.config().settings().world());
        int rebuilt = 0;
        if (world != null) {
            for (Mine m : manager.mines()) {
                if (!m.placed() || m.def().custom()) continue;
                Region expected = MineLayout.regionFor(manager.config(), m, world);
                if (!expected.equals(m.region())) continue;
                MineLayout.buildShell(manager.config(), m, expected, world);
                rebuilt++;
            }
        }
        store.layoutVersion(MineLayout.VERSION);
        if (rebuilt > 0) plugin.getLogger().info("[mines] Rebuilt " + rebuilt + " mines with layout v" + MineLayout.VERSION
                + (manager.config().settings().enclosed() ? " (enclosed rooms)" : ""));
    }

    /** Loads (or with create=true / create-if-missing, creates) the void mines world. */
    World loadWorld(MinesConfig config, boolean create) {
        String name = config.settings().world();
        World w = plugin.getServer().getWorld(name);
        if (w != null) return w;
        boolean exists = new File(plugin.getServer().getWorldContainer(), name).isDirectory();
        if (!exists && !create && !config.settings().createWorld()) return null;
        try {
            w = new WorldCreator(name).generator(new VoidGenerator(config.settings().baseY() + 40)).createWorld();
            if (w != null) w.setDifficulty(Difficulty.PEACEFUL);
            return w;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "[mines] could not load world " + name, e);
            return null;
        }
    }

    private Refiller createRefiller(MinesConfig config) {
        boolean fawe = config.settings().useFawe()
                && plugin.getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        if (fawe) {
            try {
                // Reflection keeps FAWE classes from loading when FAWE is not installed.
                return (Refiller) Class.forName("dev.spacepunk.prison.mines.FaweRefiller")
                        .getDeclaredConstructor(org.bukkit.plugin.Plugin.class).newInstance(plugin);
            } catch (ReflectiveOperationException | LinkageError e) {
                plugin.getLogger().log(Level.WARNING, "[mines] FAWE found but could not be used; using built-in refiller", e);
            }
        }
        return new NativeRefiller(plugin, config.settings().blocksPerTick());
    }
}
