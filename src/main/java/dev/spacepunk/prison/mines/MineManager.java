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
        // A hand break is one block: hand it to listeners as the origin so area enchants can center on it.
        Block origin = cause == BreakCause.HAND && blocks.size() == 1 ? blocks.iterator().next() : null;
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
            plugin.getServer().getPluginManager().callEvent(new MineBlocksBrokenEvent(player, m, cause, n, e.getValue(), origin));
            checkThreshold(m);
        }
        return total;
    }

    // ------------------------------------------------------------------ access

    /** Grade gate. Without a progression module everyone can use every mine. */
    public boolean canAccess(Player p, Mine m) {
        return p.hasPermission(BYPASS_PERMISSION) || hasGrade(p, m);
    }

    /** Grade check only, ignoring the admin bypass. Used to pick a player's own mine. */
    public boolean hasGrade(Player p, Mine m) {
        Optional<ProgressionService> prog = Services.find(ProgressionService.class);
        if (prog.isEmpty()) return true;
        try {
            return prog.get().grade(p.getUniqueId()) >= m.requiredGrade();
        } catch (RuntimeException e) {
            return false; // profile still loading
        }
    }

    /**
     * Highest placed mine the player's grade unlocks, or empty. Uses the real grade even for admins with the
     * bypass permission, so ops are not always sent to the last mine.
     */
    public Optional<Mine> bestFor(Player p) {
        Mine best = null;
        for (Mine m : mines.values()) {
            if (m.placed() && hasGrade(p, m) && (best == null || m.requiredGrade() >= best.requiredGrade())) best = m;
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

    /** Seconds before a pending refill at which nearby players get a chat line or a title. */
    private static final java.util.Set<Integer> CHAT_AT = java.util.Set.of(60, 30, 10);
    private static final java.util.Set<Integer> TITLE_AT = java.util.Set.of(10, 5, 4, 3, 2, 1);

    /**
     * Refill after a countdown (refill.warn-seconds) while anyone is in or around the mine; immediately if the
     * mine room is empty. Mining continues during the countdown; the boss bar shows the time left.
     */
    public void requestRefill(Mine m, String reason) {
        if (!m.placed() || m.refilling() || m.refillPending()) return;
        int warn = config.settings().warnSeconds();
        List<Player> near = playersNear(m);
        if (warn <= 0 || near.isEmpty()) {
            refillNow(m);
            return;
        }
        m.refillPending(true);
        m.refillAt(System.currentTimeMillis() + warn * 1000L);
        m.lastAnnounced(warn);
        for (Player p : near) {
            Text.send(p, "<yellow>" + m.displayName() + " <gray>is " + reason + ". Resetting in <white>" + formatSeconds(warn)
                    + "<gray>. You'll be moved to the entrance when it resets.");
        }
    }

    /** Runs every second for mines with a pending refill: countdown messages, then the refill. */
    private void countdowns(long now) {
        for (Mine m : mines.values()) {
            if (!m.refillPending()) continue;
            int left = (int) Math.ceil((m.refillAt() - now) / 1000.0);
            if (left <= 0) {
                m.refillPending(false);
                refillNow(m);
                continue;
            }
            if (left >= m.lastAnnounced()) continue;
            m.lastAnnounced(left);
            if (!CHAT_AT.contains(left) && !TITLE_AT.contains(left)) continue;
            for (Player p : playersNear(m)) {
                if (CHAT_AT.contains(left)) {
                    Text.send(p, "<yellow>" + m.displayName() + " <gray>resets in <white>" + formatSeconds(left) + "<gray>.");
                }
                if (TITLE_AT.contains(left)) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                            Text.mm("<yellow>" + left),
                            Text.mm("<gray>" + m.displayName() + " is resetting"),
                            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO,
                                    java.time.Duration.ofMillis(1100), java.time.Duration.ofMillis(200))));
                }
            }
        }
    }

    private static String formatSeconds(int s) {
        if (s >= 60 && s % 60 == 0) return (s / 60) + (s == 60 ? " minute" : " minutes");
        return s + (s == 1 ? " second" : " seconds");
    }

    /** Players in the mine room: the pit, the walkway and up to the ceiling. */
    List<Player> playersNear(Mine m) {
        List<Player> out = new ArrayList<>();
        if (m.region() == null) return out;
        int h = config.settings().rimWidth() + 2;
        int up = config.settings().roomHeight() + 2;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (m.region().near(p.getLocation(), h, 1, up)) out.add(p);
        }
        return out;
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
        countdowns(now);
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
            String state = m.refilling() ? "<yellow>refilling"
                    : m.refillPending() ? "<red>resets in " + Math.max(0, (int) Math.ceil((m.refillAt() - System.currentTimeMillis()) / 1000.0)) + "s"
                    : "<gray>" + pct + "% ore left";
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
