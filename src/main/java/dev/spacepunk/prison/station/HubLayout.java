package dev.spacepunk.prison.station;

import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

import static dev.spacepunk.prison.station.Palette.*;

/**
 * The station hub, drawn in code so it is reproducible and versioned. Compact on purpose: a domed central
 * concourse with four short corridors to four wings, and terminals (consoles with a button) that send players
 * where they need to go.
 *
 * <pre>
 *                 [ MINE LIFTS ]          north: one lift per deck, best-mine express, parkour list
 *                       |
 *   [ CREW DECK ] -- (CONCOURSE) -- [ EXCHANGE ]   west: quests, contracts, gangs  east: sell, cargo, drill, auction, salvage
 *                       |
 *                 [ SHUTTLE BAY ]-[CHECKPOINT]     south: shuttle board, workshop airlock, Undercroft gate
 * </pre>
 *
 * Players stand at y = {@link #FLOOR}. Bump {@link #VERSION} whenever the layout changes so servers rebuild it.
 */
final class HubLayout {
    /** Layout version; the module rebuilds the hub on start when the stored version is older. */
    static final int VERSION = 2;
    static final int FLOOR = 64;
    static final double SPAWN_X = 0.5, SPAWN_Z = 9.5;
    static final float SPAWN_YAW = 180f;

    /** A console at (x, FLOOR, z) with a button on top. key maps to an action in station.yml. */
    record Terminal(String key, int x, int y, int z, String title, String subtitle) {}

    /** A floating text display. */
    record Label(double x, double y, double z, String text, float scale) {}

    final Canvas canvas = new Canvas(-64, 52, -64, 64, 92, 66);
    final List<Terminal> terminals = new ArrayList<>();
    final List<Label> labels = new ArrayList<>();
    private final String stationName;

    private static final int F = FLOOR, FY = FLOOR - 1;

    HubLayout(String stationName) { this.stationName = stationName; }

    HubLayout draw() {
        concourse();
        mineLifts();
        exchange();
        crewDeck();
        shuttleBay();
        checkpoint();
        // Corridors last so they cut doorways through the wing walls.
        corridor(0, -1);
        corridor(1, 0);
        corridor(-1, 0);
        corridor(0, 1);
        return this;
    }

    // ------------------------------------------------------------------ concourse

    private static final double R_IN = 18.5, R_OUT = 20.5;

    private static int domeHeight(double r) {
        if (r > R_OUT) return -1;
        return 75 + (int) Math.round(7 * Math.sqrt(Math.max(0, 1 - (r / R_OUT) * (r / R_OUT))));
    }

    /** Arc distance (blocks) from (x, z) to the nearest of 16 radial ribs. */
    private static double ribDistance(int x, int z) {
        double r = Math.hypot(x, z);
        double a = Math.toDegrees(Math.atan2(z, x));
        double frac = ((a % 22.5) + 22.5) % 22.5;
        return Math.min(frac, 22.5 - frac) * Math.PI / 180 * r;
    }

