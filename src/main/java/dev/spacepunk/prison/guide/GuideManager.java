package dev.spacepunk.prison.guide;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.guide.GuideConfig.Chapter;
import dev.spacepunk.prison.guide.GuideConfig.Step;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The in-game guide: first-visit popups (title + chat brief), boss bar checklists that walk a player through a
 * system step by step, and guide books. Everything a player does reaches {@link #fire} as an event
 * (kind + value): join, world, warp, command, menu, objective, grade, prestige, region, guide.
 */
final class GuideManager {
    static final String DATA_KEY = "guide";
    private static final Gson GSON = new Gson();
    private static final Pattern CLICK = Pattern.compile("\\[\\[(~?)(/[^\\]]+)]]");
    /** Book styling tags. Books are parchment, so colours are dark. */
    private static final TagResolver BOOK_TAGS = TagResolver.resolver(
            TagResolver.resolver("h", Tag.styling(TextColor.color(0x6b2a0c), TextDecoration.BOLD)),
            TagResolver.resolver("em", Tag.styling(TextColor.color(0x7a4a00))),
            TagResolver.resolver("cmd", Tag.styling(TextColor.color(0x1d4b6e))),
            TagResolver.resolver("note", Tag.styling(TextColor.color(0x555555))),
            TagResolver.resolver("bad", Tag.styling(TextColor.color(0x8b1a1a))));

    private final Plugin plugin;
    private final Supplier<GuideConfig> cfg;
    private final Map<UUID, GuideData> data = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> nextPopup = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    /** Called when a checklist starts or ends (the workshop bay board follows the workshop checklist). */
    Runnable onChecklistChange = () -> {};

    GuideManager(Plugin plugin, Supplier<GuideConfig> cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    GuideConfig cfg() { return cfg.get(); }

    // ------------------------------------------------------------------ data

    void load(Player p, PlayerProfile profile) {
        GuideData d = null;
        String json = profile.moduleData(DATA_KEY);
        if (json != null) {
            try {
                d = GSON.fromJson(json, GuideData.class);
            } catch (JsonSyntaxException e) {
                plugin.getLogger().warning("[guide] bad guide data for " + p.getName() + ", starting fresh");
            }
        }
        if (d == null) d = new GuideData();
        d.normalize();
        d.active.removeIf(id -> !cfg().chapters.containsKey(id));
        data.put(p.getUniqueId(), d);
        updateBar(p);
    }

    void unload(Player p) {
        write(p.getUniqueId());
        data.remove(p.getUniqueId());
        nextPopup.remove(p.getUniqueId());
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    GuideData data(UUID id) { return data.get(id); }

    void markDirty(UUID id) { dirty.add(id); }

    void flush() {
        for (UUID id : new ArrayList<>(dirty)) write(id);
    }

    private void write(UUID id) {
        dirty.remove(id);
        GuideData d = data.get(id);
        if (d == null) return;
        Services.find(ProfileService.class).ifPresent(ps -> ps.get(id).ifPresent(prof -> {
            prof.moduleData(DATA_KEY, GSON.toJson(d));
            ps.markDirty(id);
        }));
    }

    boolean popups(GuideData d) { return d.popups == null ? cfg().popupsDefault : d.popups; }

    boolean barOn(GuideData d) { return d.bar == null ? cfg().barDefault : d.bar; }

    // ------------------------------------------------------------------ events

    /** One thing the player did. value/targets are compared case-insensitively. */
    void fire(Player p, String kind, String value, List<String> targets, long amount) {
        GuideData d = data.get(p.getUniqueId());
        if (d == null) return;
        String v = value == null ? "" : value.toLowerCase(Locale.ROOT);
        // First visits
        for (Chapter c : cfg().chapters.values()) {
            if (d.seen.contains(c.id())) continue;
            for (String t : c.triggers()) {
                if (matches(t, kind, v)) {
                    d.seen.add(c.id());
                    markDirty(p.getUniqueId());
                    firstVisit(p, d, c);
                    break;
                }
            }
        }
        // Checklist steps
        for (String id : new ArrayList<>(d.active)) {
            Chapter c = cfg().chapters.get(id);
            if (c == null) continue;
            int idx = d.step.getOrDefault(id, 0);
            if (idx >= c.steps().size()) continue;
            Step s = c.steps().get(idx);
            if (!matches(s.on(), kind, v) || !targetsMatch(s, targets)) continue;
            long add = "objective".equals(kind) ? amount : 1;
            long have = Math.min(s.amount(), d.have.getOrDefault(id, 0L) + Math.max(1, add));
            d.have.put(id, have);
            markDirty(p.getUniqueId());
            if (have >= s.amount()) advance(p, d, c);
            else if (id.equals(top(d))) updateBar(p);
        }
    }

    private static boolean targetsMatch(Step s, List<String> targets) {
        if (s.targets().isEmpty()) return true;
        if (targets == null) return false;
        for (String t : targets) if (t != null && s.targets().contains(t.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    /** Does an event (kind, lower-case value) match a trigger/step spec such as "world:workshop" or "menu:drill."? */
    static boolean matches(String spec, String kind, String value) {
        int colon = spec.indexOf(':');
        String sk = colon < 0 ? spec : spec.substring(0, colon);
        String arg = colon < 0 ? "" : spec.substring(colon + 1);
        if (!sk.equals(kind)) return false;
        return switch (sk) {
            case "join", "guide" -> arg.isEmpty() || arg.equals(value);
            case "world", "objective" -> arg.equals(value);
            case "warp" -> glob(arg, value);
            case "menu" -> value.contains(arg);
            case "command" -> {
                for (String a : arg.split(",")) {
                    String c = a.trim();
                    if (c.startsWith("/")) c = c.substring(1);
                    if (c.equals(value)) yield true;
                }
                yield false;
            }
            case "grade", "prestige" -> {
                try {
                    yield Integer.parseInt(value) >= Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "region" -> regionContains(arg, value);
            default -> false;
        };
    }

    private static boolean glob(String pattern, String value) {
        if (!pattern.contains("*")) return pattern.equals(value);
        String regex = ("\\Q" + pattern + "\\E").replace("*", "\\E.*\\Q");
        return value.matches(regex);
    }

    /** spec "world:x1:z1:x2:z2", value "world:x:z". */
    static boolean regionContains(String spec, String value) {
        String[] r = spec.split(":");
        String[] v = value.split(":");
        if (r.length != 5 || v.length != 3 || !r[0].equals(v[0])) return false;
        try {
            double x = Double.parseDouble(v[1]), z = Double.parseDouble(v[2]);
            double x1 = Double.parseDouble(r[1]), z1 = Double.parseDouble(r[2]);
            double x2 = Double.parseDouble(r[3]), z2 = Double.parseDouble(r[4]);
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) + 1 && z >= Math.min(z1, z2) && z <= Math.max(z1, z2) + 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Chapters with region triggers that this player hasn't seen yet are checked about once a second. */
    void regionTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            GuideData d = data.get(p.getUniqueId());
            if (d == null) continue;
            Location l = p.getLocation();
            String v = l.getWorld().getName().toLowerCase(Locale.ROOT) + ":" + l.getX() + ":" + l.getZ();
            boolean any = false;
            for (Chapter c : cfg().chapters.values()) {
                if (d.seen.contains(c.id())) continue;
                for (String t : c.triggers()) if (t.startsWith("region:")) { any = true; break; }
                if (any) break;
            }
            if (any) fire(p, "region", v, List.of(), 1);
        }
    }

    // ------------------------------------------------------------------ first visit

    private void firstVisit(Player p, GuideData d, Chapter c) {
        if (c.hasChecklist() && !d.done.contains(c.id()) && !d.active.contains(c.id())) startChecklist(p, d, c, false);
        if (!popups(d) || (c.title() == null && c.brief().isEmpty())) return;
        int now = Bukkit.getCurrentTick();
        int at = Math.max(now, nextPopup.getOrDefault(p.getUniqueId(), 0));
        nextPopup.put(p.getUniqueId(), at + cfg().popupGapTicks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) popup(p, c);
        }, Math.max(1, at - now));
    }

    void popup(Player p, Chapter c) {
        if (c.title() != null) {
            p.showTitle(Title.title(Text.mm(c.title()), Text.mm(c.subtitle()),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3500), Duration.ofMillis(900))));
        }
        p.sendMessage(Component.empty());
        p.sendMessage(Text.mm(c.color() + "<bold>▌ " + c.name().toUpperCase(Locale.ROOT) + "</bold>"));
        for (String line : c.brief()) p.sendMessage(Text.mm("<dark_gray>▌</dark_gray> " + line));
        p.sendMessage(Text.mm("<dark_gray>▌</dark_gray> " + buttons(c)));
        p.sendMessage(Component.empty());
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
    }

    String buttons(Chapter c) {
        StringBuilder sb = new StringBuilder();
        sb.append("<click:run_command:'/guide ").append(c.id()).append("'><hover:show_text:'<gray>Open the ")
                .append(Text.MM.escapeTags(c.name())).append(" guide'><#e0b354>[Read the guide]</#e0b354></hover></click>");
        String url = cfg().wiki(c);
        if (url != null) {
            sb.append("  <click:open_url:'").append(url).append("'><hover:show_text:'<gray>").append(url)
                    .append("'><#8fd3ff>[Wiki]</#8fd3ff></hover></click>");
        }
        sb.append("  <click:run_command:'/guide'><hover:show_text:'<gray>Every guide'><gray>[All guides]</gray></hover></click>");
        return sb.toString();
    }

    // ------------------------------------------------------------------ checklists

    void startChecklist(Player p, GuideData d, Chapter c, boolean restart) {
        if (!c.hasChecklist()) return;
        if (restart) d.done.remove(c.id());
        d.active.remove(c.id());
        d.active.add(c.id());
        d.step.put(c.id(), 0);
        d.have.put(c.id(), 0L);
        markDirty(p.getUniqueId());
        Text.send(p, c.color() + "▶ Guide: " + c.name() + "</color> <gray>- follow the checklist at the top of your screen."
                + (barOn(d) ? "" : " <dark_gray>(bar hidden: /guide bar)"));
        hint(p, c, 0);
        checkState(p, d, c);
        updateBar(p);
        onChecklistChange.run();
    }

    void skip(Player p, GuideData d, String id) {
        if (!d.active.remove(id)) return;
        d.done.add(id);
        d.step.remove(id);
        d.have.remove(id);
        markDirty(p.getUniqueId());
        updateBar(p);
        onChecklistChange.run();
    }

    private void advance(Player p, GuideData d, Chapter c) {
        int idx = d.step.getOrDefault(c.id(), 0);
        Step s = c.steps().get(idx);
        p.sendMessage(Text.mm("<green>✔</green> <white>" + s.text()));
        idx++;
        d.have.put(c.id(), 0L);
        if (idx >= c.steps().size()) {
            d.active.remove(c.id());
            d.step.remove(c.id());
            d.have.remove(c.id());
            d.done.add(c.id());
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            Text.send(p, c.color() + "<bold>Guide complete: " + c.name() + "</bold>");
            if (c.done() != null) p.sendMessage(Text.mm("<gray>" + c.done()));
            markDirty(p.getUniqueId());
            write(p.getUniqueId());
            updateBar(p);
            onChecklistChange.run();
            return;
        }
        d.step.put(c.id(), idx);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.8f);
        hint(p, c, idx);
        markDirty(p.getUniqueId());
        write(p.getUniqueId());
        checkState(p, d, c);
        updateBar(p);
    }

    private void hint(Player p, Chapter c, int idx) {
        if (idx >= c.steps().size()) return;
        Step s = c.steps().get(idx);
        p.sendMessage(Text.mm(c.color() + "➜</color> <white>" + s.text() + "</white> <dark_gray>(" + (idx + 1) + "/" + c.steps().size() + ")"));
        for (String h : s.hint()) p.sendMessage(Text.mm("  <gray>" + h));
    }

    /** Steps about where you are or what you already have (grade, prestige, world) can be done the moment they start. */
    private void checkState(Player p, GuideData d, Chapter c) {
        if (!d.active.contains(c.id())) return;
        int idx = d.step.getOrDefault(c.id(), 0);
        if (idx >= c.steps().size()) return;
        String on = c.steps().get(idx).on();
        String kind = on.contains(":") ? on.substring(0, on.indexOf(':')) : on;
        String value = switch (kind) {
            case "world" -> p.getWorld().getName().toLowerCase(Locale.ROOT);
            case "grade" -> Services.find(ProfileService.class).flatMap(ps -> ps.get(p.getUniqueId()))
                    .map(pr -> String.valueOf(pr.grade())).orElse(null);
            case "prestige" -> Services.find(ProfileService.class).flatMap(ps -> ps.get(p.getUniqueId()))
                    .map(pr -> String.valueOf(pr.prestige())).orElse(null);
            default -> null;
        };
        if (value != null && matches(on, kind, value)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) fire(p, kind, value, List.of(), 1);
            });
        }
    }

    static String top(GuideData d) { return d.active.isEmpty() ? null : d.active.get(d.active.size() - 1); }

    void updateBar(Player p) {
        GuideData d = data.get(p.getUniqueId());
        BossBar bar = bars.get(p.getUniqueId());
        String id = d == null ? null : top(d);
        Chapter c = id == null ? null : cfg().chapters.get(id);
        if (c == null || !barOn(d)) {
            if (bar != null) p.hideBossBar(bar);
            return;
        }
        int idx = Math.min(d.step.getOrDefault(id, 0), c.steps().size() - 1);
        Step s = c.steps().get(idx);
        long have = d.have.getOrDefault(id, 0L);
        String count = s.amount() > 1 ? " <gray>" + have + "/" + s.amount() : "";
        Component name = Text.mm(c.color() + "<bold>GUIDE</bold></color> <dark_gray>·</dark_gray> <white>" + s.text()
                + "</white>" + count + " <dark_gray>(" + (idx + 1) + "/" + c.steps().size() + ")");
        float progress = (float) ((idx + (double) have / s.amount()) / c.steps().size());
        progress = Math.max(0f, Math.min(1f, progress));
        if (bar == null) {
            bar = BossBar.bossBar(name, progress, c.barColor(), BossBar.Overlay.NOTCHED_10);
            bars.put(p.getUniqueId(), bar);
        } else {
            bar.name(name);
            bar.progress(progress);
            bar.color(c.barColor());
        }
        p.showBossBar(bar);
    }

    // ------------------------------------------------------------------ books

    void openBook(Player p, Chapter c) {
        List<Component> pages = new ArrayList<>();
        for (String page : c.pages()) pages.add(bookPage(page));
        StringBuilder last = new StringBuilder("<h>MORE</h>\n\n");
        String url = cfg().wiki(c);
        if (url != null) {
            last.append("<click:open_url:'").append(url).append("'><hover:show_text:'<gray>").append(url)
                    .append("'><cmd><u>Full wiki page</u></cmd></hover></click>\n<note>Every number and table, on the web.</note>\n\n");
        }
        if (c.hasChecklist()) last.append("[[/guide restart ").append(c.id()).append("]]\n<note>Run the checklist again.</note>\n\n");
        last.append("[[/guide]]\n<note>Every guide.</note>");
        pages.add(bookPage(last.toString()));
        Book book = Book.book(Text.mm(c.name()), Text.mm("The Station"), pages);
        p.openBook(book);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
        GuideData d = data.get(p.getUniqueId());
        if (d != null && d.read.add(c.id())) markDirty(p.getUniqueId());
        fire(p, "guide", c.id(), List.of(), 1);
    }

    static Component bookPage(String raw) {
        Matcher m = CLICK.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String cmd = m.group(2).trim();
            String action = m.group(1).isEmpty() ? "run_command" : "suggest_command";
            String repl = "<click:" + action + ":'" + cmd + "'><hover:show_text:'<gray>Click: " + cmd + "'><cmd>"
                    + cmd + "</cmd></hover></click>";
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return Text.MM.deserialize(sb.toString(), BOOK_TAGS);
    }

    void shutdown() {
        flush();
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        bars.clear();
    }
}
