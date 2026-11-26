package dev.spacepunk.prison.quests;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Parsed quests.yml. Immutable; /questadmin reload swaps in a new instance. */
final class QuestConfig {
    record ObjectiveDef(String type, List<String> targets, Amount amount, String text) {}

    record CommandReward(String command, String text) {}

    record RewardDef(Amount credits, Amount tokens, Amount shards, Amount points, List<CommandReward> commands) {
        static final RewardDef NONE = new RewardDef(Amount.ZERO, Amount.ZERO, Amount.ZERO, Amount.ZERO, List.of());
    }

    record QuestDef(String id, QuestKind kind, String name, String icon, String character, String chapter,
                    int minGrade, int maxGrade, int minPrestige, List<String> requires, String after, int weight,
                    List<String> intro, List<String> outro, List<ObjectiveDef> objectives, RewardDef rewards) {}

    record Chapter(String id, String name, int minGrade, String character, String icon, List<QuestDef> steps) {}

    record Character(String id, String name, String color, String title, String profession) {
        String display() { return color + name; }
    }

    record TrackTier(int index, int points, String icon, RewardDef rewards) {}

    final ZoneId zone;
    final int dailyCount, weeklyCount;
    final RewardDef dailyBonus;
    final List<String> earnTokenReasons;
    final boolean sidebar;
    final String storyTitle;
    final Map<String, Character> characters = new LinkedHashMap<>();
    /** Notable ore ids per mine grade, for the {mine_item} placeholder. */
    final Map<Integer, List<String>> mineItems = new LinkedHashMap<>();
    final List<Chapter> chapters = new ArrayList<>();
    /** Every story step in order. */
    final List<QuestDef> storySteps = new ArrayList<>();
    final Map<String, QuestDef> tutorials = new LinkedHashMap<>();
    final Map<String, QuestDef> daily = new LinkedHashMap<>();
    final Map<String, QuestDef> weekly = new LinkedHashMap<>();
    final String trackName;
    /** Quest NPCs placed automatically in the station hub: world name, and character id to spot. */
    final boolean npcAutoPlace;
    final String npcWorld;
    final Map<String, NpcSpot> npcSpots = new LinkedHashMap<>();

    /** Where an auto-placed quest NPC stands. */
    record NpcSpot(String character, double x, double y, double z, float yaw) {}

    /** Crew Deck spots used when quests.yml has no npcs section (files written before it existed). */
    private static final List<NpcSpot> DEFAULT_SPOTS = List.of(
            new NpcSpot("warden", -51.5, 64, -1.5, -90f),
            new NpcSpot("rusk", -49.5, 64, -9.5, -90f),
            new NpcSpot("vell", -49.5, 64, 5.5, -90f),
            new NpcSpot("nhem", -38.5, 64, -9.5, 90f),
            new NpcSpot("kade", -40.5, 64, 12.5, 180f));
    final List<TrackTier> tiers = new ArrayList<>();

