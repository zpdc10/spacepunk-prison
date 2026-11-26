package dev.spacepunk.prison.quests;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.ObjectiveTypes;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.QuestService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.quests.QuestConfig.ObjectiveDef;
import dev.spacepunk.prison.quests.QuestConfig.QuestDef;
import dev.spacepunk.prison.quests.QuestConfig.RewardDef;
import dev.spacepunk.prison.quests.QuestData.Active;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Quest engine: per-player state, objective matching, contract rolls, rewards and the sidebar tracker. */
final class QuestManager implements QuestService {
    static final String DATA_KEY = "quests";
    static final String REACH_PRESTIGE = "reach_prestige";
    private static final Set<String> ABSOLUTE = Set.of(ObjectiveTypes.REACH_GRADE, REACH_PRESTIGE);
    private static final String HEAD = "<#5a4030>▍</#5a4030><#c8743a><bold>";
    private static final Gson GSON = new Gson();

    private final Plugin plugin;
    private final Supplier<QuestConfig> config;
    private final Map<UUID, QuestData> data = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    private final Set<UUID> trackerDirty = new HashSet<>();
    private final Map<String, String> types = new LinkedHashMap<>();
    private int depth;

    QuestManager(Plugin plugin, Supplier<QuestConfig> config) {
        this.plugin = plugin;
        this.config = config;
        registerObjectiveType(ObjectiveTypes.MINE_BLOCKS, "Mine blocks");
        registerObjectiveType(ObjectiveTypes.MINE_ITEM, "Mine a block type");
        registerObjectiveType(ObjectiveTypes.SELL_CREDITS, "Earn Credits selling");
        registerObjectiveType(ObjectiveTypes.EARN_TOKENS, "Find Tokens");
        registerObjectiveType(ObjectiveTypes.RANK_UP, "Rank up");
        registerObjectiveType(ObjectiveTypes.REACH_GRADE, "Reach a grade");
        registerObjectiveType(REACH_PRESTIGE, "Reach a prestige");
        registerObjectiveType(ObjectiveTypes.PRESTIGE, "Prestige");
        registerObjectiveType(ObjectiveTypes.ENCHANT_UPGRADE, "Upgrade drill enchants");
        registerObjectiveType(ObjectiveTypes.FRAME_UPGRADE, "Install a drill frame");
        registerObjectiveType(ObjectiveTypes.CARGO_UPGRADE, "Upgrade the cargo hold");
        registerObjectiveType(ObjectiveTypes.AUTOSELL_UNLOCK, "Unlock the Auto-Seller");
        registerObjectiveType(ObjectiveTypes.DELIVER_CARGO, "Deliver cargo");
        registerObjectiveType(ObjectiveTypes.PLAY_MINUTES, "Play");
        registerObjectiveType(ObjectiveTypes.CONTRACT_COMPLETE, "Finish contracts");
    }

    QuestConfig cfg() { return config.get(); }

    // ------------------------------------------------------------------ QuestService

