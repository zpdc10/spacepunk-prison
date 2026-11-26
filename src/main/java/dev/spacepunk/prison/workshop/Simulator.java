package dev.spacepunk.prison.workshop;

import dev.spacepunk.prison.workshop.BayData.MachineData;
import dev.spacepunk.prison.workshop.WorkshopConfig.Byproduct;
import dev.spacepunk.prison.workshop.WorkshopConfig.MachineDef;
import dev.spacepunk.prison.workshop.WorkshopConfig.Recipe;
import dev.spacepunk.prison.workshop.WorkshopConfig.Role;
import dev.spacepunk.prison.workshop.WorkshopConfig.Tier;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Runs a bay's machines for dt seconds. Pure data: never touches the world (bays tick while the owner is
 * elsewhere, with their chunks unloaded). Order each step:
 * work out which machines want to run, burn just enough fuel to cover their power, pump coolant, run them at
 * the resulting efficiency (brownout when power or coolant is short), add and shed heat, then push outputs
 * forward along conveyors.
 */
final class Simulator {
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    /** What the simulator needs from the rest of the module. */
    interface Hooks {
        long lockerCapacity(Bay bay);

        Map<String, Long> cargo(UUID owner);

        long cargoWithdraw(UUID owner, String id, long amount);

        /** Sells n units through a Trade Chute (market + payout). */
        void chuteSale(Bay bay, String key, long n);

        /** Called when machines finish goods (for stats and other modules). */
        void crafted(Bay bay, String key, long n);
    }

    private final Supplier<WorkshopConfig> cfg;
    private final Hooks hooks;
    private final Market market;

    Simulator(Supplier<WorkshopConfig> cfg, Hooks hooks, Market market) {
        this.cfg = cfg;
        this.hooks = hooks;
        this.market = market;
    }

