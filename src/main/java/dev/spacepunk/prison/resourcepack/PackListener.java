package dev.spacepunk.prison.resourcepack;

import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Sends the pack on join and kicks players who decline it (or whose download fails) when the pack is required. */
final class PackListener implements Listener {
    private final ResourcePackModule module;

    PackListener(ResourcePackModule module) { this.module = module; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long delay = Math.max(0, module.cfg().getLong("send-delay-ticks", 40));
        Bukkit.getScheduler().runTaskLater(module.plugin(), () -> {
            if (player.isOnline()) module.send(player);
        }, delay);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!ResourcePackModule.PACK_ID.equals(event.getID())) return;
        Player player = event.getPlayer();
        String messageKey = switch (event.getStatus()) {
            case DECLINED -> "messages.declined";
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> "messages.failed";
            default -> null;
        };
        if (messageKey == null) return;
        if (messageKey.equals("messages.failed")) {
            module.plugin().getLogger().warning("Resource pack " + event.getStatus() + " for " + player.getName()
                    + " (url " + module.urlFor(player) + "). Check http.port is reachable and public-url in resourcepack.yml.");
        }
        boolean kick = module.cfg().getBoolean("required", true)
                && !player.hasPermission(ResourcePackModule.BYPASS_PERMISSION)
                && (messageKey.equals("messages.declined") || module.cfg().getBoolean("kick-on-failed-download", true));
        if (!kick) return;
        String msg = module.cfg().getString(messageKey, "<red>The station texture pack is required.");
        Bukkit.getScheduler().runTask(module.plugin(), () -> {
            if (player.isOnline()) player.kick(Text.mm(msg));
        });
    }
}
