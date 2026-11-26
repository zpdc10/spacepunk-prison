package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.undercroft.UndercroftConfig.LabSite;
import dev.spacepunk.prison.undercroft.UndercroftConfig.Ring;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.logging.Level;

/**
 * The Undercroft world: creation, zone lookup (checkpoint / ring by X), and the generated layout.
 * Layout: a long hull corridor running east. Checkpoint room at the west end, then each ring behind a bulkhead
 * with a gate gap, cover blocks, hot ore veins, a drug lab per ring, and a ceiling with deck lights.
 */
final class UndercroftWorld {
    private final Plugin plugin;
    private UndercroftConfig cfg;

    UndercroftWorld(Plugin plugin, UndercroftConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    void config(UndercroftConfig cfg) { this.cfg = cfg; }

    World world() { return plugin.getServer().getWorld(cfg.world); }

    boolean isWorld(World w) { return w != null && w.getName().equals(cfg.world); }

    World loadOrCreate() {
        World w = world();
        if (w == null) {
            try {
                w = new WorldCreator(cfg.world).generator(new Empty(cfg.floorY + 1)).createWorld();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "[undercroft] could not load world " + cfg.world, e);
                return null;
            }
        }
        if (w != null) {
            w.setDifficulty(cfg.difficulty);
            w.setSpawnLocation(checkpointSpawn(w));
        }
        return w;
    }

    /** -1 outside the Undercroft, 0 in the checkpoint, 1..n in a ring. */
    int ringAt(Location loc) {
        if (loc == null || !isWorld(loc.getWorld())) return -1;
        double x = loc.getX();
        if (x < cfg.checkpointXMax) return 0;
        for (Ring r : cfg.rings) if (x < r.xMax()) return r.number();
        return cfg.rings.size();
    }

    Location checkpointSpawn(World w) {
        Location l = new Location(w, cfg.checkpointXMin + 4.5, cfg.floorY + 1, 0.5);
        l.setYaw(-90f);
        return l;
    }

    /** The gate terminal: right-click it to go through the scanner. */
    Block terminal(World w) { return w.getBlockAt(cfg.checkpointXMin + 2, cfg.floorY + 1, 3); }

    Location labLocation(World w, LabSite s) { return new Location(w, s.x() + 0.5, cfg.floorY + 1, s.z() + 0.5); }

    int ringOfX(int x) {
        if (x < cfg.checkpointXMax) return 0;
        for (Ring r : cfg.rings) if (x < r.xMax()) return r.number();
        return cfg.rings.size();
    }

    // ---- generation ----

    /** Builds (or rebuilds) the whole layout. Records vein positions in the store. Main thread; takes a moment. */
    void generate(UndercroftStore store) {
        World w = loadOrCreate();
        if (w == null) return;
        Random rng = new Random(0x5C0FF);
        int f = cfg.floorY, top = f + cfg.ceilingHeight, hw = cfg.halfWidth;
        int x0 = cfg.checkpointXMin - 1, x1 = cfg.xEnd();
        // Clear the volume, then floor, ceiling and outer walls.
        for (int x = x0; x <= x1; x++) {
            for (int z = -hw - 1; z <= hw + 1; z++) {
                for (int y = f; y <= top; y++) w.getBlockAt(x, y, z).setType(Material.AIR, false);
                int ring = ringOfX(x);
                boolean edge = x == x0 || x == x1 || Math.abs(z) == hw + 1;
                w.getBlockAt(x, f - 1, z).setType(Material.POLISHED_DEEPSLATE, false);
                w.getBlockAt(x, f, z).setType(floor(ring, x, z, rng), false);
                w.getBlockAt(x, top, z).setType(ceiling(ring, x, z), false);
                if (edge) for (int y = f; y <= top; y++) w.getBlockAt(x, y, z).setType(wall(ring, y - f), false);
            }
        }
        // Bulkheads between zones, each with a gate gap in the middle.
        bulkhead(w, cfg.checkpointXMax - 1, 2, Material.POLISHED_ANDESITE);
        for (int i = 0; i < cfg.rings.size() - 1; i++) bulkhead(w, cfg.rings.get(i).xMax() - 1, 3, Material.POLISHED_BLACKSTONE_BRICKS);

        buildCheckpoint(w);
        store.veins.clear();
        for (Ring r : cfg.rings) {
            cover(w, r, rng);
            veins(w, r, rng, store);
        }
        for (LabSite s : cfg.labs.values()) buildLab(w, s);
        store.generated = true;
        store.save();
        w.setSpawnLocation(checkpointSpawn(w));
    }

