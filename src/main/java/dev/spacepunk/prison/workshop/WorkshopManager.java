package dev.spacepunk.prison.workshop;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.ObjectiveTypes;
import dev.spacepunk.prison.api.Objectives;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.ScrapService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.TokenDrops;
import dev.spacepunk.prison.api.WorkshopService;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import dev.spacepunk.prison.workshop.BayData.MachineData;
import dev.spacepunk.prison.workshop.PlotRegistry.Plot;
import dev.spacepunk.prison.workshop.WorkshopConfig.Assembly;
import dev.spacepunk.prison.workshop.WorkshopConfig.BayLevel;
import dev.spacepunk.prison.workshop.WorkshopConfig.Decor;
import dev.spacepunk.prison.workshop.WorkshopConfig.MachineDef;
import dev.spacepunk.prison.workshop.WorkshopConfig.Role;
import dev.spacepunk.prison.workshop.WorkshopConfig.Tier;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The workshop's state and rules: loaded bays, buying, placing, upgrading, expanding, selling.
 * Menus, commands and listeners call into this. Implements {@link WorkshopService} for other modules.
 * Main thread only.
 */
final class WorkshopManager implements WorkshopService, Simulator.Hooks {
    static final String DATA_KEY = "workshop";
    static final String INCOME_REASON = "income:workshop";
    private static final Gson GSON = new Gson();

    final Plugin plugin;
    private final Supplier<WorkshopConfig> cfg;
    final PlotRegistry plots;
    final BayWorld world;
    final WorkshopItems items;
    final Market market;
    final Simulator sim;
    private final Map<UUID, BayData> loaded = new HashMap<>();
    private final Map<UUID, Bay> bays = new HashMap<>();
    /** Bays being built right now (avoid double builds). */
    private final java.util.Set<Integer> building = new java.util.HashSet<>();

    WorkshopManager(Plugin plugin, Supplier<WorkshopConfig> cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.plots = new PlotRegistry(plugin);
        this.world = new BayWorld(plugin, cfg);
        this.items = new WorkshopItems(plugin, cfg, this::rawName);
        this.market = new Market(plugin, cfg);
        this.sim = new Simulator(cfg, this, market);
    }

    WorkshopConfig cfg() { return cfg.get(); }

    // ---- services (looked up lazily) ----

    private static EconomyService economy() { return Services.get(EconomyService.class); }

    private static ProfileService profiles() { return Services.get(ProfileService.class); }

    private static Optional<ProgressionService> progression() { return Services.find(ProgressionService.class); }

    int grade(UUID p) { return progression().map(s -> s.grade(p)).orElse(1); }

    String rawName(String id) {
        return progression().map(s -> s.itemName(id)).orElseGet(() -> {
            String k = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            String[] parts = k.split("_");
            StringBuilder sb = new StringBuilder();
            for (String s : parts) if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
            return sb.toString().trim();
        });
    }

    // ---- loading and saving ----

    void load(PlayerProfile profile) {
        BayData d = null;
        String json = profile.moduleData(DATA_KEY);
        if (json != null) {
            try {
                d = GSON.fromJson(json, BayData.class);
            } catch (JsonSyntaxException e) {
                plugin.getLogger().warning("Bad workshop data for " + profile.uuid() + ", starting a fresh bay record: " + e.getMessage());
            }
        }
        if (d == null) d = new BayData();
        sanitize(d);
        loaded.put(profile.uuid(), d);
        Plot plot = plots.get(profile.uuid());
        if (plot != null) bays.put(profile.uuid(), new Bay(profile.uuid(), plot, d));
    }

    private static void sanitize(BayData d) {
        if (d.machines == null) d.machines = new ArrayList<>();
        if (d.locker == null) d.locker = new LinkedHashMap<>();
        if (d.bought == null) d.bought = new HashMap<>();
        if (d.conveyors == null) d.conveyors = new HashMap<>();
        d.locker.values().removeIf(v -> v == null || v <= 0);
        for (MachineData m : d.machines) {
            if (m.input == null) m.input = new LinkedHashMap<>();
            if (m.output == null) m.output = new LinkedHashMap<>();
            if (m.facing == null) m.facing = "NORTH";
            if (m.source == null) m.source = "cargo";
            if (m.filter == null) m.filter = "any";
            if (m.status == null) m.status = "";
        }
    }

    /** Writes the bay into the profile (the profile autosave puts it on disk). */
    void save(UUID owner) {
        BayData d = loaded.get(owner);
        if (d == null) return;
        profiles().get(owner).ifPresent(p -> {
            p.moduleData(DATA_KEY, GSON.toJson(d));
            profiles().markDirty(owner);
        });
    }

    void saveAll() { for (UUID u : new ArrayList<>(loaded.keySet())) save(u); }

    void unload(UUID owner) {
        save(owner);
        loaded.remove(owner);
        bays.remove(owner);
    }

