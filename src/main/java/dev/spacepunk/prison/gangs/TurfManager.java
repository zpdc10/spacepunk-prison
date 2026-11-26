package dev.spacepunk.prison.gangs;

import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.UndercroftService;
import dev.spacepunk.prison.api.event.BountyTurnInEvent;
import dev.spacepunk.prison.api.event.GunHitEvent;
import dev.spacepunk.prison.api.event.StimCookedEvent;
import dev.spacepunk.prison.api.event.UndercroftKillEvent;
import dev.spacepunk.prison.util.Numbers;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Undercroft turf: gangs seize drug labs by holding the area around them, earn a cut of every batch cooked there
 * plus hourly lab income, and score gang points for kills and bounty turn-ins. Runs once a second.
 */
final class TurfManager implements Listener {
    private final GangManager gangs;
    private final Map<String, Capture> captures = new HashMap<>();
    private int seconds;

    private static final class Capture {
        final String gangId;
        int progress;
        boolean warned;

        Capture(String gangId) { this.gangId = gangId; }
    }

    TurfManager(GangManager gangs) { this.gangs = gangs; }

    void tick() {
        Optional<UndercroftService> svc = Services.find(UndercroftService.class);
        if (svc.isEmpty()) return;
        UndercroftService uc = svc.get();
        GangsConfig c = gangs.cfg();
        seconds++;
        boolean payout = c.labPayoutMinutes > 0 && seconds % (c.labPayoutMinutes * 60) == 0;
        double r2 = (double) c.captureRadius * c.captureRadius;

        for (UndercroftService.LabInfo lab : uc.labs()) {
            String owner = lab.controllerId().orElse(null);
            // Staff can set labs with /undercroft admin lab: adopt that as the gang's turf.
            gangs.syncTerritory(lab.id(), owner != null && gangs.gang(owner).isPresent() ? owner : null);
            if (owner != null && gangs.gang(owner).isEmpty()) owner = null;

            if (payout && owner != null) {
                long amount = c.labIncome(lab.ring());
                long paid = gangs.depositFromServer(owner, amount, "turf " + lab.id());
                if (paid > 0) gangs.broadcast(gangs.byId(owner), "<gray>" + lab.displayName() + "<gray> paid the crew "
                        + "<gold>" + Numbers.shortFormat(paid) + " Credits</gold>.");
            }

            Location loc = lab.location();
            if (loc == null || loc.getWorld() == null || c.captureSeconds <= 0) continue;
            Set<String> present = new HashSet<>();
            List<Player> nearby = new ArrayList<>();
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
                if (p.getLocation().distanceSquared(loc) > r2) continue;
                Gang g = gangs.gangFor(p.getUniqueId());
                if (g == null) continue;
                present.add(g.id);
                nearby.add(p);
            }
            Capture cap = captures.get(lab.id());
            if (present.size() == 1) {
                String gid = present.iterator().next();
                if (gid.equals(owner)) {
                    if (cap != null) {
                        captures.remove(lab.id());
                        for (Player p : nearby) flash(p, "<green>" + plain(lab) + " held.");
                    }
                    continue;
                }
                if (cap == null || !cap.gangId.equals(gid)) {
                    cap = new Capture(gid);
                    captures.put(lab.id(), cap);
                }
                cap.progress++;
                if (!cap.warned && owner != null) {
                    cap.warned = true;
                    Gang og = gangs.byId(owner);
                    Gang ng = gangs.byId(gid);
                    if (og != null && ng != null) gangs.broadcast(og, "<red>⚠ [" + ng.coloredTag() + "<red>] " + ng.name
                            + " is taking " + lab.displayName() + "<red>! Get down there.");
                }
                for (Player p : nearby) flash(p, "<#c8743a>Seizing " + plain(lab) + " <white>" + cap.progress + "/" + c.captureSeconds + "s");
                if (cap.progress >= c.captureSeconds) {
                    captures.remove(lab.id());
                    gangs.setTerritoryOwner(lab.id(), gid);
                    Gang ng = gangs.byId(gid);
                    if (ng != null) gangs.addPointsTo(ng, c.capturePoints);
                    for (Player p : nearby) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);
                }
            } else if (present.size() > 1) {
                if (cap != null) for (Player p : nearby) flash(p, "<red>" + plain(lab) + " contested!");
            } else if (cap != null) {
                cap.progress -= 2;
                if (cap.progress <= 0) captures.remove(lab.id());
            }
        }
    }

    private static String plain(UndercroftService.LabInfo lab) { return lab.displayName(); }

    private static void flash(Player p, String mm) {
        Optional<HudService> hud = Services.find(HudService.class);
        if (hud.isPresent()) hud.get().flash(p, mm);
        else p.sendActionBar(dev.spacepunk.prison.util.Text.mm(mm));
    }

    /** Lab cut: gang cooks keep their doses; anyone else's cut is paid to the gang bank as Credits. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCooked(StimCookedEvent e) {
        String gid = e.controllerId();
        if (gid == null || e.controllerCut() <= 0) return;
        Gang g = gangs.byId(gid);
        if (g == null) return;
        if (g.member(e.cook().getUniqueId()) != null) {
            e.amount(e.amount() + e.controllerCut());
            return;
        }
        long paid = gangs.depositFromServer(gid, e.controllerCut() * gangs.cfg().creditsPerDose, "lab cut");
        if (paid > 0) gangs.broadcast(g, "<gray>Cut from a batch at the lab: <gold>" + Numbers.shortFormat(paid) + " Credits</gold>.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(UndercroftKillEvent e) {
        if (!e.tagDropped()) return;
        if (gangs.sameGang(e.killer().getUniqueId(), e.victim().getUniqueId())) return;
        long pts = gangs.cfg().killPoints * Math.max(1, e.ring());
        gangs.addPoints(e.killer().getUniqueId(), pts, "undercroft kill");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBounty(BountyTurnInEvent e) {
        gangs.addPoints(e.player().getUniqueId(), e.points(), "bounty");
    }

    /** Guns skip EntityDamageByEntity checks, so block gangmate hits here too. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGunHit(GunHitEvent e) {
        if (gangs.cfg().friendlyFire) return;
        if (e.target() instanceof Player victim && victim != e.shooter()
                && gangs.sameGang(e.shooter().getUniqueId(), victim.getUniqueId())) e.setCancelled(true);
    }
}
