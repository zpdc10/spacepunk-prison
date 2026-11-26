package dev.spacepunk.prison.guide;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import dev.spacepunk.prison.guide.GuideConfig.Chapter;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The in-game guide (onboarding): /guide chapters for every system as books, first-visit briefings, boss bar
 * checklists that walk new players through a system, and info boards around the station, mines, fishing hub,
 * Undercroft checkpoint and workshop bays. Content: guide.yml in the jar; a guide.yml in the plugin folder, if
 * present, overrides it key by key. Boards placed in game: guide-boards.yml.
 */
public final class GuideModule implements FeatureModule {
    private static final String ADMIN = "spacepunk.admin";
    private SpacepunkPrison plugin;
    private volatile GuideConfig config;
    private GuideManager manager;
    private GuideBoards boards;
    private GuideMenu menu;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override public String id() { return "guide"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        config = load();
        manager = new GuideManager(plugin, () -> config);
        boards = new GuideBoards(plugin, () -> config, manager);
        manager.onChecklistChange = () -> Bukkit.getScheduler().runTask(plugin, boards::tick);
        menu = new GuideMenu(manager);
        listeners.add(new GuideListener(plugin, manager, boards));
        listeners.add(menu);
        for (Listener l : listeners) plugin.getServer().getPluginManager().registerEvents(l, plugin);

        BasicCommand guide = playerCommand();
        plugin.addCommand("guide", "The station guide: how every system works", List.of("tutorial", "tutorials", "info"), guide);
        // Override the vanilla /help so new players land on the guide.
        plugin.addCommand("help", "The station guide", List.of(), guide);
        plugin.addCommand("guideadmin", "Guide boards and player guide state", List.of(), adminCommand());

        ProfileService profiles = Services.get(ProfileService.class);
        for (Player p : Bukkit.getOnlinePlayers()) profiles.get(p.getUniqueId()).ifPresent(prof -> manager.load(p, prof));

        // Boards: after every module (mines, workshop) is up and the worlds exist.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boards.cleanAll();
            boards.tick();
        }, 40L);
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> boards.tick(), 60L, config.boardRefreshTicks));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, manager::regionTick, 40L, 20L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, manager::flush, 100L, 100L));
        plugin.getLogger().info("[guide] " + config.chapters.size() + " chapters");
    }

    @Override
    public void disable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        if (boards != null) boards.shutdown();
        if (manager != null) manager.shutdown();
    }

    private GuideConfig load() {
        YamlConfiguration jar = new YamlConfiguration();
        try (InputStream in = plugin.getResource("guide.yml")) {
            if (in != null) jar = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("[guide] could not read the default guide.yml: " + e.getMessage());
        }
        // The guide's text ships with the jar so updates always reach the server. An optional guide.yml in the
        // plugin folder overrides single keys (for example settings.wiki-url once the wiki has a public address).
        File override = new File(plugin.getDataFolder(), "guide.yml");
        YamlConfiguration y = jar;
        if (override.exists()) {
            y = YamlConfiguration.loadConfiguration(override);
            y.setDefaults(jar);
            y.options().copyDefaults(true);
        }
        return new GuideConfig(y, plugin.getLogger());
    }

    private Stream<String> chapterIds() { return config.chapters.keySet().stream(); }

    private Chapter chapter(String id) {
        if (id == null) return null;
        String k = id.toLowerCase(Locale.ROOT);
        Chapter c = config.chapters.get(k);
        if (c != null) return c;
        for (Chapter ch : config.chapters.values()) {
            if (ch.name().toLowerCase(Locale.ROOT).replace(" ", "").startsWith(k)) return ch;
        }
        return null;
    }

    private BasicCommand playerCommand() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) {
                    Text.send(src.getSender(), "<gray>Chapters: " + String.join(", ", config.chapters.keySet()));
                    return;
                }
                GuideData d = manager.data(p.getUniqueId());
                if (args.length == 0) { menu.open(p); return; }
                String sub = args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "wiki" -> {
                        if (config.wikiUrl.isEmpty()) Text.send(p, "<gray>No wiki address is set yet.");
                        else p.sendMessage(Text.mm("<#8fd3ff>Station wiki: <click:open_url:'" + config.wikiUrl + "'><u>" + config.wikiUrl + "</u></click>"));
                        return;
                    }
                    case "bar" -> {
                        if (d == null) return;
                        d.bar = !manager.barOn(d);
                        manager.markDirty(p.getUniqueId());
                        manager.updateBar(p);
                        Text.send(p, "<gray>Checklist bar " + (d.bar ? "<green>shown" : "<red>hidden") + "<gray>.");
                        return;
                    }
                    case "tips" -> {
                        if (d == null) return;
                        d.popups = !manager.popups(d);
                        manager.markDirty(p.getUniqueId());
                        Text.send(p, "<gray>First-visit tips " + (d.popups ? "<green>on" : "<red>off") + "<gray>.");
                        return;
                    }
                    case "skip" -> {
                        String top = d == null ? null : GuideManager.top(d);
                        if (top == null) { Text.send(p, "<gray>No checklist is running."); return; }
                        manager.skip(p, d, top);
                        Text.send(p, "<gray>Checklist skipped. Restart it any time from <white>/guide</white>.");
                        return;
                    }
                    case "restart", "start" -> {
                        Chapter c = args.length > 1 ? chapter(args[1]) : null;
                        if (c == null || !c.hasChecklist() || d == null) {
                            Text.send(p, "<red>/guide restart CHAPTER <gray>(one with a checklist)");
                            return;
                        }
                        manager.startChecklist(p, d, c, true);
                        return;
                    }
                    default -> { }
                }
                Chapter c = chapter(sub);
                if (c == null) {
                    Text.send(p, "<red>No guide called " + Text.MM.escapeTags(args[0]) + ". <gray>Try <white>/guide</white>.");
                    return;
                }
                manager.openBook(p, c);
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length <= 1) {
                    String pre = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                    return Stream.concat(chapterIds(), Stream.of("wiki", "bar", "tips", "skip", "restart"))
                            .filter(s -> s.startsWith(pre)).toList();
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("restart") || args[0].equalsIgnoreCase("start"))) {
                    return config.chapters.values().stream().filter(Chapter::hasChecklist).map(Chapter::id)
                            .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
                }
                return List.of();
            }
        };
    }

    private BasicCommand adminCommand() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                var s = src.getSender();
                if (!s.hasPermission(ADMIN)) { Text.send(s, "<red>No permission."); return; }
                String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "reload" -> {
                        config = load();
                        boards.refresh();
                        for (Player p : Bukkit.getOnlinePlayers()) manager.updateBar(p);
                        Text.send(s, "<green>Guide reloaded: " + config.chapters.size() + " chapters.");
                    }
                    case "board" -> board(s, args);
                    case "reset" -> {
                        Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                        GuideData d = t == null ? null : manager.data(t.getUniqueId());
                        if (d == null) { Text.send(s, "<red>/guideadmin reset PLAYER [CHAPTER]"); return; }
                        if (args.length > 2) {
                            String id = args[2].toLowerCase(Locale.ROOT);
                            d.seen.remove(id);
                            d.done.remove(id);
                            d.read.remove(id);
                            d.active.remove(id);
                        } else {
                            d.seen.clear();
                            d.done.clear();
                            d.read.clear();
                            d.active.clear();
                            d.step.clear();
                            d.have.clear();
                        }
                        manager.markDirty(t.getUniqueId());
                        manager.updateBar(t);
                        Text.send(s, "<green>Guide state reset for " + t.getName() + ". <gray>First-visit tips fire again.");
                    }
                    case "show" -> {
                        Player t = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                        Chapter c = args.length > 2 ? chapter(args[2]) : null;
                        if (t == null || c == null) { Text.send(s, "<red>/guideadmin show PLAYER CHAPTER"); return; }
                        manager.popup(t, c);
                        GuideData d = manager.data(t.getUniqueId());
                        if (d != null && c.hasChecklist()) manager.startChecklist(t, d, c, true);
                    }
                    default -> {
                        Text.send(s, "<gray>/guideadmin reload");
                        Text.send(s, "<gray>/guideadmin board add CHAPTER <dark_gray>- an info board at your feet");
                        Text.send(s, "<gray>/guideadmin board remove <dark_gray>- the nearest placed board");
                        Text.send(s, "<gray>/guideadmin board list");
                        Text.send(s, "<gray>/guideadmin reset PLAYER [CHAPTER] <dark_gray>- first-visit tips fire again");
                        Text.send(s, "<gray>/guideadmin show PLAYER CHAPTER <dark_gray>- play a briefing + checklist");
                    }
                }
            }

            private void board(org.bukkit.command.CommandSender s, String[] args) {
                String op = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
                if (op.equals("list")) {
                    List<String> lines = boards.describe();
                    if (lines.isEmpty()) Text.send(s, "<gray>No fixed boards.");
                    lines.forEach(l -> Text.send(s, l));
                    return;
                }
                if (!(s instanceof Player p)) { Text.send(s, "<red>Players only."); return; }
                if (op.equals("add")) {
                    Chapter c = args.length > 2 ? chapter(args[2]) : null;
                    if (c == null) { Text.send(s, "<red>/guideadmin board add CHAPTER"); return; }
                    Text.send(s, "<green>Placed board " + boards.addAdmin(c.id(), p.getLocation()) + " for " + c.name() + ".");
                } else if (op.equals("remove")) {
                    String id = boards.removeNearest(p.getLocation());
                    Text.send(s, id == null ? "<gray>No placed board within 4 blocks. <dark_gray>(Boards from guide.yml are moved by editing it.)"
                            : "<green>Removed board " + id + ".");
                }
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length <= 1) return Stream.of("reload", "board", "reset", "show")
                        .filter(x -> args.length == 0 || x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
                if (args[0].equalsIgnoreCase("board")) {
                    if (args.length == 2) return Stream.of("add", "remove", "list").filter(x -> x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
                    if (args.length == 3 && args[1].equalsIgnoreCase("add")) return chapterIds().filter(x -> x.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
                }
                if ((args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("show"))) {
                    if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    if (args.length == 3) return chapterIds().filter(x -> x.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
                }
                return List.of();
            }

            @Override public String permission() { return ADMIN; }
        };
    }
}
