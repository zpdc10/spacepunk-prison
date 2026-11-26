package dev.spacepunk.prison.station;

import dev.spacepunk.prison.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;

/** Copies a {@link HubLayout} into the world over several ticks, then replaces the hub's text displays. */
final class StationBuilder {
    static final String DISPLAY_TAG = "spacepunk_station";

    private StationBuilder() {}

    static void build(Plugin plugin, World world, HubLayout layout, int blocksPerTick, Runnable done) {
        Canvas c = layout.canvas;
        // Load every chunk first so the copy and the display cleanup see the whole hub.
        for (int cx = c.minX >> 4; cx <= c.maxX() >> 4; cx++)
            for (int cz = c.minZ >> 4; cz <= c.maxZ() >> 4; cz++) world.getChunkAt(cx, cz);
        removeDisplays(world);

        new BukkitRunnable() {
            int i = 0;

            @Override public void run() {
                int end = Math.min(c.volume(), i + Math.max(1000, blocksPerTick));
                for (; i < end; i++) {
                    BlockData b = c.at(i);
                    var block = world.getBlockAt(c.xOf(i), c.yOf(i), c.zOf(i));
                    if (!block.getBlockData().equals(b)) block.setBlockData(b, false);
                }
                if (i >= c.volume()) {
                    cancel();
                    spawnDisplays(world, layout);
                    done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    static void removeDisplays(World world) {
        for (Entity e : new ArrayList<>(world.getEntitiesByClass(TextDisplay.class))) {
            if (e.getScoreboardTags().contains(DISPLAY_TAG)) e.remove();
        }
    }

    private static void spawnDisplays(World world, HubLayout layout) {
        for (HubLayout.Label l : layout.labels) {
            Location at = new Location(world, l.x(), l.y(), l.z());
            world.spawn(at, TextDisplay.class, td -> {
                td.text(Text.mm(l.text()));
                td.setBillboard(Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setLineWidth(240);
                td.setBackgroundColor(Color.fromARGB(150, 12, 10, 14));
                td.setPersistent(true);
                td.addScoreboardTag(DISPLAY_TAG);
                float s = l.scale();
                td.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(s, s, s), new AxisAngle4f()));
            });
        }
    }
}
