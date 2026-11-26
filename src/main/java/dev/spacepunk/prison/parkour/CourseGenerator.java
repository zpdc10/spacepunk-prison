package dev.spacepunk.prison.parkour;

import dev.spacepunk.prison.parkour.Course.Placed;
import dev.spacepunk.prison.parkour.Course.Pos;
import dev.spacepunk.prison.parkour.ParkourConfig.Shape;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.TrapDoor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Plans a parkour course in the void north of a mine's spawn rim. The path snakes east-west in rows that step north
 * and climb, so a course of 40 jumps fits in about 45 x 45 blocks. Every jump is kept inside vanilla limits for its
 * height change (sprint jump: 3-block gap flat or down, 2-block gap up one), and no platform is placed within one
 * block (or three up/down) of an earlier one, so there are no head-hitters or easy shortcuts.
 * The plan is pure data; {@link ParkourManager} places the blocks.
 */
final class CourseGenerator {
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {-1, 0}}; // north, east, west

    private final ParkourConfig cfg;
    private final ParkourConfig.Tier tier;
    private final ParkourConfig.Theme theme;
    private final Random rnd;
    private final Map<Pos, String> blocks = new LinkedHashMap<>();
    private final Set<Pos> solid = new HashSet<>();

    private CourseGenerator(ParkourConfig cfg, int deck, long seed) {
        this.cfg = cfg;
        this.tier = cfg.tier(deck);
        this.theme = cfg.theme(deck);
        this.rnd = new Random(seed);
    }

    /**
     * @param sx    x of the mine spawn (course centre line)
     * @param baseY y of the rim surface block (the start pad is built at this level)
     * @param gateZ z of the parapet in front of the spawn, or {@code Integer.MIN_VALUE} if there is none
     * @param spawnZ z of the spawn block
     */
    static Course plan(ParkourConfig cfg, String mineId, String world, int grade, int sx, int baseY, int gateZ, int spawnZ, long seed) {
        // The planner is greedy and can box itself in early. Try a series of seeds and keep the first layout that
        // places every jump, or else the longest one found.
        int want = cfg.jumps(grade);
        Course best = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long s = seed + attempt * 0x9E3779B97F4A7C15L;
            Course c = new CourseGenerator(cfg, ParkourConfig.deckOf(grade), s).build(mineId, world, grade, sx, baseY, gateZ, spawnZ, s);
            if (best == null || c.jumps > best.jumps) best = c;
            if (c.jumps >= want) break;
        }
        return best;
    }

    /** Layouts tried per course before settling for the longest. */
    static final int ATTEMPTS = 40;

    private Course build(String mineId, String world, int grade, int sx, int baseY, int gateZ, int spawnZ, long seed) {
        Course c = new Course();
        c.mineId = mineId;
        c.world = world;
        c.grade = grade;
        c.seed = seed;

        // Catwalk from the rim through the parapet gap, then a 3x3 start pad.
        int walkZ = gateZ == Integer.MIN_VALUE ? spawnZ - 2 : gateZ - 1;
        for (int dx = -1; dx <= 1; dx++) put(new Pos(sx + dx, baseY, walkZ), theme.pad());
        int padZ = walkZ - 2;
        pad(sx, baseY, padZ, theme.pad(), theme.light());
        c.start = new Pos(sx, baseY + 1, padZ);
        blocks.put(c.start, "minecraft:heavy_weighted_pressure_plate");
        c.respawns.add(new Course.Spawn(sx + 0.5, baseY + 1, padZ + 0.5, 180f));

        int zMax = padZ - 2, zMin = zMax - cfg.depth;
        int xMin = sx - cfg.halfWidth, xMax = sx + cfg.halfWidth;
        int yMax = baseY + cfg.maxRise;

        int jumps = cfg.jumps(grade);
        Pos front = new Pos(sx, baseY, padZ - 1);
        List<Pos> prev = new ArrayList<>(); // previous platform: excluded from the spacing check
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) prev.add(new Pos(sx + dx, baseY, padZ + dz));
        boolean north = true;
        int northUntil = front.z() - 3;
        int lateral = rnd.nextBoolean() ? 1 : -1;
        int sectionMin = baseY;
        int placedJumps = 0;

        for (int j = 0; j < jumps; j++) {
            boolean last = j == jumps - 1;
            boolean checkpoint = !last && (j + 1) % tier.checkpointEvery() == 0;
            // Steering: north for a short stretch, then across, then north again, alternating sides.
            if (north && front.z() <= northUntil) {
                north = false;
            } else if (!north && (lateral > 0 ? front.x() >= xMax - 5 : front.x() <= xMin + 5)) {
                north = true;
                northUntil = front.z() - cfg.rowGap;
                lateral = -lateral;
            }
            Jump jump = null;
            for (int attempt = 0; attempt < 90 && jump == null; attempt++) {
                // Preferred heading first, then the other one, then doubling back across the row.
                boolean back = attempt >= 60;
                boolean goNorth = !back && (attempt < 40 ? north : !north);
                int side = back ? -lateral : lateral;
                int[] dir = goNorth ? DIRS[0] : (side > 0 ? DIRS[1] : DIRS[2]);
                jump = tryJump(front, dir, prev, last, checkpoint, xMin, xMax, zMin, zMax, baseY, yMax);
                if (jump != null && back) lateral = -lateral;
            }
            if (jump == null) break; // boxed in: finish here
            for (Pos p : jump.cells) put(p, jump.materials.get(p));
            blocks.putAll(jump.extra);
            solid.addAll(jump.extra.keySet());
            prev = jump.cells;
            front = jump.front;
            placedJumps++;
            sectionMin = Math.min(sectionMin, jump.landing.y());
            if (jump.finish) {
                c.finish = jump.landing.up();
                blocks.put(c.finish, "minecraft:light_weighted_pressure_plate");
                break;
            }
            if (checkpoint) {
                Pos plate = jump.landing.up();
                blocks.put(plate, "minecraft:light_weighted_pressure_plate");
                c.checkpoints.add(plate);
                c.fallY.add(sectionMin - 2);
                sectionMin = jump.landing.y();
                c.respawns.add(new Course.Spawn(plate.x() + 0.5, plate.y(), plate.z() + 0.5, yaw(jump.dir)));
            }
        }
        if (c.finish == null) {
            // Ran out of room or jumps: the last platform becomes the finish.
            Pos land = prev.get(0);
            c.finish = land.up();
            blocks.put(c.finish, "minecraft:light_weighted_pressure_plate");
        }
        c.fallY.add(sectionMin - 2);
        c.jumps = placedJumps;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<Pos, String> e : blocks.entrySet()) {
            Pos p = e.getKey();
            c.placed.add(new Placed(p, e.getValue()));
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y()); minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y()); maxZ = Math.max(maxZ, p.z());
        }
        c.minX = minX; c.minY = minY; c.minZ = minZ; c.maxX = maxX; c.maxY = maxY; c.maxZ = maxZ;
        return c;
    }

    private record Jump(List<Pos> cells, Map<Pos, Material> materials, Map<Pos, String> extra, Pos landing, Pos front,
                        int[] dir, boolean finish) {}

    private Jump tryJump(Pos from, int[] dir, List<Pos> prev, boolean last, boolean checkpoint,
                         int xMin, int xMax, int zMin, int zMax, int yMin, int yMax) {
        int gap = tier.gapMin() + rnd.nextInt(tier.gapMax() - tier.gapMin() + 1);
        double roll = rnd.nextDouble();
        int dy = roll < tier.upChance() ? 1 : roll < tier.upChance() + tier.downChance() ? -1 : 0;
        if (from.y() + dy > yMax) dy = 0;
        if (from.y() + dy < yMin) dy = 0;
        if (dy > 0) gap = Math.min(gap, tier.upGap());
        int side = 0;
        if (!last && !checkpoint && rnd.nextDouble() < tier.lateralChance()) {
            side = rnd.nextBoolean() ? 1 : -1;
            gap = Math.min(gap, dy > 0 ? 1 : 2);
        }
        int[] perp = {-dir[1], dir[0]};
        Pos land = new Pos(from.x() + dir[0] * (gap + 1) + perp[0] * side, from.y() + dy, from.z() + dir[1] * (gap + 1) + perp[1] * side);

        Shape shape = last || checkpoint ? Shape.WIDE : pickShape();
        List<Pos> cells = new ArrayList<>();
        Map<Pos, Material> mats = new LinkedHashMap<>();
        Map<Pos, String> extra = new LinkedHashMap<>();
        Pos front = land;
        Material block = theme.blocks().get(rnd.nextInt(theme.blocks().size()));
        switch (shape) {
            case LINE2, LINE3 -> {
                int n = shape == Shape.LINE2 ? 2 : 3;
                for (int i = 0; i < n; i++) {
                    Pos p = new Pos(land.x() + dir[0] * i, land.y(), land.z() + dir[1] * i);
                    cells.add(p);
                    mats.put(p, block);
                }
                front = cells.get(cells.size() - 1);
            }
            case WIDE -> {
                Material m = last || checkpoint ? theme.pad() : block;
                if (last) {
                    // 3x3 finish pad, entered from its near edge.
                    Pos centre = new Pos(land.x() + dir[0], land.y(), land.z() + dir[1]);
                    for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                        Pos p = new Pos(centre.x() + a, centre.y(), centre.z() + b);
                        cells.add(p);
                        mats.put(p, a == 0 && b == 0 ? m : (Math.abs(a) + Math.abs(b) == 2 ? theme.light() : m));
                    }
                    land = centre;
                } else {
                    for (int i = -1; i <= 1; i++) {
                        Pos p = new Pos(land.x() + perp[0] * i, land.y(), land.z() + perp[1] * i);
                        cells.add(p);
                        mats.put(p, i == 0 && checkpoint ? theme.light() : m);
                    }
                }
                front = land;
            }
            case SLAB -> { cells.add(land); mats.put(land, theme.slab()); }
            case TRAPDOOR -> { cells.add(land); mats.put(land, theme.trapdoor()); }
            default -> { cells.add(land); mats.put(land, block); }
        }

        // Bounds and spacing.
        for (Pos p : cells) {
            if (p.x() < xMin || p.x() > xMax || p.z() < zMin || p.z() > zMax || p.y() < yMin || p.y() > yMax) return null;
            if (crowded(p, prev)) return null;
        }
        // Clear flight path: nothing solid in the two blocks above the straight line between the platforms.
        int steps = gap + 1;
        for (int i = 1; i < steps; i++) {
            int x = from.x() + dir[0] * i + (side != 0 && i * 2 >= steps ? perp[0] * side : 0);
            int z = from.z() + dir[1] * i + (side != 0 && i * 2 >= steps ? perp[1] * side : 0);
            for (int h = 0; h <= 3; h++) if (solid.contains(new Pos(x, from.y() + h, z))) return null;
        }

        // Decoration hanging under the platform: a chain, or a lantern under checkpoints and the finish.
        Pos under = cells.get(cells.size() / 2).down();
        if ((checkpoint || last) && theme.lantern() != null) {
            extra.put(under, data(theme.lantern(), true));
        } else if (theme.chain() != null && rnd.nextDouble() < theme.decorChance()) {
            int len = 1 + rnd.nextInt(3);
            for (int i = 0; i < len; i++) {
                Pos p = new Pos(under.x(), under.y() - i, under.z());
                if (crowded(p, cells)) break;
                extra.put(p, theme.chain().getKey().toString());
            }
        }
        for (Pos p : extra.keySet()) if (crowded(p, cells)) return null;

        Map<Pos, Material> ordered = new LinkedHashMap<>(mats);
        return new Jump(cells, ordered, extra, land, front, dir, last);
    }

    /** True if an earlier block (other than the given platform) is within 1 block sideways and 3 up or down. */
    private boolean crowded(Pos p, List<Pos> except) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = -3; y <= 3; y++) {
            Pos q = new Pos(p.x() + x, p.y() + y, p.z() + z);
            if (solid.contains(q) && !except.contains(q)) return true;
        }
        return false;
    }

    private Shape pickShape() {
        int total = tier.shapes().values().stream().mapToInt(Integer::intValue).sum();
        int r = rnd.nextInt(Math.max(1, total));
        for (Map.Entry<Shape, Integer> e : tier.shapes().entrySet()) {
            r -= e.getValue();
            if (r < 0) return e.getKey();
        }
        return Shape.SINGLE;
    }

    private void pad(int cx, int y, int cz, Material m, Material corner) {
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            put(new Pos(cx + a, y, cz + b), Math.abs(a) + Math.abs(b) == 2 ? corner : m);
        }
    }

    private void put(Pos p, Material m) {
        blocks.put(p, data(m, false));
        solid.add(p);
    }

    /** Block data string; trapdoors sit closed in the top half so they are flush with full blocks. */
    private static String data(Material m, boolean hanging) {
        BlockData d = Bukkit.createBlockData(m);
        if (d instanceof TrapDoor t) {
            t.setHalf(Bisected.Half.TOP);
            t.setOpen(false);
        }
        if (d instanceof Lantern l) l.setHanging(hanging);
        return d.getAsString();
    }

    private static float yaw(int[] dir) {
        if (dir[0] > 0) return -90f;
        if (dir[0] < 0) return 90f;
        return dir[1] < 0 ? 180f : 0f;
    }
}
