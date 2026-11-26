package dev.spacepunk.prison.station;

import dev.spacepunk.prison.api.WarpService;
import dev.spacepunk.prison.api.event.PlayerWarpEvent;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** {@link WarpService}: registry of destinations, plus the hub spawn. */
final class WarpManager implements WarpService {
    private final Map<String, Warp> warps = new LinkedHashMap<>();
    private final Supplier<Location> hub;

    WarpManager(Supplier<Location> hub) {
        this.hub = hub;
        register(new Warp("hub", "<#c8743a>Station Concourse", STATION, org.bukkit.Material.LODESTONE,
                List.of("<gray>The main hub."), p -> hub(), p -> null));
    }

    @Override public void register(Warp warp) { warps.put(warp.id().toLowerCase(Locale.ROOT), warp); }

    @Override public void unregister(String id) { warps.remove(id.toLowerCase(Locale.ROOT)); }

    @Override public Optional<Warp> get(String id) { return Optional.ofNullable(warps.get(id.toLowerCase(Locale.ROOT))); }

    @Override public Collection<Warp> all() { return List.copyOf(warps.values()); }

    @Override
    public boolean warp(Player player, String id) {
        Warp w = warps.get(id.toLowerCase(Locale.ROOT));
        if (w == null) {
            Text.send(player, "<red>That route is not in service yet.");
            return false;
        }
        String lock;
        Location to;
        try {
            lock = w.lockReason().apply(player);
            to = lock == null ? w.destination().apply(player) : null;
        } catch (RuntimeException e) {
            Bukkit.getLogger().warning("[station] warp " + w.id() + " failed: " + e);
            Text.send(player, "<red>" + w.displayName() + " <red>is offline right now.");
            return false;
        }
        if (lock != null) {
            Text.send(player, "<red>" + w.displayName() + "<red>: " + lock);
            return false;
        }
        if (to == null || to.getWorld() == null) {
            Text.send(player, "<red>" + w.displayName() + " <red>is not available right now.");
            return false;
        }
        PlayerWarpEvent event = new PlayerWarpEvent(player, w.id(), player.getLocation(), to);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Text.send(player, event.cancelReason() != null ? event.cancelReason() : "<red>Transit denied.");
            return false;
        }
        player.teleportAsync(event.to()).thenAccept(ok -> {
            if (!ok) return;
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.4f);
            Text.send(player, "<gray>Arrived at " + w.displayName());
        });
        return true;
    }

    @Override public Location hub() {
        Location l = hub.get();
        return l == null ? null : l.clone();
    }

    @Override public void sendToHub(Player player) {
        Location l = hub();
        if (l != null) player.teleportAsync(l);
    }
}
