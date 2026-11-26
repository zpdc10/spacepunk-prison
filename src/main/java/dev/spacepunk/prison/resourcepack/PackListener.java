package dev.spacepunk.prison.resourcepack;

import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends the pack on join and kicks players who decline it (or whose download fails) when the pack is required.
 *
 * <p>A world change or teleport right after join can close the client's pack prompt without any status coming
 * back. So until the client answers, the pack is re-sent after a world change and again whenever no status
 * arrives within {@code retry-seconds} (up to {@code max-retries} times).
 */
final class PackListener implements Listener {
    private final ResourcePackModule module;
    /** Players we sent the pack to who have not answered yet, with the number of sends so far. */
    private final Map<UUID, Integer> awaiting = new ConcurrentHashMap<>();

    PackListener(ResourcePackModule module) { this.module = module; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        awaiting.put(player.getUniqueId(), 0);
        sendLater(player, Math.max(0, module.cfg().getLong("send-delay-ticks", 40)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // Only matters once a prompt was shown and not answered; before the first send, the join send covers it.
        Integer sends = awaiting.get(player.getUniqueId());
        if (sends != null && sends > 0) sendLater(player, 10);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaiting.remove(event.getPlayer().getUniqueId());
    }

    private void sendLater(Player player, long delayTicks) {
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(module.plugin(), () -> {
            Integer sends = awaiting.get(id);
            if (sends == null || !player.isOnline()) return;
            int max = Math.max(0, module.cfg().getInt("max-retries", 3));
            if (sends > max) {
                module.plugin().getLogger().warning(player.getName() + " never answered the resource pack prompt after "
                        + sends + " sends; giving up (url " + module.urlFor(player) + ")");
                awaiting.remove(id);
                return;
            }
            awaiting.put(id, sends + 1);
            module.send(player);
            int attempt = sends + 1;
            long retryTicks = Math.max(1, module.cfg().getLong("retry-seconds", 15)) * 20L;
            Bukkit.getScheduler().runTaskLater(module.plugin(), () -> {
                Integer now = awaiting.get(id);
                // Still no answer and no newer send since this one: try again.
                if (now != null && now == attempt && player.isOnline()) {
                    if (module.cfg().getBoolean("debug", false)) {
                        module.plugin().getLogger().info("No pack status from " + player.getName() + " after send " + attempt + "; re-sending");
                    }
                    sendLater(player, 0);
                }
            }, retryTicks);
        }, delayTicks);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!ResourcePackModule.PACK_ID.equals(event.getID())) return;
        Player player = event.getPlayer();
        // Any answer means the prompt reached the client; stop retrying.
        awaiting.remove(player.getUniqueId());
        if (module.cfg().getBoolean("debug", false)) {
            module.plugin().getLogger().info("Pack status from " + player.getName() + ": " + event.getStatus());
        }
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
