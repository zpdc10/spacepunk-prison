package dev.spacepunk.prison.trade;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Direct player-to-player trades. Main thread only.
 *
 * Money flow: Credits move only when the trade completes, with reasons "trade:send" and "trade:receive" (refunds
 * "trade:refund"). None start with "sell:" or "income:", so traded Credits never count as server income (prestige
 * stats, gang points, quests). Items on the table are held by the trade, mirrored to trades.json so a crash hands
 * them back on the owner's next join.
 *
 * Prestige locks, so Credits can't be parked with a friend through the prestige wipe:
 *  - From prestige-lock.credit-lock-grade on (like the gang bank deposit lock) you can't put Credits in a trade.
 *  - Credits you sent someone this run are tracked per partner. When you prestige, that partner can't send Credits
 *    back to you for return-lock-days (or until they prestige themselves).
 *  - You can't prestige with a trade window open.
 */
final class TradeManager {
    enum ItemResult { OK, NOT_ALLOWED, FULL }

    private final Plugin plugin;
    private final TradeStore store;
    private TradeConfig cfg;
    private TradeMenu menu;
    /** Target -> (requester -> expires at). */
    private final Map<UUID, Map<UUID, Long>> requests = new HashMap<>();
    private final Map<UUID, TradeSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    TradeManager(Plugin plugin, TradeStore store, TradeConfig cfg) {
        this.plugin = plugin;
        this.store = store;
        this.cfg = cfg;
    }

    void menu(TradeMenu menu) { this.menu = menu; }

    TradeConfig cfg() { return cfg; }

    void config(TradeConfig cfg) { this.cfg = cfg; }

    TradeStore store() { return store; }

    private static EconomyService eco() { return Services.get(EconomyService.class); }

    private static boolean loaded(UUID id) { return Services.get(ProfileService.class).get(id).isPresent(); }

    private static int gradeOf(UUID id) { return Services.find(ProgressionService.class).map(s -> s.grade(id)).orElse(1); }

    TradeSession session(UUID player) { return sessions.get(player); }

    List<TradeSession> sessions() { return sessions.values().stream().distinct().toList(); }

    // =====================================================================================
    // Requests
    // =====================================================================================

