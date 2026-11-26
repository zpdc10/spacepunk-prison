package dev.spacepunk.prison.drill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The physical footprint of a module on the drill's module rig. Cells are (x, y) offsets; rotation turns the
 * shape 90 degrees clockwise per step. The pivot is the top-most, then left-most cell after rotation: that is the
 * cell that lands on the rig square the player clicks.
 */
public enum ModuleShape {
    DOT("1x1", new int[][]{{0, 0}}),
    BAR2("1x2 bar", new int[][]{{0, 0}, {1, 0}}),
    BAR3("1x3 bar", new int[][]{{0, 0}, {1, 0}, {2, 0}}),
    ELL("L", new int[][]{{0, 0}, {0, 1}, {1, 1}}),
    TEE("T", new int[][]{{0, 0}, {1, 0}, {2, 0}, {1, 1}}),
    ZIG("Z", new int[][]{{0, 0}, {1, 0}, {1, 1}, {2, 1}}),
    SQUARE("2x2", new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}});

    public final String label;
    private final int[][] base;

    ModuleShape(String label, int[][] base) {
        this.label = label;
        this.base = base;
    }

    public int size() { return base.length; }

    public static ModuleShape parse(String s, ModuleShape def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    /** Cells after rot clockwise quarter turns, normalised so the bounding box starts at (0, 0), pivot first. */
    public List<int[]> cells(int rot) {
        List<int[]> out = new ArrayList<>();
        int r = Math.floorMod(rot, 4);
        for (int[] c : base) {
            int x = c[0], y = c[1];
            for (int i = 0; i < r; i++) {
                int nx = -y;
                y = x;
                x = nx;
            }
            out.add(new int[]{x, y});
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (int[] c : out) {
            minX = Math.min(minX, c[0]);
            minY = Math.min(minY, c[1]);
        }
        for (int[] c : out) {
            c[0] -= minX;
            c[1] -= minY;
        }
        out.sort(Comparator.<int[]>comparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        return out;
    }

    /** Board cells covered when the pivot sits on (px, py). */
    public List<int[]> cellsAt(int px, int py, int rot) {
        List<int[]> cells = cells(rot);
        int[] pivot = cells.getFirst();
        List<int[]> out = new ArrayList<>(cells.size());
        for (int[] c : cells) out.add(new int[]{px + c[0] - pivot[0], py + c[1] - pivot[1]});
        return out;
    }

    /** Distinct rotations (a square has one, a bar two). */
    public int rotations() {
        return switch (this) {
            case DOT, SQUARE -> 1;
            case BAR2, BAR3, ZIG -> 2;
            default -> 4;
        };
    }

    /** The shape as small text rows for item lore (■ filled, □ empty). */
    public List<String> picture(int rot, String fill) { return picture(rot, fill, false); }

    /** As {@link #picture(int, String)}; markPivot draws the pivot cell (the one that goes on the clicked cell) as ◆. */
    public List<String> picture(int rot, String fill, boolean markPivot) {
        List<int[]> cells = cells(rot);
        int w = 0, h = 0;
        for (int[] c : cells) {
            w = Math.max(w, c[0] + 1);
            h = Math.max(h, c[1] + 1);
        }
        int[][] grid = new int[h][w];
        for (int[] c : cells) grid[c[1]][c[0]] = 1;
        if (markPivot) grid[cells.getFirst()[1]][cells.getFirst()[0]] = 2;
        List<String> rows = new ArrayList<>();
        for (int[] row : grid) {
            StringBuilder sb = new StringBuilder("  ");
            for (int b : row) sb.append(b == 2 ? "<white>◆" : b == 1 ? fill + "■" : "<dark_gray>□");
            rows.add(sb.toString());
        }
        return rows;
    }
}
