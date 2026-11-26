package dev.spacepunk.prison.gangs;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.event.CurrencyChangeEvent;
import dev.spacepunk.prison.api.event.GradeChangeEvent;
import dev.spacepunk.prison.api.event.MineBlocksBrokenEvent;
import dev.spacepunk.prison.api.event.OreSellEvent;
import dev.spacepunk.prison.data.ProfileLoadedEvent;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Feeds gang points/contracts from gameplay events, applies the prestige wipe, gang chat and friendly fire. */
final class GangListener implements Listener {
    private final Plugin plugin;
    private final GangManager gangs;

    GangListener(Plugin plugin, GangManager gangs) {
        this.plugin = plugin;
        this.gangs = gangs;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoaded(ProfileLoadedEvent e) {
        Player p = e.player();
        gangs.updateGrade(p.getUniqueId(), p.getName(), e.profile().grade());
        Gang g = gangs.gangFor(p.getUniqueId());
        if (g != null && !g.motd.isBlank()) {
            Text.send(p, "<dark_gray>[" + g.coloredTag() + "<dark_gray>]</dark_gray> <gray>Crew notice: <white>" + Text.MM.escapeTags(g.motd));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { gangs.clearInvites(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGrade(GradeChangeEvent e) {
        Player p = e.player();
        gangs.updateGrade(p.getUniqueId(), p.getName(), e.newGrade());
        if (e.isPrestige()) {
            gangs.onPrestige(p);
            return;
        }
        int ups = e.newGrade() - e.oldGrade();
        Gang g = gangs.gangFor(p.getUniqueId());
        if (g != null && ups > 0 && e.newPrestige() == e.oldPrestige()) {
            gangs.addPoints(p.getUniqueId(), gangs.cfg().pointsPerRankup * ups, "rankup");
            gangs.progress(g, "rankups", ups);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMined(MineBlocksBrokenEvent e) {
        Gang g = gangs.gangFor(e.player().getUniqueId());
        if (g == null || e.blockCount() <= 0) return;
        gangs.addFractional(e.player().getUniqueId(), e.blockCount() * gangs.cfg().pointsPerBlock);
        gangs.progress(g, "blocks_mined", e.blockCount());
    }

    /** Fence Network upgrade: sell bonus for every member. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSell(OreSellEvent e) {
        Gang g = gangs.gangFor(e.player().getUniqueId());
        if (g == null) return;
        double bonus = gangs.cfg().sellBonus(g);
        if (bonus > 0) e.multiplier(e.multiplier() * (1 + bonus));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSold(OreSellEvent e) {
        Gang g = gangs.gangFor(e.player().getUniqueId());
        if (g == null) return;
        long units = 0;
        for (long v : e.items().values()) units += v;
        if (units > 0) gangs.progress(g, "ore_sold", units);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIncome(CurrencyChangeEvent e) {
        if (e.currency() != Currency.CREDITS || e.newBalance() <= e.oldBalance()) return;
        if (!gangs.cfg().isServerIncome(e.reason())) return;
        Gang g = gangs.gangFor(e.player());
        if (g == null) return;
        long gained = e.newBalance() - e.oldBalance();
        gangs.addFractional(e.player(), (double) gained / gangs.cfg().creditsPerPoint);
        gangs.progress(g, "credits_earned", gained);
    }

    /** Gang chat toggle: route normal chat to the crew. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!gangs.chatToggled(p.getUniqueId())) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> gangs.chat(p, msg));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        if (gangs.cfg().friendlyFire) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker == victim) return;
        if (gangs.sameGang(attacker.getUniqueId(), victim.getUniqueId())) e.setCancelled(true);
    }
}