    private QuestConfig(YamlConfiguration y, Logger log) {
        ConfigurationSection s = section(y, "settings");
        String tz = s.getString("timezone", "");
        ZoneId z;
        try {
            z = tz == null || tz.isBlank() ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            log.warning("[quests] bad settings.timezone '" + tz + "', using the server's zone");
            z = ZoneId.systemDefault();
        }
        zone = z;
        dailyCount = Math.max(0, s.getInt("daily-count", 3));
        weeklyCount = Math.max(0, s.getInt("weekly-count", 3));
        dailyBonus = rewards(s.getConfigurationSection("daily-bonus"));
        earnTokenReasons = s.getStringList("earn-token-reasons");
        sidebar = s.getBoolean("sidebar-tracker", true);

        ConfigurationSection chars = section(y, "characters");
        for (String id : chars.getKeys(false)) {
            ConfigurationSection c = chars.getConfigurationSection(id);
            if (c == null) continue;
            characters.put(id, new Character(id, c.getString("name", id), c.getString("color", "<white>"),
                    c.getString("title", ""), c.getString("profession", "NONE")));
        }

        ConfigurationSection npcs = y.getConfigurationSection("npcs");
        npcAutoPlace = npcs == null || npcs.getBoolean("auto-place", true);
        npcWorld = npcs == null ? "station" : npcs.getString("world", "station");
        ConfigurationSection spots = npcs == null ? null : npcs.getConfigurationSection("spots");
        if (spots == null) {
            for (NpcSpot spot : DEFAULT_SPOTS) npcSpots.put(spot.character(), spot);
        } else {
            for (String id : spots.getKeys(false)) {
                ConfigurationSection sp = spots.getConfigurationSection(id);
                String c = id.toLowerCase(Locale.ROOT);
                if (sp == null || (!characters.containsKey(c) && !c.equals("board"))) {
                    log.warning("[quests] npcs.spots." + id + " is not a character (or 'board'), skipped");
                    continue;
                }
                npcSpots.put(c, new NpcSpot(c, sp.getDouble("x"), sp.getDouble("y", 64), sp.getDouble("z"), (float) sp.getDouble("yaw", 0)));
            }
        }

        ConfigurationSection items = section(y, "mine-items");
        for (String k : items.getKeys(false)) {
            try {
                mineItems.put(Integer.parseInt(k), items.getStringList(k).stream().map(x -> x.toLowerCase(Locale.ROOT)).toList());
            } catch (NumberFormatException ignored) {
                log.warning("[quests] mine-items keys must be grades 1-25, got " + k);
            }
        }

        ConfigurationSection story = section(y, "story");
        storyTitle = story.getString("title", "Story");
        for (Map<?, ?> raw : story.getMapList("chapters")) {
            ConfigurationSection c = asSection(raw);
            String cid = c.getString("id", "chapter" + (chapters.size() + 1));
            int minGrade = c.getInt("min-grade", 1);
            String character = c.getString("character", "");
            List<QuestDef> steps = new ArrayList<>();
            for (Map<?, ?> rs : c.getMapList("steps")) {
                ConfigurationSection st = asSection(rs);
                String sid = cid + "." + st.getString("id", "step" + (steps.size() + 1));
                QuestDef q = quest(sid, QuestKind.STORY, st, character, cid, steps.isEmpty() ? minGrade : 1, log);
                if (q != null) steps.add(q);
            }
            Chapter ch = new Chapter(cid, c.getString("name", cid), minGrade, character, c.getString("icon", "WRITABLE_BOOK"),
                    List.copyOf(steps));
            chapters.add(ch);
            storySteps.addAll(steps);
        }

        ConfigurationSection tuts = section(y, "tutorials");
        for (String id : tuts.getKeys(false)) {
            QuestDef q = quest(id, QuestKind.TUTORIAL, tuts.getConfigurationSection(id), "", "", 1, log);
            if (q != null) tutorials.put(id, q);
        }

        ConfigurationSection contracts = section(y, "contracts");
        for (QuestKind k : List.of(QuestKind.DAILY, QuestKind.WEEKLY)) {
            ConfigurationSection pool = contracts.getConfigurationSection(k.key);
            if (pool == null) continue;
            for (String id : pool.getKeys(false)) {
                QuestDef q = quest(id, k, pool.getConfigurationSection(id), "", "", 1, log);
                if (q != null) (k == QuestKind.DAILY ? daily : weekly).put(id, q);
            }
        }

        ConfigurationSection track = section(y, "track");
        trackName = track.getString("name", "Contract Track");
        int i = 0;
        int last = 0;
        for (Map<?, ?> raw : track.getMapList("tiers")) {
            ConfigurationSection t = asSection(raw);
            int pts = Math.max(last + 1, t.getInt("points", last + 10));
            last = pts;
            tiers.add(new TrackTier(i++, pts, t.getString("icon", ""), rewards(t.getConfigurationSection("rewards"))));
        }
    }