    /** Offline catch-up after login: run the bay for the time away, at reduced speed. */
    void catchUp(Player player) {
        Bay bay = bays.get(player.getUniqueId());
        if (bay == null || bay.data.lastTick <= 0 || bay.data.machines.isEmpty()) return;
        WorkshopConfig c = cfg();
        double away = (System.currentTimeMillis() - bay.data.lastTick) / 1000.0;
        double seconds = Math.min(away, c.offlineHours * 3600) * c.offlineEfficiency;
        if (seconds < 30) return;
        bay.reportCrafted = 0;
        bay.reportSold = 0;
        int steps = (int) Math.min(5000, Math.ceil(seconds / 5.0));
        double dt = seconds / steps;
        long start = System.nanoTime();
        try {
            for (int i = 0; i < steps; i++) sim.tick(bay, dt);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Workshop catch-up failed for " + player.getName(), e);
        }
        payPending(bay);
        bay.data.lastTick = System.currentTimeMillis();
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (ms > 50) plugin.getLogger().info("Workshop catch-up for " + player.getName() + " took " + ms + " ms");
        if (bay.reportCrafted > 0 || bay.reportSold > 0) {
            Text.send(player, "<gray>While you were away your bay made <white>" + Numbers.shortFormat(bay.reportCrafted)
                    + "</white> goods" + (bay.reportSold > 0 ? " and its Trade Chutes earned " + economy().format(Currency.CREDITS, bay.reportSold) : "")
                    + ". <dark_gray>(/ws)");
        }
        bay.reportCrafted = 0;
        bay.reportSold = 0;
    }

    /** One simulation second for every loaded bay. */
    void tickAll() {
        long now = System.currentTimeMillis();
        for (Bay bay : bays.values()) {
            try {
                sim.tick(bay, 1.0);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Workshop tick failed for bay " + bay.plot.index, e);
            }
            payPending(bay);
            bay.data.lastTick = now;
        }
    }

    Bay bay(UUID owner) { return bays.get(owner); }

    BayData data(UUID owner) { return loaded.get(owner); }

    Bay bayAt(Location loc) {
        if (loc == null || !world.isWorld(loc.getWorld())) return null;
        int idx = world.indexAt(loc.getBlockX(), loc.getBlockZ());
        Plot p = idx < 0 ? null : plots.at(idx);
        return p == null ? null : bays.get(p.ownerId());
    }

    Plot plotAt(Location loc) {
        if (loc == null || !world.isWorld(loc.getWorld())) return null;
        int idx = world.indexAt(loc.getBlockX(), loc.getBlockZ());
        return idx < 0 ? null : plots.at(idx);
    }

    long lockerCapacityOf(Plot p) { return cfg().level(p.level).locker(); }

    // ---- Simulator hooks ----

    @Override public long lockerCapacity(Bay bay) { return lockerCapacityOf(bay.plot); }

    @Override public Map<String, Long> cargo(UUID owner) { return progression().map(s -> s.cargoContents(owner)).orElse(Map.of()); }

    @Override public long cargoWithdraw(UUID owner, String id, long amount) {
        return progression().map(s -> s.cargoWithdraw(owner, id, amount)).orElse(0L);
    }

    @Override public void chuteSale(Bay bay, String key, long n) {
        double value = market.sell(key, n) * cfg().chuteRate * sellMultiplier(bay.owner);
        bay.pendingCredits += value;
    }

    @Override public void crafted(Bay bay, String key, long n) {
        Services.find(GangService.class).ifPresent(g -> g.addContractProgress(bay.owner, GANG_OBJECTIVE, n));
        Player owner = Bukkit.getPlayer(bay.owner);
        if (owner != null) {
            Objectives.report(owner, ObjectiveTypes.PROCESS_ITEM, n, key);
            tokenFind(owner, bay);
        }
    }

    /** Uncommon Token find while the owner is online and their bay is processing (never during offline catch-up). */
    private void tokenFind(Player owner, Bay bay) {
        WorkshopConfig c = cfg();
        long now = System.currentTimeMillis();
        if (c.tokenChance <= 0 || now - bay.lastTokenRoll < c.tokenRollSeconds * 1000L) return;
        bay.lastTokenRoll = now;
        // Scrap parts shaken loose from the machines: rolled on the same once-a-minute clock.
        dev.spacepunk.prison.api.ScrapDrops.roll(owner, dev.spacepunk.prison.api.ScrapService.WORKSHOP, 1);
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= c.tokenChance) return;
        long amount = TokenDrops.roll(owner.getUniqueId(), c.tokenMin, c.tokenMax);
        economy().give(owner.getUniqueId(), Currency.TOKENS, amount, "workshop:token-find");
        owner.playSound(owner.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
        Text.send(owner, "<aqua>✦ Token find</aqua> <gray>scraped out of your bay's machines: <aqua>+"
                + Numbers.shortFormat(amount) + " Tokens");
    }

    static final String GANG_OBJECTIVE = "goods_processed";

    private void payPending(Bay bay) {
        if (bay.pendingCredits < 1) return;
        long pay = (long) Math.floor(bay.pendingCredits);
        bay.pendingCredits -= pay;
        if (profiles().get(bay.owner).isEmpty()) return;
        economy().give(bay.owner, Currency.CREDITS, pay, INCOME_REASON);
        bay.data.lifetimeSales += pay;
        bay.reportSold += pay;
    }

