package dev.spacepunk.prison.resourcepack;

import dev.spacepunk.prison.SpacepunkPrison;
import dev.spacepunk.prison.core.FeatureModule;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Serves the spacepunk resource pack from a built-in HTTP server and sends it to every player on join as a
 * required pack. The pack zip is built by Gradle from /resourcepack and embedded in the plugin jar, so the
 * jar and the pack always match. Settings live in resourcepack.yml.
 */
public final class ResourcePackModule implements FeatureModule {
    static final String EMBEDDED_PACK = "pack/spacepunk-pack.zip";
    static final String BYPASS_PERMISSION = "spacepunk.resourcepack.bypass";
    /** Stable id so clients replace the old copy instead of stacking packs. */
    static final UUID PACK_ID = UUID.nameUUIDFromBytes("spacepunk:server-pack".getBytes(StandardCharsets.UTF_8));

    private SpacepunkPrison plugin;
    private PackListener listener;
    private PackHttpServer http;
    private volatile PackFile pack;
    private YamlConfiguration cfg;
    private int boundPort = -1;
    private String boundAddress = "";

    @Override public String id() { return "resourcepack"; }

    @Override
    public void enable(SpacepunkPrison plugin) throws Exception {
        this.plugin = plugin;
        load();
        listener = new PackListener(this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        plugin.addCommand("resourcepack", "Re-send the station texture pack", List.of("rp"), command());
    }

    @Override
    public void disable() {
        if (listener != null) HandlerList.unregisterAll(listener);
        if (http != null) http.stop();
        http = null;
    }

    /** (Re)reads resourcepack.yml and the pack file, and (re)starts the HTTP server if its address changed. */
    private void load() throws IOException {
        File file = new File(plugin.getDataFolder(), "resourcepack.yml");
        if (!file.exists()) plugin.saveResource("resourcepack.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);

        pack = readPack();
        plugin.getLogger().info("Resource pack " + pack.source() + ": " + pack.bytes().length / 1024 + " KiB, sha1 " + pack.sha1());

        if (!cfg.getBoolean("http.enabled", true)) {
            if (http != null) http.stop();
            http = null;
            boundPort = -1;
            return;
        }
        int port = cfg.getInt("http.port", 25580);
        String bind = cfg.getString("http.bind", "0.0.0.0");
        if (http != null && port == boundPort && bind.equals(boundAddress)) {
            http.setPack(pack);
            return;
        }
        if (http != null) http.stop();
        http = new PackHttpServer(plugin.getLogger(), bind, port, pack);
        http.start();
        boundPort = port;
        boundAddress = bind;
        plugin.getLogger().info("Serving the resource pack over HTTP on " + bind + ":" + port);
    }

    private PackFile readPack() throws IOException {
        String override = cfg.getString("pack-file", "");
        if (override != null && !override.isBlank()) {
            File f = new File(override);
            if (!f.isAbsolute()) f = new File(plugin.getDataFolder(), override);
            if (f.isFile()) return PackFile.read(new FileInputStream(f), f.getPath());
            plugin.getLogger().warning("pack-file " + f.getPath() + " not found; using the pack built into the jar");
        }
        InputStream in = plugin.getResource(EMBEDDED_PACK);
        if (in == null) throw new IOException("The plugin jar has no " + EMBEDDED_PACK + "; was it built with ./gradlew build?");
        return PackFile.read(in, "built-in");
    }

    YamlConfiguration cfg() { return cfg; }

    SpacepunkPrison plugin() { return plugin; }

    /** The URL this player should download the pack from. */
    String urlFor(Player player) {
        String file = pack.sha1() + ".zip";
        String base = cfg.getString("public-url", "");
        if (base != null && !base.isBlank()) {
            return base.endsWith(".zip") ? base : base.replaceAll("/+$", "") + "/" + file;
        }
        // Default: the same host name the player typed to join, on the pack port. Works for LAN IPs and domains.
        String host = null;
        InetSocketAddress vh = player.getVirtualHost();
        if (vh != null) host = vh.getHostString();
        if (host == null || host.isBlank()) host = Bukkit.getIp();
        if (host == null || host.isBlank()) host = "localhost";
        int nul = host.indexOf('\0');
        if (nul >= 0) host = host.substring(0, nul);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        int port = cfg.getInt("http.public-port", 0) > 0 ? cfg.getInt("http.public-port") : cfg.getInt("http.port", 25580);
        return "http://" + host + ":" + port + "/" + file;
    }

    void send(Player player) {
        PackFile p = pack;
        String url = urlFor(player);
        boolean required = cfg.getBoolean("required", true) && !player.hasPermission(BYPASS_PERMISSION);
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), p.sha1());
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(required)
                    .replace(true)
                    .prompt(Text.mm(cfg.getString("messages.prompt", "Station textures required.")))
                    .build());
            if (cfg.getBoolean("debug", false)) plugin.getLogger().info("Sent pack to " + player.getName() + ": " + url);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Bad resource pack URL " + url + "; check public-url in resourcepack.yml", e);
        }
    }

    private BasicCommand command() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender sender = src.getSender();
                String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
                switch (sub) {
                    case "reload" -> {
                        if (!sender.hasPermission("spacepunk.admin")) { Text.send(sender, "<red>No permission."); return; }
                        try {
                            load();
                        } catch (IOException e) {
                            plugin.getLogger().log(Level.SEVERE, "Resource pack reload failed", e);
                            Text.send(sender, "<red>Reload failed: " + e.getMessage());
                            return;
                        }
                        for (Player p : Bukkit.getOnlinePlayers()) send(p);
                        Text.send(sender, "<gray>Pack reloaded (sha1 <white>" + pack.sha1().substring(0, 12) + "</white>) and re-sent to everyone online.");
                    }
                    case "info" -> {
                        if (!sender.hasPermission("spacepunk.admin")) { Text.send(sender, "<red>No permission."); return; }
                        PackFile p = pack;
                        String url = sender instanceof Player pl ? urlFor(pl) : "(depends on the player's join address)";
                        Text.send(sender, "<gray>Pack: <white>" + p.source() + "</white>, " + p.bytes().length / 1024 + " KiB"
                                + "\n  sha1 <white>" + p.sha1() + "</white>"
                                + "\n  http " + (http == null ? "<red>off" : "<white>" + boundAddress + ":" + boundPort)
                                + "\n  <gray>url <white>" + url);
                    }
                    default -> {
                        if (sender instanceof Player p) {
                            send(p);
                            Text.send(sender, "<gray>Re-sending station textures...");
                        } else {
                            Text.send(sender, "<gray>/resourcepack [reload|info]");
                        }
                    }
                }
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length <= 1 && src.getSender().hasPermission("spacepunk.admin")) return List.of("reload", "info");
                return List.of();
            }
        };
    }
}
