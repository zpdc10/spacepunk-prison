package dev.spacepunk.prison.fishing;

import dev.spacepunk.prison.fishing.FishingConfig.Area;
import dev.spacepunk.prison.fishing.FishingWorld.Pool;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Wall;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The decorated builds of the station waters: the Bilge Hub and one sealed deck module per pool. Each module is a
 * walled, roofed room floating in the void around its pool, themed for its deck, with a docking pad, a Dock Terminal
 * and a Fish Market kiosk. Modules are far apart and not connected: players travel between them by warp.
 *
 * Coordinates: y0 is the floor level (base-y - 1); "dy" is the height above it. Only static blocks are used (no
 * gravity blocks, no exposed liquids outside the pools), and every liquid is fully enclosed.
 */
final class FishingBuilds {
    /** Room border between the walkway's outer edge and the room wall. */
    static final int MARGIN = 7;
    static final int ROOM_H = 9;
    static final int HUB_HALF = 13;

    // Block names resolved at runtime, so a renamed block falls back instead of breaking the build.
    private static final Material CHAIN = m("IRON_CHAIN", "CHAIN");
    private static final Material LANTERN = m("LANTERN");
    private static final Material SOUL_LANTERN = m("SOUL_LANTERN");
    private static final Material BARRIER = m("BARRIER");
    private static final Material AIR = Material.AIR;

    private final World world;
    private final FishingConfig cfg;
    private final List<Block> connect = new ArrayList<>();
    private Random rng = new Random(1);

    FishingBuilds(World world, FishingConfig cfg) {
        this.world = world;
        this.cfg = cfg;
    }

    static Material m(String... names) {
        for (String n : names) {
            Material x = Material.matchMaterial(n);
            if (x != null && x.isBlock()) return x;
        }
        return Material.STONE;
    }

    /** The look of one module. */
    record Theme(Material floor, Material trim, Material wall, Material window, Material roof, Material light, Material pipe,
                 Material accent) {}

    static Theme theme(Area a) {
        return switch (a.id()) {
            case "scrapyard" -> new Theme(a.floor(), m("WAXED_EXPOSED_CUT_COPPER"), a.wall(), m("BROWN_STAINED_GLASS"),
                    m("WAXED_WEATHERED_COPPER_GRATE", "IRON_BARS"), a.light(), m("WAXED_OXIDIZED_COPPER"), m("RUSTY_COPPER", "WAXED_WEATHERED_COPPER"));
            case "reactor" -> new Theme(a.floor(), m("POLISHED_DEEPSLATE"), a.wall(), m("LIME_STAINED_GLASS"),
                    m("TINTED_GLASS"), a.light(), m("WAXED_OXIDIZED_CUT_COPPER"), m("YELLOW_CONCRETE"));
            case "cryo" -> new Theme(a.floor(), m("QUARTZ_BRICKS"), a.wall(), m("LIGHT_BLUE_STAINED_GLASS"),
                    m("WHITE_STAINED_GLASS"), a.light(), m("POLISHED_DIORITE"), m("PACKED_ICE"));
            case "hull" -> new Theme(a.floor(), m("POLISHED_BLACKSTONE"), a.wall(), m("TINTED_GLASS"),
                    m("TINTED_GLASS"), a.light(), m("POLISHED_BLACKSTONE_BRICKS"), m("AMETHYST_BLOCK"));
            case "foundry" -> new Theme(a.floor(), m("POLISHED_BLACKSTONE_BRICKS"), a.wall(), m("ORANGE_STAINED_GLASS"),
                    m("POLISHED_BLACKSTONE"), a.light(), m("POLISHED_BASALT"), m("GILDED_BLACKSTONE"));
            default -> new Theme(a.floor(), a.wall(), a.wall(), m("GRAY_STAINED_GLASS"), m("GLASS"), a.light(), a.wall(), a.light());
        };
    }

    // ------------------------------------------------------------------ blocks

    private void set(int x, int y, int z, Material mat) {
        set(x, y, z, mat, null);
    }

