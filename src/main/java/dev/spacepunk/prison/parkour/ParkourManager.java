package dev.spacepunk.prison.parkour;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.ObjectiveTypes;
import dev.spacepunk.prison.api.Objectives;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.WarpService;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.parkour.Course.Placed;
import dev.spacepunk.prison.parkour.Course.Pos;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Built courses, active runs, daily reward rolls and personal bests. Main thread only. */
final class ParkourManager {
    static final String DATA_KEY = "parkour";
    private static final Gson GSON = new Gson();
    private static final Type STATS_TYPE = new TypeToken<LinkedHashMap<String, Stats>>() {}.getType();

    /** Per player, per course. */
    static final class Stats {
        long best;
        int completions;
        String rewardDay = "";
    }

    record Record(String name, long millis) {}

    static final class Run {
        final String course;
        long start;
        int next; // index of the next checkpoint to reach; == checkpoints.size() means the finish is next
        int falls;

        Run(String course) { this.course = course; }
    }

    enum Role { START, CHECKPOINT, FINISH }

    record Hit(String course, Role role, int index) {}

    private final Plugin plugin;
    private final Supplier<ParkourConfig> config;
    private final File dataFile;
    private final NamespacedKey displayKey;
    private final Map<String, Course> courses = new LinkedHashMap<>();
    private final Map<String, Record> records = new HashMap<>();
    private final Map<String, Hit> index = new HashMap<>();
    private final Map<UUID, Run> runs = new HashMap<>();
    private final Map<UUID, Map<String, Stats>> stats = new HashMap<>();

    ParkourManager(Plugin plugin, Supplier<ParkourConfig> config) {
        this.plugin = plugin;
        this.config = config;
        this.dataFile = new File(plugin.getDataFolder(), "parkour-data.yml");
        this.displayKey = new NamespacedKey(plugin, "parkour_display");
        load();
    }

    ParkourConfig cfg() { return config.get(); }

    Map<String, Course> courses() { return courses; }

    Optional<Record> record(String course) { return Optional.ofNullable(records.get(course)); }

    // ------------------------------------------------------------------ storage