    void tick(Bay bay, double dt) {
        WorkshopConfig c = cfg.get();
        BayData d = bay.data;
        List<MachineData> wanting = new ArrayList<>();
        Map<MachineData, Recipe> chosen = new LinkedHashMap<>();
        List<MachineData> generators = new ArrayList<>();
        List<MachineData> pumps = new ArrayList<>();
        double demand = 0, coolDemand = 0;
        int alerts = 0;

        for (MachineData m : d.machines) {
            m.running = false;
            m.lastEfficiency = 0;
            MachineDef def = c.machine(m.type);
            if (def == null) {
                m.status = "<red>Unknown machine";
                continue;
            }
            if (!m.enabled) {
                m.status = "<gray>Switched off";
                continue;
            }
            if (m.overheated) {
                m.status = "<red>Overheated, cooling down";
                alerts++;
                continue;
            }
            switch (def.role()) {
                case GENERATOR -> generators.add(m);
                case PUMP -> pumps.add(m);
                case PROCESSOR -> {
                    if (m.outputTotal() >= c.outputBuffer) {
                        m.status = frontTarget(bay, m) == null ? "<red>No output link" : "<yellow>Output blocked";
                        alerts++;
                        continue;
                    }
                    Recipe r = pick(c, m);
                    if (r == null) {
                        m.status = "<gray>Waiting for input";
                        continue;
                    }
                    chosen.put(m, r);
                    wanting.add(m);
                    demand += def.power() * c.tier(m.tier).power();
                    coolDemand += def.coolant();
                }
                case SOURCE -> {
                    MachineData target = frontTarget(bay, m);
                    if (target == null) {
                        m.status = "<red>No output link";
                        alerts++;
                        continue;
                    }
                    wanting.add(m);
                    demand += def.power() * c.tier(m.tier).power();
                }
                case CHUTE -> {
                    if (m.input.isEmpty()) {
                        m.status = "<gray>Waiting for goods";
                        continue;
                    }
                    wanting.add(m);
                    demand += def.power() * c.tier(m.tier).power();
                }
                case DEPOT -> m.status = hooks.lockerCapacity(bay) - d.lockerUsed() > 0 ? "<green>Ready" : "<red>Bay Locker full";
                case RADIATOR -> m.status = "<aqua>Cooling";
            }
        }

        // Pumps run only when something needs coolant.
        double coolSupply = 0;
        List<MachineData> activePumps = new ArrayList<>();
        for (MachineData p : pumps) {
            if (coolDemand <= 0) {
                p.status = "<gray>Idle, nothing needs coolant";
                continue;
            }
            MachineDef def = c.machine(p.type);
            activePumps.add(p);
            demand += def.power() * c.tier(p.tier).power();
        }

        // Generators burn only as much as the bay needs.
        double supply = 0;
        for (MachineData g : generators) {
            MachineDef def = c.machine(g.type);
            double output = def.speed() * c.tier(g.tier).speed();
            if (supply >= demand) {
                g.status = demand <= 0 ? "<gray>Idle, nothing needs power" : "<gray>Standby";
                continue;
            }
            refuel(bay, g, dt);
            if (g.fuel <= 0) {
                g.status = "<red>No fuel";
                alerts++;
                continue;
            }
            g.fuel = Math.max(0, g.fuel - dt);
            supply += output;
            g.running = true;
            g.lastEfficiency = 1;
            g.status = "<green>Burning";
        }
        double ratioP = demand <= 0 ? 1 : Math.min(1, supply / demand);

        for (MachineData p : activePumps) {
            MachineDef def = c.machine(p.type);
            if (ratioP <= 0) {
                p.status = "<red>No power";
                alerts++;
                continue;
            }
            coolSupply += def.speed() * c.tier(p.tier).speed() * ratioP;
            p.running = true;
            p.lastEfficiency = ratioP;
            p.status = ratioP < 1 ? "<yellow>Low power (" + pct(ratioP) + ")" : "<green>Pumping";
        }
        double ratioC = coolDemand <= 0 ? 1 : Math.min(1, coolSupply / coolDemand);

        for (MachineData m : wanting) {
            MachineDef def = c.machine(m.type);
            Tier t = c.tier(m.tier);
            double eff = ratioP * (def.coolant() > 0 ? ratioC : 1);
            if (eff <= 0) {
                m.status = ratioP <= 0 ? "<red>No power" : "<red>No coolant";
                alerts++;
                continue;
            }
            m.running = true;
            m.lastEfficiency = eff;
            String suffix = eff < 1 ? (ratioP < 1 ? " <yellow>(low power " + pct(eff) + ")" : " <yellow>(low coolant " + pct(eff) + ")") : "";
            switch (def.role()) {
                case PROCESSOR -> {
                    Recipe r = chosen.get(m);
                    m.progress += dt * eff * t.speed() * def.speed() / Math.max(0.05, r.time());
                    int crafts = 0;
                    while (m.progress >= 1 && r != null && m.outputTotal() < c.outputBuffer) {
                        craft(bay, c, m, def, t, r);
                        m.progress -= 1;
                        crafts++;
                        if (crafts > 200) break;
                        r = pick(c, m);
                    }
                    if (r == null || m.outputTotal() >= c.outputBuffer) m.progress = Math.min(m.progress, 0.99);
                    WorkshopConfig.Good g = c.goods.get(chosen.get(m).output());
                    m.status = "<green>Making " + (g == null ? chosen.get(m).output() : g.name()) + suffix;
                }
                case SOURCE -> pull(bay, c, m, def, t, dt * eff, suffix);
                case CHUTE -> {
                    for (Map.Entry<String, Long> e : new ArrayList<>(m.input.entrySet())) {
                        hooks.chuteSale(bay, e.getKey(), e.getValue());
                    }
                    m.input.clear();
                    m.status = "<green>Selling" + suffix;
                }
                default -> { }
            }
        }

        // Heat: running machines heat up (more when packed together), everything cools, radiators help.
        for (MachineData m : d.machines) {
            MachineDef def = c.machine(m.type);
            if (def == null) continue;
            Tier t = c.tier(m.tier);
            double gain = 0;
            int runningNeighbours = 0;
            double radiatorCooling = 0;
            for (BlockFace f : SIDES) {
                MachineData n = bay.machineAt(m.x + f.getModX(), m.y + f.getModY(), m.z + f.getModZ());
                if (n == null) continue;
                MachineDef nd = c.machine(n.type);
                if (nd == null) continue;
                if (nd.role() == Role.RADIATOR && n.enabled) radiatorCooling += nd.speed() * c.tier(n.tier).speed();
                else if (n.running) runningNeighbours++;
            }
            if (m.running && def.heat() > 0) {
                gain = def.heat() * t.heat() * m.lastEfficiency * (1 + c.neighbourHeat * runningNeighbours);
            }
            m.heat = Math.max(0, Math.min(c.heatMax, m.heat + (gain - c.baseCooling - radiatorCooling) * dt));
            if (m.heat >= c.heatMax && !m.overheated) {
                m.overheated = true;
                m.progress = 0;
            } else if (m.overheated && m.heat <= c.heatResume) {
                m.overheated = false;
            }
        }

        // Push finished goods forward.
        for (MachineData m : d.machines) {
            if (m.output.isEmpty()) continue;
            MachineData target = frontTarget(bay, m);
            if (target == null) continue;
            double budget = c.transfer * c.tier(m.tier).speed() * dt;
            for (Map.Entry<String, Long> e : new ArrayList<>(m.output.entrySet())) {
                if (budget < 1) break;
                long n = Math.min(e.getValue(), Math.min((long) budget, accepts(bay, c, target, e.getKey())));
                if (n <= 0) continue;
                BayData.take(m.output, e.getKey(), n);
                deliver(bay, target, e.getKey(), n);
                budget -= n;
            }
        }

        bay.powerSupply = supply;
        bay.powerDemand = demand;
        bay.coolantSupply = coolSupply;
        bay.coolantDemand = coolDemand;
        bay.alerts = alerts;
    }