    static QuestConfig load(File file, Logger log) {
        return new QuestConfig(YamlConfiguration.loadConfiguration(file), log);
    }

    QuestDef byKey(QuestKind kind, String id) {
        return switch (kind) {
            case STORY -> storySteps.stream().filter(q -> q.id().equals(id)).findFirst().orElse(null);
            case TUTORIAL -> tutorials.get(id);
            case DAILY -> daily.get(id);
            case WEEKLY -> weekly.get(id);
        };
    }

    Chapter chapterOf(QuestDef step) {
        for (Chapter c : chapters) if (c.id().equals(step.chapter())) return c;
        return null;
    }

    Character character(String id) { return id == null ? null : characters.get(id); }

    int maxPoints() { return tiers.isEmpty() ? 0 : tiers.get(tiers.size() - 1).points(); }

    // ------------------------------------------------------------------ parsing helpers

    private static QuestDef quest(String id, QuestKind kind, ConfigurationSection q, String character, String chapter,
                                  int defaultMinGrade, Logger log) {
        if (q == null) return null;
        List<ObjectiveDef> objectives = new ArrayList<>();
        for (Map<?, ?> raw : q.getMapList("objectives")) {
            ConfigurationSection o = asSection(raw);
            String type = o.getString("type", "").toLowerCase(Locale.ROOT);
            if (type.isEmpty()) continue;
            List<String> targets = o.isList("target") ? o.getStringList("target")
                    : o.getString("target", "").isEmpty() ? List.of() : List.of(o.getString("target"));
            Amount amount = Amount.parse(o, "amount");
            if (amount.isZero()) amount = Amount.fixed(1);
            objectives.add(new ObjectiveDef(type, targets.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList(), amount,
                    o.getString("text", type)));
        }
        if (objectives.isEmpty()) {
            log.warning("[quests] " + kind.key + " quest " + id + " has no objectives; skipped");
            return null;
        }
        List<String> requires = new ArrayList<>(q.getStringList("requires"));
        // A quest needs every non-core objective type it uses, so systems that are not installed stay hidden.
        for (ObjectiveDef o : objectives) if (!requires.contains(o.type())) requires.add(o.type());
        return new QuestDef(id, kind, q.getString("name", id), q.getString("icon", ""),
                q.getString("character", character), chapter, q.getInt("min-grade", defaultMinGrade),
                q.getInt("max-grade", 25), q.getInt("min-prestige", 0), List.copyOf(requires), q.getString("after", ""),
                Math.max(1, q.getInt("weight", 10)), lines(q, "intro"), lines(q, "outro"), List.copyOf(objectives),
                rewards(q.getConfigurationSection("rewards")));
    }

    static RewardDef rewards(ConfigurationSection r) {
        if (r == null) return RewardDef.NONE;
        List<CommandReward> cmds = new ArrayList<>();
        for (Map<?, ?> raw : r.getMapList("commands")) {
            ConfigurationSection c = asSection(raw);
            String cmd = c.getString("command", "");
            if (!cmd.isBlank()) cmds.add(new CommandReward(cmd.startsWith("/") ? cmd.substring(1) : cmd, c.getString("text", "")));
        }
        return new RewardDef(Amount.parse(r, "credits"), Amount.parse(r, "tokens"), Amount.parse(r, "shards"),
                Amount.parse(r, "points"), List.copyOf(cmds));
    }

    private static List<String> lines(ConfigurationSection s, String key) {
        if (s.isList(key)) return List.copyOf(s.getStringList(key));
        String one = s.getString(key, "");
        return one.isEmpty() ? List.of() : List.of(one);
    }

    private static ConfigurationSection section(ConfigurationSection y, String key) {
        ConfigurationSection s = y.getConfigurationSection(key);
        return s != null ? s : new MemoryConfiguration();
    }

    private static ConfigurationSection asSection(Map<?, ?> map) {
        return new MemoryConfiguration().createSection("x", map == null ? Collections.emptyMap() : map);
    }
}