    double sellMultiplier(UUID owner) { return progression().map(s -> s.sellMultiplier(owner)).orElse(1.0); }

    // ---- entering ----

    /** Claims (and builds) the player's bay, then teleports them in. */
    void enter(Player p) {
        BayData d = loaded.get(p.getUniqueId());
        if (d == null) {
            Text.send(p, "<red>Your profile is still loading.");
            return;
        }
        Plot plot = ensureBay(p);
        if (!plot.built) {
            if (!building.add(plot.index)) return;
            Text.send(p, "<gray>Pressurising your hull bay...");
            world.build(plot, -1).whenComplete((v, err) -> {
                building.remove(plot.index);
                if (err != null) {
                    plugin.getLogger().log(Level.SEVERE, "Could not build bay " + plot.index, err);
                    Text.send(p, "<red>Your bay failed to build. Tell staff.");
                    return;
                }
                plots.save();
                teleportIn(p, plot, true);
            });
            return;
        }
        teleportIn(p, plot, false);
    }

    Plot ensureBay(Player p) {
        Plot plot = plots.claim(p.getUniqueId(), p.getName());
        if (!bays.containsKey(p.getUniqueId()) && loaded.containsKey(p.getUniqueId())) {
            bays.put(p.getUniqueId(), new Bay(p.getUniqueId(), plot, loaded.get(p.getUniqueId())));
        }
        return plot;
    }

