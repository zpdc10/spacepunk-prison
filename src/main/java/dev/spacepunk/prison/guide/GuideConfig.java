package dev.spacepunk.prison.guide;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** guide.yml: chapters (book pages, first-visit popups, checklists), info boards and settings. */
final class GuideConfig {
    /** One checklist step. {@code on} uses the same event syntax as triggers (see guide.yml). */
    record Step(String on, Set<String> targets, long amount, String text, List<String> hint) {}

    record Chapter(String id, String name, String color, Material icon, String model, BossBar.Color barColor,
                   List<String> summary, List<String> triggers, String title, String subtitle, List<String> brief,
                   List<Step> steps, String done, List<String> pages, String wiki, List<String> board) {
        boolean hasChecklist() { return !steps.isEmpty(); }
    }

    /** A fixed info board: floating text, and a clickable hitbox that opens the chapter. */
    record Board(String id, String chapter, String world, double x, double y, double z, float scale,
                 boolean clickable, List<String> lines) {}

    /** A board placed relative to a world's spawn (fishing hub, Undercroft checkpoint) or to every mine's spawn. */
    record Anchor(String chapter, String world, double forward, double up, double side, float scale,
                  boolean clickable, List<String> lines) {}

    final String wikiUrl;
    final int joinDelayTicks;
    final int popupGapTicks;
    final boolean popupsDefault;
    final boolean barDefault;
    final int boardRefreshTicks;
    final Map<String, Chapter> chapters = new LinkedHashMap<>();
    final List<Board> boards = new ArrayList<>();
    final List<Anchor> spawnBoards = new ArrayList<>();
    final Anchor mineBoard;
    final boolean bayBoard;
    final double[] bayOffset;
    final float bayScale;

    GuideConfig(YamlConfiguration y, Logger log) {
        ConfigurationSection s = section(y, "settings");
        wikiUrl = trimSlash(s.getString("wiki-url", ""));
        joinDelayTicks = Math.max(0, s.getInt("join-delay-seconds", 8)) * 20;
        popupGapTicks = Math.max(20, s.getInt("popup-gap-seconds", 4) * 20);
        popupsDefault = s.getBoolean("popups", true);
        barDefault = s.getBoolean("checklist-bar", true);
        boardRefreshTicks = Math.max(20, s.getInt("board-refresh-seconds", 5) * 20);

        ConfigurationSection cs = y.getConfigurationSection("chapters");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection c = cs.getConfigurationSection(id);
                if (c == null) continue;
                try {
                    chapters.put(id, chapter(id, c));
                } catch (RuntimeException e) {
                    log.warning("[guide] chapter " + id + ": " + e.getMessage());
                }
            }
        }

        ConfigurationSection bs = y.getConfigurationSection("boards");
        if (bs != null) {
            ConfigurationSection fixed = bs.getConfigurationSection("fixed");
            if (fixed != null) {
                for (String id : fixed.getKeys(false)) {
                    Board b = board(id, fixed.getConfigurationSection(id));
                    if (b != null) boards.add(b);
                }
            }
            for (Map<?, ?> raw : bs.getMapList("world-spawns")) {
                Anchor a = anchor(raw);
                if (a != null) spawnBoards.add(a);
            }
            ConfigurationSection mb = bs.getConfigurationSection("mines");
            mineBoard = mb != null && mb.getBoolean("enabled", true) ? anchor(mb.getValues(false)) : null;
            ConfigurationSection bay = bs.getConfigurationSection("workshop-bay");
            bayBoard = bay == null || bay.getBoolean("enabled", true);
            List<Double> off = bay == null ? List.of() : bay.getDoubleList("offset");
            bayOffset = off.size() == 3 ? new double[]{off.get(0), off.get(1), off.get(2)} : new double[]{0, 3.0, 4.5};
            bayScale = bay == null ? 0.8f : (float) bay.getDouble("scale", 0.8);
        } else {
            mineBoard = null;
            bayBoard = true;
            bayOffset = new double[]{0, 3.0, 4.5};
            bayScale = 0.8f;
        }
    }

    private static Chapter chapter(String id, ConfigurationSection c) {
        String name = c.getString("name", id);
        String color = c.getString("color", "<#c8743a>");
        Material icon = material(c.getString("icon"), Material.BOOK);
        BossBar.Color bar;
        try {
            bar = BossBar.Color.valueOf(c.getString("bar-color", "YELLOW").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            bar = BossBar.Color.YELLOW;
        }
        List<Step> steps = new ArrayList<>();
        for (Map<?, ?> raw : c.getMapList("steps")) {
            Object on = raw.get("on");
            if (on == null) continue;
            Set<String> targets = new LinkedHashSet<>();
            Object t = raw.get("target");
            if (t instanceof List<?> l) l.forEach(o -> targets.add(String.valueOf(o).toLowerCase(Locale.ROOT)));
            else if (t != null) targets.add(String.valueOf(t).toLowerCase(Locale.ROOT));
            long amount = raw.get("amount") instanceof Number n ? Math.max(1, n.longValue()) : 1;
            String text = raw.get("text") == null ? String.valueOf(on) : String.valueOf(raw.get("text"));
            steps.add(new Step(String.valueOf(on).toLowerCase(Locale.ROOT), targets, amount, text, strings(raw.get("hint"))));
        }
        List<String> title = c.getStringList("title");
        return new Chapter(id, name, color, icon, c.getString("model"), bar,
                c.getStringList("summary"), lower(c.getStringList("triggers")),
                title.isEmpty() ? null : title.get(0), title.size() > 1 ? title.get(1) : "",
                c.getStringList("brief"), List.copyOf(steps), c.getString("done"),
                c.getStringList("pages"), c.getString("wiki"), c.getStringList("board"));
    }

    static Board board(String id, ConfigurationSection c) {
        if (c == null || c.getString("chapter") == null || c.getString("world") == null) return null;
        return new Board(id, c.getString("chapter"), c.getString("world"), c.getDouble("x"), c.getDouble("y"),
                c.getDouble("z"), (float) c.getDouble("scale", 1.0), c.getBoolean("clickable", true), c.getStringList("lines"));
    }

    private static Anchor anchor(Map<?, ?> raw) {
        if (raw == null || raw.get("chapter") == null) return null;
        return new Anchor(String.valueOf(raw.get("chapter")), raw.get("world") == null ? null : String.valueOf(raw.get("world")),
                num(raw.get("forward"), 3), num(raw.get("up"), 0), num(raw.get("side"), 0),
                (float) num(raw.get("scale"), 1.0), !Boolean.FALSE.equals(raw.get("clickable")), strings(raw.get("lines")));
    }

    private static double num(Object o, double def) { return o instanceof Number n ? n.doubleValue() : def; }

    private static List<String> strings(Object o) {
        if (o == null) return List.of();
        if (o instanceof List<?> l) return l.stream().map(String::valueOf).toList();
        return List.of(String.valueOf(o));
    }

    private static List<String> lower(List<String> in) {
        return in.stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
    }

    private static Material material(String name, Material def) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name);
        return m == null ? def : m;
    }

    private static ConfigurationSection section(YamlConfiguration y, String key) {
        ConfigurationSection s = y.getConfigurationSection(key);
        return s != null ? s : y.createSection(key);
    }

    private static String trimSlash(String s) {
        s = s == null ? "" : s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Full wiki URL for a chapter, or null when no wiki is configured. */
    String wiki(Chapter c) {
        if (wikiUrl.isEmpty()) return null;
        return c.wiki() == null || c.wiki().isBlank() ? wikiUrl + "/" : wikiUrl + "/" + c.wiki();
    }
}