    // ---- recipes ----

    /** Next recipe whose inputs are all buffered, round-robin so mixed inputs all get processed. */
    private Recipe pick(WorkshopConfig c, MachineData m) {
        List<Recipe> rs = c.recipesFor(m.type);
        if (rs.isEmpty() || m.input.isEmpty()) return null;
        int n = rs.size();
        for (int i = 0; i < n; i++) {
            int idx = Math.floorMod(m.rr + i, n);
            Recipe r = rs.get(idx);
            if (satisfied(m, r)) {
                m.rr = idx;
                return r;
            }
        }
        return null;
    }

    private static boolean satisfied(MachineData m, Recipe r) {
        for (Map.Entry<String, Integer> e : r.inputs().entrySet()) {
            if (BayData.countBase(m.input, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    private void craft(Bay bay, WorkshopConfig c, MachineData m, MachineDef def, Tier t, Recipe r) {
        int units = 0, qualitySum = 0;
        for (Map.Entry<String, Integer> e : r.inputs().entrySet()) {
            long need = e.getValue();
            // Use the lowest quality first.
            for (int q = 0; q < c.qualities.size() && need > 0; q++) {
                long took = BayData.take(m.input, BayData.key(e.getKey(), q), need);
                need -= took;
                units += (int) took;
                qualitySum += (int) took * q;
            }
        }
        double avgQ = units == 0 ? 0 : (double) qualitySum / units;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        long amount = (long) Math.floor(r.outputAmount());
        if (rng.nextDouble() < r.outputAmount() - amount) amount++;
        int quality = 0;
        if (def.quality()) {
            double pristine = t.pristine() + 0.15 * avgQ;
            double refined = t.refined() + 0.25 * avgQ;
            double roll = rng.nextDouble();
            quality = roll < pristine ? 2 : roll < pristine + refined ? 1 : 0;
            quality = Math.min(quality, c.qualities.size() - 1);
        }
        if (amount > 0) {
            String key = BayData.key(r.output(), quality);
            BayData.add(m.output, key, amount);
            bay.data.lifetimeCrafted += amount;
            bay.reportCrafted += amount;
            hooks.crafted(bay, key, amount);
        }
        Byproduct bp = c.byproducts.get(m.type);
        if (bp != null && rng.nextDouble() < bp.chance()) {
            if (hooks.lockerCapacity(bay) - bay.data.lockerUsed() > 0) BayData.add(bay.data.locker, bp.id(), 1);
            else BayData.add(m.output, bp.id(), 1);
        }
        m.rr = m.rr + 1;
    }

    // ---- sources and fuel ----

    private void pull(Bay bay, WorkshopConfig c, MachineData m, MachineDef def, Tier t, double seconds, String suffix) {
        MachineData target = frontTarget(bay, m);
        if (target == null) return;
        Map<String, Long> from = "locker".equals(m.source) ? bay.data.locker : hooks.cargo(bay.owner);
        m.progress = Math.min(m.progress + def.speed() * t.speed() * seconds, Math.max(1, def.speed() * t.speed() * 10));
        long budget = (long) Math.floor(m.progress);
        if (budget <= 0) {
            m.status = "<green>Pulling" + suffix;
            return;
        }
        long moved = 0;
        for (Map.Entry<String, Long> e : new ArrayList<>(from.entrySet())) {
            if (budget <= 0) break;
            String key = e.getKey();
            if (!"any".equals(m.filter) && !BayData.base(key).equals(m.filter)) continue;
            long n = Math.min(Math.min(budget, e.getValue()), accepts(bay, c, target, key));
            if (n <= 0) continue;
            long got = "locker".equals(m.source) ? BayData.take(bay.data.locker, key, n) : hooks.cargoWithdraw(bay.owner, key, n);
            if (got <= 0) continue;
            deliver(bay, target, key, got);
            budget -= got;
            moved += got;
        }
        m.progress -= moved;
        if (moved == 0) {
            m.progress = Math.min(m.progress, 1);
            m.running = false;
            m.status = "<gray>Nothing to pull" + ("any".equals(m.filter) ? " that the next machine takes" : "");
        } else {
            m.status = "<green>Pulling" + suffix;
        }
    }

    private void refuel(Bay bay, MachineData g, double dt) {
        WorkshopConfig c = cfg.get();
        int guard = 0;
        while (g.fuel < dt && guard++ < 64) {
            String key = firstFuel(c, g.input);
            Map<String, Long> from = g.input;
            if (key == null) {
                key = firstFuel(c, bay.data.locker);
                from = bay.data.locker;
            }
            if (key == null) return;
            BayData.take(from, key, 1);
            g.fuel += c.fuels.getOrDefault(BayData.base(key), 0.0);
        }
    }

    private static String firstFuel(WorkshopConfig c, Map<String, Long> m) {
        String best = null;
        double bestBurn = Double.MAX_VALUE;
        for (String k : m.keySet()) {
            Double burn = c.fuels.get(BayData.base(k));
            // Burn the cheapest fuel first so fuel cells are saved for when nothing else is left.
            if (burn != null && burn < bestBurn) {
                best = k;
                bestBurn = burn;
            }
        }
        return best;
    }

    // ---- logistics ----

    /** The machine this one pushes into: the block in front, following conveyor lines. Null if none. */
    MachineData frontTarget(Bay bay, MachineData m) {
        WorkshopConfig c = cfg.get();
        BlockFace dir = BayWorld.face(m.facing);
        int x = m.x + dir.getModX(), y = m.y, z = m.z + dir.getModZ();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i <= c.maxConveyor; i++) {
            MachineData t = bay.machineAt(x, y, z);
            if (t != null) return t == m ? null : t;
            String flow = bay.conveyorAt(x, y, z);
            if (flow == null || !seen.add(BayData.pos(x, y, z))) return null;
            BlockFace f = BayWorld.face(flow);
            x += f.getModX();
            z += f.getModZ();
        }
        return null;
    }

    /** How many units of this item the target can take right now. */
    long accepts(Bay bay, WorkshopConfig c, MachineData target, String key) {
        MachineDef def = c.machine(target.type);
        if (def == null || !target.enabled) return 0;
        String base = BayData.base(key);
        return switch (def.role()) {
            case PROCESSOR -> c.consumes(target.type, base) ? Math.max(0, c.inputBuffer - BayData.countBase(target.input, base)) : 0;
            case GENERATOR -> c.isFuel(base) ? Math.max(0, c.inputBuffer - BayData.countBase(target.input, base)) : 0;
            case DEPOT -> Math.max(0, hooks.lockerCapacity(bay) - bay.data.lockerUsed());
            case CHUTE -> market.buys(key) ? Math.max(0, c.inputBuffer * 4L - target.inputTotal()) : 0;
            default -> 0;
        };
    }

    private void deliver(Bay bay, MachineData target, String key, long n) {
        MachineDef def = cfg.get().machine(target.type);
        if (def != null && def.role() == Role.DEPOT) BayData.add(bay.data.locker, key, n);
        else BayData.add(target.input, key, n);
    }

    private static String pct(double v) { return Math.round(v * 100) + "%"; }
}
