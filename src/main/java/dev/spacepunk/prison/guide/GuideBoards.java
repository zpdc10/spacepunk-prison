package dev.spacepunk.prison.guide;

import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.WorkshopService;
import dev.spacepunk.prison.guide.GuideConfig.Anchor;
import dev.spacepunk.prison.guide.GuideConfig.Board;
import dev.spacepunk.prison.guide.GuideConfig.Chapter;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Info boards: floating text (and a clickable hitbox that opens the chapter's guide book) in the station wings, at
 * every mine's landing pad, the fishing hub, the Undercroft checkpoint and inside each player's own workshop bay.
 * Boards are never saved with the world (non-persistent entities): they are spawned whenever their chunk is
 * loaded and vanish when it unloads, so moving or removing one is just a config change.
 */
final class GuideBoards {
    static final String TAG = "spacepunk_guide";

    private final Plugin plugin;
    private final Supplier<GuideConfig> cfg;
    private final GuideManager guide;
    private final NamespacedKey chapterKey;
    private final File adminFile;
    /** Boards placed in game with /guideadmin board. */
    private final List<Board> adminBoards = new ArrayList<>();
    /** board key -> its entities. */
    private final Map<String, List<Entity>> spawned = new HashMap<>();
    /** workshop bay owner -> their bay board (only they can see it). */
    private final Map<UUID, Entity> bayBoards = new HashMap<>();

    GuideBoards(Plugin plugin, Supplier<GuideConfig> cfg, GuideManager guide) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.guide = guide;
        this.chapterKey = new NamespacedKey(plugin, "guide_chapter");
        this.adminFile = new File(plugin.getDataFolder(), "guide-boards.yml");
        loadAdmin();
    }

    NamespacedKey chapterKey() { return chapterKey; }

    // ------------------------------------------------------------------ admin boards

    private void loadAdmin() {
        adminBoards.clear();
        if (!adminFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(adminFile);
        for (String id : y.getKeys(false)) {
            Board b = GuideConfig.board(id, y.getConfigurationSection(id));
            if (b != null) adminBoards.add(b);
        }
    }

    private void saveAdmin() {
        YamlConfiguration y = new YamlConfiguration();
        for (Board b : adminBoards) {
            ConfigurationSection s = y.createSection(b.id());
            s.set("chapter", b.chapter());
            s.set("world", b.world());
            s.set("x", b.x());
            s.set("y", b.y());
            s.set("z", b.z());
            s.set("scale", (double) b.scale());
            s.set("clickable", b.clickable());
            if (!b.lines().isEmpty()) s.set("lines", b.lines());
        }
        try {
            y.save(adminFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[guide] could not save guide-boards.yml: " + e.getMessage());
        }
    }

    String addAdmin(String chapter, Location at) {
        int n = 1;
        while (exists("admin-" + n)) n++;
        String id = "admin-" + n;
        adminBoards.add(new Board(id, chapter, at.getWorld().getName(), Math.floor(at.getX()) + 0.5, Math.floor(at.getY()),
                Math.floor(at.getZ()) + 0.5, 1.0f, true, List.of()));
        saveAdmin();
        refresh();
        return id;
    }

    private boolean exists(String id) { return adminBoards.stream().anyMatch(b -> b.id().equals(id)); }

    /** Removes the nearest admin board within 4 blocks. Returns its id, or null. */
    String removeNearest(Location at) {
        Board best = null;
        double bestD = 16;
        for (Board b : adminBoards) {
            if (!b.world().equals(at.getWorld().getName())) continue;
            double d = at.distanceSquared(new Location(at.getWorld(), b.x(), b.y(), b.z()));
            if (d < bestD) { bestD = d; best = b; }
        }
        if (best == null) return null;
        adminBoards.remove(best);
        saveAdmin();
        despawn("fixed:" + best.id());
        return best.id();
    }

    List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Board b : cfg.get().boards) out.add("<gray>" + b.id() + " <dark_gray>(config)</dark_gray> " + b.chapter() + " @ " + b.world() + " " + b.x() + " " + b.y() + " " + b.z());
        for (Board b : adminBoards) out.add("<white>" + b.id() + " <dark_gray>(placed)</dark_gray> " + b.chapter() + " @ " + b.world() + " " + b.x() + " " + b.y() + " " + b.z());
        return out;
    }

    // ------------------------------------------------------------------ spawning

    /** Removes leftovers (from a crash or /reload) in every loaded world. */
    void cleanAll() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        }
        spawned.clear();
        bayBoards.clear();
    }

    /** Respawns everything (after a config reload or an admin change). */
    void refresh() {
        for (List<Entity> l : spawned.values()) l.forEach(Entity::remove);
        spawned.clear();
        tick();
    }

    void tick() {
        GuideConfig c = cfg.get();
        Map<String, Board> wanted = new HashMap<>();
        for (Board b : c.boards) wanted.put("fixed:" + b.id(), b);
        for (Board b : adminBoards) wanted.put("fixed:" + b.id(), b);
        for (Anchor a : c.spawnBoards) {
            World w = a.world() == null ? null : Bukkit.getWorld(a.world());
            if (w != null) wanted.put("spawn:" + a.world(), fromAnchor("spawn:" + a.world(), a, w.getSpawnLocation()));
        }
        if (c.mineBoard != null) {
            Services.find(MineService.class).ifPresent(ms -> {
                for (MineService.MineInfo m : ms.mines()) {
                    Location s = m.spawn();
                    if (s != null && s.getWorld() != null) wanted.put("mine:" + m.id(), fromAnchor("mine:" + m.id(), c.mineBoard, s));
                }
            });
        }
        // Drop boards that are no longer configured.
        for (Iterator<Map.Entry<String, List<Entity>>> it = spawned.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, List<Entity>> e = it.next();
            if (!wanted.containsKey(e.getKey())) {
                e.getValue().forEach(Entity::remove);
                it.remove();
            }
        }
        for (Map.Entry<String, Board> e : wanted.entrySet()) ensure(e.getKey(), e.getValue());
        bayTick();
    }

    private static Board fromAnchor(String id, Anchor a, Location base) {
        float yaw = base.getYaw();
        Vector fwd = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        Location at = base.clone().add(fwd.multiply(a.forward())).add(right.multiply(a.side()));
        return new Board(id, a.chapter(), base.getWorld().getName(), at.getX(), base.getY() + a.up(), at.getZ(), a.scale(), a.clickable(), a.lines());
    }

    private void ensure(String key, Board b) {
        List<Entity> have = spawned.get(key);
        if (have != null && !have.isEmpty() && have.stream().allMatch(Entity::isValid)) return;
        if (have != null) have.forEach(Entity::remove);
        spawned.remove(key);
        World w = Bukkit.getWorld(b.world());
        if (w == null || !w.isChunkLoaded((int) Math.floor(b.x()) >> 4, (int) Math.floor(b.z()) >> 4)) return;
        Chapter c = cfg.get().chapters.get(b.chapter());
        if (c == null) return;
        List<String> lines = !b.lines().isEmpty() ? b.lines() : c.board();
        spawned.put(key, spawnBoard(new Location(w, b.x(), b.y(), b.z()), c, lines, b.scale(), b.clickable(), true));
    }

    private List<Entity> spawnBoard(Location base, Chapter c, List<String> lines, float scale, boolean clickable, boolean visible) {
        List<Entity> out = new ArrayList<>();
        World w = base.getWorld();
        String text = String.join("\n", lines.isEmpty() ? defaultLines(c, clickable) : lines);
        double textY = clickable ? 2.1 : 0;
        out.add(w.spawn(base.clone().add(0, textY, 0), TextDisplay.class, td -> {
            td.text(Text.mm(text));
            td.setBillboard(Display.Billboard.VERTICAL);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setLineWidth(260);
            td.setShadowed(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(170, 18, 14, 12));
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.5f);
            if (scale != 1f) td.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            tag(td, c, visible);
        }));
        if (clickable) {
            // A floating info icon above a hitbox the size of a person: right-click (or hit) it for the book.
            out.add(w.spawn(base.clone().add(0, 1.45, 0), ItemDisplay.class, id -> {
                ItemStack icon = new ItemStack(Material.PAPER);
                icon.editMeta(m -> m.setItemModel(NamespacedKey.fromString("spacepunk:ui/info")));
                id.setItemStack(icon);
                id.setBillboard(Display.Billboard.CENTER);
                id.setBrightness(new Display.Brightness(15, 15));
                id.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
                tag(id, c, visible);
            }));
            out.add(w.spawn(base.clone().add(0, 0.9, 0), Interaction.class, in -> {
                in.setInteractionWidth(0.9f);
                in.setInteractionHeight(1.2f);
                in.setResponsive(true);
                tag(in, c, visible);
            }));
        }
        return out;
    }

    private void tag(Entity e, Chapter c, boolean visible) {
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
        e.getPersistentDataContainer().set(chapterKey, PersistentDataType.STRING, c.id());
        if (!visible) e.setVisibleByDefault(false);
    }

    private static List<String> defaultLines(Chapter c, boolean clickable) {
        List<String> l = new ArrayList<>();
        l.add(c.color() + "<bold>ⓘ " + c.name().toUpperCase() + "</bold>");
        for (String s : c.summary()) l.add("<gray>" + s);
        l.add(clickable ? "<#e0b354>Right-click the ⓘ for the full guide" : "<dark_gray>/guide " + c.id());
        return l;
    }

    private void despawn(String key) {
        List<Entity> l = spawned.remove(key);
        if (l != null) l.forEach(Entity::remove);
    }

    // ------------------------------------------------------------------ workshop bay board

    /** Each bay owner sees a "build your first line" board in their bay until they finish the workshop checklist. */
    private void bayTick() {
        GuideConfig c = cfg.get();
        Chapter ch = c.chapters.get("workshop");
        WorkshopService ws = Services.find(WorkshopService.class).orElse(null);
        for (Iterator<Map.Entry<UUID, Entity>> it = bayBoards.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Entity> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || ws == null || !c.bayBoard || ch == null || !wantsBayBoard(p, ws) || !e.getValue().isValid()) {
                e.getValue().remove();
                it.remove();
            }
        }
        if (ws == null || !c.bayBoard || ch == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (bayBoards.containsKey(p.getUniqueId()) || !wantsBayBoard(p, ws)) continue;
            Location spawn = ws.bayLocation(p);
            if (spawn == null) continue;
            Location at = spawn.clone();
            at.setYaw(0);
            at.add(c.bayOffset[0], c.bayOffset[1], c.bayOffset[2]);
            List<String> lines = ch.board().isEmpty() ? defaultLines(ch, false) : ch.board();
            List<Entity> made = spawnBoard(at, ch, lines, c.bayScale, false, false);
            Entity board = made.get(0);
            p.showEntity(plugin, board);
            bayBoards.put(p.getUniqueId(), board);
        }
    }

    private boolean wantsBayBoard(Player p, WorkshopService ws) {
        if (!ws.isWorkshop(p.getLocation())) return false;
        if (!ws.bayOwnerAt(p.getLocation()).map(o -> o.equals(p.getUniqueId())).orElse(false)) return false;
        GuideData d = guide.data(p.getUniqueId());
        return d != null && !d.done.contains("workshop");
    }

    void removeBayBoard(UUID owner) {
        Entity e = bayBoards.remove(owner);
        if (e != null) e.remove();
    }

    void shutdown() {
        for (List<Entity> l : spawned.values()) l.forEach(Entity::remove);
        spawned.clear();
        bayBoards.values().forEach(Entity::remove);
        bayBoards.clear();
    }
}
