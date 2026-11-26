package dev.spacepunk.prison.fishing;

import dev.spacepunk.prison.fishing.FishingConfig.Area;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

/**
 * The station waters: a void world with the Bilge Hub and one sealed deck module per pool, far apart along +X and
 * not connected (players fly between them from a Dock Terminal, /waters or the Shuttle Board). Everything is computed from fishing.yml, so lookups need no stored regions. Signs, the leaderboard
 * boards and the Fish Market are non-persistent display entities spawned on enable (their chunks are kept loaded).
 */
final class FishingWorld {
    static final int LAYOUT_VERSION = 2;
    private static final int HUB_RADIUS = 7; // v1 hub, for clearing the old layout

    private final Plugin plugin;
    private final NamespacedKey displayKey;
    private final File stateFile;
    private FishingConfig cfg;
    private World world;
    private final List<Entity> displays = new ArrayList<>();
    private TextDisplay weeklyBoard, levelBoard;
    private final Set<Long> tickets = new HashSet<>();

    FishingWorld(Plugin plugin, FishingConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.displayKey = new NamespacedKey(plugin, "fishing_display");
        this.stateFile = new File(plugin.getDataFolder(), "fishing-world.yml");
    }

    void config(FishingConfig cfg) { this.cfg = cfg; }

    World world() { return world; }

    boolean isWaters(World w) { return world != null && world.equals(w); }

    NamespacedKey displayKey() { return displayKey; }

    // ------------------------------------------------------------------ geometry

    /** Pool box for an area: water x0..x1, z0..z1, wBot..wTop. */
    record Pool(Area area, int cx, int x0, int x1, int z0, int z1, int wBot, int wTop) {
        int outerX0(int walkway) { return x0 - 1 - walkway; }
        int outerX1(int walkway) { return x1 + 1 + walkway; }
        int outerZ0(int walkway) { return z0 - 1 - walkway; }
        int outerZ1(int walkway) { return z1 + 1 + walkway; }
    }

    Pool pool(Area a) {
        int cx = (a.index() + 1) * cfg.spacing;
        int x0 = cx - cfg.poolX / 2, z0 = -cfg.poolZ / 2;
        int wTop = cfg.baseY - 2, wBot = cfg.baseY - 1 - cfg.poolDepth;
        return new Pool(a, cx, x0, x0 + cfg.poolX - 1, z0, z0 + cfg.poolZ - 1, wBot, wTop);
    }

    /** The area whose water holds this location (a hook), if any. */
    Area areaAtWater(Location l) {
        if (l == null || !isWaters(l.getWorld())) return null;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        for (Area a : cfg.areas.values()) {
            Pool p = pool(a);
            if (x >= p.x0() - 1 && x <= p.x1() + 1 && z >= p.z0() - 1 && z <= p.z1() + 1 && y >= p.wBot() - 1 && y <= cfg.baseY + 1) return a;
        }
        return null;
    }

    /** The area whose module a player is in (pool, walkway and the room around it), if any. */
    Area areaNear(Location l) {
        if (l == null || !isWaters(l.getWorld())) return null;
        int x = l.getBlockX(), z = l.getBlockZ();
        for (Area a : cfg.areas.values()) {
            Pool p = pool(a);
            int pad = FishingBuilds.MARGIN + 4;
            if (x >= p.outerX0(cfg.walkway) - pad && x <= p.outerX1(cfg.walkway) + pad
                    && z >= p.outerZ0(cfg.walkway) - pad && z <= p.outerZ1(cfg.walkway) + pad) return a;
        }
        return null;
    }

    Location hub() {
        if (world == null) return null;
        return new Location(world, 0.5, cfg.baseY, 0.5, -90f, 0f);
    }

    /** Arrival point on the west walkway of a pool, facing the water. */
    Location arrival(Area a) {
        if (world == null) return null;
        FishingBuilds.Room r = new FishingBuilds(world, cfg).room(pool(a));
        return new Location(world, r.x0() + 3.5, cfg.baseY, r.cz() + 0.5, -90f, 5f);
    }

    // ------------------------------------------------------------------ world

