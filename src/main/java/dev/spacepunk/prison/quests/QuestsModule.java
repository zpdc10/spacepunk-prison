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
            Services.find(GangService.class).ifPresent(g -> {
                manager.registerObjectiveType(ObjectiveTypes.GANG_JOIN, "Join a gang");
                g.registerContractObjective("quests_done", "Quests and contracts finished");
            });
            ProfileService profiles = Services.get(ProfileService.class);
            for (Player p : Bukkit.getOnlinePlayers()) profiles.get(p.getUniqueId()).ifPresent(prof -> manager.load(p, prof));
        });
        plugin.getLogger().info("[quests] " + config.storySteps.size() + " story steps in " + config.chapters.size() + " chapters, "
                + config.tutorials.size() + " tutorials, " + config.daily.size() + " daily and " + config.weekly.size()
                + " weekly contracts, " + config.tiers.size() + " track tiers");
    }

    @Override
    public void disable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        if (manager != null) {
            manager.flushAll();
            manager.clearTrackers();
        }
    }

    void reload() {
        config = QuestConfig.load(new File(plugin.getDataFolder(), "quests.yml"), plugin.getLogger());
        for (Player p : Bukkit.getOnlinePlayers()) {
            QuestData d = manager.data(p.getUniqueId());
            if (d != null) manager.settle(p, d, true);
        }
    }

    List<String> typeIds() { return manager.registeredTypes(); }

    QuestManager manager() { return manager; }
    QuestMenu menu() { return menu; }
    QuestNpcs npcs() { return npcs; }
}
