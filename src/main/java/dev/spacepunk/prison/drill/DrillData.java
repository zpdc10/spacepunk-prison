package dev.spacepunk.prison.drill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persistent per-player drill state, stored as JSON in PlayerProfile.moduleData("drill"). */
public final class DrillData {
    public int version = 2;
    public String frame;
    public Map<String, Integer> levels = new HashMap<>();
    /** Old (version 1) socketed module ids. Emptied on join: those modules go back to the player as items. */
    public List<String> sockets = new ArrayList<>();
    /** Modules fitted on the module rig. */
    public List<Placed> rig = new ArrayList<>();
    /** Modules on the other rigs, by rig key: "rod" (Rod Rig), "gun:<gun id>" (that gun's Weapon Rig). */
    public Map<String, List<Placed>> gear = new HashMap<>();
    /** Module recipes the player has learned (Assembly Bench). */
    public Set<String> schematics = new LinkedHashSet<>();
    /** Hidden module combos the player has discovered. */
    public Set<String> combos = new LinkedHashSet<>();
    /** Exo-Rig tiers bought (0 = none yet). */
    public int exoTier;
    /** Rig expansions bought with Shards. */
    public int expansion;
    public long blocksMined;
    public long tokensFound;
    public long modulesFound;
    public long modulesFused;
    public long overheats;
    public long perfectVents;

    /** One module on the rig: pivot cell (x, y), quarter turns clockwise, mark. */
    public static final class Placed {
        public String id;
        public int mk = 1;
        public int x;
        public int y;
        public int rot;

        public Placed() {}

        public Placed(String id, int mk, int x, int y, int rot) {
            this.id = id;
            this.mk = mk;
            this.x = x;
            this.y = y;
            this.rot = rot;
        }
    }

    /** Fills in anything missing after Gson. */
    public DrillData normalize(String defaultFrame) {
        if (frame == null) frame = defaultFrame;
        if (levels == null) levels = new HashMap<>();
        if (sockets == null) sockets = new ArrayList<>();
        if (rig == null) rig = new ArrayList<>();
        rig.removeIf(p -> p == null || p.id == null);
        for (Placed p : rig) if (p.mk < 1) p.mk = 1;
        if (gear == null) gear = new HashMap<>();
        gear.values().removeIf(l -> l == null);
        for (List<Placed> l : gear.values()) {
            l.removeIf(p -> p == null || p.id == null);
            for (Placed p : l) if (p.mk < 1) p.mk = 1;
        }
        if (schematics == null) schematics = new LinkedHashSet<>();
        if (combos == null) combos = new LinkedHashSet<>();
        if (expansion < 0) expansion = 0;
        return this;
    }

    public int level(Enchant e) { return levels.getOrDefault(e.id(), 0); }

    public void level(Enchant e, int lvl) {
        if (lvl <= 0) levels.remove(e.id()); else levels.put(e.id(), lvl);
    }

    public boolean hasModule(String moduleId) {
        for (Placed p : rig) if (p.id.equals(moduleId)) return true;
        return false;
    }
}
