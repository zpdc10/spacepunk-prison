package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.undercroft.UndercroftConfig.Ring;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Timed supply drops: announced server-wide, land in a ring after a warning, despawn if nobody loots them. */
final class SupplyDrops {
    private final UndercroftManager uc;
    private final Loot loot;
    private long nextDropAt;
    private final List<Drop> drops = new ArrayList<>();

    private static final class Drop {
        final Block block;
        final int ring;
        long landAt;
        long despawnAt;
        boolean landed;

        Drop(Block block, int ring) {
            this.block = block;
            this.ring = ring;
        }
    }

    SupplyDrops(UndercroftManager uc, Loot loot) {
        this.uc = uc;
        this.loot = loot;
        schedule();
    }

    private void schedule() { nextDropAt = System.currentTimeMillis() + uc.cfg().dropInterval * 60_000L; }

    /** Every second. */
    void tick() {
        long now = System.currentTimeMillis();
        if (uc.cfg().dropsEnabled && now >= nextDropAt) {
            schedule();
            if (uc.playersInZone() >= uc.cfg().dropMinPlayers) launch(pickRing());
        }
        for (Iterator<Drop> it = drops.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            World w = d.block.getWorld();
            Location c = d.block.getLocation().add(0.5, 0.5, 0.5);
            if (!d.landed) {
                for (int y = 1; y < uc.cfg().ceilingHeight - 1; y += 2) w.spawnParticle(Particle.END_ROD, c.clone().add(0, y, 0), 1, 0.05, 0.2, 0.05, 0);
                if (now >= d.landAt) land(d);
                continue;
            }
            boolean empty = d.block.getType() != Material.CHEST
                    || (d.block.getState() instanceof Chest ch && ch.getBlockInventory().isEmpty());
            if (empty || now >= d.despawnAt) {
                remove(d);
                it.remove();
                continue;
            }
            w.spawnParticle(Particle.FIREWORK, c.clone().add(0, 1.2, 0), 2, 0.2, 0.4, 0.2, 0.01);
        }
    }

    private int pickRing() {
        List<Integer> weights = uc.cfg().ringWeights;
        int rings = uc.cfg().rings.size();
        int total = 0;
        for (int i = 0; i < rings; i++) total += i < weights.size() ? Math.max(0, weights.get(i)) : 1;
        if (total <= 0) return 1;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < rings; i++) {
            r -= i < weights.size() ? Math.max(0, weights.get(i)) : 1;
            if (r < 0) return i + 1;
        }
        return 1;
    }

    /** Announces and schedules a drop in a ring. Returns false if no landing spot was found. */
    boolean launch(int ringNumber) {
        World w = uc.world.world();
        Ring ring = uc.cfg().ring(ringNumber);
        if (w == null || ring == null) return false;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int f = uc.cfg().floorY, hw = uc.cfg().halfWidth;
        for (int tries = 0; tries < 40; tries++) {
            int x = r.nextInt(ring.xMin() + 3, Math.max(ring.xMin() + 4, ring.xMax() - 3));
            int z = r.nextInt(-hw + 2, hw - 1);
            Block b = w.getBlockAt(x, f + 1, z);
            if (!b.getType().isAir() || !b.getRelative(0, 1, 0).getType().isAir() || !w.getBlockAt(x, f, z).getType().isSolid()) continue;
            Drop d = new Drop(b, ringNumber);
            d.landAt = System.currentTimeMillis() + uc.cfg().dropWarning * 1000L;
            drops.add(d);
            String msg = "<#ff5040><b>SUPPLY DROP</b></#ff5040> <gray>inbound to the Undercroft: " + ring.name()
                    + " <gray>at <white>" + x + ", " + z + "</white> in <white>" + uc.cfg().dropWarning + "s</white>.";
            for (Player p : Bukkit.getOnlinePlayers()) {
                Text.send(p, msg);
                if (uc.world.isWorld(p.getWorld())) p.playSound(Sound.sound(Key.key("block.bell.use"), Sound.Source.MASTER, 1f, 0.6f));
            }
            return true;
        }
        return false;
    }

    private void land(Drop d) {
        d.landed = true;
        d.despawnAt = System.currentTimeMillis() + uc.cfg().dropDespawn * 60_000L;
        if (!d.block.getType().isAir()) {
            d.despawnAt = 0; // spot got blocked, give up
            return;
        }
        d.block.setType(Material.CHEST, false);
        if (d.block.getState() instanceof Chest ch) {
            List<ItemStack> items = loot.supply(d.ring);
            var inv = ch.getBlockInventory();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (ItemStack it : items) {
                int slot = r.nextInt(inv.getSize());
                for (int k = 0; k < inv.getSize() && inv.getItem(slot) != null; k++) slot = (slot + 1) % inv.getSize();
                inv.setItem(slot, it);
            }
        }
        World w = d.block.getWorld();
        Location c = d.block.getLocation().add(0.5, 0.5, 0.5);
        w.spawnParticle(Particle.EXPLOSION, c, 1);
        w.playSound(Sound.sound(Key.key("entity.generic.explode"), Sound.Source.BLOCK, 1.2f, 0.8f), c.getX(), c.getY(), c.getZ());
        uc.broadcastZone("<#ff5040>The supply drop has landed at <white>" + d.block.getX() + ", " + d.block.getZ() + "</white>. Go get it.");
    }

    private void remove(Drop d) {
        if (d.block.getType() == Material.CHEST) {
            if (d.block.getState() instanceof Chest ch) ch.getBlockInventory().clear();
            d.block.setType(Material.AIR, false);
        }
    }

    boolean isDrop(Block b) {
        for (Drop d : drops) if (d.landed && d.block.equals(b)) return true;
        return false;
    }

    void clearAll() {
        for (Drop d : drops) remove(d);
        drops.clear();
    }

    long secondsToNext() { return Math.max(0, (nextDropAt - System.currentTimeMillis()) / 1000); }
}
