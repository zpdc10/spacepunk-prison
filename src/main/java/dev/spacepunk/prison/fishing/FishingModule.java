package dev.spacepunk.prison.fishing;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.FishingService;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.WarpService;
import dev.spacepunk.prison.core.FeatureModule;
import dev.spacepunk.prison.fishing.FishingConfig.Area;
import dev.spacepunk.prison.fishing.FishingConfig.FishDef;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fishing: the station waters world (a hub and one flooded pool per deck), catches per deck, the reel-in
 * minigame, a soulbound levelling rod with enchants, lures and bait, sea creatures, collections and leaderboards.
 */
public final class FishingModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private FishingConfig cfg;
    private FishingManager manager;
    private FishingWorld world;
    private SeaCreatures creatures;
    private Leaderboard leaderboard;
    private FishingMenus menus;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<org.bukkit.event.Listener> listeners = new ArrayList<>();
    private final List<String> warps = new ArrayList<>();

    @Override public String id() { return "fishing"; }

    @Override
    public void enable(SpacepunkPrison plugin) throws Exception {
        this.plugin = plugin;
        cfg = loadConfig();
        FishingItems items = new FishingItems(plugin, cfg);
        leaderboard = new Leaderboard(plugin);
        manager = new FishingManager(this, cfg, items);
        world = new FishingWorld(plugin, cfg);
        creatures = new SeaCreatures(this, plugin);
        menus = new FishingMenus(this);
        Services.register(FishingService.class, manager);

        boolean created = world.load();
        if (world.world() == null) plugin.getLogger().warning("[fishing] waters world unavailable; fishing areas disabled");

        listeners.add(new FishingListener(this));
        listeners.add(creatures);
        listeners.add(menus);
        for (var l : listeners) plugin.getServer().getPluginManager().registerEvents(l, plugin);

        FishingCommands cmds = new FishingCommands(this);
        plugin.addCommand("fish", "Fishing: market, bait, collection, leaderboards", List.of("fishing"), cmds.fish());
        plugin.addCommand("rod", "Open your rod bay", List.of(), cmds.rod());
        plugin.addCommand("waters", "Travel to the station's fishing pools", List.of("pools"), cmds.waters());
        plugin.addCommand("fishadmin", "Fishing admin", List.of(), cmds.admin());

        var sched = Bukkit.getScheduler();
        tasks.add(sched.runTaskTimer(plugin, manager::tickGames, 1L, 1L));
        tasks.add(sched.runTaskTimer(plugin, creatures::tick, 20L, 20L));
        tasks.add(sched.runTaskTimer(plugin, this::minute, 20L * 10, 20L * 60));
        tasks.add(sched.runTaskTimer(plugin, this::refreshBoards, 40L, 20L * 15));

        // One tick later so every world is up, then build (first run) and spawn signs and boards.
        sched.runTask(plugin, () -> {
            if (world.world() == null) return;
            if (cfg.autoBuild && (created || !world.built())) {
                plugin.getLogger().info("[fishing] building the station waters (hub + " + cfg.areas.size() + " pools)");
                world.build();
            }
            world.spawnDisplays(marketIcon());
            refreshBoards();
        });

        Services.find(GangService.class).ifPresent(g -> {
            g.registerContractObjective("fish_caught", "Fish caught");
            g.registerContractObjective("sea_creatures_slain", "Sea creatures slain");
        });
        registerWarps();

        // /reload safety: players already online with loaded profiles.
        ProfileService profiles = Services.get(ProfileService.class);
        for (Player p : Bukkit.getOnlinePlayers()) {
            profiles.get(p.getUniqueId()).ifPresent(prof -> {
                manager.load(prof);
                manager.refreshRod(p);
            });
        }
    }

    @Override
    public void disable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        for (var l : listeners) HandlerList.unregisterAll(l);
        listeners.clear();
        Services.find(WarpService.class).ifPresent(w -> warps.forEach(w::unregister));
        warps.clear();
        if (menus != null) menus.closeAll();
        if (manager != null) {
            manager.cancelAll();
            manager.saveAll();
        }
        if (creatures != null) creatures.removeAll();
        if (leaderboard != null) leaderboard.save(true);
        if (world != null) world.shutdown();
    }

    private FishingConfig loadConfig() {
        File file = new File(plugin.getDataFolder(), "fishing.yml");
        if (!file.exists()) plugin.saveResource("fishing.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var in = plugin.getResource("fishing.yml");
        if (in != null) {
            // New top-level settings from updates show up without wiping the server's edits.
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String key : defaults.getKeys(false)) if (!y.contains(key)) y.set(key, defaults.get(key));
        }
        return FishingConfig.load(y, plugin.getLogger());
    }

    void reload() {
        cfg = loadConfig();
        manager.config(cfg);
        world.config(cfg);
        world.spawnDisplays(marketIcon());
        registerWarps();
        for (Player p : Bukkit.getOnlinePlayers()) manager.refreshRod(p);
        refreshBoards();
    }

    void rebuild() {
        world.load();
        world.build();
        world.spawnDisplays(marketIcon());
        refreshBoards();
    }

    private ItemStack marketIcon() {
        FishDef f = cfg.fish.values().stream().filter(x -> "legendary".equals(x.rarity())).findFirst()
                .orElse(cfg.fish.values().stream().findFirst().orElse(null));
        return f == null ? new ItemStack(Material.COD) : manager.items().fish(f, 1);
    }

    private void registerWarps() {
        WarpService ws = Services.find(WarpService.class).orElse(null);
        if (ws == null) return;
        warps.forEach(ws::unregister);
        warps.clear();
        ws.register(new WarpService.Warp("fishing", cfg.hubName, WarpService.PLANETSIDE, Material.FISHING_ROD,
                List.of("<gray>The station waters: Fish Market,", "<gray>leaderboards and bridges to every pool."),
                p -> world.hub(), p -> world.world() == null ? "<red>The waters are flooded shut." : null));
        warps.add("fishing");
        for (Area a : cfg.areas.values()) {
            String id = "fishing-" + a.id();
            ws.register(new WarpService.Warp(id, a.colored(), WarpService.PLANETSIDE, Material.FISHING_ROD,
                    List.of("<#8a8070><i>" + a.description(), "<gray>Fishing · Grade " + a.grade() + "+"),
                    p -> world.arrival(a),
                    p -> manager.grade(p.getUniqueId()) < a.grade() ? "<red>Needs Grade " + a.grade() : null));
            warps.add(id);
        }
    }

    void travel(Player p, Area a) {
        if (manager.grade(p.getUniqueId()) < a.grade()) {
            Text.send(p, "<red>Your clearance is too low for the " + a.name() + " (Grade " + a.grade() + ").");
            return;
        }
        WarpService ws = Services.find(WarpService.class).orElse(null);
        if (ws != null && ws.get("fishing-" + a.id()).isPresent()) {
            ws.warp(p, "fishing-" + a.id());
            return;
        }
        Location to = world.arrival(a);
        if (to == null) {
            Text.send(p, "<red>The station waters are not available right now.");
            return;
        }
        p.teleportAsync(to).thenRun(() -> Text.send(p, "<gray>Arrived at the " + a.colored() + "<gray>."));
    }

    void travelHub(Player p) {
        WarpService ws = Services.find(WarpService.class).orElse(null);
        if (ws != null && ws.get("fishing").isPresent()) {
            ws.warp(p, "fishing");
            return;
        }
        Location to = world.hub();
        if (to == null) {
            Text.send(p, "<red>The station waters are not available right now.");
            return;
        }
        p.teleportAsync(to);
    }

    /** Every minute: weekly roll-over, keep the waters dark, save records. */
    private void minute() {
        List<String> winners = leaderboard.rollWeek(cfg.weeklyRewards, id -> {
            FishDef f = cfg.fish.get(id);
            return f == null ? id : f.name();
        });
        if (winners != null && !winners.isEmpty()) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<#ffb43a>The weekly biggest-catch board has closed:"));
            for (String w : winners) Bukkit.broadcast(Text.mm("<gray>  " + w));
            for (Player p : Bukkit.getOnlinePlayers()) manager.claimWeekly(p);
        }
        if (world.world() != null) world.world().setTime(18000);
        leaderboard.save(false);
    }

    private void refreshBoards() {
        if (world.world() == null) return;
        String weekly = "<#ffb43a><bold>BIGGEST CATCH THIS WEEK</bold>\n" + String.join("\n", menus.weeklyLines());
        String levels = "<#5fb4c8><bold>TOP ANGLERS</bold>\n" + String.join("\n", menus.levelLines());
        world.boards(weekly, levels);
    }

    void hudFlash(Player p, String mm) {
        var hud = Services.find(HudService.class);
        if (hud.isPresent()) hud.get().flash(p, mm);
        else p.sendActionBar(Text.mm(mm));
    }

    SpacepunkPrison plugin() { return plugin; }

    FishingConfig cfg() { return cfg; }

    FishingManager manager() { return manager; }

    FishingWorld world() { return world; }

    SeaCreatures creatures() { return creatures; }

    Leaderboard leaderboard() { return leaderboard; }

    FishingMenus menus() { return menus; }
}