    private void set(int x, int y, int z, Material mat, Consumer<BlockData> data) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;
        Block b = world.getBlockAt(x, y, z);
        BlockData bd = mat.createBlockData();
        if (data != null) data.accept(bd);
        b.setBlockData(bd, false);
        if (bd instanceof MultipleFacing || bd instanceof Wall) connect.add(b);
    }

    private static Consumer<BlockData> lit() {
        return bd -> {
            if (bd instanceof Lightable l) l.setLit(true);
        };
    }

    private static Consumer<BlockData> facing(BlockFace f) {
        return bd -> {
            if (bd instanceof Directional d && d.getFaces().contains(f)) d.setFacing(f);
        };
    }

    private static Consumer<BlockData> hanging() {
        return bd -> {
            if (bd instanceof Lantern l) l.setHanging(true);
        };
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, Material mat) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, mat);
    }

    /** Joins panes, bars and walls to their neighbours (blocks are placed without physics). */
    void finish() {
        BlockFace[] sides = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        for (Block b : connect) {
            BlockData bd = b.getBlockData();
            if (bd instanceof MultipleFacing mf) {
                for (BlockFace f : sides) if (mf.getAllowedFaces().contains(f)) mf.setFace(f, joins(b.getRelative(f)));
            } else if (bd instanceof Wall w) {
                boolean any = false;
                for (BlockFace f : sides) {
                    boolean j = joins(b.getRelative(f));
                    w.setHeight(f, j ? Wall.Height.LOW : Wall.Height.NONE);
                    any |= j;
                }
                w.setUp(!any || b.getRelative(BlockFace.UP).getType().isSolid());
            } else {
                continue;
            }
            b.setBlockData(bd, false);
        }
        connect.clear();
    }

    private static boolean joins(Block n) {
        BlockData d = n.getBlockData();
        return n.getType().isOccluding() || d instanceof MultipleFacing || d instanceof Wall || n.getType().name().endsWith("GLASS");
    }

    // ------------------------------------------------------------------ shared shell

    /** Room bounds of a pool's module. */
    record Room(int x0, int x1, int z0, int z1) {
        int cx() { return (x0 + x1) / 2; }
        int cz() { return (z0 + z1) / 2; }
    }

    Room room(Pool p) {
        int w = cfg.walkway;
        return new Room(p.outerX0(w) - MARGIN, p.outerX1(w) + MARGIN, p.outerZ0(w) - MARGIN, p.outerZ1(w) + MARGIN);
    }

    /** Clears the room's volume (rebuilds start clean). */
    void clear(Room r, int y0, int below) {
        for (int x = r.x0() - 3; x <= r.x1() + 3; x++)
            for (int z = r.z0() - 3; z <= r.z1() + 3; z++)
                for (int y = y0 - below; y <= y0 + ROOM_H + 3; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) b.setType(AIR, false);
                }
    }

    /**
     * Floor plating, a tapered hull underneath, walls with window bands and lit pillars, a beamed glass roof with
     * hanging lamps, and conduits along the top of the walls.
     */
    void shell(Room r, int y0, Theme t, boolean roofed) {
        int cx = r.cx(), cz = r.cz();
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                int dx = x - cx, dz = z - cz;
                boolean seam = Math.floorMod(dx, 5) == 0 || Math.floorMod(dz, 5) == 0;
                set(x, y0, z, seam ? t.trim() : t.floor());
                set(x, y0 - 1, z, t.wall());
                int inset = Math.min(Math.min(x - r.x0(), r.x1() - x), Math.min(z - r.z0(), r.z1() - z));
                if (inset >= 1) set(x, y0 - 2, z, t.trim());
                if (inset >= 4) set(x, y0 - 3, z, t.wall());
                if (inset == 4 && Math.floorMod(dx + dz, 7) == 0) set(x, y0 - 4, z, t.light());
            }
        }
        // Walls
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                boolean edgeX = x == r.x0() || x == r.x1(), edgeZ = z == r.z0() || z == r.z1();
                if (!edgeX && !edgeZ) continue;
                int along = edgeX ? z - cz : x - cx;
                boolean corner = edgeX && edgeZ;
                boolean pillar = corner || Math.floorMod(along, 6) == 0;
                for (int dy = 1; dy <= ROOM_H; dy++) {
                    Material mat;
                    if (pillar) mat = dy == ROOM_H - 1 ? t.light() : t.trim();
                    else if (dy == 1 || dy == ROOM_H) mat = t.trim();
                    else if (dy >= 3 && dy <= 6) mat = t.window();
                    else mat = t.wall();
                    set(x, y0 + dy, z, mat);
                }
            }
        }
        // Conduits under the roof line, just inside the walls
        for (int x = r.x0() + 1; x <= r.x1() - 1; x++) {
            set(x, y0 + ROOM_H - 1, r.z0() + 1, t.pipe());
            set(x, y0 + ROOM_H - 1, r.z1() - 1, t.pipe());
        }
        for (int z = r.z0() + 1; z <= r.z1() - 1; z++) {
            set(r.x0() + 1, y0 + ROOM_H - 1, z, t.pipe());
            set(r.x1() - 1, y0 + ROOM_H - 1, z, t.pipe());
        }
        if (!roofed) return;
        int ry = y0 + ROOM_H + 1;
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                int dx = x - cx, dz = z - cz;
                boolean edge = x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1();
                boolean beam = Math.floorMod(dx, 6) == 0 || Math.floorMod(dz, 6) == 0;
                set(x, ry, z, edge ? t.wall() : beam ? t.trim() : t.roof());
                if (!edge && Math.floorMod(dx, 6) == 0 && Math.floorMod(dz, 6) == 0) {
                    set(x, ry - 1, z, CHAIN);
                    set(x, ry - 2, z, LANTERN, hanging());
                }
            }
        }
    }

    /** The docking pad on the west side where players arrive, lit at its corners. */
    void dock(Room r, int y0, Theme t, Material padLight) {
        int cz = r.cz();
        int x0 = r.x0() + 1, x1 = r.x0() + 5;
        for (int x = x0; x <= x1; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                boolean border = x == x0 || x == x1 || Math.abs(z - cz) == 3;
                set(x, y0, z, border ? t.trim() : m("POLISHED_ANDESITE"));
                if (border && (x == x0 || x == x1) && Math.abs(z - cz) == 3) set(x, y0, z, padLight);
            }
        }
        set(x0 + 2, y0, cz, padLight);
        // The sealed airlock behind the pad
        for (int z = cz - 2; z <= cz + 2; z++) {
            for (int dy = 1; dy <= 5; dy++) {
                boolean frame = Math.abs(z - cz) == 2 || dy == 5;
                set(r.x0(), y0 + dy, z, frame ? t.trim() : m("IRON_BLOCK"));
            }
            set(r.x0() + 1, y0 + 6, z, padLight);
        }
        set(r.x0(), y0 + 3, cz, m("REDSTONE_LAMP"), lit());
        // Pedestals for the Dock Terminal (south) and the Fish Market kiosk (north)
        set(x0 + 2, y0 + 1, cz + 4, t.trim());
        set(x0 + 2, y0 + 1, cz - 4, m("BARREL"));
    }

    /** Terminal pedestal top (Interaction sits on it). */
    static double[] terminalSpot(Room r) {
        return new double[] {r.x0() + 3 + 0.5, r.cz() + 4 + 0.5};
    }

    static double[] marketSpot(Room r) {
        return new double[] {r.x0() + 3 + 0.5, r.cz() - 4 + 0.5};
    }

    // ------------------------------------------------------------------ hub

    void hub(int y0) {
        Theme t = new Theme(m("POLISHED_ANDESITE"), m("POLISHED_DEEPSLATE"), m("POLISHED_DEEPSLATE"),
                m("CYAN_STAINED_GLASS"), m("GLASS"), m("SEA_LANTERN"), m("WAXED_COPPER_BLOCK", "COPPER_BLOCK"), m("WAXED_CUT_COPPER"));
        Room r = new Room(-HUB_HALF, HUB_HALF, -HUB_HALF, HUB_HALF);
        rng = new Random(42);
        clear(r, y0, 6);
        shell(r, y0, t, true);
        // Centre: a lit compass rose
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d == 3) set(x, y0, z, m("SMOOTH_STONE"));
                else if (x == 0 || z == 0) set(x, y0, z, m("WAXED_CUT_COPPER"));
            }
        set(0, y0, 0, m("SEA_LANTERN"));
        // Dock Terminal walkway to the east wall, with a lit strip
        for (int x = 4; x <= HUB_HALF - 1; x++) {
            set(x, y0, 0, m("WAXED_CUT_COPPER"));
            if (x % 3 == 0) {
                set(x, y0, -2, m("SEA_LANTERN"));
                set(x, y0, 2, m("SEA_LANTERN"));
            }
        }
        set(8, y0 + 1, 0, m("POLISHED_DEEPSLATE"));
        // East: big viewport out over the void
        for (int z = -4; z <= 4; z++)
            for (int dy = 2; dy <= 7; dy++) set(HUB_HALF, y0 + dy, z, Math.abs(z) == 4 || dy == 7 ? t.trim() : m("GLASS"));
        // Market stall frame on the west side
        for (int z = -2; z <= 2; z++) {
            set(-6, y0 + 5, z, m("WAXED_CUT_COPPER_SLAB", "CUT_COPPER_SLAB"));
            set(-3, y0 + 5, z, m("WAXED_CUT_COPPER_SLAB", "CUT_COPPER_SLAB"));
        }
        for (int dy = 1; dy <= 4; dy++) {
            for (int[] c : new int[][] {{-6, -2}, {-6, 2}, {-3, -2}, {-3, 2}}) set(c[0], y0 + dy, c[1], m("POLISHED_DEEPSLATE_WALL"));
        }
        for (int x = -6; x <= -3; x++) for (int z = -2; z <= 2; z++) set(x, y0 + 6, z, m("WAXED_CUT_COPPER", "CUT_COPPER"));
        // Crates and coils in the corners
        for (int[] c : new int[][] {{-11, -11}, {-11, 11}, {11, -11}, {11, 11}}) crates(c[0], y0, c[1], 3);
        // Benches facing the boards
        for (int x = -2; x <= 2; x++) {
            set(x, y0 + 1, -9, m("POLISHED_DEEPSLATE_STAIRS"), facing(BlockFace.NORTH));
            set(x, y0 + 1, 9, m("POLISHED_DEEPSLATE_STAIRS"), facing(BlockFace.SOUTH));
        }
        // Bilge pipes on the north and south walls
        for (int x = -10; x <= 10; x += 5) {
            fill(x, y0 + 1, -HUB_HALF + 1, x, y0 + 7, -HUB_HALF + 1, t.pipe());
            fill(x, y0 + 1, HUB_HALF - 1, x, y0 + 7, HUB_HALF - 1, t.pipe());
            set(x, y0 + 1, -HUB_HALF + 2, m("WAXED_COPPER_BULB", "COPPER_BULB"), lit());
            set(x, y0 + 1, HUB_HALF - 2, m("WAXED_COPPER_BULB", "COPPER_BULB"), lit());
        }
        finish();
    }

    /** Hub's Dock Terminal pedestal top. */
    static double[] hubTerminal() {
        return new double[] {8.5, 0.5};
    }

    private void crates(int cx, int y0, int cz, int n) {
        for (int i = 0; i < n * 3; i++) {
            int x = cx + rng.nextInt(n) - n / 2, z = cz + rng.nextInt(n) - n / 2;
            int h = 1;
            while (h < 3 && !world.getBlockAt(x, y0 + h, z).getType().isAir()) h++;
            if (h >= 3) continue;
            set(x, y0 + h, z, rng.nextInt(3) == 0 ? m("BARREL") : m("STRIPPED_SPRUCE_WOOD"));
        }
    }

    // ------------------------------------------------------------------ deck modules

    /** Builds a pool's module: clears the space, lays the shell, lets {@code pool} build the basin, then decorates. */
    void module(Pool p, int y0, Runnable pool) {
        Area a = p.area();
        Theme t = theme(a);
        Room r = room(p);
        rng = new Random(a.id().hashCode());
        clear(r, y0, cfg.poolDepth + 6);
        shell(r, y0, t, true);
        pool.run();
        dock(r, y0, t, t.light());
        switch (a.id()) {
            case "scrapyard" -> scrapyard(p, r, y0, t);
            case "reactor" -> reactor(p, r, y0, t);
            case "cryo" -> cryo(p, r, y0, t);
            case "hull" -> hull(p, r, y0, t);
            case "foundry" -> foundry(p, r, y0, t);
            default -> { }
        }
        finish();
    }

    /** Corner zone centres of the border (inside the room, outside the walkway). */
    private static int[][] corners(Room r) {
        return new int[][] {{r.x0() + 3, r.z0() + 3}, {r.x0() + 3, r.z1() - 3}, {r.x1() - 3, r.z0() + 3}, {r.x1() - 3, r.z1() - 3}};
    }

    private boolean inBorder(Pool p, Room r, int x, int z) {
        int w = cfg.walkway;
        boolean insideRoom = x > r.x0() && x < r.x1() && z > r.z0() && z < r.z1();
        boolean onPool = x >= p.outerX0(w) && x <= p.outerX1(w) && z >= p.outerZ0(w) && z <= p.outerZ1(w);
        boolean onDock = x <= r.x0() + 6 && Math.abs(z - r.cz()) <= 5;
        return insideRoom && !onPool && !onDock;
    }

    /** A rounded heap of mixed blocks. */
    private void heap(int cx, int y0, int cz, int radius, int height, Material[] mix) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                int h = (int) Math.round(height * (1 - d / (radius + 0.8)) + rng.nextDouble() - 0.4);
                for (int dy = 1; dy <= h; dy++) set(x, y0 + dy, z, mix[rng.nextInt(mix.length)]);
            }
        }
    }

    /** A vertical glass tank with a core, a capped base and a lit lid. */
    private void tank(int cx, int y0, int cz, int radius, int height, Material glass, Material core, Theme t) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > radius + 0.3) continue;
                set(x, y0 + 1, z, t.trim());
                set(x, y0 + height + 2, z, t.trim());
                for (int dy = 2; dy <= height + 1; dy++) set(x, y0 + dy, z, d > radius - 0.7 ? glass : core);
            }
        }
        set(cx, y0 + height + 3, cz, t.light());
    }

    /** Wall-mounted pipe stubs and a dripping outlet over a spot. */
    private void pipeRun(int x0, int z0, int x1, int z1, int y, Material mat) {
        int x = x0, z = z0;
        while (true) {
            set(x, y, z, mat);
            if (x == x1 && z == z1) break;
            if (x != x1) x += Integer.signum(x1 - x);
            else z += Integer.signum(z1 - z);
        }
    }

    private void scrapyard(Pool p, Room r, int y0, Theme t) {
        Material[] scrap = {m("WAXED_WEATHERED_COPPER"), m("WAXED_EXPOSED_CUT_COPPER"), m("WAXED_OXIDIZED_COPPER"), m("COBBLED_DEEPSLATE"),
                m("RAW_IRON_BLOCK"), m("TUFF"), m("PACKED_MUD"), m("WAXED_WEATHERED_CUT_COPPER_STAIRS"), m("IRON_BLOCK"), m("COARSE_DIRT")};
        for (int[] c : corners(r)) heap(c[0], y0, c[1], 3, 4, scrap);
        // Oil and grime on the deck
        for (int x = r.x0() + 1; x < r.x1(); x++)
            for (int z = r.z0() + 1; z < r.z1(); z++)
                if (inBorder(p, r, x, z) && rng.nextDouble() < 0.18) set(x, y0, z, rng.nextBoolean() ? m("COARSE_DIRT") : m("BLACK_CONCRETE_POWDER", "BLACK_CONCRETE"));
        // Gantry crane over the pool
        int gx = p.cx() + 3;
        for (int dy = 1; dy <= 8; dy++) {
            set(gx, y0 + dy, r.z0() + 2, t.trim());
            set(gx, y0 + dy, r.z1() - 2, t.trim());
        }
        for (int z = r.z0() + 2; z <= r.z1() - 2; z++) set(gx, y0 + 8, z, m("WAXED_WEATHERED_CUT_COPPER"));
        set(gx, y0 + 7, -3, m("IRON_BLOCK"));
        for (int dy = 3; dy <= 6; dy++) set(gx, y0 + dy, -3, CHAIN);
        set(gx, y0 + 2, -3, m("LIGHTNING_ROD"), facing(BlockFace.DOWN));
        set(gx, y0 + 8, r.z0() + 2, t.light());
        set(gx, y0 + 8, r.z1() - 2, t.light());
        // Drain outlets on the north and south walls with rusty stains below
        for (int x = p.cx() - 8; x <= p.cx() + 8; x += 8) {
            for (int z : new int[] {r.z0() + 1, r.z1() - 1}) {
                set(x, y0 + 4, z, t.pipe());
                set(x, y0 + 3, z, m("WAXED_OXIDIZED_COPPER"));
                set(x, y0 + 1, z, m("CAULDRON"));
            }
        }
        // A wrecked shuttle hull in the east border
        int ex = r.x1() - 3;
        for (int z = r.cz() - 4; z <= r.cz() + 4; z++) {
            int h = 4 - Math.abs(z - r.cz()) / 2;
            for (int dy = 1; dy <= h; dy++) {
                boolean shell = dy == h || Math.abs(z - r.cz()) == 4;
                if (!shell && rng.nextDouble() < 0.6) continue;
                set(ex, y0 + dy, z, rng.nextDouble() < 0.3 ? m("WAXED_OXIDIZED_COPPER") : m("WAXED_WEATHERED_COPPER"));
                set(ex + 1, y0 + dy, z, m("WAXED_WEATHERED_CUT_COPPER"));
            }
        }
        set(ex - 1, y0 + 1, r.cz(), t.light());
        crates(r.x1() - 3, y0, r.z0() + 9, 3);
        crates(r.x1() - 3, y0, r.z1() - 9, 3);
    }

    private void reactor(Pool p, Room r, int y0, Theme t) {
        // Hazard striping round the walkway
        int w = cfg.walkway;
        int hx0 = p.outerX0(w) - 1, hx1 = p.outerX1(w) + 1, hz0 = p.outerZ0(w) - 1, hz1 = p.outerZ1(w) + 1;
        for (int x = hx0; x <= hx1; x++)
            for (int z = hz0; z <= hz1; z++)
                if (x == hx0 || x == hx1 || z == hz0 || z == hz1)
                    set(x, y0, z, Math.floorMod(x + z, 2) == 0 ? m("YELLOW_CONCRETE") : m("BLACK_CONCRETE"));
        // Four coolant tanks, each piped into a corner of the pool
        for (int[] c : corners(r)) {
            tank(c[0], y0, c[1], 2, 5, m("LIME_STAINED_GLASS"), m("SLIME_BLOCK"), t);
            int px = c[0] < p.cx() ? p.x0() : p.x1(), pz = c[1] < 0 ? p.z0() : p.z1();
            int top = y0 + 8;
            pipeRun(c[0], c[1], px, c[1], top, t.pipe());
            pipeRun(px, c[1], px, pz, top, t.pipe());
            fill(px, y0 + 1, pz, px, top, pz, t.pipe());
            set(px, y0, pz, m("VERDANT_FROGLIGHT"));
            set(c[0], top + 1, c[1], t.light());
        }
        // Control consoles on the north wall
        for (int x = p.cx() - 6; x <= p.cx() + 6; x++) {
            set(x, y0 + 1, r.z0() + 2, m("POLISHED_DEEPSLATE"));
            Material top = switch (Math.floorMod(x, 4)) {
                case 0 -> m("REDSTONE_LAMP");
                case 1 -> m("OBSERVER");
                case 2 -> m("TARGET");
                default -> m("POLISHED_DEEPSLATE_SLAB");
            };
            set(x, y0 + 2, r.z0() + 2, top, lit());
            for (int dy = 2; dy <= 5; dy++)
                set(x, y0 + dy, r.z0() + 1, rng.nextDouble() < 0.25 ? m("LIME_STAINED_GLASS") : m("BLACK_STAINED_GLASS"));
        }
        // Radiation drums along the south wall
        for (int x = p.cx() - 7; x <= p.cx() + 7; x += 2) {
            set(x, y0 + 1, r.z1() - 2, m("WAXED_OXIDIZED_COPPER"));
            if (rng.nextBoolean()) set(x, y0 + 2, r.z1() - 2, m("WAXED_OXIDIZED_CUT_COPPER_SLAB", "YELLOW_CONCRETE"));
            set(x + 1, y0 + 1, r.z1() - 2, m("YELLOW_CONCRETE"));
        }
        // Reactor core housing in the east border
        int ex = r.x1() - 3;
        tank(ex, y0, r.cz(), 2, 6, m("GREEN_STAINED_GLASS"), m("VERDANT_FROGLIGHT"), t);
    }

    private void cryo(Pool p, Room r, int y0, Theme t) {
        // Stasis pods along the north and south walls
        for (int x = p.cx() - 12; x <= p.cx() + 12; x += 4) {
            for (int side = -1; side <= 1; side += 2) {
                int back = side < 0 ? r.z0() + 1 : r.z1() - 1, front = back - side;
                set(x, y0 + 1, back, t.trim());
                set(x, y0 + 1, front, t.trim());
                for (int dy = 2; dy <= 4; dy++) {
                    set(x, y0 + dy, back, m("BLUE_ICE"));
                    set(x, y0 + dy, front, m("LIGHT_BLUE_STAINED_GLASS"));
                    set(x - 1, y0 + dy, front, m("POLISHED_DIORITE_WALL"));
                    set(x + 1, y0 + dy, front, m("POLISHED_DIORITE_WALL"));
                }
                set(x, y0 + 5, back, t.light());
                set(x, y0 + 5, front, t.trim());
            }
        }
        // Ice crystals in the corners
        for (int[] c : corners(r)) {
            for (int i = 0; i < 6; i++) {
                int x = c[0] + rng.nextInt(5) - 2, z = c[1] + rng.nextInt(5) - 2;
                int h = 2 + rng.nextInt(5);
                Material ice = switch (rng.nextInt(3)) {
                    case 0 -> m("PACKED_ICE");
                    case 1 -> m("BLUE_ICE");
                    default -> m("ICE");
                };
                for (int dy = 1; dy <= h; dy++) set(x, y0 + dy, z, ice);
            }
        }
        // Frost on the deck
        for (int x = r.x0() + 1; x < r.x1(); x++)
            for (int z = r.z0() + 1; z < r.z1(); z++) {
                if (!inBorder(p, r, x, z) || !world.getBlockAt(x, y0 + 1, z).getType().isAir()) continue;
                double roll = rng.nextDouble();
                if (roll < 0.25) set(x, y0 + 1, z, m("SNOW"));
                else if (roll < 0.35) set(x, y0, z, m("SNOW_BLOCK"));
            }
        // Ice floes drifting on the pool
        for (int i = 0; i < 5; i++) {
            int x = p.x0() + 2 + rng.nextInt(p.x1() - p.x0() - 3), z = p.z0() + 2 + rng.nextInt(p.z1() - p.z0() - 3);
            set(x, p.wTop(), z, m("PACKED_ICE"));
            if (rng.nextBoolean()) set(x + 1, p.wTop(), z, m("ICE"));
            if (rng.nextBoolean()) set(x, p.wTop(), z + 1, m("PACKED_ICE"));
        }
        // Coolant ducts frosting over
        for (int z = r.z0() + 2; z <= r.z1() - 2; z++) set(r.x1() - 1, y0 + 2, z, t.pipe());
        for (int z = r.cz() - 5; z <= r.cz() + 5; z += 5) {
            fill(r.x1() - 2, y0 + 1, z, r.x1() - 2, y0 + 3, z, m("PACKED_ICE"));
            set(r.x1() - 2, y0 + 4, z, t.light());
        }
    }

    private void hull(Pool p, Room r, int y0, Theme t) {
        int cz = r.cz();
        // The breach: a ragged hole in the east wall and the roof above it, sealed by a force field
        for (int z = cz - 9; z <= cz + 9; z++) {
            for (int dy = 1; dy <= ROOM_H + 1; dy++) {
                double edge = Math.abs(z - cz) / 9.0 + (dy - 5) * (dy - 5) / 40.0 + rng.nextDouble() * 0.25;
                if (edge > 1.05) continue;
                boolean rim = edge > 0.85;
                set(r.x1(), y0 + dy, z, rim ? m("CRACKED_POLISHED_BLACKSTONE_BRICKS") : (dy <= ROOM_H ? BARRIER : AIR));
            }
            set(r.x1() - 1, y0 + 1, z, Math.floorMod(z, 3) == 0 ? m("END_ROD") : m("POLISHED_BLACKSTONE_SLAB"));
        }
        for (int x = r.x1() - 12; x < r.x1(); x++) {
            for (int z = cz - 8; z <= cz + 8; z++) {
                double d = Math.sqrt(Math.pow((x - r.x1()) / 12.0, 2) + Math.pow((z - cz) / 8.0, 2)) + rng.nextDouble() * 0.2;
                if (d < 0.8) set(x, y0 + ROOM_H + 1, z, AIR);
                else if (d < 1.0 && rng.nextBoolean()) set(x, y0 + ROOM_H + 1, z, m("CRACKED_POLISHED_BLACKSTONE_BRICKS"));
            }
        }
        // Debris drifting out in the void past the breach
        Material[] debris = {t.wall(), t.floor(), m("TINTED_GLASS"), m("CRYING_OBSIDIAN"), m("POLISHED_BLACKSTONE"), m("AMETHYST_BLOCK")};
        for (int i = 0; i < 26; i++) {
            int x = r.x1() + 3 + rng.nextInt(22), y = y0 - 4 + rng.nextInt(16), z = cz - 14 + rng.nextInt(29);
            int size = 1 + rng.nextInt(2);
            for (int k = 0; k < size * 3; k++) set(x + rng.nextInt(size + 1), y + rng.nextInt(size), z + rng.nextInt(size + 1), debris[rng.nextInt(debris.length)]);
        }
        // Crystal growths where the strange water seeps in
        for (int[] c : corners(r)) {
            heap(c[0], y0, c[1], 2, 3, new Material[] {m("AMETHYST_BLOCK"), m("BUDDING_AMETHYST", "AMETHYST_BLOCK"), m("CRYING_OBSIDIAN"), m("PRISMARINE")});
            set(c[0], y0 + 5, c[1], m("AMETHYST_CLUSTER"));
        }
        for (int x = r.x0() + 1; x < r.x1(); x++)
            for (int z = r.z0() + 1; z < r.z1(); z++) {
                if (!inBorder(p, r, x, z)) continue;
                if (rng.nextDouble() < 0.12) set(x, y0, z, rng.nextBoolean() ? m("PRISMARINE") : m("DARK_PRISMARINE"));
                else if (rng.nextDouble() < 0.04 && world.getBlockAt(x, y0 + 1, z).getType().isAir()) set(x, y0 + 1, z, m("SEA_PICKLE"));
            }
        // Emergency bulkhead struts on the north and south walls
        for (int x = p.cx() - 9; x <= p.cx() + 9; x += 6) {
            for (int z : new int[] {r.z0() + 1, r.z1() - 1}) {
                fill(x, y0 + 1, z, x, y0 + ROOM_H - 2, z, m("POLISHED_BLACKSTONE_WALL"));
                set(x, y0 + ROOM_H - 1, z, m("PEARLESCENT_FROGLIGHT"));
            }
        }
    }

    private void foundry(Pool p, Room r, int y0, Theme t) {
        // Lava channels sealed behind glass in the north and south walls
        for (int x = p.cx() - 12; x <= p.cx() + 12; x += 6) {
            for (int side = -1; side <= 1; side += 2) {
                int wallZ = side < 0 ? r.z0() : r.z1();
                int outside = wallZ + side, inside = wallZ - side;
                for (int dy = 1; dy <= ROOM_H - 1; dy++) {
                    set(x, y0 + dy, outside, t.wall());
                    set(x, y0 + dy, wallZ, Material.LAVA);
                    set(x, y0 + dy, inside, m("ORANGE_STAINED_GLASS"));
                    set(x - 1, y0 + dy, inside, t.trim());
                    set(x + 1, y0 + dy, inside, t.trim());
                }
                set(x, y0 + ROOM_H, inside, t.trim());
                set(x, y0 + ROOM_H, wallZ, t.trim());
                set(x, y0 + ROOM_H, outside, t.wall());
                set(x - 1, y0 + ROOM_H, wallZ, t.trim());
                set(x + 1, y0 + ROOM_H, wallZ, t.trim());
                set(x, y0, outside, t.wall());
            }
        }
        // Casting molds in the corners
        for (int[] c : corners(r)) {
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    set(c[0] + dx, y0 + 1, c[1] + dz, rim ? m("POLISHED_BLACKSTONE_BRICKS") : (Math.floorMod(dx + dz, 2) == 0 ? m("LAVA_CAULDRON") : m("GILDED_BLACKSTONE")));
                }
            set(c[0], y0 + 2, c[1] - 2, m("SHROOMLIGHT"));
        }
        // Basalt columns and gilded flecks
        for (int x = r.x0() + 1; x < r.x1(); x++)
            for (int z = r.z0() + 1; z < r.z1(); z++)
                if (inBorder(p, r, x, z) && rng.nextDouble() < 0.1) set(x, y0, z, rng.nextBoolean() ? m("GILDED_BLACKSTONE") : m("BLACKSTONE"));
        // Castings hanging on chains over the border
        for (int i = 0; i < 10; i++) {
            int x = r.x0() + 2 + rng.nextInt(r.x1() - r.x0() - 3), z = r.z0() + 2 + rng.nextInt(r.z1() - r.z0() - 3);
            if (!inBorder(p, r, x, z)) continue;
            int low = y0 + 4 + rng.nextInt(2);
            for (int y = low + 1; y <= y0 + ROOM_H; y++) set(x, y, z, CHAIN);
            set(x, low, z, rng.nextInt(3) == 0 ? m("GOLD_BLOCK") : m("IRON_BLOCK"));
        }
        // Blast furnaces along the east wall
        for (int z = r.cz() - 6; z <= r.cz() + 6; z += 2) {
            set(r.x1() - 1, y0 + 1, z, m("BLAST_FURNACE"), bd -> {
                if (bd instanceof Directional d) d.setFacing(BlockFace.WEST);
                if (bd instanceof Lightable l) l.setLit(true);
            });
            set(r.x1() - 1, y0 + 2, z, m("POLISHED_BLACKSTONE_BRICK_WALL"));
            set(r.x1() - 1, y0 + 3, z, SOUL_LANTERN);
        }
    }
}
