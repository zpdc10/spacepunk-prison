package dev.spacepunk.prison.hud;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.core.FeatureModule;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Player HUD: sidebar, action bar (shared through {@link HudService}), and tab list.
 * Loaded last so every service it reads is registered. Config: hud.yml.
 */
public final class HudModule implements FeatureModule {
    private static final int FAST_TICKS = 4;

    private SpacepunkPrison plugin;
    private volatile HudConfig config;
    private HudManager hud;
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override public String id() { return "hud"; }

    @Override
    public void enable(SpacepunkPrison plugin) {
        this.plugin = plugin;
        reload();
        hud = new HudManager(() -> config);
        Services.register(HudService.class, hud);
        Bukkit.getPluginManager().registerEvents(hud, plugin);
        plugin.addCommand("hud", "Toggle your sidebar", List.of("sidebar"), new HudCommand());
        schedule();

        // /reload safety: players already online with loaded profiles.
        ProfileService profiles = Services.get(ProfileService.class);
        for (Player p : Bukkit.getOnlinePlayers()) profiles.get(p.getUniqueId()).ifPresent(prof -> hud.attach(p, prof));
    }

    private void schedule() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        HudConfig c = config;
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, hud::tickFast, FAST_TICKS, FAST_TICKS));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, hud::tickSidebar, c.sidebarTicks(), c.sidebarTicks()));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, hud::tickTab, c.tabTicks(), c.tabTicks()));
    }

    @Override
    public void disable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (hud != null) hud.detachAll();
    }

    private void reload() {
        File file = new File(plugin.getDataFolder(), "hud.yml");
        if (!file.exists()) plugin.saveResource("hud.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("hud.yml")) {
            if (in != null) y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read default hud.yml: " + e.getMessage());
        }
        config = HudConfig.load(y);
    }

    private final class HudCommand implements BasicCommand {
        @Override
        public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
            CommandSender sender = src.getSender();
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("spacepunk.admin")) {
                    Text.send(sender, "<red>No permission.");
                    return;
                }
                reload();
                schedule();
                Text.send(sender, "<gray>hud.yml reloaded.");
                return;
            }
            if (!(sender instanceof Player p)) {
                Text.send(sender, "<gray>/hud reload");
                return;
            }
            boolean on = hud.toggleSidebar(p);
            Text.send(p, on ? "<gray>Sidebar <green>on</green>." : "<gray>Sidebar <red>off</red>. <white>/hud</white> to bring it back.");
        }

        @Override
        public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
            if (args.length <= 1 && src.getSender().hasPermission("spacepunk.admin")) return List.of("reload");
            return List.of();
        }
    }
}
