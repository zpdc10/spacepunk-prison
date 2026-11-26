package dev.spacepunk.prison.station;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.WarpService;
import dev.spacepunk.prison.core.FeatureModule;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * The station hub: builds the hub world from {@link HubLayout}, runs the warp registry ({@link WarpService}),
 * terminals, /hub, /warp, /warps and /stationadmin.
 */
public final class StationModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private YamlConfiguration config;
    private File dataFile;
    private YamlConfiguration data;
    private WarpManager warps;
    private ShuttleBoard board;
    private final List<Listener> listeners = new ArrayList<>();
    private BukkitTask clock;
    /** Console and button positions -> terminal key, filled when the layout is drawn. */
    private final Map<Long, String> terminals = new HashMap<>();
    private boolean building;

    @Override public String id() { return "station"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        loadConfig();
        dataFile = new File(plugin.getDataFolder(), "station-data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        warps = new WarpManager(this::hubSpawn);
        Services.register(WarpService.class, warps);
        loadAdminWarps();

        board = new ShuttleBoard(warps);
        listeners.add(board);
        listeners.add(new StationListener(this));
        for (Listener l : listeners) plugin.getServer().getPluginManager().registerEvents(l, plugin);

        StationCommands commands = new StationCommands(this);
        plugin.addCommand("hub", "Return to the station concourse", List.of("spawn", "station"), commands.hub());
        plugin.addCommand("warp", "Take a shuttle to a destination", List.of(), commands.warp());
        plugin.addCommand("warps", "Open the shuttle board", List.of("shuttle"), commands.warps());
        plugin.addCommand("stationadmin", "Build the hub and manage warps", List.of("sadmin"), commands.admin());

        indexTerminals();
        World w = world(true);
        if (w != null) {
            clock = plugin.getServer().getScheduler().runTaskTimer(plugin, this::holdSky, 20L, 200L);
            int built = data.getInt("built-version", 0);
            if (config.getBoolean("world.auto-build", true) && built < HubLayout.VERSION) {
                plugin.getLogger().info("[station] Hub layout v" + HubLayout.VERSION + " not built yet (have v" + built + "); building it now.");
                plugin.getServer().getScheduler().runTask(plugin, () -> build(null));
            }
        }
    }

    @Override
    public void disable() {
        if (clock != null) clock.cancel();
        for (Listener l : listeners) HandlerList.unregisterAll(l);
        listeners.clear();
    }

    // ------------------------------------------------------------------ config

    private void loadConfig() {
        File f = new File(plugin.getDataFolder(), "station.yml");
        if (!f.exists()) plugin.saveResource("station.yml", false);
        config = YamlConfiguration.loadConfiguration(f);
        // Keys added in newer versions fall back to the jar's copy.
        var res = plugin.getResource("station.yml");
        if (res != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(res, StandardCharsets.UTF_8)));
        }
    }

    void reload() {
        loadConfig();
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadAdminWarps();
        indexTerminals();
    }

    YamlConfiguration config() { return config; }
    WarpManager warps() { return warps; }
    ShuttleBoard board() { return board; }
    SpacepunkPrison plugin() { return plugin; }
    boolean building() { return building; }

    String worldName() { return config.getString("world.name", "station"); }

    String stationName() { return config.getString("name", "<#c8743a><bold>SPACEPUNK</bold> <gray>STATION"); }

    // ------------------------------------------------------------------ world + hub

    World world(boolean create) {
        String name = worldName();
        World w = plugin.getServer().getWorld(name);
        if (w != null) return w;
        boolean exists = new File(plugin.getServer().getWorldContainer(), name).isDirectory();
        if (!exists && !create) return null;
        try {
            w = new WorldCreator(name).generator(new StationWorld()).createWorld();
            if (w != null) {
                w.setDifficulty(Difficulty.PEACEFUL);
                w.setSpawnLocation(defaultSpawn(w));
            }
            return w;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "[station] could not load world " + name, e);
            return null;
        }
    }

    /** Keeps the station sky dark and starry (clear night), unless lock-time is -1. */
    private void holdSky() {
        World w = plugin.getServer().getWorld(worldName());
        if (w == null) return;
        long t = config.getLong("world.lock-time", 18000);
        if (t >= 0) w.setTime(t);
        if (w.hasStorm()) w.setStorm(false);
        if (w.isThundering()) w.setThundering(false);
    }

    private static Location defaultSpawn(World w) {
        return new Location(w, HubLayout.SPAWN_X, HubLayout.FLOOR, HubLayout.SPAWN_Z, HubLayout.SPAWN_YAW, 0f);
    }

    /** Hub spawn, or null until the hub has been built. */
    Location hubSpawn() {
        if (data.getInt("built-version", 0) <= 0) return null;
        World w = plugin.getServer().getWorld(worldName());
        if (w == null) return null;
        ConfigurationSection s = data.getConfigurationSection("spawn");
        if (s == null) return defaultSpawn(w);
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    void setSpawn(Location l) {
        data.set("spawn.x", l.getX());
        data.set("spawn.y", l.getY());
        data.set("spawn.z", l.getZ());
        data.set("spawn.yaw", (double) l.getYaw());
        data.set("spawn.pitch", (double) l.getPitch());
        if (l.getWorld() != null) l.getWorld().setSpawnLocation(l);
        saveData();
    }

    /** Draws and builds the hub. Players in the station world are moved to the spawn when it finishes. */
    boolean build(Runnable after) {
        if (building) return false;
        World w = world(true);
        if (w == null) return false;
        building = true;
        long start = System.currentTimeMillis();
        HubLayout layout = new HubLayout(stationName()).draw();
        indexTerminals(layout);
        StationBuilder.build(plugin, w, layout, config.getInt("world.blocks-per-tick", 60000), () -> {
            building = false;
            data.set("built-version", HubLayout.VERSION);
            saveData();
            Location spawn = hubSpawn();
            if (spawn != null) {
                w.setSpawnLocation(spawn);
                for (Player p : w.getPlayers()) p.teleportAsync(spawn);
            }
            plugin.getLogger().info("[station] Hub v" + HubLayout.VERSION + " built in "
                    + (System.currentTimeMillis() - start) + " ms (" + layout.canvas.volume() + " blocks, "
                    + layout.terminals.size() + " terminals).");
            if (after != null) after.run();
        });
        return true;
    }

    // ------------------------------------------------------------------ terminals

    private void indexTerminals() { indexTerminals(new HubLayout(stationName()).draw()); }

    private void indexTerminals(HubLayout layout) {
        terminals.clear();
        for (HubLayout.Terminal t : layout.terminals) {
            terminals.put(key(t.x(), t.y(), t.z()), t.key());
            terminals.put(key(t.x(), t.y() + 1, t.z()), t.key());
        }
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Terminal key at this block of the station world, or null. */
    String terminalAt(int x, int y, int z) { return terminals.get(key(x, y, z)); }

    /**
     * Runs a terminal's action from station.yml. An action is one or more steps separated by "|"; the first
     * usable one runs: "warp:ID" (skipped if no such warp is registered yet), "board:CAT,CAT", "command:LINE", "hub".
     */
    void runTerminal(Player p, String key) {
        String action = config.getString("terminals." + key);
        if (action == null || action.isBlank()) {
            Text.send(p, "<gray>This terminal is offline.");
            return;
        }
        for (String step : action.split("\\|")) {
            step = step.trim();
            int colon = step.indexOf(':');
            String type = (colon < 0 ? step : step.substring(0, colon)).toLowerCase(Locale.ROOT);
            String arg = colon < 0 ? "" : step.substring(colon + 1).trim();
            switch (type) {
                case "warp" -> {
                    if (warps.get(arg).isEmpty()) continue;
                    warps.warp(p, arg);
                    return;
                }
                case "board" -> {
                    board.open(p, config.getString("board-title", "Shuttle Board"),
                            Arrays.stream(arg.split(",")).map(String::trim).toList());
                    return;
                }
                case "command" -> {
                    p.performCommand(arg.startsWith("/") ? arg.substring(1) : arg);
                    return;
                }
                case "hub" -> {
                    warps.sendToHub(p);
                    return;
                }
                default -> plugin.getLogger().warning("[station] unknown terminal step '" + step + "' for " + key);
            }
        }
        Text.send(p, "<gray>This route is not in service yet. Check back soon.");
    }

    // ------------------------------------------------------------------ admin warps

    private final List<String> adminWarpIds = new ArrayList<>();

    private void loadAdminWarps() {
        for (String id : adminWarpIds) warps.unregister(id);
        adminWarpIds.clear();
        ConfigurationSection sec = data.getConfigurationSection("warps");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            String worldName = s.getString("world", "");
            double x = s.getDouble("x"), y = s.getDouble("y"), z = s.getDouble("z");
            float yaw = (float) s.getDouble("yaw"), pitch = (float) s.getDouble("pitch");
            Material icon = Material.matchMaterial(s.getString("icon", "ENDER_PEARL"));
            warps.register(new WarpService.Warp(id, s.getString("name", id), s.getString("category", WarpService.PLANETSIDE),
                    icon == null ? Material.ENDER_PEARL : icon, s.getStringList("description"), p -> {
                        World w = plugin.getServer().getWorld(worldName);
                        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
                    }, p -> null));
            adminWarpIds.add(id);
        }
    }

    void saveAdminWarp(String id, String name, String category, Location l) {
        String base = "warps." + id;
        data.set(base + ".name", name);
        data.set(base + ".category", category);
        data.set(base + ".world", l.getWorld().getName());
        data.set(base + ".x", l.getX());
        data.set(base + ".y", l.getY());
        data.set(base + ".z", l.getZ());
        data.set(base + ".yaw", (double) l.getYaw());
        data.set(base + ".pitch", (double) l.getPitch());
        saveData();
        loadAdminWarps();
    }

    boolean deleteAdminWarp(String id) {
        if (!adminWarpIds.contains(id)) return false;
        data.set("warps." + id, null);
        saveData();
        loadAdminWarps();
        return true;
    }

    List<String> adminWarpIds() { return List.copyOf(adminWarpIds); }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[station] could not save " + dataFile.getName(), e);
        }
    }
}
