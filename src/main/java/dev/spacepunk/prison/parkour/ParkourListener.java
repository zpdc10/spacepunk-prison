package dev.spacepunk.prison.parkour;

import dev.spacepunk.prison.data.ProfileLoadedEvent;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Pressure plates on the courses, profile load/unload, and cancelling runs on teleport-away. */
final class ParkourListener implements Listener {
    private final ParkourManager parkour;

    ParkourListener(ParkourManager parkour) { this.parkour = parkour; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoaded(ProfileLoadedEvent e) { parkour.loadPlayer(e.player().getUniqueId(), e.profile()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) { parkour.unloadPlayer(e.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlate(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        ParkourManager.Hit hit = parkour.hitAt(b);
        if (hit == null) return;
        parkour.stepOn(e.getPlayer(), hit);
    }

    /** Commands like /mine or /spawn end a run; our own respawns stay on the course and are ignored. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        ParkourManager.Run run = parkour.run(e.getPlayer().getUniqueId());
        if (run == null) return;
        Course c = parkour.courses().get(run.course);
        if (c == null || !c.near(e.getTo(), 4)) parkour.endRun(e.getPlayer(), "<gray>Run cancelled.");
    }
}
