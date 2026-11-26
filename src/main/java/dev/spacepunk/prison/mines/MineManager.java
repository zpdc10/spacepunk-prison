package dev.spacepunk.prison.mines;

import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.MineBlocksBrokenEvent;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Owns all mines: lookup, breaking, refills, access and the HUD. Main thread only. */
public final class MineManager implements MineService {
    public static final String BYPASS_PERMISSION = "spacepunk.mines.bypass";
    public static final String BUILD_PERMISSION = "spacepunk.mines.build";

    private final Plugin plugin;
    private MinesConfig config;
    private final MineStore store;
    private Refiller refiller;
    private final Map<String, Mine> mines = new LinkedHashMap<>();
    private final Map<UUID, Mine> barShown = new HashMap<>();

    MineManager(Plugin plugin, MinesConfig config, MineStore store, Refiller refiller) {
        this.plugin = plugin;
        this.store = store;
        this.refiller = refiller;
        apply(config);
    }

    // ------------------------------------------------------------------ setup

    /** (Re)builds mines from config + stored placements. Existing HUD bars are hidden. */
    void apply(MinesConfig cfg) {
        hideAllBars();
        this.config = cfg;
        mines.clear();
        for (MinesConfig.MineDef def : cfg.mines().values()) {
            Mine m = new Mine(def, cfg.decks().get(def.deck()));
            Region r = store.region(def.id());
            if (r != null) {
                m.region(r);
                m.spawn(store.spawn(def.id()));
            }
            mines.put(def.id(), m);
        }
    }

    void refiller(Refiller r) { this.refiller = r; }
    Refiller refiller() { return refiller; }
    MinesConfig config() { return config; }
    MineStore store() { return store; }

    public Collection<Mine> mines() { return mines.values(); }

    public Optional<Mine> byId(String id) { return Optional.ofNullable(mines.get(id.toLowerCase(Locale.ROOT))); }

    /** Accepts a mine id or a grade number. */
    public Optional<Mine> resolve(String arg) {
        Optional<Mine> m = byId(arg);
        if (m.isPresent()) return m;
        try {
            int g = Integer.parseInt(arg);
            return mines.values().stream().filter(x -> x.requiredGrade() == g).findFirst();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ MineService

    @Override
    public Optional<MineInfo> mineAt(Block block) {
        return Optional.ofNullable(mineFor(block));
    }

    Mine mineFor(Block block) {
        String w = block.getWorld().getName();
        int x = block.getX(), y = block.getY(), z = block.getZ();
        for (Mine m : mines.values()) {
            Region r = m.region();
            if (r != null && r.contains(w, x, y, z)) return m;
        }
        return null;
    }

    @Override
    public int breakBlocks(Player player, Collection<Block> blocks, BreakCause cause) {
        Map<Mine, Map<Material, Long>> drops = new LinkedHashMap<>();
        Map<Mine, Integer> counts = new HashMap<>();
        for (Block b : blocks) {
            Mine m = mineFor(b);
            if (m == null || m.refilling()) continue;
            Material type = b.getType();
            if (type.isAir()) continue;
            b.setType(Material.AIR, false);
            drops.computeIfAbsent(m, k -> new EnumMap<>(Material.class)).merge(type, 1L, Long::sum);
            counts.merge(m, 1, Integer::sum);
        }
        int total = 0;
        for (Map.Entry<Mine, Map<Material, Long>> e : drops.entrySet()) {
            Mine m = e.getKey();
            int n = counts.get(m);
            total += n;
            m.removed(n);
            plugin.getServer().getPluginManager().callEvent(new MineBlocksBrokenEvent(player, m, cause, n, e.getValue()));
            checkThreshold(m);
        }
        return total;
    }

    // ------------------------------------------------------------------ access

    /** Grade gate. Without a progression module everyone can use every mine. */
    public boolean canAccess(Player p, Mine m) {
        if (p.hasPermission(BYPASS_PERMISSION)) return true;
        Optional<ProgressionService> prog = Services.find(ProgressionService.class);
        if (prog.isEmpty()) return true;
        try {
            return prog.get().grade(p.getUniqueId()) >= m.requiredGrade();
        } catch (RuntimeException e) {
            return false; // profile still loading
        }
    }

    /** Highest placed mine the player can use, or empty. */
    public Optional<Mine> bestFor(Player p) {
        Mine best = null;
        for (Mine m : mines.values()) {
            if (m.placed() && canAccess(p, m) && (best == null || m.requiredGrade() >= best.requiredGrade())) best = m;
        }
        return Optional.ofNullable(best);
    }

    // ------------------------------------------------------------------ placement

    void place(Mine m, Region r, Location spawn) {
        m.region(r);
        m.spawn(spawn);
        store.savePlacement(m.id(), r, spawn);
        refillNow(m);
    }

    void setSpawn(Mine m, Location spawn) {
        m.spawn(spawn);
        store.savePlacement(m.id(), m.region(), spawn);
    }

    void unplace(Mine m) {
        m.region(null);
        m.spawn(null);
        store.savePlacement(m.id(), null, null);
        if (m.def().custom()) {
            store.removeCustom(m.id());
            mines.remove(m.id());
        }
        hideAllBars();
    }

    Mine addCustom(MinesConfig.MineDef def) {
        store.saveCustom(def);
        Mine m = new Mine(def, config.decks().get(def.deck()));
        mines.put(def.id(), m);
        return m;
    }

    // ------------------------------------------------------------------ refills

    private void checkThreshold(Mine m) {
        if (m.refilling() || m.refillPending() || m.region() == null) return;
        if (1.0 - m.fillRatio() >= config.settings().refillMinedFraction()) requestRefill(m, "mined out");
    }

    /** Refill with a warning to players inside. */
    public void requestRefill(Mine m, String reason) {
        if (!m.placed() || m.refilling() || m.refillPending()) return;
        int warn = config.settings().warnSeconds();
        List<Player> inside = playersInside(m);
        if (warn <= 0 || inside.isEmpty()) {
            refillNow(m);
            return;
        }
        m.refillPending(true);
        for (Player p : inside) {
            Text.send(p, "<yellow>" + m.displayName() + " <gray>is " + reason + ". Refilling in <white>" + warn + "s<gray>, stand clear.");
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            m.refillPending(false);
            if (mines.get(m.id()) == m) refillNow(m);
        }, warn * 20L);
    }

    /** Refill immediately: move players out, fill, then rescue anyone who walked back in. */
    public void refillNow(Mine m) {
        if (!m.placed() || m.refilling()) return;
        Location safe = m.spawn();
        for (Player p : playersInside(m)) p.teleportAsync(safe);
        m.refilling(true);
        refiller.fill(m.region(), m.mix(), () -> {
            m.refilling(false);
            m.filled();
            Location s = m.spawn();
            if (s == null) return;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (m.region().near(p.getLocation(), 0, 0, 0)) p.teleportAsync(s);
            }
        });
    }

