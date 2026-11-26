package dev.spacepunk.prison.workshop;

import dev.spacepunk.prison.workshop.BayData.MachineData;
import dev.spacepunk.prison.workshop.PlotRegistry.Plot;
import dev.spacepunk.prison.workshop.WorkshopConfig.BayLevel;
import dev.spacepunk.prison.workshop.WorkshopConfig.Shell;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The workshop world: an empty void with one hull bay per player on a grid. Builds and grows bay shells,
 * answers "whose bay is this block in", and keeps machine model displays in sync with the data.
 */
final class BayWorld {
    private final Plugin plugin;
    private final Supplier<WorkshopConfig> cfg;
    private final NamespacedKey displayKey;
    private World world;

    BayWorld(Plugin plugin, Supplier<WorkshopConfig> cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.displayKey = new NamespacedKey(plugin, "ws_display");
    }

    World world() {
        if (world == null) {
            WorkshopConfig c = cfg.get();
            World w = Bukkit.getWorld(c.worldName);
            if (w == null) {
                w = new WorldCreator(c.worldName).generator(new VoidGen(c.floorY + 1)).environment(World.Environment.NORMAL)
                        .generateStructures(false).createWorld();
            }
            if (w != null) {
                w.setDifficulty(Difficulty.PEACEFUL);
                w.setSpawnFlags(false, false);
                w.setStorm(false);
                w.setThundering(false);
                w.setTime(18000);
            }
            world = w;
        }
        return world;
    }

    boolean isWorld(World w) { return w != null && world != null && w.getUID().equals(world.getUID()); }

    /** Called every second: keeps the bay sky a starry night with no weather (no gamerules needed). */
    void keepNight() {
        if (world == null) return;
        if (Math.abs(world.getTime() - 18000) > 40) world.setTime(18000);
        if (world.hasStorm()) world.setStorm(false);
    }

    // ---- geometry ----

    int centerX(Plot p) { return (p.index % cfg.get().gridWidth) * cfg.get().spacing; }

    int centerZ(Plot p) { return (p.index / cfg.get().gridWidth) * cfg.get().spacing; }

    /** Plot index under these block coordinates, or -1 if none. */
    int indexAt(int x, int z) {
        WorkshopConfig c = cfg.get();
        int gx = Math.floorDiv(x + c.spacing / 2, c.spacing);
        int gz = Math.floorDiv(z + c.spacing / 2, c.spacing);
        if (gx < 0 || gx >= c.gridWidth || gz < 0) return -1;
        return gz * c.gridWidth + gx;
    }

    /** True if the block is inside the bay's buildable interior. */
    boolean inInterior(Plot p, int x, int y, int z) {
        BayLevel l = cfg.get().level(p.level);
        int fy = cfg.get().floorY;
        return Math.abs(x - centerX(p)) <= l.half() && Math.abs(z - centerZ(p)) <= l.half() && y > fy && y <= fy + l.height();
    }

    /** True if the block is part of the bay's shell (floor, walls, ceiling). */
    boolean inShell(Plot p, int x, int y, int z) {
        BayLevel l = cfg.get().level(p.level);
        int fy = cfg.get().floorY;
        int h = l.half() + 1;
        boolean inBox = Math.abs(x - centerX(p)) <= h && Math.abs(z - centerZ(p)) <= h && y >= fy && y <= fy + l.height() + 1;
        return inBox && !inInterior(p, x, y, z);
    }

    Location spawn(Plot p) {
        BayLevel l = cfg.get().level(p.level);
        return new Location(world(), centerX(p) + 0.5, cfg.get().floorY + 1, centerZ(p) - l.half() + 1.5, 0f, 10f);
    }

    Block terminal(Plot p) {
        BayLevel l = cfg.get().level(p.level);
        return world().getBlockAt(centerX(p), cfg.get().floorY + 2, centerZ(p) - l.half() - 1);
    }

    // ---- building ----

