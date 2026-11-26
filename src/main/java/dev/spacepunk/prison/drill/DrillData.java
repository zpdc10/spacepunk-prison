package dev.spacepunk.prison.drill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistent per-player drill state, stored as JSON in PlayerProfile.moduleData("drill"). */
public final class DrillData {
    public int version = 1;
    public String frame;
    public Map<String, Integer> levels = new HashMap<>();
    /** Socketed module ids by socket index; null = empty. */
    public List<String> sockets = new ArrayList<>();
    public long blocksMined;
    public long tokensFound;
    public long modulesFound;
    public long overheats;
    public long perfectVents;

    /** Fills in anything missing after Gson. */
    public DrillData normalize(String defaultFrame) {
        if (frame == null) frame = defaultFrame;
        if (levels == null) levels = new HashMap<>();
        if (sockets == null) sockets = new ArrayList<>();
        return this;
    }

    public int level(Enchant e) { return levels.getOrDefault(e.id(), 0); }

    public void level(Enchant e, int lvl) {
        if (lvl <= 0) levels.remove(e.id()); else levels.put(e.id(), lvl);
    }

    public String socket(int i) { return i < sockets.size() ? sockets.get(i) : null; }

    public void socket(int i, String moduleId) {
        while (sockets.size() <= i) sockets.add(null);
        sockets.set(i, moduleId);
    }

    public boolean hasModule(String moduleId) { return sockets.contains(moduleId); }
}
