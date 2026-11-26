package dev.spacepunk.prison.quests;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.ObjectiveTypes;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.QuestService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Quests: the deck-by-deck story, tutorials for each system, daily and weekly contracts and the monthly contract
 * track. Loads early so other modules can register objective types from their enable(). Config: quests.yml.
 */
public final class QuestsModule implements FeatureModule {
    private SpacepunkPrison plugin;
    private volatile QuestConfig config;
    private QuestManager manager;
    private QuestMenu menu;
    private QuestNpcs npcs;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override public String id() { return "quests"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) plugin.saveResource("quests.yml", false);
        config = QuestConfig.load(file, plugin.getLogger());

        manager = new QuestManager(plugin, () -> config);
        Services.register(QuestService.class, manager);
        menu = new QuestMenu(manager);
        npcs = new QuestNpcs(plugin, manager, menu);
        listeners.add(new QuestListener(manager));
        listeners.add(menu);
        listeners.add(npcs);
        for (Listener l : listeners) plugin.getServer().getPluginManager().registerEvents(l, plugin);

        QuestCommands commands = new QuestCommands(this);
        plugin.addCommand("quests", "Open your quest log", List.of("quest", "q", "questlog"), commands.quests());
        plugin.addCommand("contracts", "Open your daily and weekly contracts", List.of("contract"), commands.contracts());
        plugin.addCommand("questadmin", "Quest admin tools", List.of("qadmin"), commands.admin());

        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, manager::flushTracker, 10L, 10L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, manager::flush, 600L, 600L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, manager::minuteTick, 1200L, 1200L));

        // One tick later, after every module is up: hooks into installed systems, then players already online (/reload).
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Services.find(dev.spacepunk.prison.api.FishingService.class).isPresent()) {
                manager.registerObjectiveType(ObjectiveTypes.CATCH_FISH, "Catch fish");
            }
            if (Services.find(dev.spacepunk.prison.api.UndercroftService.class).isPresent()) {
                manager.registerObjectiveType(ObjectiveTypes.UNDERCROFT_WIN, "Win Undercroft fights");
                manager.registerObjectiveType(ObjectiveTypes.SMUGGLE, "Smuggle contraband");
            }
            java.util.Set<String> loaded = new java.util.HashSet<>();
            plugin.modules().forEach(m -> loaded.add(m.id()));
            if (loaded.contains("workshop")) manager.registerObjectiveType(ObjectiveTypes.PROCESS_ITEM, "Process workshop goods");
            if (loaded.contains("raredrops")) manager.registerObjectiveType(ObjectiveTypes.RARE_DROP, "Find rare drops");
            if (loaded.contains("auction")) {
                manager.registerObjectiveType(ObjectiveTypes.AUCTION_SELL, "Sell on the auction house");
                manager.registerObjectiveType(ObjectiveTypes.AUCTION_BUY, "Buy on the auction house");
            }
            Services.find(GangService.class).ifPresent(g -> {
                manager.registerObjectiveType(ObjectiveTypes.GANG_JOIN, "Join a gang");
                g.registerContractObjective("quests_done", "Quests and contracts finished");
            });
            placeNpcs(0);
            ProfileService profiles = Services.get(ProfileService.class);
            for (Player p : Bukkit.getOnlinePlayers()) profiles.get(p.getUniqueId()).ifPresent(prof -> manager.load(p, prof));
        });
        plugin.getLogger().info("[quests] " + config.storySteps.size() + " story steps in " + config.chapters.size() + " chapters, "
                + config.tutorials.size() + " tutorials, " + config.daily.size() + " daily and " + config.weekly.size()
                + " weekly contracts, " + config.tiers.size() + " track tiers");
    }

    @Override
    public void disable() {
        releaseNpcChunks();
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        if (manager != null) {
            manager.flushAll();
            manager.clearTrackers();
        }
    }

    /**
     * Places the quest NPCs on the Crew Deck once the station hub exists. Keeps the spot chunks loaded with a plugin
     * ticket, then waits until their entities are in memory, so villagers saved on disk are found instead of doubled.
     */
    void placeNpcs(int attempt) {
        if (!config.npcAutoPlace || config.npcSpots.isEmpty()) return;
        var warps = Services.find(dev.spacepunk.prison.api.WarpService.class);
        org.bukkit.World world = Bukkit.getWorld(config.npcWorld);
        boolean hubReady = warps.isPresent() && warps.get().hub() != null && world != null;
        if (!hubReady || !npcChunksReady(world)) {
            if (attempt < 150) tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> placeNpcs(attempt + 1), 40L));
            else plugin.getLogger().warning("[quests] station hub '" + config.npcWorld + "' not ready after 5 minutes; quest NPCs not placed");
            return;
        }
        int n = npcs.place(world);
        releaseNpcChunks();
        if (n > 0) plugin.getLogger().info("[quests] placed " + n + " quest NPC(s) on the Crew Deck");
    }

    private final List<org.bukkit.Chunk> ticketed = new ArrayList<>();

    private void releaseNpcChunks() {
        ticketed.forEach(c -> c.removePluginChunkTicket(plugin));
        ticketed.clear();
    }

    private boolean npcChunksReady(org.bukkit.World world) {
        boolean ready = true;
        for (org.bukkit.Chunk c : npcs.spotChunks(world)) {
            if (!ticketed.contains(c)) { c.addPluginChunkTicket(plugin); ticketed.add(c); }
            if (!c.isEntitiesLoaded()) ready = false;
        }
        return ready;
    }

    void reload() {
        config = QuestConfig.load(new File(plugin.getDataFolder(), "quests.yml"), plugin.getLogger());
        for (Player p : Bukkit.getOnlinePlayers()) {
            QuestData d = manager.data(p.getUniqueId());
            if (d != null) manager.settle(p, d, true);
        }
        placeNpcs(0);
    }

    List<String> typeIds() { return manager.registeredTypes(); }

    QuestManager manager() { return manager; }
    QuestMenu menu() { return menu; }
    QuestNpcs npcs() { return npcs; }
}
