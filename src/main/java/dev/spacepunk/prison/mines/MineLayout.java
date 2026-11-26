package dev.spacepunk.prison.mines;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto layout for a fresh test server: one row per deck, one column per mine in that deck. Each mine is a pit
 * with walls, floor and wall lights, a walkway around it with a lit landing pad at the spawn on the north side,
 * and (by default) a closed room around it all: outer walls and a lit ceiling.
 */
final class MineLayout {
    /** Bump when the generated structure changes; placed auto-layout mines are rebuilt on the next start. */
    static final int VERSION = 3;

    private MineLayout() {}

    /** Region a mine gets in the auto layout. */
    static Region regionFor(MinesConfig cfg, Mine mine, World world) {
        MinesConfig.Settings s = cfg.settings();
        List<String> deckIds = new ArrayList<>(cfg.decks().keySet());
        int idx = deckIds.indexOf(mine.def().deck());
        int row = idx < 0 ? deckIds.size() : idx; // unassigned mines go on their own row
        int col = 0;
        for (MinesConfig.MineDef d : cfg.mines().values()) {
            if (d.id().equals(mine.id())) break;
            if (d.deck().equals(mine.def().deck())) col++;
        }
        MinesConfig.Deck deck = mine.deck();
        int sx = deck == null ? 33 : deck.sizeX(), sy = deck == null ? 24 : deck.sizeY(), sz = deck == null ? 33 : deck.sizeZ();
        int cx = s.originX() + col * s.spacing(), cz = s.originZ() + row * s.spacing();
        int minX = cx - sx / 2, minZ = cz - sz / 2, minY = s.baseY();
        return new Region(world.getName(), minX, minY, minZ, minX + sx - 1, minY + sy - 1, minZ + sz - 1);
    }

    /** Spawn on the north rim, facing the pit. */
    static Location spawnFor(MinesConfig cfg, Region r, World world) {
        int rim = Math.max(1, cfg.settings().rimWidth());
        double x = (r.minX() + r.maxX()) / 2.0 + 0.5;
        double z = r.minZ() - 1 - (rim + 1) / 2 + 0.5;
        return new Location(world, x, r.maxY() + 1, z, 0f, 20f);
    }

    /** Builds the shell around the region (the region itself is left for the refiller). */
    static void buildShell(MinesConfig cfg, Mine mine, Region r, World world) {
        MinesConfig.Deck deck = mine.deck();
        Material wall = deck == null ? Material.POLISHED_DEEPSLATE : deck.wall();
        Material rimMat = deck == null ? Material.SMOOTH_STONE : deck.rim();
        Material light = deck == null ? Material.SEA_LANTERN : deck.light();
        int rim = Math.max(1, cfg.settings().rimWidth());

        int x0 = r.minX() - 1, x1 = r.maxX() + 1, z0 = r.minZ() - 1, z1 = r.maxZ() + 1;
        // Floor
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) set(world, x, r.minY() - 1, z, wall);
        // Walls with a light every 4 blocks on every 5th layer
        for (int y = r.minY(); y <= r.maxY(); y++) {
            boolean lightLayer = (r.maxY() - y) % 5 == 2;
            for (int x = x0; x <= x1; x++) {
                set(world, x, y, z0, lightLayer && x % 4 == 0 ? light : wall);
                set(world, x, y, z1, lightLayer && x % 4 == 0 ? light : wall);
            }
            for (int z = z0; z <= z1; z++) {
                set(world, x0, y, z, lightLayer && z % 4 == 0 ? light : wall);
                set(world, x1, y, z, lightLayer && z % 4 == 0 ? light : wall);
            }
        }
        // Rim walkway at the top layer, with its support layer underneath.
        int ox0 = x0 - rim, ox1 = x1 + rim, oz0 = z0 - rim, oz1 = z1 + rim;
        for (int x = ox0; x <= ox1; x++) {
            for (int z = oz0; z <= oz1; z++) {
                boolean inPit = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                if (inPit) continue;
                set(world, x, r.maxY(), z, rimMat);
                set(world, x, r.maxY() - 1, z, wall);
            }
        }
        // Top of the pit walls flush with the rim
        for (int x = x0; x <= x1; x++) { set(world, x, r.maxY(), z0, rimMat); set(world, x, r.maxY(), z1, rimMat); }
        for (int z = z0; z <= z1; z++) { set(world, x0, r.maxY(), z, rimMat); set(world, x1, r.maxY(), z, rimMat); }
        buildLadders(cfg, r, world, wall);

        Location spawn = spawnFor(cfg, r, world);
        int sx = spawn.getBlockX(), sz = spawn.getBlockZ();
        // Landing pad: a lit 3x3 under the spawn so the entry point is easy to find.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(world, sx + dx, r.maxY(), sz + dz, light);