    private void teleportIn(Player p, Plot plot, boolean first) {
        if (!p.isOnline()) return;
        p.teleportAsync(world.spawn(plot)).thenAccept(ok -> {
            if (!ok) return;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Bay bay = bays.get(plot.ownerId());
                if (bay != null) reconcile(bay);
                p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 0.7f);
                if (first) {
                    Text.send(p, "<#c8743a>Welcome to your hull bay.</#c8743a> <gray>Right-click the terminal on the wall for the workshop menu.");
                    Text.send(p, "<gray>Start with a <white>Cargo Intake</white> feeding a <white>Jaw Crusher</white> feeding a <white>Depot Hatch</white>, plus a <white>Slag Burner</white> for power.");
                } else {
                    Text.send(p, "<gray>Entered your hull bay. <dark_gray>/ws menu");
                }
            }, 2L);
        });
    }

    /** Sends a visitor to someone's bay if it is open to them. */
    void visit(Player p, Plot plot) {
        if (!plot.built) {
            Text.send(p, "<red>That bay hasn't been built yet.");
            return;
        }
        if (!canEnter(p, plot)) {
            Text.send(p, "<red>" + plot.name + "'s bay is sealed. They can open it with /ws open or add you as crew.");
            return;
        }
        p.teleportAsync(world.spawn(plot)).thenAccept(ok -> {
            if (ok) Text.send(p, "<gray>Visiting <white>" + plot.name + "</white>'s hull bay.");
        });
    }

    boolean canEnter(Player p, Plot plot) {
        return plot.ownerId().equals(p.getUniqueId()) || plot.open || plot.isCrew(p.getUniqueId()) || p.hasPermission("spacepunk.admin");
    }

    boolean canBuild(Player p, Plot plot) {
        return plot.ownerId().equals(p.getUniqueId()) || plot.isCrew(p.getUniqueId()) || p.hasPermission("spacepunk.workshop.bypass");
    }

    /** Fixes blocks/displays/conveyors for a bay whose chunks are loaded (someone is standing in it). */
    void reconcile(Bay bay) {
        world.reconcile(bay.owner, bay.plot, bay.data, m -> items.displayModel(m.type));
        bay.data.conveyors.entrySet().removeIf(e -> {
            String[] xyz = e.getKey().split(",");
            try {
                Block b = world.world().getBlockAt(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                return b.getType() != Material.REPEATER;
            } catch (RuntimeException ex) {
                return true;
            }
        });
    }

    // ---- machines ----

    long machinePrice(UUID owner, MachineDef def) {
        BayData d = loaded.get(owner);
        int n = d == null ? 0 : d.bought.getOrDefault(def.id(), 0);
        return Math.round(def.cost() * Math.pow(cfg().costGrowth, n));
    }

    void buyMachine(Player p, MachineDef def) {
        BayData d = loaded.get(p.getUniqueId());
        if (d == null) return;
        if (grade(p.getUniqueId()) < def.grade()) {
            Gui.deny(p);
            Text.send(p, "<red>" + def.name() + " needs Grade " + def.grade() + ".");
            return;
        }
        long price = machinePrice(p.getUniqueId(), def);
        if (!economy().take(p.getUniqueId(), Currency.CREDITS, price, "workshop:machine")) {
            Gui.deny(p);
            Text.send(p, "<red>You need " + economy().format(Currency.CREDITS, price) + ".");
            return;
        }
        d.bought.merge(def.id(), 1, Integer::sum);
        give(p, items.machine(def, 0, 1));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        Text.send(p, "<gray>Bought a <white>" + def.name() + "</white>. Place it in your bay.");
        save(p.getUniqueId());
    }

    void buyConveyors(Player p) {
        WorkshopConfig c = cfg();
        if (!economy().take(p.getUniqueId(), Currency.CREDITS, c.conveyorPrice, "workshop:conveyor")) {
            Gui.deny(p);
            Text.send(p, "<red>You need " + economy().format(Currency.CREDITS, c.conveyorPrice) + ".");
            return;
        }
        give(p, items.conveyor(c.conveyorBundle));
        Gui.click(p);
    }

    void buyDecor(Player p, Decor decor) {
        if (!economy().take(p.getUniqueId(), Currency.CREDITS, decor.price(), "workshop:decor")) {
            Gui.deny(p);
            Text.send(p, "<red>You need " + economy().format(Currency.CREDITS, decor.price()) + ".");
            return;
        }
        give(p, new ItemStack(decor.material(), decor.amount()));
        Gui.click(p);
    }

    /** Places a machine item at the target block. Returns true if placed (caller removes one item). */
    boolean place(Player p, Block target, String type, int tier, BlockFace facing) {
        Plot plot = plotAt(target.getLocation());
        Bay bay = bays.get(p.getUniqueId());
        if (plot == null || bay == null || !plot.ownerId().equals(p.getUniqueId())) {
            Text.send(p, "<red>Machines can only be placed in your own bay.");
            return false;
        }
        if (!world.inInterior(plot, target.getX(), target.getY(), target.getZ())) {
            Text.send(p, "<red>That spot is outside your bay.");
            return false;
        }
        if (!target.getType().isAir()) {
            Text.send(p, "<red>That spot is taken.");
            return false;
        }
        if (!target.getWorld().getNearbyEntities(org.bukkit.util.BoundingBox.of(target).expand(-0.01), e -> e instanceof Player).isEmpty()) {
            Text.send(p, "<red>Someone is standing there.");
            return false;
        }
        MachineDef def = cfg().machine(type);
        if (def == null) return false;
        BayLevel lv = cfg().level(plot.level);
        if (bay.machineCount() >= lv.slots()) {
            Text.send(p, "<red>Your bay holds " + lv.slots() + " machines. Expand it in the workshop menu.");
            return false;
        }
        MachineData m = new MachineData();
        m.id = bay.data.nextMachineId++;
        m.type = type;
        m.tier = Math.max(0, Math.min(cfg().tiers.size() - 1, tier));
        m.x = target.getX();
        m.y = target.getY();
        m.z = target.getZ();
        m.facing = horizontal(facing).name();
        bay.data.machines.add(m);
        bay.reindex();
        target.setType(Material.BARRIER, false);
        world.spawnDisplay(bay.owner, m, items.displayModel(type));
        p.playSound(target.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.4f);
        showOutput(p, m);
        save(p.getUniqueId());
        Objectives.report(p, ObjectiveTypes.MACHINE_UPGRADE, 1, "machine:" + type, "placed");
        return true;
    }

    void pickup(Player p, Bay bay, MachineData m) {
        if (!bay.owner.equals(p.getUniqueId())) {
            Text.send(p, "<red>Only the bay owner can remove machines.");
            return;
        }
        // Buffers go to the locker (anything that doesn't fit is lost, so warn first via the menu lore).
        for (Map<String, Long> buf : List.of(m.input, m.output)) {
            for (Map.Entry<String, Long> e : buf.entrySet()) lockerAddClamped(bay, e.getKey(), e.getValue());
        }
        bay.data.machines.remove(m);
        bay.reindex();
        world.removeDisplay(m);
        Block b = world.machineBlock(m);
        if (b.getType() == Material.BARRIER) b.setType(Material.AIR, false);
        MachineDef def = cfg().machine(m.type);
        if (def != null) give(p, items.machine(def, m.tier, 1));
        p.closeInventory();
        p.playSound(b.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.8f, 0.8f);
        save(p.getUniqueId());
    }

    void rotate(Player p, Bay bay, MachineData m) {
        BlockFace f = switch (BayWorld.face(m.facing)) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
        m.facing = f.name();
        world.updateDisplay(m, items.displayModel(m.type));
        showOutput(p, m);
        save(bay.owner);
    }

    /** Credits for the next tier of this machine, or -1 at max. */
    long upgradeCost(MachineData m) {
        MachineDef def = cfg().machine(m.type);
        if (def == null || m.tier + 1 >= cfg().tiers.size()) return -1;
        return Math.round(def.cost() * cfg().tier(m.tier + 1).costMultiplier());
    }

    void upgrade(Player p, Bay bay, MachineData m) {
        long cost = upgradeCost(m);
        if (cost < 0) return;
        Tier next = cfg().tier(m.tier + 1);
        for (Map.Entry<String, Long> e : next.components().entrySet()) {
            if (BayData.countBase(bay.data.locker, e.getKey()) < e.getValue()) {
                Gui.deny(p);
                Text.send(p, "<red>Needs " + e.getValue() + "x " + items.name(e.getKey()) + " in your Bay Locker.");
                return;
            }
        }
        if (!economy().take(p.getUniqueId(), Currency.CREDITS, cost, "workshop:upgrade")) {
            Gui.deny(p);
            Text.send(p, "<red>You need " + economy().format(Currency.CREDITS, cost) + ".");
            return;
        }
        for (Map.Entry<String, Long> e : next.components().entrySet()) lockerTakeBase(bay.data, e.getKey(), e.getValue());
        m.tier++;
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.1f);
        Text.send(p, "<gray>Upgraded to <white>" + next.name() + "</white>.");
        save(bay.owner);
    }

    void showOutput(Player p, MachineData m) {
        BlockFace f = BayWorld.face(m.facing);
        Location from = new Location(world.world(), m.x + 0.5, m.y + 0.5, m.z + 0.5);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0xE0A050), 0.9f);
        for (int i = 1; i <= 6; i++) {
            Location at = from.clone().add(f.getModX() * (0.4 + i * 0.12), 0, f.getModZ() * (0.4 + i * 0.12));
            p.spawnParticle(Particle.DUST, at, 2, 0.02, 0.02, 0.02, 0, dust);
        }
    }

    static BlockFace horizontal(BlockFace f) {
        return switch (f) {
            case NORTH, EAST, SOUTH, WEST -> f;
            default -> BlockFace.NORTH;
        };
    }

    // ---- conveyors ----

    void conveyorPlaced(Block b, BlockFace flow) {
        Bay bay = bayAt(b.getLocation());
        if (bay != null) bay.data.conveyors.put(BayData.pos(b.getX(), b.getY(), b.getZ()), horizontal(flow).name());
    }

    void conveyorRemoved(Block b) {
        Bay bay = bayAt(b.getLocation());
        if (bay != null) bay.data.conveyors.remove(BayData.pos(b.getX(), b.getY(), b.getZ()));
    }

    // ---- expansion ----

    void expand(Player p) {
        Plot plot = plots.get(p.getUniqueId());
        Bay bay = bays.get(p.getUniqueId());
        if (plot == null || bay == null) return;
        if (plot.level >= cfg().maxLevel()) {
            Text.send(p, "<gray>Your bay is already as big as it gets.");
            return;
        }
        if (!building.add(plot.index)) return;
        BayLevel next = cfg().level(plot.level + 1);
        UUID u = p.getUniqueId();
        if (grade(u) < next.grade()) {
            building.remove(plot.index);
            Gui.deny(p);
            Text.send(p, "<red>The next expansion needs Grade " + next.grade() + ".");
            return;
        }
        EconomyService eco = economy();
        if (eco.balance(u, Currency.CREDITS) < next.credits() || eco.balance(u, Currency.SHARDS) < next.shards()) {
            building.remove(plot.index);
            Gui.deny(p);
            Text.send(p, "<red>The next expansion costs " + costText(next) + ".");
            return;
        }
        eco.take(u, Currency.CREDITS, next.credits(), "workshop:expand");
        if (next.shards() > 0) eco.take(u, Currency.SHARDS, next.shards(), "workshop:expand");
        int prev = plot.level;
        plot.level++;
        plots.save();
        p.closeInventory();
        Text.send(p, "<gray>Cutting through the bulkheads...");
        world.build(plot, prev).whenComplete((v, err) -> {
            building.remove(plot.index);
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Bay expansion failed for " + p.getName(), err);
                return;
            }
            // Anyone standing in the old walls gets moved to the new spawn.
            for (Player o : world.world().getPlayers()) {
                Plot at = plotAt(o.getLocation());
                if (at == plot && !world.inInterior(plot, o.getLocation().getBlockX(), o.getLocation().getBlockY(), o.getLocation().getBlockZ())) {
                    o.teleportAsync(world.spawn(plot));
                }
            }
            if (p.isOnline()) {
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
                Text.send(p, "<green>Bay expanded to " + next.size() + "x" + next.size() + "<gray>, " + next.slots() + " machine slots.");
            }
        });
    }

    String costText(BayLevel l) {
        String s = economy().format(Currency.CREDITS, l.credits());
        if (l.shards() > 0) s += " <gray>+</gray> " + economy().format(Currency.SHARDS, l.shards());
        return s;
    }

    // ---- crew / access ----

    void setOpen(Player p, boolean open) {
        Plot plot = plots.get(p.getUniqueId());
        if (plot == null) {
            Text.send(p, "<red>You don't have a bay yet. /ws");
            return;
        }
        plot.open = open;
        plots.save();
        Text.send(p, open ? "<green>Your bay is open to visitors (/ws visit " + p.getName() + ")." : "<gray>Your bay is sealed. Only crew can enter.");
    }

    void crew(Player p, UUID other, String name, boolean add) {
        Plot plot = plots.get(p.getUniqueId());
        if (plot == null) {
            Text.send(p, "<red>You don't have a bay yet. /ws");
            return;
        }
        if (other.equals(p.getUniqueId())) return;
        if (add) plot.crew.add(other.toString()); else plot.crew.remove(other.toString());
        plots.save();
        Text.send(p, add ? "<green>" + name + " is now crew: they can enter and build in your bay." : "<gray>" + name + " is no longer crew.");
        Player o = Bukkit.getPlayer(other);
        if (o != null) Text.send(o, add ? "<gray>" + p.getName() + " added you as crew on their hull bay. <white>/ws visit " + p.getName() : "<gray>" + p.getName() + " removed you from their bay crew.");
        if (!add && o != null && plotAt(o.getLocation()) == plot && !plot.open) o.teleportAsync(fallbackLocation(o));
    }

    /** Where to send someone kicked out of a bay: their own bay if they have one, else the main world spawn. */
    Location fallbackLocation(Player o) {
        Plot own = plots.get(o.getUniqueId());
        if (own != null && own.built) return world.spawn(own);
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    // ---- locker and selling ----

    long lockerAddClamped(Bay bay, String key, long n) {
        long space = lockerCapacityOf(bay.plot) - bay.data.lockerUsed();
        long add = Math.max(0, Math.min(space, n));
        BayData.add(bay.data.locker, key, add);
        return add;
    }

    static long lockerTakeBase(BayData d, String base, long n) {
        long took = 0;
        for (int q = 0; q <= 4 && took < n; q++) took += BayData.take(d.locker, BayData.key(base, q), n - took);
        return took;
    }

    /** Sells n of one locker entry at the market. Returns Credits paid. */
    long sell(Player p, String key, long n) {
        BayData d = loaded.get(p.getUniqueId());
        if (d == null || !market.buys(key)) return 0;
        long have = d.locker.getOrDefault(key, 0L);
        long k = Math.min(have, n);
        if (k <= 0) return 0;
        double value = market.sell(key, k) * sellMultiplier(p.getUniqueId());
        long pay = Math.round(value);
        BayData.take(d.locker, key, k);
        if (pay > 0) economy().give(p.getUniqueId(), Currency.CREDITS, pay, INCOME_REASON);
        d.lifetimeSales += pay;
        save(p.getUniqueId());
        return pay;
    }

    long sellAll(Player p) {
        BayData d = loaded.get(p.getUniqueId());
        if (d == null) return 0;
        long total = 0;
        for (String key : new ArrayList<>(d.locker.keySet())) {
            if (market.buys(key)) total += sell(p, key, Long.MAX_VALUE);
        }
        return total;
    }

    /** Takes up to one stack of an entry out of the locker as items. */
    void withdraw(Player p, String key, int n) {
        BayData d = loaded.get(p.getUniqueId());
        if (d == null) return;
        long have = d.locker.getOrDefault(key, 0L);
        int k = (int) Math.min(have, n);
        if (k <= 0) return;
        ItemStack it = items.good(key, k);
        if (it == null) {
            Text.send(p, "<gray>Raw ore can't be taken out as items. Feed it to a machine with an intake set to the locker.");
            return;
        }
        BayData.take(d.locker, key, k);
        give(p, it);
        Gui.click(p);
        save(p.getUniqueId());
    }

    /** Puts a goods item stack from the player's inventory into the locker. Returns true if anything moved. */
    boolean deposit(Player p, ItemStack stack) {
        Bay bay = bays.get(p.getUniqueId());
        String key = items.goodKey(stack);
        if (bay == null || key == null) return false;
        long added = lockerAddClamped(bay, key, stack.getAmount());
        if (added <= 0) {
            Text.send(p, "<red>Your Bay Locker is full.");
            return false;
        }
        stack.setAmount(stack.getAmount() - (int) added);
        Gui.click(p);
        save(p.getUniqueId());
        return true;
    }

    // ---- assembly ----

    void assemble(Player p, Assembly a) {
        BayData d = loaded.get(p.getUniqueId());
        if (d == null) return;
        DrillService drill = Services.find(DrillService.class).orElse(null);
        ScrapService scrap = Services.find(ScrapService.class).orElse(null);
        if (drill == null || drill.createModuleItem(a.id()) == null) {
            Gui.deny(p);
            Text.send(p, "<red>The assembly line for this is offline right now. Nothing was spent.");
            return;
        }
        String name = drill.moduleName(a.id());
        if (!drill.knowsSchematic(p.getUniqueId(), a.id())) {
            Gui.deny(p);
            Text.send(p, "<red>You don't know the " + name + " <red>schematic. Decode blueprint fragments in <white>/salvage</white><red>.");
            return;
        }
        if (grade(p.getUniqueId()) < a.grade()) {
            Gui.deny(p);
            Text.send(p, "<red>" + name + " <red>needs Grade " + a.grade() + ".");
            return;
        }
        for (Map.Entry<String, Long> e : a.components().entrySet()) {
            if (BayData.countBase(d.locker, e.getKey()) < e.getValue()) {
                Gui.deny(p);
                Text.send(p, "<red>Needs " + e.getValue() + "x " + items.name(e.getKey()) + " in your Bay Locker.");
                return;
            }
        }
        if (!a.parts().isEmpty() && scrap == null) {
            Gui.deny(p);
            Text.send(p, "<red>Scrap parts are offline right now. Nothing was spent.");
            return;
        }
        for (Map.Entry<String, Integer> e : a.parts().entrySet()) {
            if (scrap.count(p, e.getKey(), ScrapService.State.WORKING) < e.getValue()) {
                Gui.deny(p);
                Text.send(p, "<red>Needs " + e.getValue() + "x " + scrap.partName(e.getKey(), ScrapService.State.WORKING)
                        + " <red>(working or pristine) in your inventory.");
                return;
            }
        }
        if (economy().balance(p.getUniqueId(), Currency.CREDITS) < a.credits()) {
            Gui.deny(p);
            Text.send(p, "<red>You need " + economy().format(Currency.CREDITS, a.credits()) + ".");
            return;
        }
        if (a.credits() > 0 && !economy().take(p.getUniqueId(), Currency.CREDITS, a.credits(), "workshop:assembly")) return;
        for (Map.Entry<String, Long> e : a.components().entrySet()) lockerTakeBase(d, e.getKey(), e.getValue());
        for (Map.Entry<String, Integer> e : a.parts().entrySet()) scrap.take(p, e.getKey(), ScrapService.State.WORKING, e.getValue());
        ItemStack module = drill.createModuleItem(a.id());
        if (scrap != null) scrap.give(p, module);
        else for (ItemStack left : p.getInventory().addItem(module).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        d.modulesBuilt++;
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 0.8f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
        Text.send(p, "<green>Assembled " + name + "<green>. Fit it on your drill's module rig.");
        save(p.getUniqueId());
    }

    // ---- refurbisher ----

    WorkshopConfig.RefurbLevel refurbLevel(BayData d) {
        List<WorkshopConfig.RefurbLevel> lv = cfg().refurbLevels;
        return lv.get(Math.max(0, Math.min(lv.size() - 1, d.refurbLevel)));
    }

    /** Repairs fried parts of one kind from the inventory (one, or all of them). Returns how many were repaired. */
    int refurbish(Player p, String partId, boolean all) {
        BayData d = loaded.get(p.getUniqueId());
        ScrapService scrap = Services.find(ScrapService.class).orElse(null);
        if (d == null || scrap == null) return 0;
        WorkshopConfig c = cfg();
        if (grade(p.getUniqueId()) < c.refurbGrade) {
            Gui.deny(p);
            Text.send(p, "<red>The Refurbisher needs Grade " + c.refurbGrade + ".");
            return 0;
        }
        int have = scrap.count(p, partId, ScrapService.State.FRIED) - scrap.count(p, partId, ScrapService.State.WORKING);
        if (have <= 0) return 0;
        ItemStack sample = scrap.create(partId, ScrapService.State.FRIED, 1);
        String tier = sample == null ? "common" : scrap.part(sample).map(ScrapService.Part::tier).orElse("common");
        WorkshopConfig.RefurbCost cost = c.refurbCosts.getOrDefault(tier, new WorkshopConfig.RefurbCost(0, Map.of()));
        int want = all ? have : 1;
        int n = 0, pristine = 0;
        double chance = refurbLevel(d).pristine();
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        String why = null;
        while (n < want) {
            for (Map.Entry<String, Long> e : cost.components().entrySet()) {
                if (BayData.countBase(d.locker, e.getKey()) < e.getValue()) { why = "Needs " + e.getValue() + "x " + items.name(e.getKey()) + " in your Bay Locker."; break; }
            }
            if (why != null) break;
            if (economy().balance(p.getUniqueId(), Currency.CREDITS) < cost.credits()) { why = "You need " + economy().format(Currency.CREDITS, cost.credits()) + "."; break; }
            if (!takeFried(p, scrap, partId)) break;
            if (cost.credits() > 0) economy().take(p.getUniqueId(), Currency.CREDITS, cost.credits(), "workshop:refurbish");
            for (Map.Entry<String, Long> e : cost.components().entrySet()) lockerTakeBase(d, e.getKey(), e.getValue());
            boolean pr = rng.nextDouble() < chance;
            if (pr) pristine++;
            scrap.give(p, scrap.create(partId, pr ? ScrapService.State.PRISTINE : ScrapService.State.WORKING, 1));
            n++;
        }
        if (n == 0) {
            Gui.deny(p);
            if (why != null) Text.send(p, "<red>" + why);
            return 0;
        }
        d.refurbished += n;
        save(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.2f);
        if (pristine > 0) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
        Text.send(p, "<gray>Repaired <white>" + n + "x</white> " + scrap.partName(partId, ScrapService.State.WORKING)
                + (pristine > 0 ? "<gray>, <gold>" + pristine + " came out pristine!" : "<gray>.")
                + (why != null && n < want ? " <red>Stopped: " + why : ""));
        return n;
    }

    /** Takes exactly one fried part (never a working one). */
    private static boolean takeFried(Player p, ScrapService scrap, String partId) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            var part = scrap.part(contents[i]);
            if (part.isEmpty() || !part.get().partId().equals(partId) || part.get().state() != ScrapService.State.FRIED) continue;
            contents[i].setAmount(contents[i].getAmount() - 1);
            if (contents[i].getAmount() <= 0) contents[i] = null;
            p.getInventory().setStorageContents(contents);
            return true;
        }
        return false;
    }

    void upgradeRefurbisher(Player p) {
        BayData d = loaded.get(p.getUniqueId());
        if (d == null) return;
        List<WorkshopConfig.RefurbLevel> lv = cfg().refurbLevels;
        if (d.refurbLevel + 1 >= lv.size()) {
            Gui.deny(p);
            Text.send(p, "<gray>Your Refurbisher is already the top mark.");
            return;
        }
        WorkshopConfig.RefurbLevel next = lv.get(d.refurbLevel + 1);
        if (grade(p.getUniqueId()) < next.grade()) {
            Gui.deny(p);
            Text.send(p, "<red>" + next.name() + " needs Grade " + next.grade() + ".");
            return;
        }
        for (Map.Entry<String, Long> e : next.components().entrySet()) {
            if (BayData.countBase(d.locker, e.getKey()) < e.getValue()) {
                Gui.deny(p);
                Text.send(p, "<red>Needs " + e.getValue() + "x " + items.name(e.getKey()) + " in your Bay Locker.");
                return;
            }
        }
        if (next.credits() > 0 && !economy().take(p.getUniqueId(), Currency.CREDITS, next.credits(), "workshop:refurbisher")) {
            Gui.deny(p);
            Text.send(p, "<red>You need " + economy().format(Currency.CREDITS, next.credits()) + ".");
            return;
        }
        for (Map.Entry<String, Long> e : next.components().entrySet()) lockerTakeBase(d, e.getKey(), e.getValue());
        d.refurbLevel++;
        save(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
        Text.send(p, "<green>Refurbisher upgraded to " + next.name() + ".");
    }

    // ---- HUD ----

    void hud(Player p) {
        Optional<HudService> hud = Services.find(HudService.class);
        if (hud.isEmpty()) return;
        Bay bay = cfg().hud ? bayAt(p.getLocation()) : null;
        if (bay == null) {
            hud.get().segment(p, "workshop", null);
            return;
        }
        String power = bay.powerDemand <= 0 ? "<dark_gray>⚡ idle" :
                (bay.powerSupply >= bay.powerDemand ? "<yellow>" : "<red>") + "⚡ " + WorkshopItems.fmt(bay.powerSupply) + "/" + WorkshopItems.fmt(bay.powerDemand);
        String cool = bay.coolantDemand <= 0 ? "" :
                " " + (bay.coolantSupply >= bay.coolantDemand ? "<aqua>" : "<red>") + "❄ " + WorkshopItems.fmt(bay.coolantSupply) + "/" + WorkshopItems.fmt(bay.coolantDemand);
        String alerts = bay.alerts > 0 ? " <red>⚠ " + bay.alerts : "";
        hud.get().segment(p, "workshop", power + cool + alerts);
    }

    void clearHud(Player p) { Services.find(HudService.class).ifPresent(h -> h.segment(p, "workshop", null)); }

    // ---- helpers ----

    static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    // ---- WorkshopService ----

    @Override public List<String> goodIds() { return List.copyOf(cfg().goods.keySet()); }

    @Override public String itemName(String itemId) { return items.name(itemId); }

    @Override public double baseValue(String itemId) {
        WorkshopConfig.Good g = cfg().goods.get(BayData.base(itemId));
        return g == null ? 0 : g.price() * cfg().quality(BayData.quality(itemId)).price();
    }

    @Override public long lockerAmount(UUID player, String itemId) {
        BayData d = loaded.get(player);
        if (d == null) return 0;
        return itemId.contains("#") ? d.locker.getOrDefault(itemId, 0L) : BayData.countBase(d.locker, itemId);
    }

    @Override public Map<String, Long> lockerContents(UUID player) {
        BayData d = loaded.get(player);
        return d == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(d.locker));
    }

    @Override public long lockerTake(UUID player, String itemId, long amount) {
        BayData d = loaded.get(player);
        if (d == null || amount <= 0) return 0;
        long took = itemId.contains("#") ? BayData.take(d.locker, itemId, amount) : lockerTakeBase(d, itemId, amount);
        if (took > 0) save(player);
        return took;
    }

    @Override public long lockerGive(UUID player, String itemId, long amount) {
        BayData d = loaded.get(player);
        if (d == null || amount <= 0) return 0;
        Plot plot = plots.get(player);
        long cap = plot == null ? cfg().level(0).locker() : lockerCapacityOf(plot);
        long add = Math.max(0, Math.min(amount, cap - d.lockerUsed()));
        BayData.add(d.locker, itemId, add);
        if (add > 0) save(player);
        return add;
    }

    @Override public Optional<ItemStack> createItem(String itemId, int amount) { return Optional.ofNullable(items.good(itemId, amount)); }

    @Override public Optional<String> itemId(ItemStack item) { return Optional.ofNullable(items.goodKey(item)); }

    @Override public boolean isWorkshop(Location location) { return location != null && world.isWorld(location.getWorld()); }

    @Override public Optional<UUID> bayOwnerAt(Location location) {
        Plot p = plotAt(location);
        return p == null ? Optional.empty() : Optional.of(p.ownerId());
    }

    @Override public Location bayLocation(Player player) {
        if (!loaded.containsKey(player.getUniqueId())) return null;
        Plot plot = ensureBay(player);
        if (!plot.built) {
            world.buildSync(plot, -1);
            plots.save();
        }
        return world.spawn(plot);
    }

    @Override public void sendToBay(Player player) { enter(player); }
}
