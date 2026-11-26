package dev.spacepunk.prison.mines;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fallback refiller with no dependencies: sets blocks on the main thread under a per-tick budget, so a big
 * refill is spread over several ticks instead of freezing the server. Jobs run one after another.
 */
final class NativeRefiller implements Refiller {
    private final Plugin plugin;
    private final int blocksPerTick;
    private final Deque<Job> jobs = new ArrayDeque<>();
    private BukkitTask task;

    NativeRefiller(Plugin plugin, int blocksPerTick) {
        this.plugin = plugin;
        this.blocksPerTick = blocksPerTick;
    }

    @Override public String name() { return "native (" + blocksPerTick + " blocks/tick)"; }

    @Override
    public void fill(Region region, BlockMix mix, Runnable onDone) {
        World world = region.bukkitWorld();
        if (world == null) { onDone.run(); return; }
        jobs.add(new Job(world, region, mix, onDone));
        if (task == null) task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        int budget = blocksPerTick;
        Random random = ThreadLocalRandom.current();
        while (budget > 0 && !jobs.isEmpty()) {
            Job job = jobs.peek();
            budget -= job.step(budget, random);
            if (job.done()) {
                jobs.poll();
                try { job.onDone.run(); } catch (RuntimeException e) { plugin.getLogger().warning("[mines] refill callback failed: " + e); }
            }
        }
        if (jobs.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void shutdown() {
        // Finish outstanding work synchronously so mines are not left half-filled on restart.
        Random random = ThreadLocalRandom.current();
        while (!jobs.isEmpty()) {
            Job job = jobs.poll();
            while (!job.done()) job.step(Integer.MAX_VALUE, random);
        }
        if (task != null) task.cancel();
        task = null;
    }

    private static final class Job {
        final World world;
        final Region r;
        final BlockMix mix;
        final Runnable onDone;
        final long total;
        long index;

        Job(World world, Region r, BlockMix mix, Runnable onDone) {
            this.world = world;
            this.r = r;
            this.mix = mix;
            this.onDone = onDone;
            this.total = r.volume();
        }

        boolean done() { return index >= total; }

        /** Places up to max blocks, returns how many were placed. Order: x fastest, then z, then y. */
        int step(int max, Random random) {
            int sx = r.sizeX(), sz = r.sizeZ();
            long layer = (long) sx * sz;
            int placed = 0;
            while (placed < max && index < total) {
                int y = r.minY() + (int) (index / layer);
                long rest = index % layer;
                int z = r.minZ() + (int) (rest / sx);
                int x = r.minX() + (int) (rest % sx);
                Material m = mix.pick(random);
                world.getBlockAt(x, y, z).setType(m, false);
                index++;
                placed++;
            }
            return Math.max(placed, 1);
        }
    }
}
