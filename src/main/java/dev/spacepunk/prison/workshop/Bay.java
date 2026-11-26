package dev.spacepunk.prison.workshop;

import dev.spacepunk.prison.workshop.BayData.MachineData;
import dev.spacepunk.prison.workshop.PlotRegistry.Plot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A loaded bay: the owner's data plus runtime caches (position index, last tick's power summary). */
final class Bay {
    final UUID owner;
    final Plot plot;
    final BayData data;
    private final Map<String, MachineData> byPos = new HashMap<>();

    // last simulation step, for the HUD and menus
    double powerSupply, powerDemand, coolantSupply, coolantDemand;
    int alerts;
    /** Credits owed from Trade Chute sales, paid in whole Credits. */
    double pendingCredits;
    /** Totals since the counters were last read (offline catch-up report). */
    long reportCrafted;
    long reportSold;

    Bay(UUID owner, Plot plot, BayData data) {
        this.owner = owner;
        this.plot = plot;
        this.data = data;
        reindex();
    }

    void reindex() {
        byPos.clear();
        for (MachineData m : data.machines) byPos.put(BayData.pos(m.x, m.y, m.z), m);
    }

    MachineData machineAt(int x, int y, int z) { return byPos.get(BayData.pos(x, y, z)); }

    String conveyorAt(int x, int y, int z) { return data.conveyors.get(BayData.pos(x, y, z)); }

    int machineCount() { return data.machines.size(); }
}
