package dev.spacepunk.prison.mines;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockType;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.logging.Level;

/**
 * FastAsyncWorldEdit refiller: places the blocks off the main thread. Only loaded (by reflection) when the
 * FastAsyncWorldEdit plugin is enabled, so the plugin still runs without FAWE installed.
 */
final class FaweRefiller implements Refiller {
    private final Plugin plugin;

    FaweRefiller(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "FastAsyncWorldEdit"; }

    @Override
    public void fill(Region region, BlockMix mix, Runnable onDone) {
        World world = region.bukkitWorld();
        if (world == null) { onDone.run(); return; }
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        RandomPattern pattern = new RandomPattern();
        for (Map.Entry<Material, Double> e : mix.weights().entrySet()) {
            BlockType type = BukkitAdapter.asBlockType(e.getKey());
            if (type != null) pattern.add(type.getDefaultState(), e.getValue());
        }
        CuboidRegion cuboid = new CuboidRegion(weWorld,
                BlockVector3.at(region.minX(), region.minY(), region.minZ()),
                BlockVector3.at(region.maxX(), region.maxY(), region.maxZ()));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .fastMode(true)
                    .limitUnlimited()
                    .changeSetNull()
                    .checkMemory(false)
                    .allowedRegionsEverywhere()
                    .build()) {
                session.setBlocks((com.sk89q.worldedit.regions.Region) cuboid, pattern);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "[mines] FAWE refill failed", t);
            } finally {
                if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, onDone);
            }
        });
    }
}