    @Override
    public void registerObjectiveType(String type, String displayName) {
        String key = type.toLowerCase(Locale.ROOT);
        boolean added = !types.containsKey(key);
        types.put(key, displayName);
        if (added && !data.isEmpty()) {
            // A system came online: its tutorials and contracts may now be available.
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    QuestData d = data.get(p.getUniqueId());
                    if (d != null) settle(p, d, true);
                }
            });
        }
    }

    List<String> registeredTypes() { return List.copyOf(types.keySet()); }

    @Override
    public boolean hasObjectiveType(String type) { return types.containsKey(type.toLowerCase(Locale.ROOT)); }

    @Override
    public void progress(Player player, String type, long amount, String... targets) {
        if (player == null || amount <= 0 || type == null) return;
        QuestData d = data.get(player.getUniqueId());
        if (d == null) return;
        String t = type.toLowerCase(Locale.ROOT);
        boolean abs = ABSOLUTE.contains(t);
        boolean changed = false;
        for (Active a : all(d)) {
            if (a.done) continue;
            for (int i = 0; i < a.types.length; i++) {
                if (!a.types[i].equals(t) || a.have[i] >= a.need[i] || !matches(a.targets.get(i), targets)) continue;
                long before = a.have[i];
                a.have[i] = abs ? Math.max(a.have[i], Math.min(a.need[i], amount)) : Math.min(a.need[i], a.have[i] + amount);
                if (a.have[i] == before) continue;
                changed = true;
                if (a.have[i] >= a.need[i] && !a.complete()) {
                    flash(player, "<green>✔ <white>" + a.texts[i]);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
                }
            }
        }
        if (changed) {
            markDirty(player.getUniqueId());
            settle(player, d, true);
        }
    }

    private static boolean matches(List<String> accepted, String[] reported) {
        if (accepted.isEmpty()) return true;
        if (reported == null) return false;
        for (String r : reported) if (r != null && accepted.contains(r.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    // ------------------------------------------------------------------ lifecycle

    void load(Player p, PlayerProfile profile) {
        QuestData d = null;
        String json = profile.moduleData(DATA_KEY);
        if (json != null) {
            try {
                d = GSON.fromJson(json, QuestData.class);
            } catch (JsonSyntaxException e) {
                plugin.getLogger().log(Level.WARNING, "[quests] bad quest data for " + p.getName() + ", starting fresh", e);
            }
        }
        if (d == null) d = new QuestData();
        d.normalize();
        boolean fresh = d.done.isEmpty() && d.story.isEmpty();
        data.put(p.getUniqueId(), d);
        rollIfNeeded(p, d, false);
        settle(p, d, true);
        refreshAbsolute(p, d);
        if (fresh && !d.story.isEmpty()) {
            Text.send(p, "<gray>Your quest log is open: <white>/quests</white>. Contracts reset daily and weekly.");
        }
        trackerDirty.add(p.getUniqueId());
        markDirty(p.getUniqueId());
    }

    void unload(UUID id) {
        write(id);
        data.remove(id);
        dirty.remove(id);
        trackerDirty.remove(id);
    }

    QuestData data(UUID id) { return data.get(id); }

    void markDirty(UUID id) {
        dirty.add(id);
        trackerDirty.add(id);
    }

    /** Writes dirty players' JSON into their profiles. */
    void flush() {
        for (UUID id : Set.copyOf(dirty)) write(id);
        dirty.clear();
    }

    void flushAll() {
        for (UUID id : Set.copyOf(data.keySet())) write(id);
        dirty.clear();
    }

    private void write(UUID id) {
        QuestData d = data.get(id);
        if (d == null) return;
        ProfileService profiles = Services.get(ProfileService.class);
        profiles.get(id).ifPresent(prof -> {
            prof.moduleData(DATA_KEY, GSON.toJson(d));
            profiles.markDirty(id);
        });
    }

    /** Once a minute: playtime, resets, unlock checks. */
    void minuteTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            QuestData d = data.get(p.getUniqueId());
            if (d == null) continue;
            rollIfNeeded(p, d, true);
            progress(p, ObjectiveTypes.PLAY_MINUTES, 1);
            settle(p, d, true);
        }
    }

    /** Re-checks absolute objectives (grade/prestige) and unlocks after a grade change. */
    void gradeChanged(Player p) {
        QuestData d = data.get(p.getUniqueId());
        if (d == null) return;
        refreshAbsolute(p, d);
        settle(p, d, true);
        markDirty(p.getUniqueId());
    }

    private void refreshAbsolute(Player p, QuestData d) {
        ProgressionService prog = Services.find(ProgressionService.class).orElse(null);
        if (prog == null) return;
        int grade = prog.grade(p.getUniqueId());
        progress(p, ObjectiveTypes.REACH_GRADE, grade);
        int prestige = prog.prestige(p.getUniqueId());
        if (prestige > 0) progress(p, REACH_PRESTIGE, prestige);
        long cost = prog.nextGradeCost(p.getUniqueId());
        if (cost > 0) d.lastRankCost = cost;
    }

    // ------------------------------------------------------------------ settle: completions + unlocks

    /** Pays finished quests, starts whatever unlocked, and repeats until nothing changes. */
    void settle(Player p, QuestData d, boolean announce) {
        if (depth > 4) return;
        depth++;
        try {
            for (int guard = 0; guard < 20; guard++) {
                boolean any = false;
                for (Active a : all(d)) {
                    if (!a.done && a.complete()) {
                        complete(p, d, a);
                        any = true;
                    }
                }
                if (startUnlocked(p, d, announce)) any = true;
                if (!any) break;
            }
        } finally {
            depth--;
        }
    }

    private boolean startUnlocked(Player p, QuestData d, boolean announce) {
        QuestConfig c = cfg();
        Ctx ctx = ctx(p, d);
        boolean started = false;
        // Story: one step at a time, in order.
        if (d.story.isEmpty()) {
            QuestDef next = nextStoryStep(d);
            if (next != null && eligible(next, ctx)) {
                Active a = start(next, ctx);
                d.story.put(next.id(), a);
                started = true;
                if (announce) announceStart(p, next, a);
            }
        }
        // Tutorials: every one that is unlocked.
        for (QuestDef t : c.tutorials.values()) {
            if (d.done.contains(t.id()) || d.tutorials.containsKey(t.id())) continue;
            if (!t.after().isEmpty() && !d.done.contains(t.after())) continue;
            if (!eligible(t, ctx)) continue;
            Active a = start(t, ctx);
            d.tutorials.put(t.id(), a);
            started = true;
            if (announce) {
                Text.send(p, "<#8fd3ff>New tutorial: <white>" + t.name() + "</white> <dark_gray>- <gray>" + a.texts[0]
                        + " <dark_gray>(/quests)");
            }
        }
        if (started) markDirty(p.getUniqueId());
        return started;
    }

    QuestDef nextStoryStep(QuestData d) {
        for (QuestDef s : cfg().storySteps) if (!d.done.contains(s.id())) return s;
        return null;
    }

    boolean eligible(QuestDef q, Ctx ctx) {
        if (ctx.grade < q.minGrade() || ctx.grade > q.maxGrade() || ctx.prestige < q.minPrestige()) return false;
        for (String r : q.requires()) if (!hasObjectiveType(r)) return false;
        return true;
    }

    private void complete(Player p, QuestData d, Active a) {
        a.done = true;
        Services.find(GangService.class).ifPresent(g -> g.addContractProgress(p.getUniqueId(), "quests_done", 1));
        QuestKind kind = kindOf(a);
        QuestDef def = cfg().byKey(kind, a.id);
        String rewards = pay(p, d, a.credits, a.tokens, a.shards, a.points, def == null ? List.<QuestConfig.CommandReward>of() : def.rewards().commands(),
                kind.contract() ? "income:contract" : "income:quest", "quest:" + kind.key);
        switch (kind) {
            case STORY -> {
                d.story.remove(a.id);
                d.done.add(a.id);
                QuestConfig.Character ch = def == null ? null : cfg().character(def.character());
                if (def != null) for (String line : def.outro()) say(p, ch, line);
                dev.spacepunk.prison.api.ScrapDrops.roll(p, dev.spacepunk.prison.api.ScrapService.STORY, 1);
                boolean chapterEnd = def != null && isLastOfChapter(def);
                String head = chapterEnd ? "CHAPTER COMPLETE" : "STORY";
                Text.send(p, "<#c8743a><bold>" + head + "</bold> <white>" + a.name + (rewards.isEmpty() ? "" : " <dark_gray>· " + rewards));
                if (chapterEnd) {
                    QuestConfig.Chapter chapter = cfg().chapterOf(def);
                    p.showTitle(Title.title(Text.mm("<#c8743a>" + (chapter == null ? "Chapter complete" : chapter.name())),
                            Text.mm("<gray>Chapter complete")));
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                } else {
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
                }
            }
            case TUTORIAL -> {
                d.tutorials.remove(a.id);
                d.done.add(a.id);
                if (def != null) for (String line : def.outro()) Text.send(p, "<gray>" + line);
                Text.send(p, "<#8fd3ff><bold>TUTORIAL</bold> <white>" + a.name + (rewards.isEmpty() ? "" : " <dark_gray>· " + rewards));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            }
            case DAILY, WEEKLY -> {
                d.completedContracts++;
                Text.send(p, "<gold><bold>CONTRACT</bold> <white>" + a.name + (rewards.isEmpty() ? "" : " <dark_gray>· " + rewards));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
                Bukkit.getScheduler().runTask(plugin, () -> progress(p, ObjectiveTypes.CONTRACT_COMPLETE, 1, kind.key));
                dev.spacepunk.prison.api.ScrapDrops.roll(p, dev.spacepunk.prison.api.ScrapService.CONTRACT, 1);
                if (kind == QuestKind.DAILY && !d.dailyBonus && !d.daily.isEmpty() && d.daily.stream().allMatch(x -> x.done)) {
                    d.dailyBonus = true;
                    Ctx ctx = ctx(p, d);
                    RewardDef b = cfg().dailyBonus;
                    String bonus = pay(p, d, b.credits().resolve(ctx.grade, ctx.rankCost), b.tokens().resolve(ctx.grade, ctx.rankCost),
                            b.shards().resolve(ctx.grade, ctx.rankCost), b.points().resolve(ctx.grade, ctx.rankCost), b.commands(),
                            "income:contract", "quest:daily-bonus");
                    Text.send(p, "<gold><bold>ALL DAILIES DONE</bold>" + (bonus.isEmpty() ? "" : " <dark_gray>· " + bonus));
                }
            }
        }
        if (d.tracked != null && d.tracked.equals(trackKey(kind, a, d))) d.tracked = null;
        markDirty(p.getUniqueId());
    }

    private boolean isLastOfChapter(QuestDef step) {
        QuestConfig.Chapter c = cfg().chapterOf(step);
        return c != null && !c.steps().isEmpty() && c.steps().get(c.steps().size() - 1).id().equals(step.id());
    }

    /** Pays rewards and returns a short MiniMessage summary such as "+1.2K ¢  +30 ✦". */
    String pay(Player p, QuestData d, long credits, long tokens, long shards, long points,
               List<QuestConfig.CommandReward> commands, String creditReason, String reason) {
        UUID id = p.getUniqueId();
        List<String> parts = new ArrayList<>();
        EconomyService eco = Services.find(EconomyService.class).orElse(null);
        if (eco != null) {
            if (credits > 0) { eco.give(id, Currency.CREDITS, credits, creditReason); parts.add("<gold>+" + Numbers.shortFormat(credits) + " ¢"); }
            if (tokens > 0) { eco.give(id, Currency.TOKENS, tokens, reason); parts.add("<aqua>+" + Numbers.shortFormat(tokens) + " ✦"); }
            if (shards > 0) { eco.give(id, Currency.SHARDS, shards, reason); parts.add("<light_purple>+" + Numbers.shortFormat(shards) + " ◆"); }
        }
        if (points > 0) {
            d.points += points;
            parts.add("<yellow>+" + points + " pts");
        }
        for (QuestConfig.CommandReward c : commands) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.command().replace("{player}", p.getName()).replace("{uuid}", id.toString()));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "[quests] reward command failed: " + c.command(), e);
            }
            if (!c.text().isEmpty()) parts.add(c.text());
        }
        return String.join(" ", parts);
    }

    // ------------------------------------------------------------------ starting quests

    /** What a quest is resolved against when it starts. */
    record Ctx(int grade, int prestige, long rankCost, String mineId, String mineName, int mineGrade, int deck, String deckName) {}

    Ctx ctx(Player p, QuestData d) {
        UUID id = p.getUniqueId();
        ProgressionService prog = Services.find(ProgressionService.class).orElse(null);
        int grade = prog == null ? 1 : prog.grade(id);
        int prestige = prog == null ? 0 : prog.prestige(id);
        long cost = prog == null ? 0 : prog.nextGradeCost(id);
        if (cost > 0) d.lastRankCost = cost;
        else cost = d.lastRankCost;
        String mineId = "", mineName = "your mine";
        int mineGrade = grade;
        MineService mines = Services.find(MineService.class).orElse(null);
        if (mines != null) {
            MineService.MineInfo best = null;
            for (MineService.MineInfo m : mines.mines()) {
                if (m.requiredGrade() <= grade && (best == null || m.requiredGrade() >= best.requiredGrade())) best = m;
            }
            if (best != null) { mineId = best.id(); mineName = best.displayName(); mineGrade = best.requiredGrade(); }
        }
        int deck = Math.max(1, Math.min(5, (grade - 1) / 5 + 1));
        String deckName = prog == null ? "Deck " + deck : prog.deckName(grade);
        return new Ctx(grade, prestige, cost, mineId, mineName, mineGrade, deck, deckName);
    }

    Active start(QuestDef q, Ctx ctx) {
        Active a = new Active();
        a.id = q.id();
        a.kind = q.kind().key;
        a.name = q.name();
        int n = q.objectives().size();
        a.types = new String[n];
        a.texts = new String[n];
        a.have = new long[n];
        a.need = new long[n];
        a.targets = new ArrayList<>();
        List<String> items = cfg().mineItems.getOrDefault(ctx.mineGrade, List.of());
        for (int i = 0; i < n; i++) {
            ObjectiveDef o = q.objectives().get(i);
            String item = items.isEmpty() ? "minecraft:stone" : items.get(ThreadLocalRandom.current().nextInt(items.size()));
            List<String> targets = new ArrayList<>();
            for (String t : o.targets()) {
                targets.add(t.replace("{mine}", "mine:" + ctx.mineId).replace("{deck}", "deck:" + ctx.deck)
                        .replace("{grade}", "grade:" + ctx.grade).replace("{mine_item}", item));
            }
            long need = Math.max(1, o.amount().resolve(ctx.grade, ctx.rankCost));
            a.types[i] = o.type();
            a.targets.add(targets);
            a.need[i] = need;
            a.texts[i] = o.text().replace("{amount}", Numbers.shortFormat(need)).replace("{mine_name}", ctx.mineName)
                    .replace("{deck_name}", ctx.deckName).replace("{item_name}", itemName(item))
                    .replace("{target_name}", targets.isEmpty() ? "" : itemName(targets.get(0)))
                    .replace("{grade}", String.valueOf(ctx.grade));
            if (o.type().equals(ObjectiveTypes.REACH_GRADE)) a.have[i] = Math.min(need, ctx.grade);
            if (o.type().equals(REACH_PRESTIGE)) a.have[i] = Math.min(need, ctx.prestige);
        }
        RewardDef r = q.rewards();
        a.credits = r.credits().resolve(ctx.grade, ctx.rankCost);
        a.tokens = r.tokens().resolve(ctx.grade, ctx.rankCost);
        a.shards = r.shards().resolve(ctx.grade, ctx.rankCost);
        a.points = r.points().resolve(ctx.grade, ctx.rankCost);
        return a;
    }

    private static String itemName(String itemId) {
        return Services.find(ProgressionService.class).map(p -> p.itemName(itemId)).orElse(itemId);
    }

    private void announceStart(Player p, QuestDef q, Active a) {
        QuestConfig.Character ch = cfg().character(q.character());
        QuestConfig.Chapter chapter = cfg().chapterOf(q);
        if (chapter != null && !chapter.steps().isEmpty() && chapter.steps().get(0).id().equals(q.id())) {
            p.showTitle(Title.title(Text.mm("<#c8743a>" + chapter.name()), Text.mm("<gray>" + cfg().storyTitle)));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.8f);
        }
        for (String line : q.intro()) say(p, ch, line);
        StringBuilder sb = new StringBuilder("<#c8743a>Story: <white>" + q.name());
        for (String t : a.texts) sb.append("\n  <dark_gray>▸ <gray>").append(t);
        Text.send(p, sb.toString());
    }

    void say(Player p, QuestConfig.Character ch, String line) {
        String who = ch == null ? "<gray>???" : ch.display();
        p.sendMessage(Text.mm(who + "<dark_gray>: <gray>" + line.replace("{player}", p.getName())));
    }

    // ------------------------------------------------------------------ contracts

    void rollIfNeeded(Player p, QuestData d, boolean announce) {
        QuestConfig c = cfg();
        String day = Periods.day(c.zone), week = Periods.week(c.zone), month = Periods.month(c.zone);
        boolean changed = false;
        if (!month.equals(d.monthKey)) {
            if (!d.monthKey.isEmpty() && announce) Text.send(p, "<yellow>A new month on the " + c.trackName + ". Points reset.");
            d.monthKey = month;
            d.points = 0;
            d.claimed.clear();
            changed = true;
        }
        if (!day.equals(d.dailyKey)) {
            d.dailyKey = day;
            d.daily = roll(p, d, c.daily, c.dailyCount);
            d.dailyBonus = false;
            changed = true;
            if (announce) Text.send(p, "<gold>New daily contracts are posted. <white>/contracts");
        }
        if (!week.equals(d.weeklyKey)) {
            d.weeklyKey = week;
            d.weekly = roll(p, d, c.weekly, c.weeklyCount);
            changed = true;
            if (announce) Text.send(p, "<gold>New weekly contracts are posted. <white>/contracts");
        }
        if (changed) markDirty(p.getUniqueId());
    }

    List<Active> roll(Player p, QuestData d, Map<String, QuestDef> pool, int count) {
        Ctx ctx = ctx(p, d);
        List<QuestDef> options = new ArrayList<>();
        for (QuestDef q : pool.values()) if (eligible(q, ctx)) options.add(q);
        List<Active> out = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (out.size() < count && !options.isEmpty()) {
            int total = options.stream().mapToInt(QuestDef::weight).sum();
            int pick = rnd.nextInt(total);
            QuestDef chosen = options.get(options.size() - 1);
            for (QuestDef q : options) {
                pick -= q.weight();
                if (pick < 0) { chosen = q; break; }
            }
            options.remove(chosen);
            out.add(start(chosen, ctx));
        }
        return out;
    }

    /** Admin: force a new roll now. */
    void reroll(Player p) {
        QuestData d = data.get(p.getUniqueId());
        if (d == null) return;
        d.dailyKey = "";
        d.weeklyKey = "";
        rollIfNeeded(p, d, false);
        settle(p, d, true);
    }

    /** Claims a track tier. Returns a message for the player. */
    String claimTier(Player p, int index) {
        QuestData d = data.get(p.getUniqueId());
        QuestConfig c = cfg();
        if (d == null || index < 0 || index >= c.tiers.size()) return "<red>Unknown tier.";
        QuestConfig.TrackTier t = c.tiers.get(index);
        if (d.claimed.contains(index)) return "<gray>Already claimed.";
        if (d.points < t.points()) return "<red>Needs " + t.points() + " points (you have " + d.points + ").";
        d.claimed.add(index);
        Ctx ctx = ctx(p, d);
        RewardDef r = t.rewards();
        String got = pay(p, d, r.credits().resolve(ctx.grade, ctx.rankCost), r.tokens().resolve(ctx.grade, ctx.rankCost),
                r.shards().resolve(ctx.grade, ctx.rankCost), 0, r.commands(), "income:contract", "quest:track");
        markDirty(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        return "<yellow>Tier " + (index + 1) + " claimed: " + got;
    }

    /** Hands in deliverable cargo for every deliver objective of the quest. Returns units handed in. */
    long deliver(Player p, Active a) {
        ProgressionService prog = Services.find(ProgressionService.class).orElse(null);
        QuestData d = data.get(p.getUniqueId());
        if (prog == null || d == null || a.done) return 0;
        long total = 0;
        for (int i = 0; i < a.types.length; i++) {
            if (!a.types[i].equals(ObjectiveTypes.DELIVER_CARGO) || a.have[i] >= a.need[i] || a.targets.get(i).isEmpty()) continue;
            String item = a.targets.get(i).get(0);
            long want = a.need[i] - a.have[i];
            long got = prog.cargoWithdraw(p.getUniqueId(), item, Math.min(want, prog.cargoAmount(p.getUniqueId(), item)));
            if (got > 0) {
                a.have[i] += got;
                total += got;
            }
        }
        if (total > 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.8f, 1f);
            markDirty(p.getUniqueId());
            settle(p, d, true);
        }
        return total;
    }

    static boolean hasDelivery(Active a) {
        for (int i = 0; i < a.types.length; i++) if (a.types[i].equals(ObjectiveTypes.DELIVER_CARGO) && a.have[i] < a.need[i]) return true;
        return false;
    }

    // ------------------------------------------------------------------ admin helpers

    void reset(Player p, String what) {
        QuestData d = data.get(p.getUniqueId());
        if (d == null) return;
        switch (what) {
            case "story" -> { d.story.clear(); d.done.removeIf(id -> id.contains(".")); }
            case "tutorials" -> { d.tutorials.clear(); d.done.removeIf(id -> !id.contains(".")); }
            case "contracts" -> { d.dailyKey = ""; d.weeklyKey = ""; }
            case "track" -> { d.points = 0; d.claimed.clear(); }
            default -> {
                QuestData fresh = new QuestData();
                data.put(p.getUniqueId(), fresh);
                d = fresh;
            }
        }
        d.tracked = null;
        rollIfNeeded(p, d, false);
        settle(p, d, false);
        refreshAbsolute(p, d);
        markDirty(p.getUniqueId());
    }

    /** Admin: completes the active quest with this key (see {@link #find}). */
    boolean forceComplete(Player p, String key) {
        QuestData d = data.get(p.getUniqueId());
        Active a = d == null ? null : find(d, key);
        if (a == null || a.done) return false;
        System.arraycopy(a.need, 0, a.have, 0, a.need.length);
        markDirty(p.getUniqueId());
        settle(p, d, true);
        return true;
    }

    // ------------------------------------------------------------------ lookup + tracker

    static List<Active> all(QuestData d) {
        List<Active> out = new ArrayList<>(d.story.values());
        out.addAll(d.tutorials.values());
        out.addAll(d.daily);
        out.addAll(d.weekly);
        return out;
    }

    static QuestKind kindOf(Active a) {
        for (QuestKind k : QuestKind.values()) if (k.key.equals(a.kind)) return k;
        return QuestKind.TUTORIAL;
    }

    static String trackKey(QuestKind kind, Active a, QuestData d) {
        return switch (kind) {
            case DAILY -> "daily:" + d.daily.indexOf(a);
            case WEEKLY -> "weekly:" + d.weekly.indexOf(a);
            default -> kind.key + ":" + a.id;
        };
    }

    /** Finds an active quest by key: "story:id", "tutorial:id", "daily:N", "weekly:N". */
    static Active find(QuestData d, String key) {
        if (key == null) return null;
        int c = key.indexOf(':');
        if (c < 0) return null;
        String kind = key.substring(0, c), rest = key.substring(c + 1);
        try {
            return switch (kind) {
                case "story" -> d.story.get(rest);
                case "tutorial" -> d.tutorials.get(rest);
                case "daily" -> d.daily.get(Integer.parseInt(rest));
                case "weekly" -> d.weekly.get(Integer.parseInt(rest));
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    void track(Player p, String key) {
        QuestData d = data.get(p.getUniqueId());
        if (d == null) return;
        d.tracked = key;
        markDirty(p.getUniqueId());
    }

    /** The quest shown on the sidebar: the tracked one, else the story step, else the first open contract. */
    Active trackedQuest(QuestData d) {
        Active a = find(d, d.tracked);
        if (a != null && !a.done) return a;
        for (Active s : d.story.values()) if (!s.done) return s;
        for (Active s : d.daily) if (!s.done) return s;
        for (Active s : d.tutorials.values()) if (!s.done) return s;
        for (Active s : d.weekly) if (!s.done) return s;
        return null;
    }

    /** Pushes the tracker to the HUD sidebar for players whose quests changed. */
    void flushTracker() {
        if (trackerDirty.isEmpty()) return;
        HudService hud = Services.find(HudService.class).orElse(null);
        for (UUID id : Set.copyOf(trackerDirty)) {
            Player p = Bukkit.getPlayer(id);
            QuestData d = data.get(id);
            if (p == null || d == null || hud == null) continue;
            hud.sidebarSection(p, DATA_KEY, cfg().sidebar ? trackerLines(d) : null);
        }
        trackerDirty.clear();
    }

    void clearTrackers() {
        HudService hud = Services.find(HudService.class).orElse(null);
        if (hud == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) hud.sidebarSection(p, DATA_KEY, null);
    }

    private List<String> trackerLines(QuestData d) {
        Active a = trackedQuest(d);
        if (a == null) return null;
        int i = Math.max(0, a.current());
        String label = switch (kindOf(a)) {
            case STORY -> "STORY";
            case TUTORIAL -> "TUTORIAL";
            case DAILY -> "DAILY";
            case WEEKLY -> "WEEKLY";
        };
        double frac = a.need[i] <= 0 ? 1 : Math.min(1.0, (double) a.have[i] / a.need[i]);
        return List.of(HEAD + label,
                " <white>" + shorten(Text.MM.stripTags(a.texts[i]), 24),
                " " + bar(frac) + " <gray>" + Numbers.shortFormat(a.have[i]) + "<dark_gray>/</dark_gray>" + Numbers.shortFormat(a.need[i]));
    }

    static String bar(double frac) {
        int cells = 10, filled = (int) Math.round(Math.max(0, Math.min(1, frac)) * cells);
        return "<dark_gray>[</dark_gray><#e0c040>" + "|".repeat(filled) + "</#e0c040><#3a3028>" + "|".repeat(cells - filled)
                + "</#3a3028><dark_gray>]</dark_gray>";
    }

    static String shorten(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }

    private void flash(Player p, String mm) {
        HudService hud = Services.find(HudService.class).orElse(null);
        if (hud != null) hud.flash(p, mm);
        else p.sendActionBar(Text.mm(mm));
    }

    // ------------------------------------------------------------------ display helpers for the menu

    String resetIn(QuestKind kind) {
        QuestConfig c = cfg();
        Duration d = switch (kind) {
            case DAILY -> Periods.untilDay(c.zone);
            case WEEKLY -> Periods.untilWeek(c.zone);
            default -> Periods.untilMonth(c.zone);
        };
        return Periods.format(d);
    }

    String resetInMonth() { return Periods.format(Periods.untilMonth(cfg().zone)); }

    String rewardText(Active a) {
        List<String> parts = new ArrayList<>();
        if (a.credits > 0) parts.add("<gold>" + Numbers.shortFormat(a.credits) + " ¢");
        if (a.tokens > 0) parts.add("<aqua>" + Numbers.shortFormat(a.tokens) + " ✦");
        if (a.shards > 0) parts.add("<light_purple>" + Numbers.shortFormat(a.shards) + " ◆");
        if (a.points > 0) parts.add("<yellow>" + a.points + " pts");
        QuestDef def = cfg().byKey(kindOf(a), a.id);
        if (def != null) for (QuestConfig.CommandReward c : def.rewards().commands()) if (!c.text().isEmpty()) parts.add(c.text());
        return String.join("<dark_gray>, ", parts);
    }
}