    /** Loads the bay's chunks, then (re)builds its shell on the main thread. prevLevel = -1 for a new bay. */
    CompletableFuture<Void> build(Plot p, int prevLevel) {
        World w = world();
        BayLevel l = cfg.get().level(p.level);
        int h = l.half() + 1;
        int cx = centerX(p), cz = centerZ(p);
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int x = (cx - h) >> 4; x <= (cx + h) >> 4; x++) {
            for (int z = (cz - h) >> 4; z <= (cz + h) >> 4; z++) loads.add(w.getChunkAtAsync(x, z));
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((v, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        buildNow(p, prevLevel);
                        done.complete(null);
                    } catch (Exception e) {
                        done.completeExceptionally(e);
                    }
                }));
        return done;
    }

    /** Builds on the spot, loading chunks synchronously (small bays only: used when a caller needs the bay now). */
    void buildSync(Plot p, int prevLevel) {
        World w = world();
        BayLevel l = cfg.get().level(p.level);
        int h = l.half() + 1;
        int cx = centerX(p), cz = centerZ(p);
        for (int x = (cx - h) >> 4; x <= (cx + h) >> 4; x++) {
            for (int z = (cz - h) >> 4; z <= (cz + h) >> 4; z++) w.getChunkAt(x, z);
        }
        buildNow(p, prevLevel);
    }

    private void buildNow(Plot p, int prevLevel) {
        World w = world();
        WorkshopConfig c = cfg.get();
        Shell s = c.shell;
        int fy = c.floorY;
        int cx = centerX(p), cz = centerZ(p);
        BayLevel l = c.level(p.level);
        int h = l.half() + 1;
        int top = fy + l.height() + 1;

        if (prevLevel >= 0 && prevLevel != p.level) {
            BayLevel old = c.level(prevLevel);
            int oh = old.half() + 1;
            int otop = fy + old.height() + 1;
            for (int x = -oh; x <= oh; x++) {
                for (int z = -oh; z <= oh; z++) {
                    boolean ring = Math.abs(x) == oh || Math.abs(z) == oh;
                    for (int y = fy + 1; y <= otop; y++) {
                        if (!ring && y != otop) continue;
                        Block b = w.getBlockAt(cx + x, y, cz + z);
                        if (s.isShell(b.getType())) b.setType(Material.AIR, false);
                    }
                }
            }
        }

        for (int x = -h; x <= h; x++) {
            for (int z = -h; z <= h; z++) {
                boolean accent = Math.abs(x) == h || Math.abs(z) == h || x % 6 == 0 || z % 6 == 0;
                w.getBlockAt(cx + x, fy, cz + z).setType(accent ? s.floorAccent() : s.floor(), false);
                boolean light = x % 4 == 0 && z % 4 == 0 && Math.abs(x) < h && Math.abs(z) < h;
                w.getBlockAt(cx + x, top, cz + z).setType(light ? s.light() : s.ceiling(), false);
                boolean ring = Math.abs(x) == h || Math.abs(z) == h;
                if (!ring) continue;
                boolean pillar = (Math.abs(x) == h && Math.abs(z) == h) || (Math.abs(x) == h ? z % 4 == 0 : x % 4 == 0);
                for (int y = fy + 1; y < top; y++) {
                    Material m;
                    if (pillar) m = s.pillar();
                    else if (y == top - 1) m = s.wallTop();
                    else if (l.height() >= 6 && (y == fy + 3 || y == fy + 4)) m = s.window();
                    else m = s.wall();
                    w.getBlockAt(cx + x, y, cz + z).setType(m, false);
                }
            }
        }
        terminal(p).setType(s.terminal(), false);
        p.built = true;
    }

    // ---- machine displays ----

    Location machineLocation(MachineData m) { return new Location(world(), m.x, m.y, m.z); }

    Block machineBlock(MachineData m) { return world().getBlockAt(m.x, m.y, m.z); }

    void spawnDisplay(UUID owner, MachineData m, ItemStack model) {
        removeDisplay(m);
        Location loc = new Location(world(), m.x + 0.5, m.y + 0.5, m.z + 0.5);
        ItemDisplay d = world().spawn(loc, ItemDisplay.class, e -> {
            e.setItemStack(model);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(transform(m));
            e.setBrightness(new Display.Brightness(13, 0));
            e.setPersistent(true);
            e.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, owner + ":" + m.id);
        });
        m.display = d.getUniqueId().toString();
    }

    void updateDisplay(MachineData m, ItemStack model) {
        Entity e = display(m);
        if (e instanceof ItemDisplay d) {
            d.setItemStack(model);
            d.setTransformation(transform(m));
        }
    }

    void removeDisplay(MachineData m) {
        Entity e = display(m);
        if (e != null) e.remove();
        m.display = null;
    }

    private Entity display(MachineData m) {
        if (m.display == null) return null;
        try {
            return world().getEntity(UUID.fromString(m.display));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Transformation transform(MachineData m) {
        float angle = switch (m.facing) {
            case "EAST" -> -90f;
            case "SOUTH" -> 180f;
            case "WEST" -> 90f;
            default -> 0f;
        } + cfg.get().yawOffset;
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(angle));
        return new Transformation(new Vector3f(), q, new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf());
    }

    /**
     * Makes the world match the data for one bay (chunks must be loaded): every machine has its barrier block
     * and display, and stray displays left by crashes or edits are removed.
     */
    void reconcile(UUID owner, Plot p, BayData bay, java.util.function.Function<MachineData, ItemStack> model) {
        Set<String> known = new HashSet<>();
        for (MachineData m : bay.machines) {
            Block b = machineBlock(m);
            if (b.getType() != Material.BARRIER) b.setType(Material.BARRIER, false);
            Entity e = display(m);
            if (!(e instanceof ItemDisplay)) spawnDisplay(owner, m, model.apply(m));
            known.add(m.display);
        }
        BayLevel l = cfg.get().level(p.level);
        int h = l.half() + 2;
        int fy = cfg.get().floorY;
        BoundingBox box = new BoundingBox(centerX(p) - h, fy - 1, centerZ(p) - h, centerX(p) + h + 1, fy + l.height() + 3, centerZ(p) + h + 1);
        for (Entity e : world().getNearbyEntities(box)) {
            if (!(e instanceof ItemDisplay)) continue;
            String tag = e.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING);
            if (tag != null && !known.contains(e.getUniqueId().toString())) e.remove();
        }
    }

    static BlockFace face(String facing) {
        try {
            return BlockFace.valueOf(facing);
        } catch (IllegalArgumentException e) {
            return BlockFace.NORTH;
        }
    }

    /** Empty world generator. */
    private static final class VoidGen extends ChunkGenerator {
        private final int spawnY;

        VoidGen(int spawnY) { this.spawnY = spawnY; }

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