    private Material floor(int ring, int x, int z, Random rng) {
        if (ring == 0) return (x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;
        if (Math.abs(z) <= 1 && x % 4 == 0) return Material.WAXED_WEATHERED_CUT_COPPER; // walkway stripe
        int roll = rng.nextInt(100);
        if (ring >= 3) return roll < 12 ? Material.POLISHED_BASALT : roll < 25 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.BLACKSTONE;
        if (ring == 2) return roll < 15 ? Material.POLISHED_TUFF : roll < 30 ? Material.TUFF : Material.DEEPSLATE;
        return roll < 15 ? Material.COBBLESTONE : roll < 30 ? Material.ANDESITE : Material.DEEPSLATE;
    }

    private Material ceiling(int ring, int x, int z) {
        if (x % 8 == 0 && Math.floorMod(z, 8) == 4) {
            if (ring == 0) return Material.SEA_LANTERN;
            Ring r = cfg.ring(ring);
            return r == null ? Material.OCHRE_FROGLIGHT : r.light();
        }
        return ring == 0 ? Material.POLISHED_ANDESITE : Material.POLISHED_DEEPSLATE;
    }

    private Material wall(int ring, int dy) {
        if (dy == 0 || dy == cfg.ceilingHeight - 1) return Material.POLISHED_BLACKSTONE;
        if (ring == 0) return Material.POLISHED_DIORITE;
        if (ring >= 3) return dy % 4 == 0 ? Material.POLISHED_BASALT : Material.POLISHED_BLACKSTONE_BRICKS;
        return dy % 4 == 0 ? Material.WAXED_WEATHERED_CUT_COPPER : Material.POLISHED_DEEPSLATE;
    }

    private void bulkhead(World w, int x, int gap, Material mat) {
        int f = cfg.floorY, top = f + cfg.ceilingHeight;
        for (int z = -cfg.halfWidth; z <= cfg.halfWidth; z++) {
            for (int y = f + 1; y < top; y++) {
                boolean opening = Math.abs(z) <= gap && y <= f + 4;
                Block b = w.getBlockAt(x, y, z);
                if (opening) b.setType(Material.AIR, false);
                else if (Math.abs(z) == gap + 1 && y <= f + 5) b.setType(Material.WAXED_WEATHERED_CUT_COPPER, false);
                else b.setType(mat, false);
            }
        }
    }

    private void buildCheckpoint(World w) {
        int f = cfg.floorY;
        int xa = cfg.checkpointXMin, xb = cfg.checkpointXMax - 2;
        // Guard booth along the north wall and a hazard line at the gate.
        for (int x = xa + 1; x <= xa + 3; x++) {
            for (int z = 2; z <= 4; z++) w.getBlockAt(x, f, z).setType(Material.POLISHED_BLACKSTONE, false);
        }
        terminal(w).setType(Material.LODESTONE, false);
        w.getBlockAt(xa + 2, f + 2, 4).setType(Material.SEA_LANTERN, false);
        for (int z = -3; z <= 3; z++) w.getBlockAt(xb, f, z).setType(Material.YELLOW_TERRACOTTA, false);
        // Benches and lockers for flavour.
        for (int x = xa + 6; x <= xb - 2; x += 3) {
            w.getBlockAt(x, f + 1, -cfg.halfWidth).setType(Material.POLISHED_ANDESITE_SLAB, false);
            w.getBlockAt(x, f + 1, cfg.halfWidth).setType(Material.POLISHED_ANDESITE_SLAB, false);
        }
    }

    private boolean nearLab(int x, int z) {
        for (LabSite s : cfg.labs.values()) if (Math.abs(s.x() - x) <= 3 && Math.abs(s.z() - z) <= 3) return true;
        return false;
    }

    private void cover(World w, Ring r, Random rng) {
        int f = cfg.floorY, hw = cfg.halfWidth;
        for (int i = 0; i < r.cover(); i++) {
            int x = r.xMin() + 3 + rng.nextInt(Math.max(1, r.xMax() - r.xMin() - 6));
            int z = -hw + 2 + rng.nextInt(hw * 2 - 3);
            if (Math.abs(z) <= 3 || nearLab(x, z)) continue; // keep the centre lane and labs open
            int kind = rng.nextInt(4);
            switch (kind) {
                case 0 -> { // pillar to the ceiling
                    for (int y = f + 1; y < f + cfg.ceilingHeight; y++)
                        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++)
                            w.getBlockAt(x + dx, y, z + dz).setType(r.number() >= 3 ? Material.POLISHED_BASALT : Material.POLISHED_DEEPSLATE, false);
                }
                case 1 -> { // low wall
                    boolean alongX = rng.nextBoolean();
                    for (int k = 0; k < 5; k++)
                        for (int y = f + 1; y <= f + 2; y++)
                            w.getBlockAt(alongX ? x + k : x, y, alongX ? z : z + k).setType(Material.WAXED_WEATHERED_CUT_COPPER, false);
                }
                case 2 -> { // crate stack
                    int h = 1 + rng.nextInt(3);
                    for (int y = f + 1; y <= f + h; y++) w.getBlockAt(x, y, z).setType(Material.RAW_IRON_BLOCK, false);
                    w.getBlockAt(x + 1, f + 1, z).setType(Material.RAW_COPPER_BLOCK, false);
                }
                default -> { // broken pipe run
                    for (int k = 0; k < 4; k++) w.getBlockAt(x + k, f + 1, z).setType(Material.POLISHED_TUFF, false);
                    w.getBlockAt(x + 1, f + 2, z).setType(Material.POLISHED_TUFF, false);
                }
            }
        }
    }