    private void concourse() {
        Canvas c = canvas;
        for (int x = -21; x <= 21; x++) {
            for (int z = -21; z <= 21; z++) {
                double r = Math.hypot(x, z);
                if (r > R_OUT) continue;
                c.set(x, FY - 1, z, BRICK);
                boolean rib = ribDistance(x, z) < 1.0;
                // Floor
                BlockData floor;
                if (r <= 4.5) floor = HULL_TILE;
                else if (r > 9.5 && r <= 10.5) floor = GRATE;
                else if (Math.abs(r - 14) <= 0.5 && (x == 0 || z == 0 || Math.abs(x) == Math.abs(z))) floor = LIGHT;
                else floor = (((x >> 1) + (z >> 1)) & 1) == 0 ? FLOOR_A : FLOOR_B;
                c.set(x, FY, z, r > R_IN ? BRICK : floor);
                // Wall ring
                if (r > R_IN) {
                    for (int y = F; y <= 74; y++) {
                        BlockData b;
                        if (rib) b = y == 72 ? WARM : HULL;
                        else if (y == F) b = BRICK;
                        else if (y <= F + 2) b = PLATE;
                        else if (y <= 71) b = WINDOW;
                        else if (y == 72) b = PLATE;
                        else b = TRIM;
                        c.set(x, y, z, b);
                    }
                }
                // Dome: filled down to the lowest neighbour so no gaps open up on the slope
                int h = domeHeight(r);
                int low = h;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nh = domeHeight(Math.hypot(x + d[0], z + d[1]));
                    if (nh < 0) nh = 74;
                    low = Math.min(low, nh + 1);
                }
                BlockData dome = r <= 2.5 ? LIGHT
                        : rib || Math.abs(r - 8) < 0.6 || Math.abs(r - 15) < 0.6 ? HULL : WINDOW;
                for (int y = low; y <= h; y++) c.set(x, y, z, dome);
            }
        }
        // Raised platform around the core
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            double r = Math.hypot(x, z);
            if (r <= 4.5 && r > 1.5) c.set(x, F, z, SLAB);
        }
        // Reactor core: green froglight column in lime glass, hull bands
        for (int y = F; y <= 76; y++) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                boolean centre = x == 0 && z == 0;
                boolean band = y == F || (y - F) % 5 == 4 || y == 76;
                c.set(x, y, z, centre ? GREEN : band ? HULL : CORE_GLASS);
            }
        }
        for (int y = 77; y <= 81; y++) c.set(0, y, 0, PIPE_Y);
        // Hanging lanterns between the ribs
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5 + 11.25);
            int x = (int) Math.round(Math.cos(a) * 16), z = (int) Math.round(Math.sin(a) * 16);
            int top = domeHeight(Math.hypot(x, z));
            for (int y = 74; y < top; y++) c.set(x, y, z, PIPE_Y);
            c.set(x, 73, z, LANTERN);
        }
        // Grunge: crates and webs against the wall
        crates(-13, -12, 2);
        crates(12, 13, 3);
        crates(-14, 11, 2);
        c.set(-12, 73, -13, COBWEB);
        c.set(13, 73, 12, COBWEB);

        labels.add(new Label(0.5, 79.2, 0.5, stationName, 3.0f));
        labels.add(new Label(0.5, 70.6, -16.8, "<#c8743a><bold>▲ MINE LIFTS</bold>\n<gray>deck lifts · express · parkour", 1.4f));
        labels.add(new Label(17.3, 70.6, 0.5, "<#e0b354><bold>▶ EXCHANGE</bold>\n<gray>sell · cargo · drill bay · black market", 1.4f));
        labels.add(new Label(-16.3, 70.6, 0.5, "<#8fd3ff><bold>◀ CREW DECK</bold>\n<gray>quests · contracts · gangs", 1.4f));
        labels.add(new Label(0.5, 70.6, 17.8, "<#ff7a5a><bold>▼ SHUTTLE BAY</bold>\n<gray>planetside · workshop · undercroft", 1.4f));
        labels.add(new Label(SPAWN_X, F + 2.6, SPAWN_Z - 3, "<gray>Welcome aboard. Right-click the <white>consoles</white> in each wing to travel.\n<white>/hub</white> brings you back here.", 0.9f));
    }

    private void crates(int x, int z, int height) {
        for (int y = F; y < F + height; y++) canvas.set(x, y, z, CRATE);
        canvas.set(x + 1, F, z, CRATE);
    }

    // ------------------------------------------------------------------ corridors

    /** Corridor from the concourse wall to a wing along (dx, dz), 7 wide, 6 tall. */
    private void corridor(int dx, int dz) {
        Canvas c = canvas;
        for (int s = 17; s <= 30; s++) {
            for (int l = -4; l <= 4; l++) {
                int x = dx != 0 ? dx * s : l, z = dz != 0 ? dz * s : l;
                for (int y = F; y <= 69; y++) if (Math.abs(l) <= 3) c.set(x, y, z, Canvas.AIR);
                if (s < 19) continue;
                c.set(x, FY - 1, z, BRICK);
                c.set(x, FY, z, l == 0 ? GRATE : Math.abs(l) == 3 ? TRIM : FLOOR_A);
                boolean frame = s == 30 || s == 19;
                boolean rib = s % 4 == 0;
                c.set(x, 70, z, frame ? hazard(l, 70) : l == 0 && s % 3 == 0 ? LIGHT : rib ? TRIM : HULL);
                if (Math.abs(l) == 4) {
                    for (int y = F; y <= 69; y++) {
                        BlockData b;
                        if (frame) b = hazard(s, y);
                        else if (rib) b = y == 68 ? WARM : HULL;
                        else if (y == F) b = BRICK;
                        else if (y == 69) b = TRIM;
                        else b = (y == 66 || y == 67) && s % 4 == 2 ? WINDOW : PLATE;
                        c.set(x, y, z, b);
                    }
                }
                // Pipe run along one ceiling edge
                if (!frame) c.set(dx != 0 ? x : 3, 69, dz != 0 ? z : 3, dx != 0 ? PIPE_X : PIPE_Z);
            }
        }
    }

    private static BlockData hazard(int a, int b) { return ((a + b) & 1) == 0 ? HAZ_Y : HAZ_B; }

    // ------------------------------------------------------------------ wings

    /**
     * Rectangular room: interior x0..x1, z0..z1, floor y=FLOOR, ceiling at FLOOR + height. Windows on the
     * walls listed in windows ("n", "s", "e", "w").
     */
    private void room(int x0, int z0, int x1, int z1, int height, String windows, BlockData accent) {
        Canvas c = canvas;
        int top = F + height;
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean wallX = x == x0 - 1 || x == x1 + 1, wallZ = z == z0 - 1 || z == z1 + 1;
                c.set(x, FY - 1, z, BRICK);
                if (!wallX && !wallZ) {
                    boolean border = x == x0 || x == x1 || z == z0 || z == z1;
                    c.set(x, FY, z, border ? TRIM : (((x >> 1) + (z >> 1)) & 1) == 0 ? FLOOR_A : FLOOR_B);
                    c.set(x, top, z, x % 4 == 0 && z % 4 == 0 ? LIGHT : (x + z) % 7 == 0 ? PLATE_DARK : HULL);
                    for (int y = F; y < top; y++) c.set(x, y, z, Canvas.AIR);
                    continue;
                }
                String side = z == z0 - 1 ? "n" : z == z1 + 1 ? "s" : x == x1 + 1 ? "e" : "w";
                boolean corner = wallX && wallZ;
                int along = wallZ ? x : z;
                boolean pilaster = corner || Math.floorMod(along, 5) == 0;
                boolean window = windows.contains(side) && !pilaster;
                for (int y = FY; y <= top; y++) {
                    BlockData b;
                    if (pilaster) b = y == top - 2 ? accent : HULL;
                    else if (y <= F) b = BRICK;
                    else if (y == F + 1 && (along & 3) == 1) b = BRICK_CRACKED;
                    else if (window && y >= F + 2 && y <= top - 3) b = WINDOW;
                    else if (y == top - 2) b = TRIM;
                    else b = PLATE;
                    c.set(x, y, z, b);
                }
            }
        }
        // Pipes along the ceiling
        if (x1 - x0 >= z1 - z0) {
            for (int x = x0; x <= x1; x++) { c.set(x, top - 1, z0, PIPE_X); c.set(x, top - 1, z1, PIPE_X); }
        } else {
            for (int z = z0; z <= z1; z++) { c.set(x0, top - 1, z, PIPE_Z); c.set(x1, top - 1, z, PIPE_Z); }
        }
    }

    private void terminal(String key, int x, int z, String title, String subtitle) {
        canvas.set(x, F, z, CONSOLE);
        canvas.set(x, F + 1, z, BUTTON);
        terminals.add(new Terminal(key, x, F, z, title, subtitle));
        labels.add(new Label(x + 0.5, F + 2.05, z + 0.5, title + (subtitle.isEmpty() ? "" : "\n<gray>" + subtitle), 0.8f));
    }

    /** A door panel set into a wall plane, with a hull frame, light strip and hazard floor in front. */
    private void door(int cx, int cz, boolean alongX, int inward, BlockData light) {
        Canvas c = canvas;
        for (int o = -2; o <= 2; o++) {
            for (int y = F; y <= F + 4; y++) {
                int x = alongX ? cx + o : cx, z = alongX ? cz : cz + o;
                boolean frame = Math.abs(o) == 2 || y == F + 3;
                c.set(x, y, z, y == F + 4 ? (Math.abs(o) <= 1 ? light : HULL) : frame ? HULL : DOOR);
            }
            if (Math.abs(o) <= 1) {
                int fx = alongX ? cx + o : cx + inward, fz = alongX ? cz + inward : cz + o;
                c.set(fx, FY, fz, hazard(o, 0));
            }
        }
    }

    private void mineLifts() {
        // Interior x -14..14, z -54..-31; door wall z = -55.
        room(-14, -54, 14, -31, 9, "ew", WARM);
        String[][] decks = {
                {"scrapyard-1", "<#c8743a>", "Scrapyard", "Grades 1-5"},
                {"reactor-6", "<#7fd65a>", "Reactor Ducts", "Grades 6-10"},
                {"cryo-11", "<#8fd3ff>", "Cryo Vaults", "Grades 11-15"},
                {"hull-16", "<#b07cff>", "Hull Breach", "Grades 16-20"},
                {"foundry-21", "<#ff5a3c>", "Core Foundry", "Grades 21-25"}};
        BlockData[] lights = {WARM, GREEN, LIGHT, VIOLET, ORANGE};
        for (int i = 0; i < decks.length; i++) {
            int x = -12 + 6 * i;
            door(x, -55, true, 1, lights[i]);
            terminal("lift-" + decks[i][0].substring(0, decks[i][0].indexOf('-')), x, -51,
                    decks[i][1] + "<bold>" + decks[i][2] + " Lift", decks[i][3]);
            labels.add(new Label(x + 0.5, F + 5.6, -53.6, decks[i][1] + "<bold>" + decks[i][2].toUpperCase(), 1.1f));
        }
        terminal("lift-best", 0, -40, "<#c8743a><bold>Express Lift", "your best open mine");
        terminal("mine-directory", -6, -40, "<white><bold>Mine Directory", "every mine, click to travel");
        terminal("parkour", 6, -40, "<#e0b354><bold>Parkour Courses", "one run a day per mine");
        crates(-13, -33, 2);
        crates(12, -53, 3);
        crates(-13, -48, 1);
    }

    private void exchange() {
        // Interior x 31..54, z -14..14.
        room(31, -14, 54, 14, 9, "ns", WARM);
        terminal("sell", 35, -12, "<#e0b354><bold>Sell Cargo", "sell your whole hold");
        terminal("sell", 40, -12, "<#e0b354><bold>Sell Cargo", "sell your whole hold");
        terminal("cargo", 45, -12, "<#e0b354><bold>Cargo Hold", "contents · upgrades · auto-sell");
        terminal("drill", 53, -6, "<#ff7a5a><bold>Drill Bay", "enchants · modules · frames");
        terminal("rankup", 53, 0, "<#7fd65a><bold>Clearance Office", "rank up your grade");
        terminal("grades", 53, 5, "<#7fd65a><bold>Grade Ladder", "costs and perks");
        // Black Market counter along the south wall
        for (int x = 34; x <= 50; x++) {
            canvas.set(x, F, 12, BRICK);
            canvas.set(x, F + 1, 12, SLAB);
            canvas.set(x, F, 14, CRATE);
            if ((x & 1) == 0) canvas.set(x, F + 1, 14, CRATE);
        }
        terminal("auction", 37, 10, "<#b07cff><bold>Black Market", "buy and sell with players");
        terminal("auction", 42, 10, "<#b07cff><bold>Black Market", "buy and sell with players");
        terminal("auction-claims", 47, 10, "<#b07cff><bold>Collection Bin", "claim sales and returns");
        labels.add(new Label(42.5, F + 5.2, 12.5, "<#b07cff><bold>BLACK MARKET", 2.0f));
        // Salvage counter and relic case on the west side of the hall
        terminal("salvage", 34, 2, "<#e0b354><bold>Salvage Counter", "relics · blueprints · stash");
        for (int z = -2; z <= 0; z++) {
            canvas.set(33, F, z, BRICK);
            canvas.set(33, F + 1, z, TINTED);
        }
        labels.add(new Label(33.5, F + 2.6, -0.5, "<gray>relic case", 0.7f));
    }

    private void crewDeck() {
        // Interior x -54..-31, z -14..14.
        room(-54, -14, -31, 14, 9, "ns", LIGHT);
        terminal("quests", -53, -5, "<#8fd3ff><bold>Quest Board", "story and side jobs");
        terminal("contracts", -53, 1, "<#8fd3ff><bold>Contracts", "daily and weekly work");
        terminal("gang", -42, -12, "<#ff5a3c><bold>Crew Office", "found or manage a gang");
        terminal("gang", -36, -12, "<#ff5a3c><bold>Crew Office", "found or manage a gang");
        // Bar along the south wall
        for (int x = -50; x <= -36; x++) {
            canvas.set(x, F, 11, BRICK);
            canvas.set(x, F + 1, 11, SLAB);
            canvas.set(x, F, 14, CRATE);
        }
        for (int x = -48; x <= -38; x += 5) canvas.set(x, F + 1, 13, BREWER);
        // Lounge benches and tables
        for (int z = -6; z <= 6; z += 6) {
            for (int x = -46; x <= -42; x++) canvas.set(x, F, z, stairs("south"));
            for (int x = -46; x <= -42; x++) canvas.set(x, F, z + 2, stairs("north"));
            canvas.set(-44, F, z + 1, SLAB);
        }
        for (int x = -46; x <= -42; x += 4) canvas.set(x, F + 8, 0, LANTERN);
        labels.add(new Label(-42.5, F + 5.2, -13.4, "<#ff5a3c><bold>CREW OFFICE", 1.6f));
        labels.add(new Label(-53.4, F + 5.2, -1.5, "<#8fd3ff><bold>JOB BOARD", 1.6f));
    }

    private void shuttleBay() {
        // Interior x -18..18, z 31..60. Bay door (force field) in the south wall.
        room(-18, 31, 18, 60, 12, "ew", ORANGE);
        Canvas c = canvas;
        for (int x = -10; x <= 10; x++) {
            for (int y = F; y <= F + 8; y++) c.set(x, y, 61, y == F + 8 || Math.abs(x) == 10 ? hazard(x, y) : FIELD);
        }
        // Landing pad
        int pz = 47;
        for (int x = -8; x <= 8; x++) for (int z = pz - 8; z <= pz + 8; z++) {
            double r = Math.hypot(x, z - pz);
            if (r <= 6.5) c.set(x, FY, z, r > 5.5 ? hazard((int) Math.round(Math.toDegrees(Math.atan2(z - pz, x)) / 20), 0) : FLOOR_PAD);
            else if (r <= 7.5 && (x == 0 || z == pz)) c.set(x, FY, z, LIGHT);
        }
        shuttle(0, pz);
        terminal("shuttle-board", -6, 35, "<#ff7a5a><bold>Shuttle Board", "planetside destinations");
        terminal("shuttle-board", 6, 35, "<#ff7a5a><bold>Shuttle Board", "planetside destinations");
        terminal("fishing", -6, 57, "<#8fd3ff><bold>Fishing Shuttle", "station waters and deck catches");
        // Workshop airlock in the west wall
        door(-19, 53, false, 1, LIGHT);
        terminal("workshop", -16, 53, "<#e0b354><bold>Workshop Airlock", "your private hull bay");
        labels.add(new Label(-18.4, F + 5.6, 53.5, "<#e0b354><bold>HULL BAYS", 1.2f));
        crates(-17, 33, 3);
        crates(15, 58, 2);
        crates(-17, 59, 1);
    }

    /** Small landed shuttle: ellipsoid hull, cockpit glass, wings and engines. Pure decoration. */
    private void shuttle(int cx, int cz) {
        Canvas c = canvas;
        double ax = 3.2, ay = 2.3, az = 6.2, cy = F + 2.0;
        for (int x = cx - 4; x <= cx + 4; x++) for (int y = F; y <= F + 5; y++) for (int z = cz - 7; z <= cz + 7; z++) {
            if (!inHull(x - cx, y - cy, z - cz, ax, ay, az)) continue;
            boolean shell = !inHull(x + 1 - cx, y - cy, z - cz, ax, ay, az) || !inHull(x - 1 - cx, y - cy, z - cz, ax, ay, az)
                    || !inHull(x - cx, y + 1 - cy, z - cz, ax, ay, az) || !inHull(x - cx, y - 1 - cy, z - cz, ax, ay, az)
                    || !inHull(x - cx, y - cy, z + 1 - cz, ax, ay, az) || !inHull(x - cx, y - cy, z - 1 - cz, ax, ay, az);
            if (!shell) continue;
            BlockData b;
            if (z - cz <= -3 && y >= cy) b = TINTED;
            else if (y <= cy - 1) b = HULL;
            else b = ((x + z) & 3) == 0 ? PLATE_DARK : PLATE;
            c.set(x, y, z, b);
        }
        for (int z = cz; z <= cz + 4; z++) for (int o = 4; o <= 7; o++) {
            if (o - 4 > (z - cz)) continue;
            c.set(cx + o, F + 1, z, HULL);
            c.set(cx - o, F + 1, z, HULL);
        }
        c.set(cx + 7, F + 2, cz + 4, ANTENNA);
        c.set(cx - 7, F + 2, cz + 4, ANTENNA);
        c.set(cx - 1, F + 2, cz + 7, ORANGE);
        c.set(cx + 1, F + 2, cz + 7, ORANGE);
        c.set(cx, F + 5, cz + 2, ANTENNA);
    }

    private static boolean inHull(double x, double y, double z, double ax, double ay, double az) {
        return (x * x) / (ax * ax) + (y * y) / (ay * ay) + (z * z) / (az * az) <= 1.0;
    }

    private void checkpoint() {
        // Guard checkpoint east of the shuttle bay: interior x 20..30, z 44..58, red lit.
        room(20, 44, 30, 58, 7, "", ORANGE);
        Canvas c = canvas;
        // Opening from the bay
        for (int z = 49; z <= 51; z++) for (int y = F; y <= F + 3; y++) c.set(19, y, z, Canvas.AIR);
        for (int z = 48; z <= 52; z++) c.set(19, F + 4, z, hazard(z, 0));
        for (int y = F; y <= F + 3; y++) { c.set(19, y, 48, hazard(y, 0)); c.set(19, y, 52, hazard(y, 1)); }
        // Red ceiling panels
        for (int x = 21; x <= 29; x += 4) for (int z = 46; z <= 56; z += 5) c.set(x, F + 7, z, RED_GLASS);
        // Scanner line: bars with a single gap
        for (int z = 44; z <= 58; z++) if (z < 49 || z > 51) { c.set(25, F, z, BARS); c.set(25, F + 1, z, BARS); }
        for (int z = 49; z <= 51; z++) { c.set(25, F + 3, z, RED_GLASS); c.set(25, FY, z, hazard(z, 1)); }
        // Guard booth
        for (int x = 21; x <= 23; x++) for (int z = 54; z <= 57; z++) for (int y = F; y <= F + 2; y++) {
            boolean edge = x == 21 || x == 23 || z == 54 || z == 57;
            if (edge) c.set(x, y, z, y == F ? BRICK : TINTED);
        }
        c.set(22, F, 55, CONSOLE);
        // Blast door to the Undercroft in the east wall
        door(31, 50, false, -1, ORANGE);
        terminal("undercroft", 28, 50, "<red><bold>Undercroft Gate", "PvP zone · contraband scanned");
        labels.add(new Label(30.4, F + 5.0, 50.5, "<red><bold>UNDERCROFT\n<gray>lower hull · PvP on · enter at your own risk", 1.1f));
        labels.add(new Label(22.5, F + 3.4, 55.5, "<gray>GUARD POST", 0.8f));
    }
}