    /** Loads or creates the world. Returns true if it was created just now. */
    boolean load() {
        world = Bukkit.getWorld(cfg.worldName);
        boolean existed = world != null || new File(Bukkit.getWorldContainer(), cfg.worldName).isDirectory();
        if (world == null) {
            try {
                world = new WorldCreator(cfg.worldName).generator(new Generator(cfg.baseY)).createWorld();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "[fishing] could not load world " + cfg.worldName, e);
                return false;
            }
        }
        if (world == null) return false;
        world.setDifficulty(Difficulty.NORMAL);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE / 2);
        world.setTime(18000);
        rule(false, "doDaylightCycle", "advance_time", "minecraft:advance_time");
        rule(false, "doWeatherCycle", "advance_weather", "minecraft:advance_weather");
        rule(false, "doMobSpawning", "spawn_mobs", "minecraft:spawn_mobs");
        rule(true, "keepInventory", "keep_inventory", "minecraft:keep_inventory");
        rule(false, "doInsomnia", "spawn_phantoms", "minecraft:spawn_phantoms");
        return !existed;
    }

    /** Sets a boolean game rule by whichever name this server version uses; silently skips unknown names. */
    @SuppressWarnings({"unchecked", "deprecation"})
    private void rule(boolean value, String... names) {
        for (String n : names) {
            GameRule<?> r = null;
            try {
                r = (GameRule<?>) GameRule.class.getMethod("getByName", String.class).invoke(null, n);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // method missing on this version
            }
            if (r != null && r.getType() == Boolean.class) {
                try {
                    world.setGameRule((GameRule<Boolean>) r, value);
                } catch (RuntimeException ignored) {
                    // rule not settable
                }
                return;
            }
        }
    }

    boolean built() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(stateFile);
        return y.getInt("built-layout", 0) >= LAYOUT_VERSION && cfg.worldName.equals(y.getString("world", cfg.worldName));
    }

    private void markBuilt() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(stateFile);
        y.set("built-layout", LAYOUT_VERSION);
        y.set("world", cfg.worldName);
        try {
            y.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[fishing] could not save " + stateFile.getName() + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ building

    /** Builds (or rebuilds) the hub and every pool module. Main thread. */
    void build() {
        if (world == null) return;
        long start = System.nanoTime();
        try {
            buildAll();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "[fishing] building the station waters failed; it will retry next start", e);
            return;
        }
        markBuilt();
        plugin.getLogger().info("[fishing] station waters built (layout v" + LAYOUT_VERSION + ", "
                + (System.nanoTime() - start) / 1_000_000 + " ms); saved to " + stateFile.getName());
    }

    private void buildAll() {
        int y = cfg.baseY - 1;
        if (YamlConfiguration.loadConfiguration(stateFile).getInt("built-layout", 0) == 1) clearLegacyLayout();
        FishingBuilds builds = new FishingBuilds(world, cfg);
        builds.hub(y);
        for (Area a : cfg.areas.values()) {
            Pool p = pool(a);
            builds.module(p, y, () -> buildPool(p));
            biome(p);
        }
        world.setSpawnLocation(hub());
    }

    /** Layout v1 put the pools 44 blocks apart along +X, joined to the hub by bridges. Clears all of it once. */
    private void clearLegacyLayout() {
        int oldSpacing = 44, half = Math.max(cfg.poolX, cfg.poolZ) / 2 + 1 + cfg.walkway + 2;
        int xMax = (cfg.areas.size() + 1) * oldSpacing + half;
        for (int x = -HUB_RADIUS - 2; x <= xMax; x++)
            for (int z = -half; z <= half; z++)
                for (int yy = cfg.baseY - cfg.poolDepth - 4; yy <= cfg.baseY + 6; yy++) {
                    var b = world.getBlockAt(x, yy, z);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }
        plugin.getLogger().info("[fishing] cleared the old bridged layout");
    }

    private void buildPool(Pool p) {
        Area a = p.area();
        int y = cfg.baseY - 1, w = cfg.walkway;
        int ox0 = p.outerX0(w), ox1 = p.outerX1(w), oz0 = p.outerZ0(w), oz1 = p.outerZ1(w);
        // Clear the space above (rebuilds)
        for (int x = ox0; x <= ox1; x++)
            for (int z = oz0; z <= oz1; z++)
                for (int yy = y + 1; yy <= y + 6; yy++) set(x, yy, z, Material.AIR);
        // Open the water surface (the module shell floors the whole room first)
        for (int x = p.x0(); x <= p.x1(); x++)
            for (int z = p.z0(); z <= p.z1(); z++) set(x, y, z, Material.AIR);
        // Basin: floor, walls, water
        for (int x = p.x0() - 1; x <= p.x1() + 1; x++) {
            for (int z = p.z0() - 1; z <= p.z1() + 1; z++) {
                set(x, p.wBot() - 1, z, a.wall());
                boolean wall = x < p.x0() || x > p.x1() || z < p.z0() || z > p.z1();
                for (int yy = p.wBot(); yy <= p.wTop(); yy++) {
                    boolean lamp = wall && yy == p.wBot() + 1 && (x + z) % 4 == 0;
                    set(x, yy, z, wall ? (lamp ? a.light() : a.wall()) : Material.WATER);
                }
            }
        }
        // Seagrass tufts on the floor
        Random rng = new Random(a.id().hashCode());
        for (int i = 0; i < cfg.poolX * cfg.poolZ / 12; i++) {
            int x = p.x0() + rng.nextInt(cfg.poolX), z = p.z0() + rng.nextInt(cfg.poolZ);
            set(x, p.wBot(), z, Material.SEAGRASS);
        }
        // Walkway ring with an underside, parapet on the outer edge, lit corner pillars
        for (int x = ox0; x <= ox1; x++) {
            for (int z = oz0; z <= oz1; z++) {
                boolean inner = x >= p.x0() - 1 && x <= p.x1() + 1 && z >= p.z0() - 1 && z <= p.z1() + 1;
                boolean lip = inner && (x == p.x0() - 1 || x == p.x1() + 1 || z == p.z0() - 1 || z == p.z1() + 1);
                if (inner && !lip) continue;
                set(x, y, z, lip ? a.wall() : a.floor());
                if (!inner) set(x, y - 1, z, a.wall());
                boolean edge = x == ox0 || x == ox1 || z == oz0 || z == oz1;
                if (!edge) continue;
                boolean gap = (x == ox0 || x == ox1) && Math.abs(z) <= 1;
                boolean corner = (x == ox0 || x == ox1) && (z == oz0 || z == oz1);
                if (gap) continue;
                set(x, y + 1, z, corner || ((x + z) % 6 == 0) ? a.light() : a.wall());
                if (corner) {
                    for (int k = 2; k <= 4; k++) set(x, y + k, z, k == 4 ? a.light() : a.wall());
                }
            }
        }
        // Inner lip lights
        for (int x = p.x0() - 1; x <= p.x1() + 1; x += 4) {
            set(x, y, p.z0() - 1, a.light());
            set(x, y, p.z1() + 1, a.light());
        }
    }

    private void biome(Pool p) {
        Biome b = biome(p.area().biome());
        if (b == null) return;
        int pad = cfg.walkway + FishingBuilds.MARGIN + 3;
        Set<Long> chunks = new HashSet<>();
        for (int x = p.x0() - pad; x <= p.x1() + pad; x += 2) {
            for (int z = p.z0() - pad; z <= p.z1() + pad; z += 2) {
                for (int yy = p.wBot() - 4; yy <= cfg.baseY + 12; yy += 2) world.setBiome(x, yy, z, b);
                chunks.add(((long) (x >> 4) << 32) | ((z >> 4) & 0xffffffffL));
            }
        }
        for (long c : chunks) refresh((int) (c >> 32), (int) c);
    }

    @SuppressWarnings("deprecation")
    private void refresh(int cx, int cz) {
        try {
            world.refreshChunk(cx, cz);
        } catch (RuntimeException ignored) {
            // not supported: clients see the new water colour after the chunk reloads
        }
    }

    private static Biome biome(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            NamespacedKey key = name.contains(":") ? NamespacedKey.fromString(name) : NamespacedKey.minecraft(name.toLowerCase());
            if (key == null) return null;
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void set(int x, int y, int z, Material m) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;
        world.getBlockAt(x, y, z).setType(m, false);
    }

    // ------------------------------------------------------------------ displays

    /** Spawns signs, the Fish Market and the two leaderboards. Call after build(). */
    void spawnDisplays(ItemStack marketIcon) {
        removeDisplays();
        if (world == null) return;
        keepLoaded(0, 0);
        int y = cfg.baseY;
        // Hub: Fish Market stall on the west side, Dock Terminal on the east, boards north and south
        market(new Location(world, -4.5, y, 0.5), marketIcon, true);
        double[] ht = FishingBuilds.hubTerminal();
        terminal(new Location(world, ht[0], y + 1, ht[1]), "<gray>Right-click to fly to a pool");
        displays.add(text(new Location(world, 0.5, y + 2.6, 0.5), cfg.hubName
                + "\n<gray>Each pool is its own deck module.\n<gray>Fly there from the <white>Dock Terminal</white> (east)\n<dark_gray>/waters · /fish · /rod", 1.0f));
        weeklyBoard = text(new Location(world, 0.5, y + 2.2, -5.5), "<gray>Loading…", 0.9f);
        levelBoard = text(new Location(world, 0.5, y + 2.2, 6.5), "<gray>Loading…", 0.9f);
        displays.add(weeklyBoard);
        displays.add(levelBoard);
        FishingBuilds b = new FishingBuilds(world, cfg);
        for (Area a : cfg.areas.values()) {
            Pool p = pool(a);
            FishingBuilds.Room r = b.room(p);
            keepLoaded((r.x0() + 3) >> 4, r.cz() >> 4);
            keepLoaded(p.outerX0(cfg.walkway) >> 4, r.cz() >> 4);
            double[] t = FishingBuilds.terminalSpot(r), mk = FishingBuilds.marketSpot(r);
            terminal(new Location(world, t[0], y + 1, t[1]), "<gray>Right-click to travel:\n<gray>other pools or the " + cfg.hubName);
            market(new Location(world, mk[0], y + 1, mk[1]), marketIcon, false);
            displays.add(text(new Location(world, p.outerX0(cfg.walkway) + 0.5, y + 3.2, r.cz() + 0.5),
                    a.color() + "<bold>" + a.name().toUpperCase() + "</bold>\n<gray>Grade " + a.grade() + "+ · fishing deck "
                            + (a.index() + 1) + "\n<#8a8070><i>" + a.description(), 1.0f));
        }
    }

    /** A Fish Market: click box, a floating fish and its label. */
    private void market(Location at, ItemStack icon, boolean big) {
        float box = big ? 2.0f : 1.2f;
        displays.add(world.spawn(at, Interaction.class, e -> {
            e.setInteractionWidth(box);
            e.setInteractionHeight(big ? 2.5f : 1.8f);
            e.setResponsive(true);
            tag(e, "market");
        }));
        float s = big ? 1.6f : 0.9f;
        displays.add(world.spawn(at.clone().add(0, big ? 1.4 : 0.9, 0), ItemDisplay.class, e -> {
            e.setItemStack(icon);
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
            tag(e, "market_icon");
        }));
        displays.add(text(at.clone().add(0, big ? 2.8 : 1.7, 0),
                big ? "<#5fb4c8><bold>FISH MARKET</bold>\n<gray>Right-click to sell, gut,\n<gray>buy bait and upgrade your rod"
                        : "<#5fb4c8><bold>FISH MARKET</bold>\n<gray>Right-click to sell", big ? 1.0f : 0.8f));
    }

    /** A Dock Terminal: opens the travel menu. */
    private void terminal(Location at, String hint) {
        displays.add(world.spawn(at.clone().add(0, -1, 0), Interaction.class, e -> {
            e.setInteractionWidth(1.2f);
            e.setInteractionHeight(2.6f);
            e.setResponsive(true);
            tag(e, "terminal");
        }));
        displays.add(world.spawn(at.clone().add(0, 0.6, 0), ItemDisplay.class, e -> {
            e.setItemStack(new ItemStack(Material.RECOVERY_COMPASS));
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            tag(e, "terminal_icon");
        }));
        displays.add(text(at.clone().add(0, 1.3, 0), "<#ffb43a><bold>DOCK TERMINAL</bold>\n" + hint, 0.8f));
    }

    void boards(String weekly, String levels) {
        if (weeklyBoard != null && weeklyBoard.isValid()) weeklyBoard.text(dev.spacepunk.prison.util.Text.mm(weekly));
        if (levelBoard != null && levelBoard.isValid()) levelBoard.text(dev.spacepunk.prison.util.Text.mm(levels));
    }

    private TextDisplay text(Location l, String mm, float scale) {
        return world.spawn(l, TextDisplay.class, e -> {
            e.text(dev.spacepunk.prison.util.Text.mm(mm));
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setDefaultBackground(false);
            e.setBackgroundColor(org.bukkit.Color.fromARGB(150, 16, 14, 14));
            e.setLineWidth(240);
            if (scale != 1.0f) {
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            }
            tag(e, "text");
        });
    }

    private void tag(Entity e, String what) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, what);
    }

    boolean isMarket(Entity e) {
        return "market".equals(e.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING));
    }

    boolean isTerminal(Entity e) {
        return "terminal".equals(e.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING));
    }

    private void keepLoaded(int cx, int cz) {
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                long key = ((long) (cx + dx) << 32) | ((cz + dz) & 0xffffffffL);
                if (tickets.add(key)) {
                    Chunk c = world.getChunkAt(cx + dx, cz + dz);
                    c.addPluginChunkTicket(plugin);
                }
            }
        }
    }

    void removeDisplays() {
        for (Entity e : displays) if (e.isValid()) e.remove();
        displays.clear();
        weeklyBoard = null;
        levelBoard = null;
        if (world != null) {
            // Leftovers from a crash, just in case
            for (Entity e : world.getEntities()) {
                if (e.getPersistentDataContainer().has(displayKey, PersistentDataType.STRING)) e.remove();
            }
        }
    }

    void shutdown() {
        removeDisplays();
        if (world != null) world.removePluginChunkTickets(plugin);
        tickets.clear();
    }

    /** Void generator for the waters world. */
    static final class Generator extends ChunkGenerator {
        private final int spawnY;

        Generator(int spawnY) { this.spawnY = spawnY; }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
            return new Location(world, 0.5, spawnY, 0.5);
        }
    }
}