        if (!cfg.settings().enclosed()) {
            // Open pit: low parapet on the outer edge with lights on the corners.
            for (int x = ox0; x <= ox1; x++) for (int z = oz0; z <= oz1; z++) {
                boolean edge = x == ox0 || x == ox1 || z == oz0 || z == oz1;
                if (!edge) continue;
                boolean corner = (x == ox0 || x == ox1) && (z == oz0 || z == oz1);
                set(world, x, r.maxY() + 1, z, corner ? light : wall);
            }
            return;
        }
        buildRoom(cfg, r, world, ox0, ox1, oz0, oz1, sx, wall, rimMat, light);
    }

    /**
     * Closes the mine into a room: outer walls from the walkway up to a ceiling over the whole pit, with support
     * pillars, a light band, ceiling beams and a grid of ceiling lights. If a parkour catwalk already leads away
     * from the north wall at the spawn, its 3x2 doorway is left open so the course stays reachable.
     */
    private static void buildRoom(MinesConfig cfg, Region r, World world, int ox0, int ox1, int oz0, int oz1, int sx,
                                  Material wall, Material trim, Material light) {
        int floorY = r.maxY();
        int ceilY = floorY + cfg.settings().roomHeight();
        int bandY = floorY + Math.max(2, cfg.settings().roomHeight() / 2);
        boolean doorway = false;
        for (int dx = -1; dx <= 1 && !doorway; dx++) {
            int y = floorY;
            if (y >= world.getMinHeight() && y < world.getMaxHeight()
                    && !world.getBlockAt(sx + dx, y, oz0 - 1).getType().isAir()) doorway = true;
        }
        // Outer walls
        for (int y = floorY + 1; y < ceilY; y++) {
            for (int x = ox0; x <= ox1; x++) {
                boolean door = doorway && Math.abs(x - sx) <= 1 && y <= floorY + 2;
                if (!door) set(world, x, y, oz0, roomWall(x - ox0, y, floorY, bandY, wall, trim, light));
                set(world, x, y, oz1, roomWall(x - ox0, y, floorY, bandY, wall, trim, light));
            }
            for (int z = oz0 + 1; z < oz1; z++) {
                set(world, ox0, y, z, roomWall(z - oz0, y, floorY, bandY, wall, trim, light));
                set(world, ox1, y, z, roomWall(z - oz0, y, floorY, bandY, wall, trim, light));
            }
        }
        // Ceiling with a light grid, and beams one block below it every 8 blocks
        for (int x = ox0; x <= ox1; x++) {
            for (int z = oz0; z <= oz1; z++) {
                boolean lamp = (x - ox0) % 6 == 3 && (z - oz0) % 6 == 3;
                set(world, x, ceilY, z, lamp ? light : wall);
                boolean beam = (z - oz0) % 8 == 0 && x > ox0 && x < ox1 && z > oz0 && z < oz1;
                if (beam) set(world, x, ceilY - 1, z, trim);
            }
        }
    }

    /** Wall block: pillars of trim every 6 blocks, a light band at mid height, a trim skirting row at the floor. */
    private static Material roomWall(int along, int y, int floorY, int bandY, Material wall, Material trim, Material light) {
        boolean pillar = along % 6 == 0;
        if (y == bandY) return pillar ? light : trim;
        if (pillar || y == floorY + 1) return trim;
        return wall;
    }

    /**
     * Ladders from the pit floor to the walkway, set into the pit walls so players can always climb out. Each
     * ladder takes the place of a wall block (outside the refill region, so refills never touch it, and outside
     * the pit, so map protection keeps it), with a backing block behind it. They are spread evenly along every wall.
     */
    private static void buildLadders(MinesConfig cfg, Region r, World world, Material backing) {
        int per = cfg.settings().laddersPerWall();
        if (per <= 0) return;
        int x0 = r.minX() - 1, x1 = r.maxX() + 1, z0 = r.minZ() - 1, z1 = r.maxZ() + 1;
        for (int i = 1; i <= per; i++) {
            int lx = r.minX() + (int) Math.round((double) i * (r.sizeX() - 1) / (per + 1));
            int lz = r.minZ() + (int) Math.round((double) i * (r.sizeZ() - 1) / (per + 1));
            ladder(world, r, x0, lz, x0 - 1, lz, BlockFace.EAST, backing);
            ladder(world, r, x1, lz, x1 + 1, lz, BlockFace.WEST, backing);
            ladder(world, r, lx, z0, lx, z0 - 1, BlockFace.SOUTH, backing);
            ladder(world, r, lx, z1, lx, z1 + 1, BlockFace.NORTH, backing);
        }
    }

    private static void ladder(World w, Region r, int x, int z, int bx, int bz, BlockFace facing, Material backing) {
        Directional data = (Directional) Material.LADDER.createBlockData();
        data.setFacing(facing);
        // Backing below the walkway support (the support and walkway layers are already solid there).
        for (int y = r.minY() - 1; y <= r.maxY() - 2; y++) set(w, bx, y, bz, backing);
        for (int y = r.minY(); y <= r.maxY(); y++) {
            if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;
            w.getBlockAt(x, y, z).setBlockData(data, false);
        }
    }

    private static void set(World w, int x, int y, int z, Material m) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight()) return;
        w.getBlockAt(x, y, z).setType(m, false);
    }
}