    void refillAll() {
        for (Mine m : mines.values()) refillNow(m);
    }

    /** Players in the pit or standing right on top of it. */
    List<Player> playersInside(Mine m) {
        List<Player> out = new ArrayList<>();
        if (m.region() == null) return out;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (m.region().near(p.getLocation(), 0, 0, 2)) out.add(p);
        }
        return out;
    }

    /** Runs every second: timed refills and the HUD. */
    void tick() {
        int interval = config.settings().refillIntervalSeconds();
        long now = System.currentTimeMillis();
        if (interval > 0) {
            for (Mine m : mines.values()) {
                if (m.placed() && !m.refilling() && m.fillRatio() < 1.0
                        && now - m.lastRefillMillis() >= interval * 1000L) {
                    requestRefill(m, "on its refill timer");
                }
            }
        }
        if (config.settings().bossBar()) updateBars();
    }

    // ------------------------------------------------------------------ HUD

    private void updateBars() {
        int rim = config.settings().rimWidth() + 3;
        for (Mine m : mines.values()) {
            if (m.region() == null) continue;
            BossBar bar = m.bar();
            int pct = (int) Math.round(m.fillRatio() * 100);
            String state = m.refilling() ? "<yellow>refilling" : "<gray>" + pct + "% ore left";
            bar.name(Text.mm(m.color() + m.displayName() + " <dark_gray>| " + state
                    + " <dark_gray>| <gray>Grade " + m.requiredGrade()));
            bar.progress((float) m.fillRatio());
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Location l = p.getLocation();
            Mine at = null;
            for (Mine m : mines.values()) {
                if (m.region() != null && m.region().near(l, rim, 2, 8)) { at = m; break; }
            }
            Mine prev = barShown.get(p.getUniqueId());
            if (prev == at) continue;
            if (prev != null) p.hideBossBar(prev.bar());
            if (at != null) {
                p.showBossBar(at.bar());
                barShown.put(p.getUniqueId(), at);
            } else {
                barShown.remove(p.getUniqueId());
            }
        }
    }

    void forget(Player p) {
        Mine prev = barShown.remove(p.getUniqueId());
        if (prev != null) p.hideBossBar(prev.bar());
    }

    void hideAllBars() {
        for (Map.Entry<UUID, Mine> e : barShown.entrySet()) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue().bar());
        }
        barShown.clear();
    }
}
