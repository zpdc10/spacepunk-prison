package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.drill.DrillConfig.ComboDef;
import dev.spacepunk.prison.drill.DrillConfig.ExpansionDef;
import dev.spacepunk.prison.drill.DrillConfig.FamilyDef;
import dev.spacepunk.prison.drill.DrillConfig.FrameDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;
import dev.spacepunk.prison.drill.DrillData.Placed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The module rig: a grid of cells set by the drill frame (plus Shard expansions) that modules are fitted into like
 * puzzle pieces. Built from {@link DrillData} on demand; pure data, no Bukkit.
 *
 * Rules: modules can't overlap or leave the grid; core cells take only Legendary modules and every Legendary module
 * must cover a core cell; one of each module; a family's max count. Placement rules (edge / needs / avoid) don't
 * block fitting: a module that breaks one stays on the rig but goes offline until it's fixed.
 */
public final class ModuleRig {
    public final int width;
    public final int height;
    private final Set<Integer> core = new HashSet<>();
    /** Cell index (y * width + x) -> index in {@link #entries}, or -1. */
    private final int[] owner;
    public final List<Entry> entries = new ArrayList<>();
    /** Online modules per family. */
    public final Map<String, Integer> familyCounts = new LinkedHashMap<>();
    public final List<ComboDef> activeCombos = new ArrayList<>();

    public static final class Entry {
        public final int index;
        public final Placed placed;
        public final ModuleDef def;
        public final List<int[]> cells;
        public final Set<Integer> neighbours = new HashSet<>();
        public boolean online = true;
        /** Why it's offline (null when online). */
        public String problem;
        /** Cons of this module are raised to this extra power by draining neighbours (1 = none). */
        public double drain = 1.0;

        Entry(int index, Placed placed, ModuleDef def, List<int[]> cells) {
            this.index = index;
            this.placed = placed;
            this.def = def;
            this.cells = cells;
        }
    }

