package dev.spacepunk.prison.station;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/** Empty void: the station world is nothing but the hub the plugin builds. */
final class StationWorld extends ChunkGenerator {
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, HubLayout.SPAWN_X, HubLayout.FLOOR, HubLayout.SPAWN_Z, HubLayout.SPAWN_YAW, 0f);
    }
}