    void request(Player from, Player to) {
        UUID f = from.getUniqueId(), t = to.getUniqueId();
        if (f.equals(t)) { Text.send(from, "<red>You can't trade with yourself."); return; }
        if (!loaded(f) || !loaded(t)) { Text.send(from, "<red>Profiles are still loading. Try again in a moment."); return; }
        if (sessions.containsKey(f)) { Text.send(from, "<red>You're already in a trade."); return; }
        if (sessions.containsKey(t)) { Text.send(from, "<red>" + to.getName() + " is already trading with someone."); return; }
        if (!inRange(from, to)) {
            Text.send(from, "<red>" + to.getName() + " is too far away. <gray>Trades need you within "
                    + (int) cfg.maxDistance + " blocks, in the same world.");
            return;
        }
        // They already asked us: open the trade straight away.
        Long theirs = requests.getOrDefault(f, Map.of()).get(t);
        if (theirs != null && theirs > System.currentTimeMillis()) {
            requests.get(f).remove(t);
            open(to, from);
            return;
        }
        if (store.state().blocking.contains(t.toString())) {
            Text.send(from, "<red>" + to.getName() + " isn't taking trade requests.");
            return;
        }
        Map<UUID, Long> pending = requests.computeIfAbsent(t, k -> new HashMap<>());
        Long existing = pending.get(f);
        if (existing != null && existing > System.currentTimeMillis()) {
            Text.send(from, "<gray>You already asked " + to.getName() + ". The request lasts " + cfg.requestSeconds + "s.");
            return;
        }
        pending.put(f, System.currentTimeMillis() + cfg.requestSeconds * 1000L);
        Text.send(from, "<gray>Trade request sent to <white>" + to.getName() + "</white>. It expires in " + cfg.requestSeconds + "s.");
        String name = from.getName();
        Text.send(to, "<white>" + name + "</white> <gray>wants to trade. "
                + "<click:run_command:'/trade accept " + name + "'><hover:show_text:'<green>Open the trade window'><green><b>[ACCEPT]</b></green></hover></click> "
                + "<click:run_command:'/trade deny " + name + "'><hover:show_text:'<red>Turn it down'><red><b>[DENY]</b></red></hover></click>");
        to.playSound(Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 0.7f, 1.4f));
    }

    void accept(Player p, Player requester) {
        Map<UUID, Long> pending = requests.get(p.getUniqueId());
        Long exp = pending == null ? null : pending.remove(requester.getUniqueId());
        if (exp == null || exp < System.currentTimeMillis()) {
            Text.send(p, "<red>No trade request from " + requester.getName() + ". <gray>Send one with <white>/trade " + requester.getName());
            return;
        }
        if (sessions.containsKey(p.getUniqueId())) { Text.send(p, "<red>You're already in a trade."); return; }
        if (sessions.containsKey(requester.getUniqueId())) { Text.send(p, "<red>" + requester.getName() + " is already trading."); return; }
        if (!inRange(p, requester)) { Text.send(p, "<red>" + requester.getName() + " is too far away now."); return; }
        open(requester, p);
    }

    void deny(Player p, String requesterName) {
        Map<UUID, Long> pending = requests.get(p.getUniqueId());
        Player r = Bukkit.getPlayerExact(requesterName);
        if (pending == null || r == null || pending.remove(r.getUniqueId()) == null) {
            Text.send(p, "<red>No trade request from " + requesterName + ".");
            return;
        }
        Text.send(p, "<gray>Declined " + r.getName() + "'s trade.");
        Text.send(r, "<gray>" + p.getName() + " declined your trade request.");
    }

    boolean toggleBlocking(Player p) {
        String id = p.getUniqueId().toString();
        boolean nowBlocking = !store.state().blocking.remove(id);
        if (nowBlocking) store.state().blocking.add(id);
        store.markDirty();
        return nowBlocking;
    }

    private boolean inRange(Player a, Player b) {
        if (cfg.maxDistance < 0) return true;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getLocation().distanceSquared(b.getLocation()) <= cfg.maxDistance * cfg.maxDistance;
    }

    private void open(Player a, Player b) {
        TradeSession s = new TradeSession(store.state().nextId++, a.getUniqueId(), a.getName(), b.getUniqueId(), b.getName());
        store.markDirty();
        sessions.put(a.getUniqueId(), s);
        sessions.put(b.getUniqueId(), s);
        requests.remove(a.getUniqueId());
        requests.remove(b.getUniqueId());
        for (Map<UUID, Long> m : requests.values()) {
            m.remove(a.getUniqueId());
            m.remove(b.getUniqueId());
        }
        menu.open(a, s.a);
        menu.open(b, s.b);
        Sound snd = Sound.sound(Key.key("block.chest.open"), Sound.Source.MASTER, 0.6f, 1.2f);
        a.playSound(snd);
        b.playSound(snd);
    }

    // =====================================================================================
    // Offers
    // =====================================================================================

    /** Moves (part of) a stack from the player's inventory onto their side of the table. */
    ItemResult offer(TradeSession.Side side, ItemStack stack) {
        if (!allowed(stack)) return ItemResult.NOT_ALLOWED;
        ItemStack rest = stack.clone();
        for (ItemStack on : side.items) {
            if (rest.getAmount() <= 0) break;
            if (!on.isSimilar(rest)) continue;
            int room = on.getMaxStackSize() - on.getAmount();
            int move = Math.min(room, rest.getAmount());
            if (move <= 0) continue;
            on.setAmount(on.getAmount() + move);
            rest.setAmount(rest.getAmount() - move);
        }
        if (rest.getAmount() > 0) {
            if (side.items.size() >= TradeSession.MAX_ITEMS) {
                int moved = stack.getAmount() - rest.getAmount();
                if (moved == 0) return ItemResult.FULL;
                stack.setAmount(moved);
                changed(side.session);
                return ItemResult.OK;
            }
            side.items.add(rest);
        }
        changed(side.session);
        return ItemResult.OK;
    }

    /** Takes up to amount of the item at index back off the table into the player's inventory. */
    void withdraw(Player p, TradeSession.Side side, int index, int amount) {
        if (index < 0 || index >= side.items.size()) return;
        ItemStack on = side.items.get(index);
        int n = Math.min(amount, on.getAmount());
        ItemStack back = on.clone();
        back.setAmount(n);
        Map<Integer, ItemStack> left = p.getInventory().addItem(back);
        int returned = n - left.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (returned <= 0) {
            Text.send(p, "<red>Your inventory is full.");
            return;
        }
        if (returned >= on.getAmount()) side.items.remove(index);
        else on.setAmount(on.getAmount() - returned);
        changed(side.session);
    }

    /** Sets this side's Credits offer. Returns null if fine, or the MiniMessage reason it was refused. */
    String setCredits(TradeSession.Side side, long amount) {
        if (amount < 0) return "<red>Amount can't be negative.";
        if (amount > 0) {
            String lock = creditLock(side.id, side.other().id, side.other().name);
            if (lock != null) return lock;
            if (amount > cfg.maxCredits) return "<red>That's over the trade limit.";
            long bal = eco().balance(side.id, Currency.CREDITS);
            if (amount > bal) return "<red>You only have " + eco().format(Currency.CREDITS, bal) + "<red>.";
        }
        if (side.credits == amount) return null;
        side.credits = amount;
        changed(side.session);
        return null;
    }

    void toggleAccept(TradeSession.Side side) {
        TradeSession s = side.session;
        if (s.over) return;
        if (!side.accepted && side.items.isEmpty() && side.credits == 0 && side.other().items.isEmpty() && side.other().credits == 0) {
            Player p = Bukkit.getPlayer(side.id);
            if (p != null) Text.send(p, "<red>Nothing is on the table yet.");
            return;
        }
        side.accepted = !side.accepted;
        if (s.a.accepted && s.b.accepted) {
            s.countdown = cfg.countdownSeconds;
            ping(s, "block.note_block.hat", 1.0f);
        } else {
            s.countdown = -1;
        }
        menu.render(s);
    }

    private void changed(TradeSession s) {
        boolean wasAccepted = s.a.accepted || s.b.accepted;
        s.changed();
        syncEscrow(s);
        menu.render(s);
        if (wasAccepted) ping(s, "block.note_block.bass", 0.8f);
    }

    /** Mirrors what's on the table to trades.json so a crash can't eat it. */
    private void syncEscrow(TradeSession s) {
        for (TradeSession.Side side : List.of(s.a, s.b)) {
            if (side.items.isEmpty()) store.state().escrow.remove(side.id.toString());
            else store.state().escrow.put(side.id.toString(), side.items.stream().map(TradeManager::encode).toList());
        }
        store.markDirty();
    }

    /** Untradeable: blocked materials, the soulbound drill and rod, and containers holding any of those. */
    boolean allowed(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (cfg.blockedMaterials.contains(item.getType())) return false;
        DrillService drill = Services.find(DrillService.class).orElse(null);
        if (drill != null && drill.isDrill(item)) return false;
        if (item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            for (String k : cfg.blockedKeys) if (pdc.has(new NamespacedKey(plugin, k))) return false;
            if (item.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
                for (ItemStack inner : box.getInventory().getContents()) if (inner != null && !inner.getType().isAir() && !allowed(inner)) return false;
            }
            if (item.getItemMeta() instanceof BundleMeta bundle) {
                for (ItemStack inner : bundle.getItems()) if (!allowed(inner)) return false;
            }
        }
        return true;
    }

    // =====================================================================================
    // Prestige locks
    // =====================================================================================

    /** Null if this player may put Credits in a trade with the partner, else the reason (MiniMessage). */
    String creditLock(UUID player, UUID partner, String partnerName) {
        if (cfg.creditLockGrade > 0 && gradeOf(player) >= cfg.creditLockGrade) {
            return "<red>Prestige lock: you can't trade Credits from Grade " + cfg.creditLockGrade
                    + " on.</red> <gray>Prestige wipes Credits, so they can't be handed off before it. Items are fine.";
        }
        TradeStore.ReturnLock lock = returnLock(player, partner);
        if (lock != null) {
            return "<red>Prestige lock: " + partnerName + " sent you " + eco().format(Currency.CREDITS, lock.amount)
                    + "<red> before they prestiged.</red> <gray>You can't send them Credits for "
                    + TradeMenu.duration(lock.until - System.currentTimeMillis()) + " (or until you prestige). Items are fine.";
        }
        return null;
    }

    TradeStore.ReturnLock returnLock(UUID holder, UUID sender) {
        Map<String, TradeStore.ReturnLock> m = store.state().returnLocks.get(holder.toString());
        if (m == null) return null;
        TradeStore.ReturnLock l = m.get(sender.toString());
        if (l == null) return null;
        if (l.until <= System.currentTimeMillis()) {
            m.remove(sender.toString());
            if (m.isEmpty()) store.state().returnLocks.remove(holder.toString());
            store.markDirty();
            return null;
        }
        return l;
    }

    /** Called on prestige (Credits just wiped). */
    void onPrestige(Player p) {
        String id = p.getUniqueId().toString();
        var st = store.state();
        // Everyone this player paid during the run they just ended can't pay it back for a while.
        Map<String, Long> sent = st.runSent.remove(id);
        if (sent != null && cfg.returnLockDays > 0) {
            long until = System.currentTimeMillis() + cfg.returnLockDays * 86_400_000L;
            for (Map.Entry<String, Long> e : sent.entrySet()) {
                if (e.getValue() <= 0) continue;
                TradeStore.ReturnLock l = st.returnLocks.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(id, k -> new TradeStore.ReturnLock());
                l.amount += e.getValue();
                l.until = Math.max(l.until, until);
                l.senderName = p.getName();
            }
        }
        // This player's own wallet was wiped too: locks on them, and what others sent them, no longer matter.
        st.returnLocks.remove(id);
        for (Map<String, Long> m : st.runSent.values()) m.remove(id);
        st.runSent.values().removeIf(Map::isEmpty);
        store.markDirty();
    }

    private void recordSent(UUID from, UUID to, long amount) {
        if (amount <= 0) return;
        var st = store.state();
        st.runSent.computeIfAbsent(from.toString(), k -> new LinkedHashMap<>()).merge(to.toString(), amount, Long::sum);
        // Paying someone back within the same run cancels out what they sent you.
        Map<String, Long> back = st.runSent.get(to.toString());
        if (back != null) {
            Long owed = back.get(from.toString());
            if (owed != null) {
                long left = owed - amount;
                if (left <= 0) back.remove(from.toString());
                else back.put(from.toString(), left);
                if (back.isEmpty()) st.runSent.remove(to.toString());
            }
        }
    }

    // =====================================================================================
    // Ticking, cancelling, completing
    // =====================================================================================

    /** Once a second: countdowns, chat-input timeouts, expired requests. */
    void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map<UUID, Long>> it = requests.values().iterator(); it.hasNext(); ) {
            Map<UUID, Long> m = it.next();
            m.values().removeIf(exp -> exp < now);
            if (m.isEmpty()) it.remove();
        }
        for (TradeSession s : sessions()) {
            if (s.over) continue;
            for (TradeSession.Side side : List.of(s.a, s.b)) {
                if (side.typing && side.typingUntil < now) {
                    side.typing = false;
                    Player p = Bukkit.getPlayer(side.id);
                    if (p != null) {
                        Text.send(p, "<gray>No amount typed. Back to the trade.");
                        menu.reopen(p, side);
                    }
                }
            }
            if (s.countdown < 0) continue;
            // Re-check money every second so nobody can spend it during the countdown.
            String problem = problem(s);
            if (problem != null) {
                s.changed();
                for (TradeSession.Side side : List.of(s.a, s.b)) {
                    Player p = Bukkit.getPlayer(side.id);
                    if (p != null) Text.send(p, problem);
                }
                menu.render(s);
                continue;
            }
            s.countdown--;
            if (s.countdown <= 0) complete(s);
            else {
                ping(s, "block.note_block.hat", 1.0f + 0.15f * (cfg.countdownSeconds - s.countdown));
                menu.render(s);
            }
        }
    }

    /** Cancels and hands everything back. */
    void cancel(TradeSession s, String reasonForA, String reasonForB) {
        if (s.over) return;
        s.over = true;
        for (TradeSession.Side side : List.of(s.a, s.b)) {
            sessions.remove(side.id);
            Player p = Bukkit.getPlayer(side.id);
            List<ItemStack> items = new ArrayList<>(side.items);
            side.items.clear();
            if (p != null && p.isOnline()) {
                give(p, items);
                if (p.getOpenInventory().getTopInventory().getHolder() == side) p.closeInventory();
                Text.send(p, side == s.a ? reasonForA : reasonForB);
            } else if (!items.isEmpty()) {
                store.state().returns.computeIfAbsent(side.id.toString(), k -> new ArrayList<>())
                        .addAll(items.stream().map(TradeManager::encode).toList());
            }
            store.state().escrow.remove(side.id.toString());
        }
        store.markDirty();
    }

    void cancel(TradeSession s, String reason) { cancel(s, reason, reason); }

    /** Why the trade can't go through right now (MiniMessage), or null. */
    private String problem(TradeSession s) {
        for (TradeSession.Side side : List.of(s.a, s.b)) {
            Player p = Bukkit.getPlayer(side.id);
            if (p == null || !loaded(side.id)) return "<red>" + side.name + " isn't available.";
            if (side.credits > 0) {
                if (eco().balance(side.id, Currency.CREDITS) < side.credits) {
                    return "<red>" + side.name + " no longer has the " + eco().format(Currency.CREDITS, side.credits) + "<red> they offered.";
                }
                String lock = creditLock(side.id, side.other().id, side.other().name);
                if (lock != null) return "<red>" + side.name + "'s Credits are prestige-locked for this trade.";
            }
        }
        return null;
    }

    private void complete(TradeSession s) {
        String problem = problem(s);
        Player pa = Bukkit.getPlayer(s.a.id), pb = Bukkit.getPlayer(s.b.id);
        if (problem == null) {
            if (!fits(pa, s.b.items)) problem = "<red>" + s.a.name + " doesn't have room for the items.";
            else if (!fits(pb, s.a.items)) problem = "<red>" + s.b.name + " doesn't have room for the items.";
        }
        if (problem != null) {
            s.changed();
            for (Player p : new Player[]{pa, pb}) if (p != null) Text.send(p, problem);
            menu.render(s);
            return;
        }
        EconomyService eco = eco();
        if (s.a.credits > 0 && !eco.take(s.a.id, Currency.CREDITS, s.a.credits, "trade:send")) { s.changed(); menu.render(s); return; }
        if (s.b.credits > 0 && !eco.take(s.b.id, Currency.CREDITS, s.b.credits, "trade:send")) {
            if (s.a.credits > 0) eco.give(s.a.id, Currency.CREDITS, s.a.credits, "trade:refund");
            s.changed();
            menu.render(s);
            return;
        }
        s.over = true;
        if (s.a.credits > 0) eco.give(s.b.id, Currency.CREDITS, s.a.credits, "trade:receive");
        if (s.b.credits > 0) eco.give(s.a.id, Currency.CREDITS, s.b.credits, "trade:receive");
        recordSent(s.a.id, s.b.id, s.a.credits);
        recordSent(s.b.id, s.a.id, s.b.credits);

        List<ItemStack> toB = new ArrayList<>(s.a.items), toA = new ArrayList<>(s.b.items);
        log(s);
        s.a.items.clear();
        s.b.items.clear();
        give(pa, toA);
        give(pb, toB);

        sessions.remove(s.a.id);
        sessions.remove(s.b.id);
        store.state().escrow.remove(s.a.id.toString());
        store.state().escrow.remove(s.b.id.toString());
        store.markDirty();
        for (TradeSession.Side side : List.of(s.a, s.b)) {
            Player p = Bukkit.getPlayer(side.id);
            if (p == null) continue;
            if (p.getOpenInventory().getTopInventory().getHolder() == side) p.closeInventory();
            TradeSession.Side o = side.other();
            Text.send(p, "<green>Trade complete with " + o.name + ".</green> <gray>You got " + summary(o) + "<gray>.");
            p.playSound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.6f, 1.6f));
        }
    }

    private void log(TradeSession s) {
        TradeRecord r = new TradeRecord();
        r.id = s.id;
        r.time = System.currentTimeMillis();
        r.a = s.a.id.toString();
        r.aName = s.a.name;
        r.b = s.b.id.toString();
        r.bName = s.b.name;
        r.aItems = s.a.items.stream().map(TradeMenu::describe).toList();
        r.bItems = s.b.items.stream().map(TradeMenu::describe).toList();
        r.aCredits = s.a.credits;
        r.bCredits = s.b.credits;
        List<TradeRecord> recent = store.state().recent;
        recent.add(r);
        while (recent.size() > cfg.logKeep) recent.remove(0);

        TradeRecord.Full f = new TradeRecord.Full();
        f.id = r.id;
        f.time = r.time;
        f.a = r.a;
        f.aName = r.aName;
        f.b = r.b;
        f.bName = r.bName;
        f.aItems = r.aItems.stream().map(x -> TradeMenu.plain(x)).toList();
        f.bItems = r.bItems.stream().map(x -> TradeMenu.plain(x)).toList();
        f.aItemData = s.a.items.stream().map(TradeManager::encode).toList();
        f.bItemData = s.b.items.stream().map(TradeManager::encode).toList();
        f.aCredits = r.aCredits;
        f.bCredits = r.bCredits;
        store.appendLog(f);
        plugin.getLogger().info("Trade #" + r.id + ": " + r.aName + " gave " + (r.aCredits > 0 ? r.aCredits + " Credits + " : "")
                + f.aItems + " | " + r.bName + " gave " + (r.bCredits > 0 ? r.bCredits + " Credits + " : "") + f.bItems);
    }

    List<TradeRecord> history(UUID player, int max) {
        String id = player == null ? null : player.toString();
        List<TradeRecord> out = new ArrayList<>();
        List<TradeRecord> recent = store.state().recent;
        for (int i = recent.size() - 1; i >= 0 && out.size() < max; i--) {
            if (id == null || recent.get(i).involves(id)) out.add(recent.get(i));
        }
        return out;
    }

    static String summary(TradeSession.Side side) {
        List<String> parts = new ArrayList<>();
        if (side.credits > 0) parts.add(eco().format(Currency.CREDITS, side.credits));
        for (ItemStack it : side.items) parts.add(TradeMenu.describe(it));
        if (parts.isEmpty()) return "<dark_gray>nothing";
        if (parts.size() > 4) {
            int more = parts.size() - 3;
            parts = new ArrayList<>(parts.subList(0, 3));
            parts.add("<gray>" + more + " more");
        }
        return String.join("<gray>, ", parts);
    }

    // =====================================================================================
    // Items
    // =====================================================================================

    private static boolean fits(Player p, List<ItemStack> items) {
        if (items.isEmpty()) return true;
        Inventory sim = Bukkit.createInventory(null, 36);
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (int i = 0; i < storage.length && i < 36; i++) sim.setItem(i, storage[i] == null ? null : storage[i].clone());
        return sim.addItem(items.stream().map(ItemStack::clone).toArray(ItemStack[]::new)).isEmpty();
    }

    /** Into the inventory; whatever doesn't fit lands at their feet. */
    static void give(Player p, List<ItemStack> items) {
        if (items.isEmpty()) return;
        for (ItemStack left : p.getInventory().addItem(items.toArray(ItemStack[]::new)).values()) {
            p.getWorld().dropItem(p.getLocation(), left, drop -> {
                drop.setOwner(p.getUniqueId());
                drop.setPickupDelay(0);
            });
        }
    }

    /** Hands back items held when the server stopped mid-trade, or from a trade cancelled while they were away. */
    void deliverReturns(Player p) {
        List<String> data = store.state().returns.remove(p.getUniqueId().toString());
        if (data == null || data.isEmpty()) return;
        store.markDirty();
        List<ItemStack> items = new ArrayList<>();
        for (String d : data) {
            ItemStack it = decode(d);
            if (it != null) items.add(it);
        }
        give(p, items);
        Text.send(p, "<gray>Returned " + items.size() + " item stack" + (items.size() == 1 ? "" : "s") + " from an unfinished trade.");
    }

    /** On enable: anything still in escrow belonged to a trade the server never finished. */
    void recoverEscrow() {
        var st = store.state();
        if (st.escrow.isEmpty()) return;
        for (Map.Entry<String, List<String>> e : st.escrow.entrySet()) {
            st.returns.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
        }
        plugin.getLogger().warning("Returning items from " + st.escrow.size() + " unfinished trade side(s) on next join.");
        st.escrow.clear();
        store.markDirty();
    }

    /** Plugin shutdown: hand everything back now. */
    void cancelAll() {
        for (TradeSession s : sessions()) cancel(s, "<red>Trade cancelled: the station is restarting. <gray>Your items are back.");
    }

    private void ping(TradeSession s, String sound, float pitch) {
        Sound snd = Sound.sound(Key.key(sound), Sound.Source.MASTER, 0.6f, Math.min(2f, pitch));
        for (TradeSession.Side side : List.of(s.a, s.b)) {
            Player p = Bukkit.getPlayer(side.id);
            if (p != null) p.playSound(snd);
        }
    }

    static String encode(ItemStack stack) { return Base64.getEncoder().encodeToString(stack.serializeAsBytes()); }

    static ItemStack decode(String data) {
        if (data == null) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