    private ModuleRig(int width, int height, List<int[]> coreCells) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        for (int[] c : coreCells) if (inside(c[0], c[1])) core.add(c[1] * this.width + c[0]);
        owner = new int[this.width * this.height];
        Arrays.fill(owner, -1);
    }

    /** Rig columns added by the expansions this player owns, given their frame. */
    public static int expansionColumns(DrillConfig c, FrameDef frame, int bought) {
        if (!frame.hasRig()) return 0;
        int cols = 0;
        for (int i = 0; i < Math.min(bought, c.expansions.size()); i++) {
            ExpansionDef e = c.expansions.get(i);
            FrameDef min = e.frame().isEmpty() ? null : c.frame(e.frame());
            if (min != null && frame.index() < min.index()) break;
            cols += e.columns();
        }
        return cols;
    }

    public static ModuleRig build(DrillConfig c, DrillData d) {
        FrameDef f = c.frame(d.frame);
        ModuleRig rig = new ModuleRig(f.rigWidth() + expansionColumns(c, f, d.expansion), f.rigHeight(), f.coreCells());
        for (Placed p : d.rig) {
            ModuleDef def = c.module(p.id);
            if (def == null) continue;
            Entry e = new Entry(rig.entries.size(), p, def, def.shape().cellsAt(p.x, p.y, p.rot));
            boolean fits = true;
            for (int[] cell : e.cells) {
                if (!rig.inside(cell[0], cell[1]) || rig.owner[rig.idx(cell[0], cell[1])] >= 0) { fits = false; break; }
            }
            rig.entries.add(e);
            if (!fits) {
                e.online = false;
                e.problem = "Doesn't fit this rig";
                continue;
            }
            for (int[] cell : e.cells) rig.owner[rig.idx(cell[0], cell[1])] = e.index;
        }
        rig.evaluate(c);
        return rig;
    }

    private void evaluate(DrillConfig c) {
        // Neighbours: modules sharing an edge.
        for (Entry e : entries) {
            if ("Doesn't fit this rig".equals(e.problem)) continue;
            for (int[] cell : e.cells) {
                int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] dlt : around) {
                    int o = ownerAt(cell[0] + dlt[0], cell[1] + dlt[1]);
                    if (o >= 0 && o != e.index) e.neighbours.add(o);
                }
            }
        }
        for (Entry e : entries) {
            if (!e.online) continue;
            ModuleDef m = e.def;
            if (m.edge() && !touchesEdge(e)) {
                e.online = false;
                e.problem = "Must sit on the edge of the rig";
            } else if (m.needs() != null && !touchesFamily(e, m.needs())) {
                FamilyDef f = c.family(m.needs());
                e.online = false;
                e.problem = "Must touch a " + (f == null ? m.needs() : f.name()) + " module";
            } else if (m.avoid() != null && touchesFamily(e, m.avoid())) {
                FamilyDef f = c.family(m.avoid());
                e.online = false;
                e.problem = "Can't touch a " + (f == null ? m.avoid() : f.name()) + " module";
            }
        }
        for (Entry e : entries) {
            if (!e.online) continue;
            if (!e.def.family().isEmpty()) familyCounts.merge(e.def.family(), 1, Integer::sum);
            for (int n : e.neighbours) {
                Entry other = entries.get(n);
                if (other.online && other.def.drain() > 1.0) e.drain *= other.def.drain();
            }
        }
        for (ComboDef combo : c.combos.values()) {
            for (Entry e : entries) {
                if (!e.online || !e.def.id().equals(combo.a())) continue;
                boolean found = false;
                for (int n : e.neighbours) {
                    Entry other = entries.get(n);
                    if (other.online && combo.matches(e.def.id(), other.def.id())) { found = true; break; }
                }
                if (found) { activeCombos.add(combo); break; }
            }
        }
    }

    // ---- geometry ----

    public boolean inside(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    private int idx(int x, int y) { return y * width + x; }

    public boolean isCore(int x, int y) { return inside(x, y) && core.contains(idx(x, y)); }

    /** Entry index covering (x, y), or -1. */
    public int ownerAt(int x, int y) { return inside(x, y) ? owner[idx(x, y)] : -1; }

    public Entry entryAt(int x, int y) {
        int o = ownerAt(x, y);
        return o < 0 ? null : entries.get(o);
    }

    public int cellCount() { return width * height; }

    public int usedCells() {
        int n = 0;
        for (int o : owner) if (o >= 0) n++;
        return n;
    }

    private boolean touchesEdge(Entry e) {
        for (int[] cell : e.cells) {
            if (cell[0] == 0 || cell[1] == 0 || cell[0] == width - 1 || cell[1] == height - 1) return true;
        }
        return false;
    }

    private boolean touchesFamily(Entry e, String family) {
        for (int n : e.neighbours) if (family.equals(entries.get(n).def.family())) return true;
        return false;
    }

    /**
     * Why this module can't be fitted with its pivot on (px, py), or null if it can.
     * ignore = an entry index to leave out (the module being moved), or -1.
     */
    public String canPlace(DrillConfig c, ModuleDef def, int px, int py, int rot, int ignore) {
        if (width == 0 || height == 0) return "Your frame has no module rig yet.";
        boolean coversCore = false;
        for (int[] cell : def.shape().cellsAt(px, py, rot)) {
            if (!inside(cell[0], cell[1])) return "It doesn't fit there: part of it hangs off the rig.";
            int o = owner[idx(cell[0], cell[1])];
            if (o >= 0 && o != ignore) return "It overlaps " + entries.get(o).def.name() + ".";
            if (isCore(cell[0], cell[1])) coversCore = true;
        }
        if (def.needsCore() && !coversCore) return "Legendary modules must cover a core cell (the orange squares).";
        if (!def.needsCore() && coversCore) return "Core cells only take Legendary modules.";
        int sameFamily = 0;
        for (Entry e : entries) {
            if (e.index == ignore) continue;
            if (e.def.id().equals(def.id())) return "You already have a " + def.name() + " fitted.";
            if (!def.family().isEmpty() && def.family().equals(e.def.family())) sameFamily++;
        }
        FamilyDef f = c.family(def.family());
        if (f != null && sameFamily >= f.max()) return "Your rig can't run more than " + f.max() + " " + f.name() + " module" + (f.max() == 1 ? "" : "s") + ".";
        return null;
    }
}
