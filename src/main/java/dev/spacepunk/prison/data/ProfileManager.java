package dev.spacepunk.prison.data;

import dev.spacepunk.prison.api.ProfileService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Loads profiles async on join, saves async on quit and on a timer. */
public final class ProfileManager implements ProfileService, Listener {
    private final Plugin plugin;
    private final ProfileStorage storage;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spacepunk-storage");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, PlayerProfile> loaded = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private BukkitTask autosave;

    public ProfileManager(Plugin plugin, ProfileStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void start(int autosaveSeconds) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long ticks = autosaveSeconds * 20L;
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::saveDirty, ticks, ticks);
        for (Player p : Bukkit.getOnlinePlayers()) load(p); // /reload safety
    }

    public void shutdown() {
        if (autosave != null) autosave.cancel();
        for (PlayerProfile p : loaded.values()) {
            ProfileStorage.Snapshot s = ProfileStorage.Snapshot.of(p);
            io.submit(() -> write(s));
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) plugin.getLogger().warning("Storage did not finish saving in time");
            storage.close();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing storage", e);
        }
    }

    @Override public Optional<PlayerProfile> get(UUID player) { return Optional.ofNullable(loaded.get(player)); }

    @Override public PlayerProfile require(UUID player) {
        PlayerProfile p = loaded.get(player);
        if (p == null) throw new IllegalStateException("Profile not loaded for " + player);
        return p;
    }

    @Override public void markDirty(UUID player) { dirty.add(player); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) { load(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        PlayerProfile p = loaded.remove(e.getPlayer().getUniqueId());
        dirty.remove(e.getPlayer().getUniqueId());
        if (p != null) {
            ProfileStorage.Snapshot s = ProfileStorage.Snapshot.of(p);
            io.submit(() -> write(s));
        }
    }

    private void load(Player player) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        io.submit(() -> {
            try {
                PlayerProfile p = storage.loadOrCreate(id, name);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Bukkit.getPlayer(id) == null) return; // left while loading
                    p.lastName(name);
                    loaded.put(id, p);
                    dirty.add(id);
                    Bukkit.getPluginManager().callEvent(new ProfileLoadedEvent(player, p));
                });
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load profile for " + name, ex);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) online.kick(net.kyori.adventure.text.Component.text("Profile failed to load. Try again shortly."));
                });
            }
        });
    }

    private void saveDirty() {
        for (UUID id : Set.copyOf(dirty)) {
            PlayerProfile p = loaded.get(id);
            dirty.remove(id);
            if (p == null) continue;
            ProfileStorage.Snapshot s = ProfileStorage.Snapshot.of(p);
            io.submit(() -> write(s));
        }
    }

    private void write(ProfileStorage.Snapshot s) {
        try {
            storage.save(s);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save profile " + s.uuid(), ex);
        }
    }
}
