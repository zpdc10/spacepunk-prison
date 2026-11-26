package dev.spacepunk.prison.mines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Inclusive axis-aligned block box in one world. Immutable. */
public record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Region of(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Region(world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }

    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

    public World bukkitWorld() { return Bukkit.getWorld(world); }

    public boolean contains(String worldName, int x, int y, int z) {
        return world.equals(worldName)
                && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Block b) {
        return contains(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    /** True if the location is inside the box grown by the given margins (horizontal, below, above). */
    public boolean near(Location l, int horizontal, int below, int above) {
        if (l.getWorld() == null || !world.equals(l.getWorld().getName())) return false;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= minX - horizontal && x <= maxX + horizontal
                && z >= minZ - horizontal && z <= maxZ + horizontal
                && y >= minY - below && y <= maxY + above;
    }

    /** Standing spot in the middle of the top face. */
    public Location topCenter() {
        World w = bukkitWorld();
        return new Location(w, (minX + maxX) / 2.0 + 0.5, maxY + 1, (minZ + maxZ) / 2.0 + 0.5);
    }
}
