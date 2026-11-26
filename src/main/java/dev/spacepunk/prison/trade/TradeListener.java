package dev.spacepunk.prison.trade;

import dev.spacepunk.prison.api.event.GradeChangeEvent;
import dev.spacepunk.prison.api.event.PrestigeAttemptEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Quits and deaths cancel trades, joins hand back stranded items, and prestige feeds the return lock. */
final class TradeListener implements Listener {
    private final TradeManager trades;

    TradeListener(TradeManager trades) { this.trades = trades; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { trades.deliverReturns(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        TradeSession s = trades.session(e.getPlayer().getUniqueId());
        if (s == null) return;
        String msg = "<gray>" + e.getPlayer().getName() + " left. <gray>Trade cancelled, your items are back.";
        trades.cancel(s, msg, msg);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        TradeSession s = trades.session(e.getPlayer().getUniqueId());
        if (s == null) return;
        String msg = "<gray>Trade cancelled.";
        trades.cancel(s, msg, msg);
    }

    @EventHandler
    public void onPrestigeAttempt(PrestigeAttemptEvent e) {
        if (trades.session(e.player().getUniqueId()) != null) {
            e.deny("<red>Finish or cancel your trade before you prestige.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrestige(GradeChangeEvent e) {
        if (e.isPrestige()) trades.onPrestige(e.player());
    }
}