    private void load() {
        courses.clear();
        records.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection cs = y.getConfigurationSection("courses");
        if (cs != null) for (String id : cs.getKeys(false)) {
            try {
                courses.put(id, Course.load(id, cs.getConfigurationSection(id)));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "[parkour] could not read course " + id, e);
            }
        }
        ConfigurationSection rs = y.getConfigurationSection("records");
        if (rs != null) for (String id : rs.getKeys(false)) {
            records.put(id, new Record(rs.getString(id + ".name", "?"), rs.getLong(id + ".millis", 0)));
        }
        reindex();
    }

    /** Registers a station warp per built run (category "parkour"), if the station module is installed. */
    void registerWarps() {
        WarpService warps = Services.find(WarpService.class).orElse(null);
        if (warps == null) return;
        for (WarpService.Warp w : List.copyOf(warps.all())) {
            if (w.id().startsWith("parkour-") && !courses.containsKey(w.id().substring("parkour-".length()))) warps.unregister(w.id());
        }
        for (Course c : courses.values()) {
            String id = c.mineId;
            warps.register(new WarpService.Warp("parkour-" + id, deckColor(c.grade) + mineName(id) + " run", WarpService.PARKOUR,
                    org.bukkit.Material.LADDER, List.of("<gray>Grade " + c.grade + " · " + c.jumps + " jumps · " + c.checkpoints.size()
                            + " checkpoints", "<gray>One reward roll a day"),
                    p -> {
                        Course now = courses.get(id);
                        World w = now == null ? null : now.bukkitWorld();
                        return w == null ? null : now.respawns.get(0).at(w);
                    },
                    p -> {
                        Course now = courses.get(id);
                        return now == null || canRun(p, now) ? null : "<red>Needs Grade " + now.grade;
                    }));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Built parkour courses (written by /parkouradmin). Edit parkour.yml instead and regenerate."));
        for (Course c : courses.values()) c.save(y.createSection("courses." + c.mineId));
        for (Map.Entry<String, Record> r : records.entrySet()) {
            y.set("records." + r.getKey() + ".name", r.getValue().name());
            y.set("records." + r.getKey() + ".millis", r.getValue().millis());
        }
        try {
            y.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[parkour] could not save parkour-data.yml", e);
        }
    }

    private void reindex() {
        index.clear();
        for (Course c : courses.values()) {
            index.put(key(c.world, c.start), new Hit(c.mineId, Role.START, 0));
            for (int i = 0; i < c.checkpoints.size(); i++) index.put(key(c.world, c.checkpoints.get(i)), new Hit(c.mineId, Role.CHECKPOINT, i));
            index.put(key(c.world, c.finish), new Hit(c.mineId, Role.FINISH, 0));
        }
    }

    private static String key(String world, Pos p) { return world + ":" + p.x() + ":" + p.y() + ":" + p.z(); }

    Hit hitAt(Block b) { return index.get(key(b.getWorld().getName(), new Pos(b.getX(), b.getY(), b.getZ()))); }

    // ------------------------------------------------------------------ building

    /** Builds (or rebuilds) the course for a mine. Returns an error message, or null on success. */
    String generate(MineService.MineInfo mine, long extraSeed) {
        Location spawn = mine.spawn();
        if (spawn == null || spawn.getWorld() == null) return mine.id() + " is not placed yet (/mineadmin generate).";
        clear(mine.id());
        World w = spawn.getWorld();
        int sx = spawn.getBlockX(), sy = spawn.getBlockY(), sz = spawn.getBlockZ();
        int gate = Integer.MIN_VALUE;
        for (int k = 1; k <= 10; k++) {
            if (!w.getBlockAt(sx, sy, sz - k).getType().isAir()) { gate = sz - k; break; }
        }
        long seed = mine.id().hashCode() * 31L + cfg().seed + extraSeed;
        Course c = CourseGenerator.plan(cfg(), mine.id(), w.getName(), mine.requiredGrade(), sx, sy - 1, gate, sz, seed);
        // Open a 3-wide gap in the parapet so the catwalk joins the rim.
        if (gate != Integer.MIN_VALUE) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) {
                Block b = w.getBlockAt(sx + dx, sy + dy, gate);
                if (b.getType().isAir()) continue;
                c.replaced.add(new Placed(new Pos(b.getX(), b.getY(), b.getZ()), b.getBlockData().getAsString()));
                b.setType(org.bukkit.Material.AIR, false);
            }
        }
        for (Placed p : c.placed) {
            Block b = w.getBlockAt(p.pos().x(), p.pos().y(), p.pos().z());
            if (!b.getType().isAir()) c.replaced.add(new Placed(p.pos(), b.getBlockData().getAsString()));
            try {
                b.setBlockData(Bukkit.createBlockData(p.data()), false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[parkour] bad block " + p.data() + " in course " + mine.id());
            }
        }
        if (cfg().displays) spawnDisplays(c, mine);
        courses.put(c.mineId, c);
        reindex();
        save();
        registerWarps();
        return null;
    }

    private void spawnDisplays(Course c, MineService.MineInfo mine) {
        World w = c.bukkitWorld();
        if (w == null) return;
        ParkourConfig.Tier tier = cfg().tier(ParkourConfig.deckOf(c.grade));
        String color = deckColor(c.grade);
        Location top = new Location(w, c.start.x() + 0.5, c.start.y() + 2.2, c.start.z() + 0.5);
        c.displays.add(display(top, color + "<bold>MAINTENANCE RUN</bold>\n<white>" + mine.displayName()
                + "\n<gray>" + tier.name() + " · " + c.jumps + " jumps · " + c.checkpoints.size() + " checkpoints"
                + "\n<dark_gray>Step on the plate. Daily reward roll at the end.").getUniqueId());
        Location fin = new Location(w, c.finish.x() + 0.5, c.finish.y() + 1.8, c.finish.z() + 0.5);
        c.displays.add(display(fin, color + "<bold>FINISH").getUniqueId());
    }

    private TextDisplay display(Location at, String text) {
        return at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(Text.mm(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(true);
            d.setShadowed(true);
            d.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, at.getWorld().getName());
        });
    }

    /** Removes a course's blocks and signs, restoring what it replaced. Returns false if there was none. */
    /**
     * Fills the 3x2 doorway that generate opened in the mine's north wall with the wall block above it. Courses built
     * when the wall was a 1-high parapet only recorded one row in {@code replaced}, which would leave a gap in today's
     * full-height wall. The catwalk is the first placed block, one block in front of the wall at rim height.
     */
    private static void closeDoorway(World w, Course c) {
        if (c.placed.isEmpty()) return;
        Pos walk = c.placed.getFirst().pos();
        int cx = walk.x() + 1, gz = walk.z() + 1, y0 = walk.y() + 1;
        for (int dx = -1; dx <= 1; dx++) {
            Block above = w.getBlockAt(cx + dx, y0 + 2, gz);
            if (above.getType().isAir()) continue;
            for (int dy = 0; dy <= 1; dy++) {
                Block b = w.getBlockAt(cx + dx, y0 + dy, gz);
                if (b.getType().isAir()) b.setBlockData(above.getBlockData(), false);
            }
        }
    }

    boolean clear(String mineId) {
        Course c = courses.remove(mineId);
        if (c == null) return false;
        for (Map.Entry<UUID, Run> r : List.copyOf(runs.entrySet())) {
            if (r.getValue().course.equals(mineId)) {
                Player p = Bukkit.getPlayer(r.getKey());
                if (p != null) endRun(p, "<gray>That course was rebuilt.");
                else runs.remove(r.getKey());
            }
        }
        World w = c.bukkitWorld();
        if (w != null) {
            for (Placed p : c.placed) w.getBlockAt(p.pos().x(), p.pos().y(), p.pos().z()).setType(org.bukkit.Material.AIR, false);
            for (Placed p : c.replaced) {
                try {
                    w.getBlockAt(p.pos().x(), p.pos().y(), p.pos().z()).setBlockData(Bukkit.createBlockData(p.data()), false);
                } catch (IllegalArgumentException ignored) { }
            }
            closeDoorway(w, c);
            for (UUID id : c.displays) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
            // Signs in unloaded chunks: sweep the area once it is loaded now.
            Location centre = new Location(w, (c.minX + c.maxX) / 2.0, (c.minY + c.maxY) / 2.0, (c.minZ + c.maxZ) / 2.0);
            double rx = (c.maxX - c.minX) / 2.0 + 3, ry = (c.maxY - c.minY) / 2.0 + 5, rz = (c.maxZ - c.minZ) / 2.0 + 3;
            for (Entity e : w.getNearbyEntities(centre, rx, ry, rz)) {
                if (e.getPersistentDataContainer().has(displayKey, PersistentDataType.STRING)) e.remove();
            }
        }
        reindex();
        save();
        registerWarps();
        return true;
    }

    // ------------------------------------------------------------------ players

    void loadPlayer(UUID id, PlayerProfile profile) {
        Map<String, Stats> m = null;
        String json = profile.moduleData(DATA_KEY);
        if (json != null) {
            try {
                m = GSON.fromJson(json, STATS_TYPE);
            } catch (JsonSyntaxException e) {
                plugin.getLogger().warning("[parkour] bad parkour data for " + id + ", starting fresh");
            }
        }
        stats.put(id, m == null ? new LinkedHashMap<>() : m);
    }

    void unloadPlayer(Player p) {
        if (runs.containsKey(p.getUniqueId())) endRun(p, null);
        stats.remove(p.getUniqueId());
    }

    Stats stats(UUID id, String course) {
        Map<String, Stats> m = stats.get(id);
        if (m == null) return null;
        return m.computeIfAbsent(course, k -> new Stats());
    }

    private void writeStats(UUID id) {
        Map<String, Stats> m = stats.get(id);
        if (m == null) return;
        ProfileService profiles = Services.get(ProfileService.class);
        profiles.get(id).ifPresent(prof -> {
            prof.moduleData(DATA_KEY, GSON.toJson(m));
            profiles.markDirty(id);
        });
    }

    String today() { return LocalDate.now(cfg().zone).toString(); }

    String untilReset() {
        ZonedDateTime now = ZonedDateTime.now(cfg().zone);
        Duration d = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(cfg().zone));
        long s = Math.max(0, d.getSeconds());
        return s >= 3600 ? (s / 3600) + "h " + (s % 3600) / 60 + "m" : (s / 60) + "m";
    }

    boolean rewardReady(UUID id, String course) {
        Stats s = stats(id, course);
        return s != null && !today().equals(s.rewardDay);
    }

    Run run(UUID id) { return runs.get(id); }

    boolean canRun(Player p, Course c) {
        int grade = Services.find(ProgressionService.class).map(pr -> pr.grade(p.getUniqueId())).orElse(25);
        return grade >= c.grade || p.hasPermission("spacepunk.mines.bypass");
    }

    // ------------------------------------------------------------------ plates

    void stepOn(Player p, Hit hit) {
        Course c = courses.get(hit.course());
        if (c == null || p.getGameMode() == GameMode.SPECTATOR) return;
        Run run = runs.get(p.getUniqueId());
        switch (hit.role()) {
            case START -> {
                if (p.isFlying() || p.getGameMode() == GameMode.CREATIVE) {
                    flash(p, "<red>Runs don't count while flying or in creative.");
                    return;
                }
                if (!canRun(p, c)) {
                    flash(p, "<red>This run needs Grade " + c.grade + ".");
                    return;
                }
                if (run != null && run.course.equals(c.mineId) && run.next == 0) {
                    run.start = System.currentTimeMillis(); // standing on the plate: the clock starts when you step off
                    return;
                }
                Run r = new Run(c.mineId);
                r.start = System.currentTimeMillis();
                runs.put(p.getUniqueId(), r);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, 1.4f);
                boolean ready = rewardReady(p.getUniqueId(), c.mineId);
                Text.send(p, "<gray>Run started. " + (ready ? "<green>Today's reward roll is waiting at the finish."
                        : "<dark_gray>Reward already claimed today (resets in " + untilReset() + "). Times still count.")
                        + " <dark_gray>/parkour leave to stop");
            }
            case CHECKPOINT -> {
                if (run == null || !run.course.equals(c.mineId)) return;
                if (hit.index() == run.next) {
                    run.next++;
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
                    flash(p, "<#e0c040>Checkpoint " + run.next + "/" + c.checkpoints.size() + " <dark_gray>· <white>" + time(System.currentTimeMillis() - run.start));
                }
            }
            case FINISH -> {
                if (run == null || !run.course.equals(c.mineId)) return;
                if (run.next < c.checkpoints.size()) {
                    flash(p, "<red>You missed checkpoint " + (run.next + 1) + ".");
                    return;
                }
                finish(p, c, run);
            }
        }
    }

    private void finish(Player p, Course c, Run run) {
        long millis = System.currentTimeMillis() - run.start;
        runs.remove(p.getUniqueId());
        clearSegment(p);
        Stats s = stats(p.getUniqueId(), c.mineId);
        boolean pb = false;
        if (s != null) {
            s.completions++;
            if (s.best <= 0 || millis < s.best) { s.best = millis; pb = true; }
        }
        Record rec = records.get(c.mineId);
        boolean record = rec == null || millis < rec.millis();
        if (record) {
            records.put(c.mineId, new Record(p.getName(), millis));
            save();
        }
        String mineName = mineName(c.mineId);
        p.showTitle(Title.title(Text.mm(deckColor(c.grade) + time(millis)), Text.mm(record ? "<gold>New course record!"
                : pb ? "<green>Personal best!" : "<gray>Run complete")));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        if (record && rec != null) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<gold>" + p.getName() + "</gold> <gray>set a new record on the " + mineName
                    + " run: <white>" + time(millis)));
        }

        if (s != null && !today().equals(s.rewardDay)) {
            s.rewardDay = today();
            roll(p, c, mineName);
            Services.find(GangService.class).ifPresent(g -> g.addContractProgress(p.getUniqueId(), "parkour_runs", 1));
            Objectives.report(p, ObjectiveTypes.PARKOUR_COMPLETE, 1, "mine:" + c.mineId, "deck:" + ParkourConfig.deckOf(c.grade));
            dev.spacepunk.prison.api.ScrapDrops.roll(p, dev.spacepunk.prison.api.ScrapService.PARKOUR, 2);
        } else {
            Text.send(p, "<gray>Finished in <white>" + time(millis) + "</white>" + (s != null && s.best > 0 ? " <dark_gray>(best "
                    + time(s.best) + ")" : "") + ". <gray>Next reward roll in <white>" + untilReset() + "</white>.");
        }
        writeStats(p.getUniqueId());
        if (cfg().returnToMine) {
            Services.find(MineService.class).flatMap(ms -> ms.mines().stream().filter(m -> m.id().equals(c.mineId)).findFirst())
                    .map(MineService.MineInfo::spawn).ifPresent(loc -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) p.teleportAsync(loc);
                    }, 40L));
        }
    }

    private void roll(Player p, Course c, String mineName) {
        ParkourConfig cfg = cfg();
        List<ParkourConfig.Reward> table = cfg.rewards.getOrDefault(ParkourConfig.deckOf(c.grade), cfg.rewards.get(1));
        if (table == null || table.isEmpty()) return;
        int total = table.stream().mapToInt(ParkourConfig.Reward::weight).sum();
        int r = ThreadLocalRandom.current().nextInt(total);
        ParkourConfig.Reward pick = table.get(table.size() - 1);
        for (ParkourConfig.Reward e : table) {
            r -= e.weight();
            if (r < 0) { pick = e; break; }
        }
        UUID id = p.getUniqueId();
        List<String> parts = new ArrayList<>();
        EconomyService eco = Services.find(EconomyService.class).orElse(null);
        long cost = Services.find(ProgressionService.class).map(pr -> pr.nextGradeCost(id)).orElse(0L);
        long credits = pick.creditsBase() + Math.round(pick.creditsGradeCost() * Math.max(0, cost));
        long tokens = pick.tokensMax() <= pick.tokensMin() ? pick.tokensMin()
                : ThreadLocalRandom.current().nextLong(pick.tokensMin(), pick.tokensMax() + 1);
        if (eco != null) {
            if (credits > 0) { eco.give(id, Currency.CREDITS, credits, "income:parkour"); parts.add("<gold>+" + Numbers.shortFormat(credits) + " ¢"); }
            if (tokens > 0) { eco.give(id, Currency.TOKENS, tokens, "parkour"); parts.add("<aqua>+" + Numbers.shortFormat(tokens) + " ✦"); }
            if (pick.shards() > 0) { eco.give(id, Currency.SHARDS, pick.shards(), "parkour"); parts.add("<light_purple>+" + pick.shards() + " ◆"); }
        }
        for (String cmd : pick.commands()) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "[parkour] reward command failed: " + cmd, e);
            }
        }
        if (!pick.text().isEmpty()) parts.add(0, pick.text());
        String got = String.join(" ", parts);
        Text.send(p, (pick.rare() ? "<gold><bold>RARE ROLL</bold> " : "<#e0c040>Daily reward: ") + got);
        if (pick.rare()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
            String reward = pick.text().isEmpty() ? got : pick.text();
            Bukkit.broadcast(Text.mm(Text.PREFIX + cfg.broadcast.replace("{player}", p.getName()).replace("{reward}", reward)
                    .replace("{mine}", mineName)));
        }
    }

    // ------------------------------------------------------------------ runs

    /** Every 2 ticks: timers, falls, leaving the course. */
    void tick() {
        if (runs.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Run> e : List.copyOf(runs.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            Run run = e.getValue();
            Course c = courses.get(run.course);
            if (p == null || c == null) { runs.remove(e.getKey()); continue; }
            if (p.isFlying() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                endRun(p, "<red>Run cancelled (flying).");
                continue;
            }
            Location l = p.getLocation();
            if (!c.near(l, 12)) {
                endRun(p, "<gray>You left the course. Run cancelled.");
                continue;
            }
            int section = Math.min(run.next, c.sections() - 1);
            if (l.getY() < c.fallY.get(section)) {
                respawn(p, c, run);
                continue;
            }
            segment(p, "<#e0c040>⏱ " + time(now - run.start) + " <dark_gray>· <gray>CP " + run.next + "/" + c.checkpoints.size());
        }
    }

    void respawn(Player p, Course c, Run run) {
        World w = c.bukkitWorld();
        if (w == null) return;
        Course.Spawn s = c.respawns.get(Math.min(run.next, c.respawns.size() - 1));
        run.falls++;
        p.setFallDistance(0);
        p.setVelocity(new Vector(0, 0, 0));
        p.teleportAsync(s.at(w));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.6f);
    }

    void endRun(Player p, String message) {
        runs.remove(p.getUniqueId());
        clearSegment(p);
        if (message != null) Text.send(p, message);
    }

    void endAll() {
        for (UUID id : List.copyOf(runs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clearSegment(p);
        }
        runs.clear();
    }

    Set<UUID> runners() { return runs.keySet(); }

    // ------------------------------------------------------------------ helpers

    String mineName(String mineId) {
        return Services.find(MineService.class).flatMap(ms -> ms.mines().stream().filter(m -> m.id().equals(mineId)).findFirst())
                .map(MineService.MineInfo::displayName).orElse(mineId);
    }

    static String deckColor(int grade) {
        return switch (ParkourConfig.deckOf(grade)) {
            case 1 -> "<#c8743a>";
            case 2 -> "<#7fd65a>";
            case 3 -> "<#8fd3ff>";
            case 4 -> "<#b07cff>";
            default -> "<#ff5a3c>";
        };
    }

    static String time(long millis) {
        long t = Math.max(0, millis) / 100;
        long tenths = t % 10, secs = (t / 10) % 60, mins = t / 600;
        return mins + ":" + (secs < 10 ? "0" : "") + secs + "." + tenths;
    }

    private void segment(Player p, String mm) {
        HudService hud = Services.find(HudService.class).orElse(null);
        if (hud != null) hud.segment(p, "parkour", mm);
        else p.sendActionBar(Text.mm(mm));
    }

    private void clearSegment(Player p) {
        HudService hud = Services.find(HudService.class).orElse(null);
        if (hud != null) hud.segment(p, "parkour", null);
        else p.sendActionBar(Component.empty());
    }

    private void flash(Player p, String mm) {
        HudService hud = Services.find(HudService.class).orElse(null);
        if (hud != null) hud.flash(p, mm);
        else p.sendActionBar(Text.mm(mm));
    }

    String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
