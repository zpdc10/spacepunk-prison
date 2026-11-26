package dev.spacepunk.prison.mines;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/** Empty world: the mines world is nothing but the mine shells the plugin builds. */
public final class VoidGenerator extends ChunkGenerator {
    private final int spawnY;

    public VoidGenerator(int spawnY) { this.spawnY = spawnY; }

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
