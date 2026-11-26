package dev.spacepunk.prison.gangs;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.GangMembershipEvent;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Gang rules: membership, ranks, the bank and its prestige lock, points, contracts, upkeep, turf. Main thread. */
final class GangManager implements GangService {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9]+");
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;

    private final Supplier<GangsConfig> cfg;
    private final GangStore store;
    private final GangStore.Root root;
    /** Live gangs by id; also root.gangs, so it is what gets saved. Concurrent for async chat reads. */
    private final Map<String, Gang> gangs = new ConcurrentHashMap<>();
    private final Map<UUID, String> memberIndex = new ConcurrentHashMap<>();
    /** invitee -> (gang id -> expiry ms) */
    private final Map<UUID, Map<String, Long>> invites = new HashMap<>();
    private final Set<UUID> chatToggled = ConcurrentHashMap.newKeySet();
    private final Map<String, Double> pointRemainder = new HashMap<>();
    private final Map<String, String> objectives = new LinkedHashMap<>();
    private final Map<UUID, Long> disbandConfirm = new HashMap<>();

    GangManager(Supplier<GangsConfig> cfg, GangStore store) {
        this.cfg = cfg;
        this.store = store;
        this.root = store.load();
        gangs.putAll(root.gangs);
        root.gangs = gangs;
        for (Gang g : gangs.values()) {
            for (String k : g.members.keySet()) memberIndex.put(UUID.fromString(k), g.id);
        }
        registerContractObjective("blocks_mined", "Blocks mined");
        registerContractObjective("ore_sold", "Ore sold");
        registerContractObjective("credits_earned", "Credits earned");
        registerContractObjective("rankups", "Grades bought");
        registerContractObjective("prestiges", "Prestiges");
    }

    GangsConfig cfg() { return cfg.get(); }

    void save() { store.saveIfDirty(root); }

    void shutdown() { store.shutdown(root); }

    private void dirty() { store.markDirty(); }

    // =====================================================================================
    // GangService
    // =====================================================================================

    @Override public Optional<GangInfo> gangOf(UUID player) { return Optional.ofNullable(gangFor(player)); }

    @Override public Optional<GangInfo> gang(String gangId) { return Optional.ofNullable(gangId == null ? null : gangs.get(gangId)); }

    @Override public boolean sameGang(UUID a, UUID b) {
        String ga = memberIndex.get(a);
        return ga != null && ga.equals(memberIndex.get(b));
    }

    @Override public void registerContractObjective(String objective, String displayName) {
        objectives.put(objective.toLowerCase(Locale.ROOT), displayName);
    }

    @Override public void addContractProgress(UUID player, String objective, long amount) {
        Gang g = gangFor(player);
        if (g == null || amount <= 0) return;
        progress(g, objective.toLowerCase(Locale.ROOT), amount);
    }

    @Override public void addPoints(UUID player, long points, String reason) {
        Gang g = gangFor(player);
        if (g == null || points <= 0) return;
        addPointsRaw(g, player, points);
    }

    @Override public long depositFromServer(String gangId, long credits, String reason) {
        Gang g = gangs.get(gangId);
        if (g == null || credits <= 0) return 0;
        long fit = Math.min(credits, Math.max(0, cfg().bankCapacity(g) - g.bank));
        if (fit <= 0) return 0;
        g.bank += fit;
        g.log("Station", "payout: " + (reason == null ? "server" : reason), fit, cfg().logSize);
        dirty();
        return fit;
    }

    @Override public Optional<String> territoryOwner(String territoryId) { return Optional.ofNullable(root.territories.get(territoryId)); }

    @Override public void setTerritoryOwner(String territoryId, String gangId) {
        String old = root.territories.get(territoryId);
        if (gangId != null && !gangs.containsKey(gangId)) return;
        if (java.util.Objects.equals(old, gangId)) return;
        String name = cfg().territoryName(territoryId);
        if (gangId == null) root.territories.remove(territoryId);
        else root.territories.put(territoryId, gangId);
        dirty();
        Gang oldGang = old == null ? null : gangs.get(old);
        Gang newGang = gangId == null ? null : gangs.get(gangId);
        if (newGang != null) {
            broadcastAll("<dark_red>☠ <gray>[" + newGang.coloredTag() + "<gray>] <white>" + newGang.name
                    + "</white> seized the <#c8743a>" + name + "</#c8743a>"
                    + (oldGang != null ? " from <white>" + oldGang.name + "</white>." : "."));
        } else if (oldGang != null) {
            broadcastAll("<gray>The <#c8743a>" + name + "</#c8743a> is no longer held by <white>" + oldGang.name + "</white>.");
        }
    }

    @Override public Map<String, String> territories() { return Map.copyOf(root.territories); }

    // =====================================================================================
    // Lookups
    // =====================================================================================

    Gang gangFor(UUID player) {
        String id = memberIndex.get(player);
        return id == null ? null : gangs.get(id);
    }

    Gang byName(String name) {
        if (name == null) return null;
        for (Gang g : gangs.values()) {
            if (g.name.equalsIgnoreCase(name) || g.tag.equalsIgnoreCase(name)) return g;
        }
        return null;
    }

    List<Gang> all() { return new ArrayList<>(gangs.values()); }

    Map<String, String> objectives() { return Collections.unmodifiableMap(objectives); }

    long weekStart() { return root.weekStart; }

    boolean chatToggled(UUID p) { return chatToggled.contains(p); }

    static UUID memberByName(Gang g, String name) {
        for (Map.Entry<String, Gang.Member> e : g.members.entrySet()) {
            if (e.getValue().name != null && e.getValue().name.equalsIgnoreCase(name)) return UUID.fromString(e.getKey());
        }
        return null;
    }

    private static int prestigeOf(UUID p) {
        return Services.find(ProgressionService.class).map(s -> s.prestige(p)).orElse(Integer.MAX_VALUE);
    }

    private static int gradeOf(UUID p) {
        return Services.find(ProgressionService.class).map(s -> s.grade(p)).orElse(1);
    }

    private static EconomyService eco() { return Services.get(EconomyService.class); }

    // =====================================================================================
    // Membership
    // =====================================================================================

    void create(Player p, String name, String tag) {
        GangsConfig c = cfg();
        UUID id = p.getUniqueId();
        if (gangFor(id) != null) { Text.send(p, "<red>You're already in a gang. <gray>/gang leave</gray> first."); return; }
        if (prestigeOf(id) < c.createPrestige) {
            Text.send(p, "<red>Gangs unlock at Prestige " + c.createPrestige + ".");
            return;
        }
        if (name.length() < c.nameMin || name.length() > c.nameMax || !NAME.matcher(name).matches()) {
            Text.send(p, "<red>Gang names are " + c.nameMin + "-" + c.nameMax + " letters, numbers or _.");
            return;
        }
        if (tag.length() < c.tagMin || tag.length() > c.tagMax || !TAG.matcher(tag).matches()) {
            Text.send(p, "<red>Tags are " + c.tagMin + "-" + c.tagMax + " letters or numbers.");
            return;
        }
        tag = tag.toUpperCase(Locale.ROOT);
        for (Gang g : gangs.values()) {
            if (g.name.equalsIgnoreCase(name)) { Text.send(p, "<red>A gang called " + name + " already exists."); return; }
            if (g.tag.equalsIgnoreCase(tag)) { Text.send(p, "<red>The tag " + tag + " is taken."); return; }
        }
        if (c.createCost > 0 && !eco().take(id, Currency.CREDITS, c.createCost, "gang:create")) {
            Text.send(p, "<red>Founding a gang costs " + eco().format(Currency.CREDITS, c.createCost) + "<red>.");
            return;
        }
        Gang g = new Gang();
        g.id = newId();
        g.name = name;
        g.tag = tag;
        g.color = c.colors.values().iterator().next();
        g.created = System.currentTimeMillis();
        g.nextUpkeepAt = g.created + c.upkeepIntervalHours * HOUR;
        gangs.put(g.id, g);
        addMember(g, p, Rank.LEADER);
        rollContracts(g);
        dirty();
        Text.send(p, "<green>You founded <white>" + name + "</white> [" + g.coloredTag() + "<green>]. <gray>/gang invite <player></gray> to recruit.");
        broadcastAll("<gray>A new crew runs the decks: [" + g.coloredTag() + "<gray>] <white>" + name + "</white>.");
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 0.8f);
    }

    private String newId() {
        String id;
        do {
            id = Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xffffffffffL);
        } while (gangs.containsKey(id));
        return id;
    }

    private void addMember(Gang g, Player p, Rank rank) {
        Gang.Member m = new Gang.Member();
        m.name = p.getName();
        m.rank = rank;
        m.joined = System.currentTimeMillis();
        m.grade = gradeOf(p.getUniqueId());
        g.members.put(p.getUniqueId().toString(), m);
        memberIndex.put(p.getUniqueId(), g.id);
        Gang.Parked pk = g.parked.get(p.getUniqueId().toString());
        if (pk != null) pk.leftAt = 0;
        invites.remove(p.getUniqueId());
        Bukkit.getPluginManager().callEvent(new GangMembershipEvent(p.getUniqueId(), g.id, GangMembershipEvent.Change.JOINED));
    }

    private void removeMember(Gang g, UUID id, GangMembershipEvent.Change why) {
        g.members.remove(id.toString());
        memberIndex.remove(id);
        chatToggled.remove(id);
        Gang.Parked pk = g.parked.get(id.toString());
        if (pk != null) {
            if (pk.amount <= 0) g.parked.remove(id.toString());
            else pk.leftAt = System.currentTimeMillis();
        }
        dirty();
        Bukkit.getPluginManager().callEvent(new GangMembershipEvent(id, g.id, why));
    }

    void invite(Player p, Player target) {
        Gang g = requireGang(p);
        if (g == null || !hasRank(p, g, cfg().rankInvite, "invite")) return;
        UUID t = target.getUniqueId();
        if (t.equals(p.getUniqueId())) { Text.send(p, "<red>You can't invite yourself."); return; }
        if (gangFor(t) != null) { Text.send(p, "<red>" + target.getName() + " is already in a gang."); return; }
        if (prestigeOf(t) < cfg().joinPrestige) {
            Text.send(p, "<red>" + target.getName() + " needs Prestige " + cfg().joinPrestige + " to join a gang.");
            return;
        }
        if (g.members.size() >= cfg().maxMembers(g)) {
            Text.send(p, "<red>No free bunks (" + g.members.size() + "/" + cfg().maxMembers(g) + "). Buy the Bunks upgrade.");
            return;
        }
        invites.computeIfAbsent(t, k -> new HashMap<>()).put(g.id, System.currentTimeMillis() + cfg().inviteSeconds * 1000L);
        Text.send(p, "<gray>Invited <white>" + target.getName() + "</white>. It expires in " + (cfg().inviteSeconds / 60) + " min.");
        Text.send(target, "<white>" + p.getName() + "</white><gray> invited you to [" + g.coloredTag() + "<gray>] <white>" + g.name
                + "</white>. <click:run_command:'/gang accept " + g.name + "'><green><u>Accept</u></green></click> <dark_gray>or</dark_gray> "
                + "<click:run_command:'/gang deny " + g.name + "'><red><u>Deny</u></red></click>");
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
    }

    void accept(Player p, String gangName) {
        UUID id = p.getUniqueId();
        if (gangFor(id) != null) { Text.send(p, "<red>You're already in a gang."); return; }
        Map<String, Long> mine = invites.getOrDefault(id, Map.of());
        long now = System.currentTimeMillis();
        Gang g = null;
        for (Map.Entry<String, Long> e : mine.entrySet()) {
            if (e.getValue() < now) continue;
            Gang cand = gangs.get(e.getKey());
            if (cand == null) continue;
            if (gangName == null || cand.name.equalsIgnoreCase(gangName) || cand.tag.equalsIgnoreCase(gangName)) {
                g = cand;
                break;
            }
        }
        if (g == null) { Text.send(p, "<red>No open invite" + (gangName == null ? "." : " from " + gangName + ".")); return; }
        if (prestigeOf(id) < cfg().joinPrestige) { Text.send(p, "<red>You need Prestige " + cfg().joinPrestige + " to join a gang."); return; }
        if (g.members.size() >= cfg().maxMembers(g)) { Text.send(p, "<red>" + g.name + " has no free bunks."); return; }
        addMember(g, p, Rank.RECRUIT);
        dirty();
        broadcast(g, "<green>" + p.getName() + " joined the crew.");
        if (!g.motd.isBlank()) Text.send(p, "<gray>Crew notice: <white>" + g.motd);
    }

    void deny(Player p, String gangName) {
        Map<String, Long> mine = invites.get(p.getUniqueId());
        if (mine == null || mine.isEmpty()) { Text.send(p, "<gray>No invites."); return; }
        if (gangName == null) mine.clear();
        else mine.keySet().removeIf(k -> { Gang g = gangs.get(k); return g == null || g.name.equalsIgnoreCase(gangName) || g.tag.equalsIgnoreCase(gangName); });
        Text.send(p, "<gray>Invite declined.");
    }

    void leave(Player p) {
        Gang g = requireGang(p);
        if (g == null) return;
        if (g.rank(p.getUniqueId()) == Rank.LEADER) {
            if (g.members.size() > 1) {
                Text.send(p, "<red>Hand over the crew first: <white>/gang transfer <player></white>, or <white>/gang disband</white>.");
                return;
            }
            disbandNow(g, p.getName());
            return;
        }
        removeMember(g, p.getUniqueId(), GangMembershipEvent.Change.LEFT);
        Text.send(p, "<gray>You left <white>" + g.name + "</white>.");
        broadcast(g, "<gray>" + p.getName() + " left the crew.");
    }

    void kick(Player p, String name) {
        Gang g = requireGang(p);
        if (g == null || !hasRank(p, g, cfg().rankKick, "kick")) return;
        UUID t = memberByName(g, name);
        if (t == null) { Text.send(p, "<red>" + name + " isn't in your gang."); return; }
        if (!g.rank(p.getUniqueId()).atLeast(g.rank(t)) || g.rank(p.getUniqueId()) == g.rank(t)) {
            Text.send(p, "<red>You can only kick lower ranks.");
            return;
        }
        String tn = g.member(t).name;
        removeMember(g, t, GangMembershipEvent.Change.KICKED);
        broadcast(g, "<gray>" + tn + " was kicked by " + p.getName() + ".");
        Player tp = Bukkit.getPlayer(t);
        if (tp != null) Text.send(tp, "<red>You were kicked from " + g.name + ".");
    }

    void promote(Player p, String name, boolean up) {
        Gang g = requireGang(p);
        if (g == null) return;
        Rank mine = g.rank(p.getUniqueId());
        UUID t = memberByName(g, name);
        if (t == null) { Text.send(p, "<red>" + name + " isn't in your gang."); return; }
        Rank theirs = g.rank(t);
        Rank next = up ? theirs.up() : theirs.down();
        // Leader can set anyone up to officer; officers can only move recruits <-> members.
        boolean ok = mine == Rank.LEADER ? theirs != Rank.LEADER
                : mine == Rank.OFFICER && theirs.ordinal() < Rank.OFFICER.ordinal() && next.ordinal() < Rank.OFFICER.ordinal();
        if (!ok || next == theirs) {
            Text.send(p, "<red>You can't " + (up ? "promote" : "demote") + " " + name + (next == theirs ? " any further." : "."));
            return;
        }
        g.member(t).rank = next;
        dirty();
        broadcast(g, "<gray>" + g.member(t).name + " is now " + next.colored() + "<gray>.");
        Bukkit.getPluginManager().callEvent(new GangMembershipEvent(t, g.id, GangMembershipEvent.Change.RANK));
    }

    void transfer(Player p, String name) {
        Gang g = requireGang(p);
        if (g == null) return;
        if (g.rank(p.getUniqueId()) != Rank.LEADER) { Text.send(p, "<red>Only the leader can hand over the crew."); return; }
        UUID t = memberByName(g, name);
        if (t == null || t.equals(p.getUniqueId())) { Text.send(p, "<red>" + name + " isn't another member of your gang."); return; }
        g.member(t).rank = Rank.LEADER;
        g.member(p.getUniqueId()).rank = Rank.OFFICER;
        dirty();
        broadcast(g, "<#c8743a>" + g.member(t).name + " now leads the crew.");
        Bukkit.getPluginManager().callEvent(new GangMembershipEvent(t, g.id, GangMembershipEvent.Change.RANK));
    }

    void disband(Player p, boolean confirm) {
        Gang g = requireGang(p);
        if (g == null) return;
        if (g.rank(p.getUniqueId()) != Rank.LEADER) { Text.send(p, "<red>Only the leader can disband the crew."); return; }
        Long asked = disbandConfirm.get(p.getUniqueId());
        if (!confirm || asked == null || System.currentTimeMillis() - asked > 30_000) {
            disbandConfirm.put(p.getUniqueId(), System.currentTimeMillis());
            Text.send(p, "<red>This deletes " + g.name + " for good" + (g.bank > 0 ? ", and the " + eco().format(Currency.CREDITS, g.bank) + "<red> in the bank is lost" : "")
                    + ". <click:run_command:'/gang disband confirm'><dark_red><u>Click to confirm</u></dark_red></click> <gray>(30s)");
            return;
        }
        disbandConfirm.remove(p.getUniqueId());
        disbandNow(g, p.getName());
    }

    void disbandNow(Gang g, String by) {
        broadcast(g, "<red>" + g.name + " was disbanded by " + by + ".");
        for (UUID m : g.members()) {
            memberIndex.remove(m);
            chatToggled.remove(m);
            Bukkit.getPluginManager().callEvent(new GangMembershipEvent(m, g.id, GangMembershipEvent.Change.DISBANDED));
        }
        gangs.remove(g.id);
        root.territories.values().removeIf(id -> id.equals(g.id));
        for (Map<String, Long> inv : invites.values()) inv.remove(g.id);
        dirty();
    }

    void setColor(Player p, String color) {
        Gang g = requireGang(p);
        if (g == null) return;
        if (g.rank(p.getUniqueId()) != Rank.LEADER) { Text.send(p, "<red>Only the leader can change the tag color."); return; }
        String hex = cfg().colors.get(color.toLowerCase(Locale.ROOT));
        if (hex == null) { Text.send(p, "<red>Colors: <white>" + String.join(", ", cfg().colors.keySet())); return; }
        g.color = hex;
        dirty();
        broadcast(g, "<gray>Crew tag is now [" + g.coloredTag() + "<gray>].");
        Bukkit.getPluginManager().callEvent(new GangMembershipEvent(null, g.id, GangMembershipEvent.Change.UPDATED));
    }

    void setMotd(Player p, String motd) {
        Gang g = requireGang(p);
        if (g == null || !hasRank(p, g, cfg().rankMotd, "set the notice")) return;
        g.motd = motd.length() > 120 ? motd.substring(0, 120) : motd;
        dirty();
        broadcast(g, "<gray>New crew notice: <white>" + Text.MM.escapeTags(g.motd));
    }

    void toggleChat(Player p) {
        if (requireGang(p) == null) return;
        if (chatToggled.remove(p.getUniqueId())) Text.send(p, "<gray>Chat goes to <white>everyone</white> again.");
        else {
            chatToggled.add(p.getUniqueId());
            Text.send(p, "<gray>Chat now goes to your <white>crew</white>. <white>/gang chat</white> to switch back.");
        }
    }

    /** Sends a gang chat line from a player to every online member. Main thread. */
    void chat(Player p, String message) {
        Gang g = gangFor(p.getUniqueId());
        if (g == null) { Text.send(p, "<red>You're not in a gang."); chatToggled.remove(p.getUniqueId()); return; }
        Rank r = g.rank(p.getUniqueId());
        String line = cfg().chatFormat
                .replace("<color>", "<" + g.color + ">").replace("</color>", "</" + g.color + ">")
                .replace("{tag}", g.tag)
                .replace("{rank}", r == null ? "" : r.display)
                .replace("{player}", p.getName())
                .replace("{message}", Text.MM.escapeTags(message));
        var comp = Text.mm(line);
        for (UUID m : g.members()) {
            Player op = Bukkit.getPlayer(m);
            if (op != null) op.sendMessage(comp);
        }
        Bukkit.getConsoleSender().sendMessage(comp);
    }

    // =====================================================================================
    // Bank (Credits) and the prestige lock
    // =====================================================================================

    /** Parked Credits of others that nobody but their owner may withdraw. */
    private long lockedFor(Gang g, UUID who) {
        GangsConfig c = cfg();
        long locked = 0;
        for (Map.Entry<String, Gang.Parked> e : g.parked.entrySet()) {
            if (e.getKey().equals(who.toString())) continue;
            if (isLocked(e.getValue(), c)) locked += e.getValue().amount;
        }
        return Math.min(locked, g.bank);
    }

    private static boolean isLocked(Gang.Parked pk, GangsConfig c) {
        if (pk.leftAt > 0) return true;
        return c.depositLockGrade > 0 && pk.grade >= c.depositLockGrade;
    }

    long withdrawable(Gang g, UUID who) { return Math.max(0, g.bank - lockedFor(g, who)); }

    long parkedBy(Gang g, UUID who) {
        Gang.Parked pk = g.parked.get(who.toString());
        return pk == null ? 0 : pk.amount;
    }

    void deposit(Player p, String arg) {
        Gang g = requireGang(p);
        if (g == null) return;
        GangsConfig c = cfg();
        UUID id = p.getUniqueId();
        int grade = gradeOf(id);
        if (c.depositLockGrade > 0 && grade >= c.depositLockGrade) {
            Text.send(p, "<red>Prestige lock: you can't deposit from Grade " + c.depositLockGrade
                    + " on. <gray>Credits parked in the bank would be wiped when you prestige anyway.");
            return;
        }
        long wallet = eco().balance(id, Currency.CREDITS);
        long room = Math.max(0, c.bankCapacity(g) - g.bank);
        long amount = parseAmount(p, arg, wallet);
        if (amount < 0) return;
        amount = Math.min(amount, room);
        if (amount <= 0) { Text.send(p, room <= 0 ? "<red>The bank is full. Buy the Strongbox upgrade." : "<red>Nothing to deposit."); return; }
        if (amount < c.minDeposit) { Text.send(p, "<red>Minimum deposit is " + Numbers.shortFormat(c.minDeposit) + "."); return; }
        if (!eco().take(id, Currency.CREDITS, amount, "gang:deposit")) { Text.send(p, "<red>You don't have that much."); return; }
        g.bank += amount;
        Gang.Parked pk = g.parked.computeIfAbsent(id.toString(), k -> new Gang.Parked());
        pk.amount += amount;
        pk.grade = grade;
        pk.leftAt = 0;
        Gang.Member m = g.member(id);
        if (m != null) m.deposited += amount;
        g.log(p.getName(), "deposit", amount, c.logSize);
        dirty();
        broadcast(g, "<gray>" + p.getName() + " deposited " + eco().format(Currency.CREDITS, amount) + "<gray>. Bank: " + eco().format(Currency.CREDITS, g.bank));
    }

    void withdraw(Player p, String arg) {
        Gang g = requireGang(p);
        if (g == null) return;
        UUID id = p.getUniqueId();
        long own = parkedBy(g, id);
        // Anyone may take back their own parked Credits; the rest needs the withdraw rank.
        boolean ranked = g.rank(id).atLeast(cfg().rankWithdraw);
        long limit = ranked ? withdrawable(g, id) : Math.min(own, g.bank);
        if (limit <= 0) {
            if (!ranked && own <= 0) Text.send(p, "<red>Only " + cfg().rankWithdraw.display + "s and up can withdraw. You can take back what you deposited this run.");
            else Text.send(p, "<red>Nothing you can withdraw right now." + (g.bank > 0 ? " <gray>The rest is prestige-locked." : ""));
            return;
        }
        long amount = parseAmount(p, arg, limit);
        if (amount < 0) return;
        if (amount > limit) {
            Text.send(p, "<red>You can withdraw up to " + eco().format(Currency.CREDITS, limit) + "<red>."
                    + (limit < g.bank ? " <gray>The rest is prestige-locked (see /gang bank)." : ""));
            return;
        }
        if (amount <= 0) return;
        takeFromBank(g, id, amount);
        eco().give(id, Currency.CREDITS, amount, "gang:withdraw");
        g.log(p.getName(), "withdraw", -amount, cfg().logSize);
        dirty();
        broadcast(g, "<gray>" + p.getName() + " withdrew " + eco().format(Currency.CREDITS, amount) + "<gray>. Bank: " + eco().format(Currency.CREDITS, g.bank));
    }

    /**
     * Removes Credits for a withdrawal by {@code who}: their own parked Credits first, then unparked gang money,
     * then other members' unlocked parked Credits pro rata. Keeps sum(parked) <= bank.
     */
    private void takeFromBank(Gang g, UUID who, long amount) {
        GangsConfig c = cfg();
        long rest = amount;
        Gang.Parked mine = g.parked.get(who.toString());
        if (mine != null) {
            long own = Math.min(mine.amount, rest);
            mine.amount -= own;
            rest -= own;
            if (mine.amount <= 0) g.parked.remove(who.toString());
        }
        long free = Math.max(0, g.bank - g.parkedTotal());
        rest -= Math.min(rest, free);
        if (rest > 0) reduceParked(g, pk -> !isLocked(pk, c), rest);
        g.bank -= amount;
    }

    /** Spends bank Credits on the gang (upgrades, upkeep): unparked money first, then everyone's parked pro rata. */
    private boolean spend(Gang g, long amount) {
        if (amount <= 0) return true;
        if (g.bank < amount) return false;
        long free = Math.max(0, g.bank - g.parkedTotal());
        long rest = amount - Math.min(amount, free);
        if (rest > 0) reduceParked(g, pk -> true, rest);
        g.bank -= amount;
        return true;
    }

    private static void reduceParked(Gang g, Predicate<Gang.Parked> include, long amount) {
        List<Map.Entry<String, Gang.Parked>> list = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Gang.Parked> e : g.parked.entrySet()) {
            if (e.getValue().amount > 0 && include.test(e.getValue())) {
                list.add(e);
                total += e.getValue().amount;
            }
        }
        if (total <= 0) return;
        long left = Math.min(amount, total);
        double ratio = (double) left / total;
        for (Map.Entry<String, Gang.Parked> e : list) {
            long cut = Math.min(e.getValue().amount, Math.min(left, (long) Math.floor(e.getValue().amount * ratio)));
            e.getValue().amount -= cut;
            left -= cut;
        }
        for (Map.Entry<String, Gang.Parked> e : list) { // rounding leftovers
            if (left <= 0) break;
            long cut = Math.min(e.getValue().amount, left);
            e.getValue().amount -= cut;
            left -= cut;
        }
        g.parked.values().removeIf(pk -> pk.amount <= 0);
    }

    /** Prestige wipe: Credits a player still has parked in any gang bank are removed with their wallet. */
    void onPrestige(Player p) {
        UUID id = p.getUniqueId();
        for (Gang g : gangs.values()) {
            Gang.Parked pk = g.parked.remove(id.toString());
            if (pk == null || pk.amount <= 0) continue;
            long seized = Math.min(pk.amount, g.bank);
            g.bank -= seized;
            g.log(p.getName(), "prestige wipe", -seized, cfg().logSize);
            dirty();
            if (seized > 0) {
                Text.send(p, "<gray>Prestige wiped the " + eco().format(Currency.CREDITS, seized) + "<gray> you had parked in the " + g.name + " bank.");
                broadcast(g, "<gray>" + p.getName() + " prestiged; their parked " + eco().format(Currency.CREDITS, seized) + "<gray> left the bank.");
            }
        }
        Gang g = gangFor(id);
        if (g != null) {
            addPointsRaw(g, id, cfg().pointsPerPrestige);
            progress(g, "prestiges", 1);
        }
    }

    /** Keeps each member's grade current (drives the lock while they're offline). */
    void updateGrade(UUID id, String name, int grade) {
        Gang g = gangFor(id);
        if (g != null) {
            Gang.Member m = g.member(id);
            if (m != null && (m.grade != grade || (name != null && !name.equals(m.name)))) {
                m.grade = grade;
                if (name != null) m.name = name;
                dirty();
            }
        }
        for (Gang other : gangs.values()) {
            Gang.Parked pk = other.parked.get(id.toString());
            if (pk != null && pk.grade != grade) {
                pk.grade = grade;
                dirty();
            }
        }
    }

    private long parseAmount(Player p, String arg, long all) {
        if (arg == null || arg.equalsIgnoreCase("all") || arg.equalsIgnoreCase("max")) return all;
        try {
            long v = Numbers.parse(arg);
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            Text.send(p, "<red>Not an amount: " + Text.MM.escapeTags(arg) + " <gray>(try 50000, 2.5M or all)");
            return -1;
        }
    }

    // =====================================================================================
    // Upgrades
    // =====================================================================================

    void buyUpgrade(Player p, String upgradeId) {
        Gang g = requireGang(p);
        if (g == null || !hasRank(p, g, cfg().rankUpgrade, "buy upgrades")) return;
        GangsConfig.UpgradeDef d = cfg().upgrades.get(upgradeId.toLowerCase(Locale.ROOT));
        if (d == null) { Text.send(p, "<red>Upgrades: <white>" + String.join(", ", cfg().upgrades.keySet())); return; }
        int lvl = g.upgrade(d.id());
        if (lvl >= d.maxLevel()) { Text.send(p, "<gold>" + d.name() + " is maxed."); return; }
        int next = lvl + 1;
        int need = d.minLevelFor(next);
        if (g.level() < need) { Text.send(p, "<red>" + d.name() + " " + next + " needs gang level " + need + "."); return; }
        long cost = d.cost(next);
        if (!spend(g, cost)) {
            Text.send(p, "<red>" + d.name() + " " + next + " costs " + eco().format(Currency.CREDITS, cost) + "<red> from the bank.");
            return;
        }
        g.upgrades.put(d.id(), next);
        g.log(p.getName(), "upgrade " + d.id() + " " + next, -cost, cfg().logSize);
        if (d.id().equals("dispatch")) topUpContracts(g);
        dirty();
        broadcast(g, "<green>" + p.getName() + " bought <white>" + d.name() + " " + next + "</white> for " + eco().format(Currency.CREDITS, cost) + "<green>.");
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1f);
    }

    // =====================================================================================
    // Points and contracts
    // =====================================================================================

    /** Fractional points (blocks mined, credits earned) build up per gang. */
    void addFractional(UUID player, double points) {
        Gang g = gangFor(player);
        if (g == null || points <= 0) return;
        double total = pointRemainder.getOrDefault(g.id, 0.0) + points;
        long whole = (long) total;
        pointRemainder.put(g.id, total - whole);
        if (whole > 0) addPointsRaw(g, player, whole);
    }

    private void addPointsRaw(Gang g, UUID player, long points) {
        int before = g.level();
        g.points += points;
        g.weeklyPoints += points;
        Gang.Member m = player == null ? null : g.member(player);
        if (m != null) {
            m.pointsEarned += points;
            m.weeklyPoints += points;
        }
        dirty();
        int after = g.level();
        if (after > before) broadcast(g, "<gold>⬆ The crew reached <white>gang level " + after + "</white>!");
    }

    void progress(Gang g, String objective, long amount) {
        for (Gang.Contract ct : g.contracts) {
            if (ct.done || !ct.objective.equals(objective)) continue;
            ct.progress = Math.min(ct.target, ct.progress + amount);
            dirty();
            if (ct.progress >= ct.target) {
                ct.done = true;
                addPointsRaw(g, null, ct.points);
                long paid = depositFromServer(g.id, ct.credits, "contract " + ct.name);
                broadcast(g, "<gold>✔ Contract complete: <white>" + ct.name + "</white>. <gray>+" + Numbers.shortFormat(ct.points)
                        + " points" + (paid > 0 ? ", +" + eco().format(Currency.CREDITS, paid) + "<gray> to the bank" : "") + ".");
                for (UUID m : g.members()) {
                    Player op = Bukkit.getPlayer(m);
                    if (op != null) op.playSound(op.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1f);
                }
            }
        }
    }

    void rollContracts(Gang g) {
        g.contracts.clear();
        topUpContracts(g);
    }

    private void topUpContracts(Gang g) {
        GangsConfig c = cfg();
        List<GangsConfig.ContractDef> pool = new ArrayList<>();
        for (GangsConfig.ContractDef d : c.contractPool.values()) {
            if (!objectives.containsKey(d.objective())) continue;
            boolean have = g.contracts.stream().anyMatch(ct -> ct.id.equals(d.id()));
            if (!have) pool.add(d);
        }
        Collections.shuffle(pool);
        int want = c.contractSlots(g);
        for (GangsConfig.ContractDef d : pool) {
            if (g.contracts.size() >= want) break;
            Gang.Contract ct = new Gang.Contract();
            ct.id = d.id();
            ct.name = d.name();
            ct.objective = d.objective();
            ct.target = d.target(g.members.size());
            ct.points = d.points();
            ct.credits = d.credits();
            g.contracts.add(ct);
            dirty();
        }
    }

    // =====================================================================================
    // Timers: weekly reset, upkeep, invite and parked cleanup
    // =====================================================================================

    long currentWeekStart() {
        ZonedDateTime now = ZonedDateTime.now(cfg().weekZone);
        return now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli();
    }

    long nextWeekStart() {
        return Instant.ofEpochMilli(currentWeekStart()).atZone(cfg().weekZone).plusWeeks(1).toInstant().toEpochMilli();
    }

    /** Runs every minute. */
    void tick() {
        long now = System.currentTimeMillis();
        long week = currentWeekStart();
        if (root.weekStart == 0) {
            root.weekStart = week;
            dirty();
        } else if (week != root.weekStart) {
            weeklyReset();
            root.weekStart = week;
        }

        GangsConfig c = cfg();
        for (Gang g : gangs.values()) {
            // Upkeep
            if (c.upkeepEnabled && now >= g.nextUpkeepAt) {
                long cost = c.upkeep(g);
                if (spend(g, cost)) {
                    if (cost > 0) g.log("Station", "upkeep", -cost, c.logSize);
                    if (g.perksSuspended) broadcast(g, "<green>Upkeep paid. Crew perks are back online.");
                    g.perksSuspended = false;
                    g.nextUpkeepAt = now + c.upkeepIntervalHours * HOUR;
                } else {
                    if (!g.perksSuspended) broadcast(g, "<red>The bank can't cover upkeep (" + eco().format(Currency.CREDITS, cost)
                            + "<red>). Crew perks are offline until it's paid.");
                    g.perksSuspended = true;
                    g.nextUpkeepAt = now + HOUR;
                }
                dirty();
            }
            // Ex-members' parked Credits become gang money after a while.
            long release = c.exMemberReleaseDays * DAY;
            if (g.parked.values().removeIf(pk -> pk.leftAt > 0 && now - pk.leftAt > release)) dirty();
            if (g.contracts.size() < c.contractSlots(g) && !c.contractPool.isEmpty()) topUpContracts(g);
        }
        for (Map<String, Long> inv : invites.values()) inv.values().removeIf(exp -> exp < now);
        invites.values().removeIf(Map::isEmpty);
    }

    void weeklyReset() {
        List<Gang> ranked = new ArrayList<>(gangs.values());
        ranked.removeIf(g -> g.weeklyPoints <= 0);
        ranked.sort(Comparator.comparingLong((Gang g) -> g.weeklyPoints).reversed());
        List<Long> rewards = cfg().weeklyRewards;
        if (!ranked.isEmpty()) {
            broadcastAll("<#c8743a><b>WEEKLY CREW STANDINGS</b>");
            for (int i = 0; i < Math.min(3, ranked.size()); i++) {
                Gang g = ranked.get(i);
                long reward = i < rewards.size() ? depositFromServer(g.id, rewards.get(i), "weekly #" + (i + 1)) : 0;
                broadcastAll("<gray>#" + (i + 1) + " [" + g.coloredTag() + "<gray>] <white>" + g.name + "</white> "
                        + Numbers.shortFormat(g.weeklyPoints) + " pts" + (reward > 0 ? " <dark_gray>·</dark_gray> " + eco().format(Currency.CREDITS, reward) : ""));
            }
        }
        for (Gang g : gangs.values()) {
            g.weeklyPoints = 0;
            for (Gang.Member m : g.members.values()) m.weeklyPoints = 0;
            rollContracts(g);
        }
        dirty();
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    Gang requireGang(Player p) {
        Gang g = gangFor(p.getUniqueId());
        if (g == null) Text.send(p, "<red>You're not in a gang. <gray>/gang create <name> <tag></gray> or get invited.");
        return g;
    }

    private boolean hasRank(Player p, Gang g, Rank need, String what) {
        Rank r = g.rank(p.getUniqueId());
        if (r != null && r.atLeast(need)) return true;
        Text.send(p, "<red>You need to be " + need.display + " or higher to " + what + ".");
        return false;
    }

    void broadcast(Gang g, String msg) {
        var comp = Text.mm("<dark_gray>[" + g.coloredTag() + "<dark_gray>]</dark_gray> " + msg);
        for (UUID m : g.members()) {
            Player op = Bukkit.getPlayer(m);
            if (op != null) op.sendMessage(comp);
        }
    }

    private static void broadcastAll(String msg) {
        Bukkit.broadcast(Text.mm(Text.PREFIX + msg));
    }

    /** Gangs by lifetime points (level) or weekly points, best first. */
    List<Gang> top(boolean weekly) {
        List<Gang> list = new ArrayList<>(gangs.values());
        list.sort(Comparator.comparingLong((Gang g) -> weekly ? g.weeklyPoints : g.points).reversed());
        return list;
    }

    void clearInvites(UUID p) { invites.remove(p); }

    void markDirty() { dirty(); }

    void adminPoints(Gang g, long points) {
        if (points > 0) addPointsRaw(g, null, points);
        else {
            g.points = Math.max(0, g.points + points);
            g.weeklyPoints = Math.max(0, g.weeklyPoints + points);
            dirty();
        }
    }
}
