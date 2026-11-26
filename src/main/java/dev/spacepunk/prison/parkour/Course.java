package dev.spacepunk.prison.parkour;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A built parkour course next to one mine. Plain data, saved in parkour-data.yml. */
final class Course {
    /** Integer block position. */
    record Pos(int x, int y, int z) {
        static Pos parse(String s) {
            String[] p = s.split(",");
            return new Pos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        }

        Pos up() { return new Pos(x, y + 1, z); }
        Pos down() { return new Pos(x, y - 1, z); }

        @Override public String toString() { return x + "," + y + "," + z; }
    }

    /** A block the generator placed (or, for {@link #replaced}, what was there before). */
    record Placed(Pos pos, String data) {}

    /** Where a player is put back: standing spot + facing. */
    record Spawn(double x, double y, double z, float yaw) {
        Location at(World w) { return new Location(w, x, y, z, yaw, 10f); }
    }

    String mineId;
    String world;
    int grade;
    int jumps;
    long seed;
    Pos start;
    final List<Pos> checkpoints = new ArrayList<>();
    Pos finish;
    /** respawns[0] = start, respawns[i] = checkpoint i (1-based). */
    final List<Spawn> respawns = new ArrayList<>();
    /** fallY[i]: below this Y while past respawn i, the player is put back there. */
    final List<Integer> fallY = new ArrayList<>();
    int minX, minY, minZ, maxX, maxY, maxZ;
    final List<Placed> placed = new ArrayList<>();
    final List<Placed> replaced = new ArrayList<>();
    final List<UUID> displays = new ArrayList<>();

    World bukkitWorld() { return Bukkit.getWorld(world); }

    int sections() { return checkpoints.size() + 1; }

    boolean near(Location l, int margin) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
        return l.getX() >= minX - margin && l.getX() <= maxX + 1 + margin && l.getZ() >= minZ - margin
                && l.getZ() <= maxZ + 1 + margin && l.getY() >= minY - 30 && l.getY() <= maxY + 20;
    }

    // ------------------------------------------------------------------ YAML

    void save(ConfigurationSection s) {
        s.set("world", world);
        s.set("grade", grade);
        s.set("jumps", jumps);
        s.set("seed", seed);
        s.set("start", start.toString());
        s.set("checkpoints", checkpoints.stream().map(Pos::toString).toList());
        s.set("finish", finish.toString());
        s.set("respawns", respawns.stream().map(r -> r.x() + "," + r.y() + "," + r.z() + "," + r.yaw()).toList());
        s.set("fall-y", fallY);
        s.set("bounds", List.of(minX, minY, minZ, maxX, maxY, maxZ));
        s.set("placed", placed.stream().map(p -> p.pos() + "|" + p.data()).toList());
        s.set("replaced", replaced.stream().map(p -> p.pos() + "|" + p.data()).toList());
        s.set("displays", displays.stream().map(UUID::toString).toList());
    }

    static Course load(String id, ConfigurationSection s) {
        Course c = new Course();
        c.mineId = id;
        c.world = s.getString("world", "mines");
        c.grade = s.getInt("grade", 1);
        c.jumps = s.getInt("jumps", 0);
        c.seed = s.getLong("seed", 0);
        c.start = Pos.parse(s.getString("start", "0,0,0"));
        for (String p : s.getStringList("checkpoints")) c.checkpoints.add(Pos.parse(p));
        c.finish = Pos.parse(s.getString("finish", "0,0,0"));
        for (String r : s.getStringList("respawns")) {
            String[] p = r.split(",");
            c.respawns.add(new Spawn(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Float.parseFloat(p[3])));
        }
        c.fallY.addAll(s.getIntegerList("fall-y"));
        List<Integer> b = s.getIntegerList("bounds");
        if (b.size() == 6) {
            c.minX = b.get(0); c.minY = b.get(1); c.minZ = b.get(2); c.maxX = b.get(3); c.maxY = b.get(4); c.maxZ = b.get(5);
        }
        for (String p : s.getStringList("placed")) c.placed.add(placed(p));
        for (String p : s.getStringList("replaced")) c.replaced.add(placed(p));
        for (String u : s.getStringList("displays")) {
            try { c.displays.add(UUID.fromString(u)); } catch (IllegalArgumentException ignored) { }
        }
        while (c.respawns.size() < c.sections()) c.respawns.add(new Spawn(c.start.x() + 0.5, c.start.y(), c.start.z() + 0.5, 180f));
        while (c.fallY.size() < c.sections()) c.fallY.add(c.minY - 2);
        return c;
    }

    private static Placed placed(String s) {
        int bar = s.indexOf('|');
        return new Placed(Pos.parse(s.substring(0, bar)), s.substring(bar + 1));
    }
}
