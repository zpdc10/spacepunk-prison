package dev.spacepunk.prison.hud;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.CurrencyChangeEvent;
import dev.spacepunk.prison.api.event.MineBlocksBrokenEvent;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.data.ProfileLoadedEvent;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Owns everything on the player's screen that isn't a menu: the action bar (module segments such as the
 * drill's heat gauge, then mining gains, then flashes), a per-player sidebar, the tab list header/footer and
 * a cargo hold boss bar while mining. Main thread only.
 */
final class HudManager implements HudService, Listener {
    static final String DATA_KEY = "hud";
    private static final Gson GSON = new Gson();
    private static final String SEP = " <dark_gray>|</dark_gray> ";
    private static final String HEAD = "<#5a4030>▍</#5a4030><#c8743a><bold>";
    private static final int BAR_CELLS = 16;

    private final Supplier<HudConfig> config;
    private final Map<UUID, State> states = new HashMap<>();

    HudManager(Supplier<HudConfig> config) { this.config = config; }

    private HudConfig cfg() { return config.get(); }

    /** Saved per-player preferences (moduleData "hud"). */
    static final class Prefs {
        boolean sidebar = true;
    }

    static final class State {
        Prefs prefs = new Prefs();
        final Map<String, String> segments = new LinkedHashMap<>();
        String flash;
        long flashUntil;

        // Rolling mining summary.
        long gainUntil;
        long gainUnits;
        /** Item name -> units in this window, most recently mined last. */
        final LinkedHashMap<String, Long> gainItems = new LinkedHashMap<>();
        long gainCredits;
        long gainTokens;
        boolean gainSold;

        long lastMined;
        long usedBefore;
        boolean full;
        long lastFullAlarm;
        long lastSound;

        String lastBar = "";
        long lastBarSent;

        Scoreboard board;
        Objective objective;
        List<String> lines = List.of();
        /** Sidebar blocks owned by other modules (HudService#sidebarSection), in insertion order. */
        final LinkedHashMap<String, List<String>> sections = new LinkedHashMap<>();

        BossBar cargoBar;
        boolean cargoBarShown;
        long valueForUsed = -1;
        long value;
    }

    // ------------------------------------------------------------------ HudService

    @Override
    public void flash(Player player, String miniMessage) {
        State s = state(player);
        s.flash = miniMessage;
        s.flashUntil = System.currentTimeMillis() + cfg().flashMillis();
        renderActionBar(player, s, true);
    }

    @Override
    public void segment(Player player, String key, String miniMessage) {
        State s = state(player);
        if (miniMessage == null) s.segments.remove(key);
        else s.segments.put(key, miniMessage);
        renderActionBar(player, s, false);
    }

    @Override
    public void sidebarSection(Player player, String key, List<String> lines) {
        State s = state(player);
        List<String> old = s.sections.get(key);
        if (lines == null || lines.isEmpty()) {
            if (s.sections.remove(key) == null) return;
        } else {
            if (lines.equals(old)) return;
            s.sections.put(key, List.copyOf(lines));
        }
        renderSidebar(player, s);
    }

    private State state(Player p) { return states.computeIfAbsent(p.getUniqueId(), k -> new State()); }

    // ------------------------------------------------------------------ lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoaded(ProfileLoadedEvent e) {
        attach(e.player(), e.profile());
        if (cfg().joinHint()) {
            Text.send(e.player(), "<gray>Ore you mine goes straight into your <#c8743a>cargo hold</#c8743a> (see the sidebar). "
                    + "<white>/sell</white> to cash it in for Credits, <white>/rankup</white> to climb grades.");
        }
    }

    /** Sets up the HUD for a loaded player (join, or plugin enable with players online). */
    void attach(Player p, PlayerProfile profile) {
        State s = state(p);
        String json = profile.moduleData(DATA_KEY);
        if (json != null) {
            try {
                Prefs prefs = GSON.fromJson(json, Prefs.class);
                if (prefs != null) s.prefs = prefs;
            } catch (JsonSyntaxException ignored) {
                // keep defaults
            }
        }
        long cap = progression().map(pr -> pr.cargoCapacity(p.getUniqueId())).orElse(0L);
        long space = progression().map(pr -> pr.cargoSpace(p.getUniqueId())).orElse(0L);
        s.usedBefore = cap - space;
        renderSidebar(p, s);
        renderTab(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        State s = states.remove(e.getPlayer().getUniqueId());
        if (s != null && s.cargoBarShown && s.cargoBar != null) e.getPlayer().hideBossBar(s.cargoBar);
    }

    void detachAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.get(p.getUniqueId());
            if (s == null) continue;
            if (s.cargoBarShown && s.cargoBar != null) p.hideBossBar(s.cargoBar);
            if (s.board != null && p.getScoreboard() == s.board) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (!s.lastBar.isEmpty()) p.sendActionBar(Component.empty());
        }
        states.clear();
    }

    /** Toggles the sidebar for one player and saves the choice. Returns the new state. */
    boolean toggleSidebar(Player p) {
        State s = state(p);
        s.prefs.sidebar = !s.prefs.sidebar;
        Services.find(ProfileService.class).ifPresent(ps -> ps.get(p.getUniqueId()).ifPresent(prof -> {
            prof.moduleData(DATA_KEY, GSON.toJson(s.prefs));
            ps.markDirty(p.getUniqueId());
        }));
        s.lines = List.of();
        renderSidebar(p, s);
        return s.prefs.sidebar;
    }

    // ------------------------------------------------------------------ mining feedback

    /** After drill modules (HIGH, registered earlier) and before progression stores ore (HIGHEST). */
    @EventHandler(priority = EventPriority.HIGH)
    public void beforeStore(MineBlocksBrokenEvent e) {
        if (e.cause() == MineService.BreakCause.ADMIN) return;
        State s = state(e.player());
        s.usedBefore = cargoUsed(e.player().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMined(MineBlocksBrokenEvent e) {
        if (e.cause() == MineService.BreakCause.ADMIN) return;
        Player p = e.player();
        UUID id = p.getUniqueId();
        State s = state(p);
        HudConfig c = cfg();
        long now = System.currentTimeMillis();
        s.lastMined = now;

        long units = 0;
        Material best = null;
        long bestPrice = -1;
        ProgressionService prog = progression().orElse(null);
        Map<Material, Long> mined = new LinkedHashMap<>();
        for (Map.Entry<Material, Long> en : e.ores().entrySet()) {
            long n = en.getValue() == null ? 0 : en.getValue();
            if (n <= 0) continue;
            units += n;
            mined.put(en.getKey(), n);
            long price = prog == null ? 0 : prog.sellPrice(en.getKey());
            if (price > bestPrice) {
                bestPrice = price;
                best = en.getKey();
            }
        }

        boolean auto = autoSelling(id);
        long used = cargoUsed(id);
        long stored = Math.max(0, used - s.usedBefore);
        s.usedBefore = used;
        long shown = auto ? units : stored;

        if (shown > 0 && best != null) {
            if (now > s.gainUntil) resetGain(s);
            // When the hold overflowed, only part of this break was stored; credit the most valuable item first.
            long left = shown;
            List<Map.Entry<Material, Long>> byValue = new ArrayList<>(mined.entrySet());
            if (prog != null) byValue.sort((a, b) -> Long.compare(prog.sellPrice(b.getKey()), prog.sellPrice(a.getKey())));
            for (Map.Entry<Material, Long> en : byValue) {
                if (left <= 0) break;
                long n = Math.min(left, en.getValue());
                left -= n;
                String itemId = en.getKey().getKey().toString();
                String name = prog == null ? itemName(itemId) : prog.itemName(itemId);
                long prev = s.gainItems.getOrDefault(name, 0L);
                s.gainItems.remove(name);
                s.gainItems.put(name, prev + n); // re-insert so it counts as most recent
            }
            s.gainUnits += shown;
            s.gainSold |= auto;
            s.gainUntil = now + c.gainMillis();
        }

        boolean overflow = !auto && units > stored && prog != null && prog.cargoSpace(id) <= 0;
        if (overflow) cargoFull(p, s, now);
        else if (shown > 0) s.full = false;

        if (e.cause() == MineService.BreakCause.HAND && shown > 0) breakEffects(p, e, best, c, s, now);
        renderActionBar(p, s, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCurrency(CurrencyChangeEvent e) {
        long delta = e.newBalance() - e.oldBalance();
        if (delta <= 0 || e.currency() == Currency.SHARDS) return;
        Player p = Bukkit.getPlayer(e.player());
        if (p == null) return;
        State s = state(p);
        long now = System.currentTimeMillis();
        if (now > s.gainUntil) resetGain(s);
        if (e.currency() == Currency.CREDITS) s.gainCredits += delta;
        else s.gainTokens += delta;
        s.gainUntil = now + cfg().gainMillis();
        renderActionBar(p, s, false);
    }

    private static void resetGain(State s) {
        s.gainUnits = 0;
        s.gainItems.clear();
        s.gainCredits = 0;
        s.gainTokens = 0;
        s.gainSold = false;
    }

    private void breakEffects(Player p, MineBlocksBrokenEvent e, Material best, HudConfig c, State s, long now) {
        boolean valuable = best != null && c.valuable(best.getKey().getKey());
        if (c.sounds() && now - s.lastSound >= 60) {
            s.lastSound = now;
            float pitch = 1.3f + ThreadLocalRandom.current().nextFloat() * 0.5f;
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25f, pitch);
            if (valuable) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
        }
        if (c.particles() && e.origin() != null) {
            Location at = e.origin().getLocation().add(0.5, 0.5, 0.5);
            if (valuable) {
                p.spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.25, 0.25, 0.25, 0.08);
                p.spawnParticle(Particle.WAX_OFF, at, 4, 0.2, 0.2, 0.2, 0.5);
            } else {
                p.spawnParticle(Particle.ELECTRIC_SPARK, at, 3, 0.2, 0.2, 0.2, 0.03);
            }
        }
    }

    private void cargoFull(Player p, State s, long now) {
        HudConfig c = cfg();
        if (!s.full) {
            s.full = true;
            s.lastFullAlarm = now;
            p.showTitle(Title.title(Text.mm("<red><bold>CARGO HOLD FULL"),
                    Text.mm("<gray>Ore is being left behind. <white>/sell</white> to cash in, <white>/cargo</white> to upgrade."),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(400))));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.35f, 0.6f);
        } else if (now - s.lastFullAlarm >= c.fullWarningMillis()) {
            s.lastFullAlarm = now;
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ action bar

    private void renderActionBar(Player p, State s, boolean force) {
        long now = System.currentTimeMillis();
        List<String> parts = new ArrayList<>(s.segments.values());
        if (now <= s.gainUntil) {
            String gain = gainText(s);
            if (!gain.isEmpty()) parts.add(gain);
        }
        if (s.full && now - s.lastMined < 5000) {
            parts.add("<red><bold>CARGO FULL</bold></red> <gray>/sell");
        }
        if (s.flash != null && now <= s.flashUntil) parts.add(s.flash);
        String bar = String.join(SEP, parts);
        if (bar.isEmpty()) {
            if (!s.lastBar.isEmpty()) {
                p.sendActionBar(Component.empty());
                s.lastBar = "";
            }
            return;
        }
        // The client fades the action bar after ~3s, so resend at least once a second.
        if (!force && bar.equals(s.lastBar) && now - s.lastBarSent < 1000) return;
        p.sendActionBar(Text.mm(bar));
        s.lastBar = bar;
        s.lastBarSent = now;
    }

    private static String gainText(State s) {
        StringBuilder sb = new StringBuilder();
        if (s.gainUnits > 0) {
            // Up to three item types, most recent first, e.g. "⛏ +1 Carbon Slag  +4 Slag  +2 more".
            List<Map.Entry<String, Long>> items = new ArrayList<>(s.gainItems.entrySet());
            sb.append("<#c8743a>⛏</#c8743a>");
            long rest = 0;
            for (int i = items.size() - 1, shownTypes = 0; i >= 0; i--) {
                Map.Entry<String, Long> it = items.get(i);
                if (shownTypes < 3) {
                    sb.append(shownTypes == 0 ? " " : "  ").append("<#c8743a>+").append(Numbers.shortFormat(it.getValue()))
                            .append("</#c8743a> <white>").append(it.getKey()).append("</white>");
                    shownTypes++;
                } else {
                    rest += it.getValue();
                }
            }
            if (rest > 0) sb.append("  <gray>+").append(Numbers.shortFormat(rest)).append(" more</gray>");
            sb.append(s.gainSold ? " <dark_gray>→</dark_gray> <gray>sold" : " <dark_gray>→</dark_gray> <gray>hold");
        }
        if (s.gainCredits > 0) {
            if (!sb.isEmpty()) sb.append("  ");
            sb.append("<gold>+").append(Numbers.shortFormat(s.gainCredits)).append(" ¢</gold>");
        }
        if (s.gainTokens > 0) {
            if (!sb.isEmpty()) sb.append("  ");
            sb.append("<aqua>+").append(Numbers.shortFormat(s.gainTokens)).append(" ✦</aqua>");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ ticking

    /** Every few ticks: expire action bar parts, cargo boss bar. */
    void tickFast() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.get(p.getUniqueId());
            if (s == null) continue;
            if (s.full && progression().map(pr -> pr.cargoSpace(p.getUniqueId()) > 0).orElse(true)) s.full = false;
            renderActionBar(p, s, false);
            renderCargoBar(p, s, now);
        }
    }

    void tickSidebar() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.get(p.getUniqueId());
            if (s != null) renderSidebar(p, s);
        }
    }

    void tickTab() {
        if (!cfg().tab()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (states.containsKey(p.getUniqueId())) renderTab(p);
        }
    }

    // ------------------------------------------------------------------ cargo boss bar

    private void renderCargoBar(Player p, State s, long now) {
        HudConfig c = cfg();
        boolean want = c.cargoBar() && (now - s.lastMined < c.cargoBarLingerMillis()) && progression().isPresent();
        if (!want) {
            if (s.cargoBarShown) {
                p.hideBossBar(s.cargoBar);
                s.cargoBarShown = false;
            }
            return;
        }
        UUID id = p.getUniqueId();
        ProgressionService prog = progression().get();
        if (s.cargoBar == null) s.cargoBar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_20);
        if (autoSelling(id)) {
            s.cargoBar.name(Text.mm("<#c8743a><bold>CARGO</bold></#c8743a> <dark_gray>|</dark_gray> <aqua>Auto-sell online</aqua> "
                    + "<dark_gray>|</dark_gray> <gray>ore is sold as you mine"));
            s.cargoBar.progress(1f);
            s.cargoBar.color(BossBar.Color.BLUE);
        } else {
            long cap = prog.cargoCapacity(id);
            long used = cap - prog.cargoSpace(id);
            double frac = cap <= 0 ? 0 : Math.min(1.0, (double) used / cap);
            String state = frac >= 1.0 ? "<red><bold>FULL</bold></red> <gray>· <white>/sell</white>"
                    : "<gray>worth <gold>~" + Numbers.shortFormat(cargoValue(p, s, used)) + " ¢</gold>";
            s.cargoBar.name(Text.mm("<#c8743a><bold>CARGO HOLD</bold></#c8743a> <white>" + Numbers.shortFormat(used)
                    + "</white><dark_gray>/</dark_gray><gray>" + Numbers.shortFormat(cap) + "</gray> <dark_gray>|</dark_gray> " + state));
            s.cargoBar.progress((float) frac);
            s.cargoBar.color(frac >= c.dangerAt() ? BossBar.Color.RED : frac >= c.warnAt() ? BossBar.Color.YELLOW : BossBar.Color.GREEN);
        }
        if (!s.cargoBarShown) {
            p.showBossBar(s.cargoBar);
            s.cargoBarShown = true;
        }
    }

    // ------------------------------------------------------------------ sidebar

    private void renderSidebar(Player p, State s) {
        HudConfig c = cfg();
        boolean show = c.sidebar() && s.prefs.sidebar;
        if (!show) {
            if (s.objective != null && s.objective.getDisplaySlot() == DisplaySlot.SIDEBAR) s.objective.setDisplaySlot(null);
            s.lines = List.of();
            return;
        }
        if (s.board == null) {
            s.board = Bukkit.getScoreboardManager().getNewScoreboard();
            s.objective = s.board.registerNewObjective("spacepunk_hud", Criteria.DUMMY, Text.mm(c.sidebarTitle()));
            s.objective.numberFormat(NumberFormat.blank());
        }
        if (p.getScoreboard() != s.board) p.setScoreboard(s.board);
        if (s.objective.getDisplaySlot() != DisplaySlot.SIDEBAR) s.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = sidebarLines(p, s);
        if (lines.equals(s.lines)) return;
        for (int i = 0; i < Math.max(lines.size(), s.lines.size()); i++) {
            String entry = "hud" + (char) ('a' + i);
            if (i >= lines.size()) {
                s.board.resetScores(entry);
                continue;
            }
            if (i < s.lines.size() && lines.get(i).equals(s.lines.get(i)) && lines.size() == s.lines.size()) continue;
            Score score = s.objective.getScore(entry);
            score.setScore(lines.size() - i);
            score.customName(Text.mm(lines.get(i)));
        }
        s.lines = lines;
    }

    private List<String> sidebarLines(Player p, State s) {
        UUID id = p.getUniqueId();
        List<String> out = new ArrayList<>();
        EconomyService eco = Services.find(EconomyService.class).orElse(null);
        ProgressionService prog = progression().orElse(null);
        long credits = eco == null ? 0 : eco.balance(id, Currency.CREDITS);

        if (prog != null) {
            int grade = prog.grade(id);
            int prestige = prog.prestige(id);
            out.add(HEAD + "CLEARANCE");
            out.add(" <white>" + prog.gradeName(grade) + "</white> <dark_gray>·</dark_gray> " + prog.deckName(grade));
            out.add(" <gray>Prestige " + (prestige > 0 ? "<light_purple>P" + prestige : "<dark_gray>none"));
            long cost = prog.nextGradeCost(id);
            if (cost < 0 || grade >= ProgressionService.MAX_GRADE) {
                out.add(" <light_purple><bold>MAX</bold></light_purple> <gray>· <white>/prestige");
            } else {
                double frac = cost == 0 ? 1.0 : Math.min(1.0, (double) credits / cost);
                String pct = frac >= 1.0 ? "<green><bold>/rankup" : "<white>" + (int) Math.floor(frac * 100) + "%";
                out.add(" " + bar(frac, frac >= 1.0 ? "#6fcf6f" : "#c8743a") + " " + pct);
            }
            out.add(" ");
        }
        if (eco != null) {
            out.add(HEAD + "WALLET");
            out.add(" <gold>¢</gold> <white>" + Numbers.shortFormat(credits) + "</white> <gray>Credits");
            out.add(" <aqua>✦</aqua> <white>" + Numbers.shortFormat(eco.balance(id, Currency.TOKENS)) + "</white> <gray>Tokens");
            out.add(" <light_purple>◆</light_purple> <white>" + Numbers.shortFormat(eco.balance(id, Currency.SHARDS)) + "</white> <gray>Shards");
            out.add("  ");
        }
        if (prog != null) {
            out.add(HEAD + "CARGO HOLD");
            if (autoSelling(id)) {
                out.add(" <aqua>Auto-sell online");
            } else {
                long cap = prog.cargoCapacity(id);
                long used = cap - prog.cargoSpace(id);
                double frac = cap <= 0 ? 0 : Math.min(1.0, (double) used / cap);
                HudConfig c = cfg();
                String color = frac >= c.dangerAt() ? "#ff3030" : frac >= c.warnAt() ? "#e0c040" : "#6fcf6f";
                out.add(" " + bar(frac, color) + " <white>" + Numbers.shortFormat(used) + "</white><dark_gray>/</dark_gray><gray>"
                        + Numbers.shortFormat(cap));
                out.add(frac >= 1.0 ? " <red><bold>FULL</bold></red> <gray>· <white>/sell"
                        : " <gray>Worth <gold>~" + Numbers.shortFormat(cargoValue(p, s, used)) + " ¢");
            }
            out.add("   ");
        }
        int pad = 4;
        for (List<String> section : s.sections.values()) {
            out.addAll(section);
            out.add(" ".repeat(pad++)); // blank lines must differ to be separate scores
        }
        String footer = cfg().sidebarFooter();
        if (footer != null && !footer.isEmpty()) out.add(footer);
        return out.size() > 15 ? out.subList(0, 15) : out;
    }

    private static String bar(double frac, String color) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, frac)) * BAR_CELLS);
        StringBuilder sb = new StringBuilder("<dark_gray>[</dark_gray><").append(color).append(">");
        for (int i = 0; i < filled; i++) sb.append('|');
        sb.append("</").append(color).append("><#3a332c>");
        for (int i = filled; i < BAR_CELLS; i++) sb.append('|');
        return sb.append("</#3a332c><dark_gray>]</dark_gray>").toString();
    }

    // ------------------------------------------------------------------ tab list

    private void renderTab(Player p) {
        HudConfig c = cfg();
        if (!c.tab()) return;
        p.sendPlayerListHeaderAndFooter(Text.mm(placeholders(p, c.tabHeader())), Text.mm(placeholders(p, c.tabFooter())));
    }

    private String placeholders(Player p, String s) {
        if (s == null || s.isEmpty() || s.indexOf('{') < 0) return s == null ? "" : s;
        UUID id = p.getUniqueId();
        EconomyService eco = Services.find(EconomyService.class).orElse(null);
        ProgressionService prog = progression().orElse(null);
        double tps = Math.min(20.0, Bukkit.getServer().getTPS()[0]);
        return s.replace("{online}", Integer.toString(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", Integer.toString(Bukkit.getMaxPlayers()))
                .replace("{ping}", Integer.toString(p.getPing()))
                .replace("{tps}", String.format(Locale.ROOT, "%.1f", tps))
                .replace("{credits}", eco == null ? "0" : Numbers.shortFormat(eco.balance(id, Currency.CREDITS)))
                .replace("{tokens}", eco == null ? "0" : Numbers.shortFormat(eco.balance(id, Currency.TOKENS)))
                .replace("{shards}", eco == null ? "0" : Numbers.shortFormat(eco.balance(id, Currency.SHARDS)))
                .replace("{grade}", prog == null ? "1" : Integer.toString(prog.grade(id)))
                .replace("{prestige}", prog == null ? "0" : Integer.toString(prog.prestige(id)));
    }

    // ------------------------------------------------------------------ helpers

    private static java.util.Optional<ProgressionService> progression() { return Services.find(ProgressionService.class); }

    private static long cargoUsed(UUID id) {
        return progression().map(pr -> pr.cargoCapacity(id) - pr.cargoSpace(id)).orElse(0L);
    }

    private static boolean autoSelling(UUID id) {
        boolean prog = progression().map(pr -> pr.autoSelling(id)).orElse(false);
        boolean drill = Services.find(DrillService.class).map(d -> d.autoSell(id)).orElse(false);
        return prog || drill;
    }

    /** Estimated Credits for the hold's contents with the player's multipliers. Cached until the fill changes. */
    private long cargoValue(Player p, State s, long used) {
        if (used == s.valueForUsed) return s.value;
        ProgressionService prog = progression().orElse(null);
        if (prog == null) return 0;
        UUID id = p.getUniqueId();
        double base = 0;
        for (Map.Entry<String, Long> e : prog.cargoContents(id).entrySet()) base += (double) prog.sellPrice(e.getKey()) * e.getValue();
        double mult = prog.sellMultiplier(id) * Services.find(DrillService.class).map(d -> d.sellMultiplier(id)).orElse(1.0);
        s.value = Math.round(base * mult);
        s.valueForUsed = used;
        return s.value;
    }

    /** "minecraft:deepslate_iron_ore" -> "Deepslate Iron Ore". */
    static String itemName(String itemId) {
        String path = itemId.substring(itemId.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
