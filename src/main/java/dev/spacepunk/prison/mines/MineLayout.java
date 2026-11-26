package dev.spacepunk.prison.mines;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto layout for a fresh test server: one row per deck, one column per mine in that deck, each mine a pit
 * with walls, floor, a walkway rim with a low parapet, wall lights, and a spawn on the north rim.
 */
final class MineLayout {
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
        // Rim walkway at the top layer, parapet one block up on the outer edge, lights on the corners
        int ox0 = x0 - rim, ox1 = x1 + rim, oz0 = z0 - rim, oz1 = z1 + rim;
        for (int x = ox0; x <= ox1; x++) {
            for (int z = oz0; z <= oz1; z++) {
                boolean inPit = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                if (inPit) continue;
                set(world, x, r.maxY(), z, rimMat);
                set(world, x, r.maxY() - 1, z, wall);
                boolean edge = x == ox0 || x == ox1 || z == oz0 || z == oz1;
                if (edge) {
                    boolean corner = (x == ox0 || x == ox1) && (z == oz0 || z == oz1);
                    set(world, x, r.maxY() + 1, z, corner ? light : wall);
                }
            }
        }
        // Top of the pit walls flush with the rim
        for (int x = x0; x <= x1; x++) { set(world, x, r.maxY(), z0, rimMat); set(world, x, r.maxY(), z1, rimMat); }
        for (int z = z0; z <= z1; z++) { set(world, x0, r.maxY(), z, rimMat); set(world, x1, r.maxY(), z, rimMat); }
    }

    private static void set(World w, int x, int y, int z, Material m) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight()) return;
        w.getBlockAt(x, y, z).setType(m, false);
    }
}
