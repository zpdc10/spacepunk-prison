package dev.spacepunk.prison.station;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * Dense 3D block buffer for the hub build. The layout code draws into it (later writes win), then
 * {@link StationBuilder} copies it into the world a slice at a time. Every cell starts as air, so building
 * also clears whatever an older version of the hub left behind.
 */
final class Canvas {
    static final BlockData AIR = Material.AIR.createBlockData();

    final int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private final BlockData[] cells;

    Canvas(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.sizeX = maxX - minX + 1; this.sizeY = maxY - minY + 1; this.sizeZ = maxZ - minZ + 1;
        this.cells = new BlockData[sizeX * sizeY * sizeZ];
        java.util.Arrays.fill(cells, AIR);
    }

    int maxX() { return minX + sizeX - 1; }
    int maxY() { return minY + sizeY - 1; }
    int maxZ() { return minZ + sizeZ - 1; }
    int volume() { return cells.length; }

    private int index(int x, int y, int z) {
        int ix = x - minX, iy = y - minY, iz = z - minZ;
        if (ix < 0 || iy < 0 || iz < 0 || ix >= sizeX || iy >= sizeY || iz >= sizeZ) return -1;
        return (iy * sizeZ + iz) * sizeX + ix;
    }

    void set(int x, int y, int z, BlockData b) {
        int i = index(x, y, z);
        if (i >= 0 && b != null) cells[i] = b;
    }

    BlockData get(int x, int y, int z) {
        int i = index(x, y, z);
        return i < 0 ? AIR : cells[i];
    }

    boolean isAir(int x, int y, int z) { return get(x, y, z).getMaterial().isAir(); }

    /** Cell by flat index, ordered y, then z, then x. */
    BlockData at(int i) { return cells[i]; }
    int xOf(int i) { return minX + i % sizeX; }
    int zOf(int i) { return minZ + (i / sizeX) % sizeZ; }
    int yOf(int i) { return minY + i / (sizeX * sizeZ); }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockData b) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) set(x, y, z, b);
    }

    /** Block data from a string such as "minecraft:lantern[hanging=true]", trying each until one parses. */
    static BlockData data(String... candidates) {
        for (String s : candidates) {
            try {
                return Bukkit.createBlockData(s);
            } catch (IllegalArgumentException ignored) {
                // Unknown block or state in this version: try the next one.
            }
        }
        return Material.STONE.createBlockData();
    }
}
