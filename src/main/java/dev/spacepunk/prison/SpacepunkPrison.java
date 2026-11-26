package dev.spacepunk.prison;

import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import dev.spacepunk.prison.data.ProfileManager;
import dev.spacepunk.prison.data.ProfileStorage;
import dev.spacepunk.prison.economy.EconomyCommands;
import dev.spacepunk.prison.economy.EconomyManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Plugin entry point. Core (profiles + economy) is wired here. Feature modules are discovered by class name
 * from {@link #MODULE_CLASSES}, so each workstream adds its module without editing this file: if a class is
 * missing, it is simply skipped.
 */
public final class SpacepunkPrison extends JavaPlugin {
    /** Load order matters: services a module needs must be registered by an earlier module. */
    private static final List<String> MODULE_CLASSES = List.of(
            "dev.spacepunk.prison.resourcepack.ResourcePackModule",
            "dev.spacepunk.prison.mines.MinesModule",
            "dev.spacepunk.prison.station.StationModule",
            "dev.spacepunk.prison.progression.ProgressionModule",
            "dev.spacepunk.prison.drill.DrillModule",
            "dev.spacepunk.prison.hud.HudModule"
    );

    private final List<FeatureModule> modules = new ArrayList<>();
    private final List<PendingCommand> commands = new ArrayList<>();
    private ProfileManager profileManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            profileManager = new ProfileManager(this, new ProfileStorage(getDataFolder()));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not open database; disabling", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Services.register(ProfileService.class, profileManager);
        EconomyManager economy = new EconomyManager(profileManager);
        Services.register(EconomyService.class, economy);
        addCommand("balance", "Show your balances", List.of("bal"), EconomyCommands.balance(economy));
        addCommand("eco", "Admin economy controls", List.of(), EconomyCommands.eco(economy));

        for (String cls : MODULE_CLASSES) {
            FeatureModule m;
            try {
                m = (FeatureModule) Class.forName(cls).getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                getLogger().info("Module not present, skipping: " + cls);
                continue;
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Could not construct module " + cls, e);
                continue;
            }
            try {
                m.enable(this);
                modules.add(m);
                getLogger().info("Enabled module " + m.id());
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Module " + m.id() + " failed to enable", e);
            }
        }

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (PendingCommand c : commands) event.registrar().register(c.label, c.description, c.aliases, c.command);
        });

        profileManager.start(getConfig().getInt("storage.autosave-seconds", 120));
    }

    @Override
    public void onDisable() {
        for (int i = modules.size() - 1; i >= 0; i--) {
            try {
                modules.get(i).disable();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Module " + modules.get(i).id() + " failed to disable", e);
            }
        }
        if (profileManager != null) profileManager.shutdown();
        Services.clear();
    }

    /** Modules call this from enable() to add a command. Registered with Brigadier after all modules load. */
    public void addCommand(String label, String description, Collection<String> aliases, BasicCommand command) {
        commands.add(new PendingCommand(label, description, List.copyOf(aliases), command));
    }

    public List<FeatureModule> modules() { return List.copyOf(modules); }

    private record PendingCommand(String label, String description, Collection<String> aliases, BasicCommand command) {}
}
