package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.api.event.ContrabandSmuggledEvent;
import dev.spacepunk.prison.api.event.FishCaughtEvent;
import dev.spacepunk.prison.api.event.SeaCreatureKilledEvent;
import dev.spacepunk.prison.api.event.UndercroftKillEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Rod and weapon module finds: fishing turns up rod modules, the Undercroft turns up weapon modules (drill.yml module-drops). */
final class GearFindListener implements Listener {
    private final DrillManager drills;

    GearFindListener(DrillManager drills) { this.drills = drills; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(FishCaughtEvent e) {
        if ("fish".equals(e.kind()) || "treasure".equals(e.kind())) drills.rollFind(e.player(), DrillConfig.KIND_ROD, e.kind());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreature(SeaCreatureKilledEvent e) {
        drills.rollFind(e.player(), DrillConfig.KIND_ROD, "sea-creature");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(UndercroftKillEvent e) {
        if (e.killer() == null) return;
        drills.rollFind(e.killer(), DrillConfig.KIND_GUN, "undercroft-kill");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmuggle(ContrabandSmuggledEvent e) {
        if (e.items() <= 0) return;
        drills.rollFind(e.player(), DrillConfig.KIND_GUN, "smuggle");
    }
}
