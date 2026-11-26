package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.WarpService;
import dev.spacepunk.prison.api.UndercroftService;
import dev.spacepunk.prison.core.FeatureModule;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;

/**
 * The Undercroft: the lawless lower hull, the station's only PvP zone. Rings gated by prestige, hot ore, drug labs,
 * stims with comedowns, bounty tags, supply drops, and a guard checkpoint that scans contraband on the way out.
 */
public final class UndercroftModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private UndercroftManager manager;
    private Checkpoint checkpoint;
    private LabManager labs;
    private SupplyDrops drops;
    private Fence fence;
    private UndercroftListener listener;
    private Menu.Listen menus;
    private BukkitTask ticker;

    @Override public String id() { return "undercroft"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        UndercroftStore store = new UndercroftStore(new File(plugin.getDataFolder(), "undercroft-data.yml"), plugin.getLogger());
        manager = new UndercroftManager(plugin, loadConfig(), store);
        Services.register(UndercroftService.class, manager);
        checkpoint = new Checkpoint(manager);
        labs = new LabManager(manager);
        Loot loot = new Loot(manager);
        drops = new SupplyDrops(manager, loot);
        fence = new Fence(manager);
        listener = new UndercroftListener(manager, checkpoint, labs, drops, loot);
        menus = new Menu.Listen();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getServer().getPluginManager().registerEvents(menus, plugin);
        // Map protection is default-deny: let players mine hot-ore veins, open supply drops and use labs/terminal.
        Services.find(dev.spacepunk.prison.api.ProtectionService.class).ifPresent(ps -> {
            ps.allowBreak("undercroft", (p, b) -> listener.isVein(b));
            ps.allowUse("undercroft", (p, b) -> drops.isDrop(b) || labs.labAt(b) != null);
        });

        UndercroftCommands commands = new UndercroftCommands(this);
        plugin.addCommand("undercroft", "Go down to the Undercroft PvP zone", List.of("uc"), commands.undercroft());
        plugin.addCommand("fence", "Sell smuggled Undercroft goods", List.of(), commands.fence());

        // One tick later so the default worlds exist; builds the layout the first time.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            World w = manager.world.loadOrCreate();
            if (w == null) return;
            registerWarp();
            Services.find(GangService.class).ifPresent(g -> {
                g.registerContractObjective("pvp_kills", "Undercroft kills");
                g.registerContractObjective("contraband_smuggled", "Contraband smuggled out");
            });
            if (!store.generated && manager.cfg().autoGenerate) {
                plugin.getLogger().info("[undercroft] building the Undercroft layout (first run)...");
                generate();
            } else {
                manager.world.regrowAll(store);
            }
        });
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Services.find(dev.spacepunk.prison.api.ProfileService.class).flatMap(ps -> ps.get(p.getUniqueId()))
                    .ifPresent(profile -> manager.load(p, profile));
        }
        plugin.getLogger().info("[undercroft] " + manager.cfg().rings.size() + " rings, " + manager.cfg().labs.size() + " labs, "
                + manager.cfg().stims.size() + " stims");
    }

    private void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) manager.stims.tick(p);
        labs.tick();
        drops.tick();
    }

    @Override
    public void disable() {
        if (ticker != null) ticker.cancel();
        if (labs != null) labs.cancelAll();
        if (drops != null) drops.clearAll();
        if (listener != null) HandlerList.unregisterAll(listener);
        if (menus != null) HandlerList.unregisterAll(menus);
        if (manager != null) for (Player p : plugin.getServer().getOnlinePlayers()) manager.save(p);
    }

    private UndercroftConfig loadConfig() {
        File f = new File(plugin.getDataFolder(), "undercroft.yml");
        if (!f.exists()) plugin.saveResource("undercroft.yml", false);
        return UndercroftConfig.load(f, plugin.getLogger());
    }

    void reload() {
        manager.config(loadConfig());
        manager.store.load();
        listener.reindexVeins();
    }

    void generate() {
        manager.world.generate(manager.store);
        listener.reindexVeins();
    }

    /** Sends a player down to the checkpoint. */
    void enter(Player p) {
        if (manager.grade(p) < manager.cfg().minGrade) {
            Text.send(p, "<red>You need grade " + manager.cfg().minGrade + " to go down there.");
            return;
        }
        World w = manager.world.loadOrCreate();
        if (w == null) { Text.send(p, "<red>The Undercroft is sealed right now."); return; }
        if (Services.find(WarpService.class).map(ws -> ws.get("undercroft").isPresent() && ws.warp(p, "undercroft")).orElse(false)) {
            return;
        }
        p.teleportAsync(manager.world.checkpointSpawn(w));
        Text.send(p, "<gray>You step off the freight lift into the " + manager.cfg().checkpointName
                + ". <red>Past the gate, PvP is on</red> <gray>and whatever you carry drops when you die.");
    }

    /** The station's Undercroft Gate terminal and Shuttle Board use warp id "undercroft". */
    private void registerWarp() {
        Services.find(WarpService.class).ifPresent(ws -> ws.register(new WarpService.Warp("undercroft",
                "<red>The Undercroft", WarpService.DANGER, Material.NETHERITE_SCRAP,
                List.of("<gray>The lawless lower hull. <red>PvP is on.", "<gray>Hot ore, drug labs, supply drops, bounties.",
                        "<gray>Whatever you carry drops when you die.", "<gray>Contraband is scanned on the way out."),
                p -> {
                    World w = manager.world.world();
                    return w == null ? null : manager.world.checkpointSpawn(w);
                },
                p -> manager.grade(p) < manager.cfg().minGrade ? "<red>Needs Grade " + manager.cfg().minGrade
                        : manager.inUndercroft(p.getLocation()) ? "<gray>You're already down there." : null)));
    }

    UndercroftManager manager() { return manager; }
    Checkpoint checkpoint() { return checkpoint; }
    SupplyDrops drops() { return drops; }
    Fence fence() { return fence; }
}
