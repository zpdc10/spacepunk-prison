package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.MineBlocksBrokenEvent;
import dev.spacepunk.prison.api.event.OreSellEvent;
import dev.spacepunk.prison.util.Numbers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Module hooks into progression's selling: Magnet Coil auto-sells, Scavenger Rig lowers sell price. */
public final class SellListener implements Listener {
    private final DrillManager drills;
    private final HeatManager heat;

    public SellListener(DrillManager drills, HeatManager heat) {
        this.drills = drills;
        this.heat = heat;
    }

    /** HIGH: after Yield (NORMAL), before progression stores ore in the cargo hold (HIGHEST). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMine(MineBlocksBrokenEvent e) {
        if (e.cause() == MineService.BreakCause.ADMIN || e.ores().isEmpty()) return;
        if (!drills.autoSell(e.player().getUniqueId())) return;
        ProgressionService prog = Services.find(ProgressionService.class).orElse(null);
        if (prog == null) return;
        long paid = prog.sell(e.player(), e.ores(), 1.0, "magnet-coil");
        e.ores().clear();
        if (paid > 0 && e.cause() == MineService.BreakCause.HAND) {
            heat.flash(e.player(), "<gold>+" + Numbers.shortFormat(paid) + " Credits");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSell(OreSellEvent e) {
        double m = drills.sellMultiplier(e.player().getUniqueId());
        if (m != 1.0) e.multiplier(e.multiplier() * m);
    }
}