    private void veins(World w, Ring r, Random rng, UndercroftStore store) {
        int f = cfg.floorY, hw = cfg.halfWidth;
        for (int i = 0; i < r.veins(); i++) {
            int cx = r.xMin() + 4 + rng.nextInt(Math.max(1, r.xMax() - r.xMin() - 8));
            int cz = -hw + 3 + rng.nextInt(hw * 2 - 5);
            if (Math.abs(cz) <= 2 || nearLab(cx, cz)) cz += cz < 0 ? -5 : 5;
            int placed = 0, tries = 0;
            while (placed < r.veinSize() && tries++ < r.veinSize() * 6) {
                int x = cx + rng.nextInt(3) - 1, z = cz + rng.nextInt(3) - 1, y = f + 1 + (rng.nextInt(3) == 0 ? 1 : 0);
                if (Math.abs(z) > hw) continue;
                Block b = w.getBlockAt(x, y, z);
                if (b.getType() == r.oreBlock()) continue;
                if (y > f + 1 && w.getBlockAt(x, y - 1, z).getType() != r.oreBlock()) y = f + 1;
                b = w.getBlockAt(x, y, z);
                if (b.getType() == r.oreBlock()) continue;
                b.setType(r.oreBlock(), false);
                store.veins.add(new UndercroftStore.Vein(x, y, z, r.number()));
                placed++;
            }
        }
    }

    private void buildLab(World w, LabSite s) {
        int f = cfg.floorY;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            for (int y = f + 1; y <= f + 4; y++) w.getBlockAt(s.x() + dx, y, s.z() + dz).setType(Material.AIR, false);
            w.getBlockAt(s.x() + dx, f, s.z() + dz).setType(Math.abs(dx) == 2 || Math.abs(dz) == 2
                    ? Material.YELLOW_TERRACOTTA : Material.POLISHED_BLACKSTONE, false);
        }
        w.getBlockAt(s.x(), f + 1, s.z()).setType(cfg.labBlock, false);
        w.getBlockAt(s.x() + 1, f + 1, s.z()).setType(Material.CAULDRON, false);
        w.getBlockAt(s.x() - 1, f + 1, s.z()).setType(Material.SMOKER, false);
        for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2}) {
            w.getBlockAt(s.x() + dx, f + 1, s.z() + dz).setType(Material.POLISHED_BLACKSTONE_WALL, false);
            w.getBlockAt(s.x() + dx, f + 2, s.z() + dz).setType(Material.SHROOMLIGHT, false);
        }
    }

    /** Puts every recorded vein back to its ore block (startup and admin). */
    void regrowAll(UndercroftStore store) {
        World w = world();
        if (w == null) return;
        for (UndercroftStore.Vein v : store.veins) {
            Ring r = cfg.ring(v.ring());
            if (r != null) w.getBlockAt(v.x(), v.y(), v.z()).setType(r.oreBlock(), false);
        }
    }

    /** Empty void world. */
    static final class Empty extends ChunkGenerator {
        private final int spawnY;

        Empty(int spawnY) { this.spawnY = spawnY; }

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
