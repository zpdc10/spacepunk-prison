package dev.spacepunk.prison.gangs;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The crew terminal (/gang): overview, bank, upgrades, contracts and roster. */
final class GangMenu implements Listener {
    private static final int SIZE = 54;
    private static final int SLOT_HEADER = 4;
    private static final int SLOT_BANK = 10;
    private static final int[] UPGRADE_SLOTS = {12, 13, 14, 15};
    private static final int SLOT_TOP = 16;
    private static final int[] CONTRACT_SLOTS = {20, 21, 22, 23, 24};
    private static final int MEMBERS_FROM = 27, MEMBERS_TO = 44;
    private static final int SLOT_CHAT = 47;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_LOG = 51;

    private final GangManager gangs;

    GangMenu(GangManager gangs) { this.gangs = gangs; }

    static final class Holder implements InventoryHolder {
        final UUID owner;
        final Map<Integer, String> upgradeAt = new HashMap<>();
        Inventory inventory;

        Holder(UUID owner) { this.owner = owner; }

        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    void open(Player p) {
        Gang g = gangs.requireGang(p);
        if (g == null) return;
        Holder h = new Holder(p.getUniqueId());
        h.inventory = Bukkit.createInventory(h, SIZE, Text.mm("<#3a3028>CREW TERMINAL · " + g.tag));
        render(p, g, h);
        p.openInventory(h.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.6f, 0.9f);
    }

    private void render(Player p, Gang g, Holder h) {
        GangsConfig c = gangs.cfg();
        EconomyService eco = Services.get(EconomyService.class);
        Inventory inv = h.inventory;
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", "<dark_gray> ", null);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        // Header
        List<String> head = new ArrayList<>();
        head.add("<gray>Level <white>" + g.level() + "</white>  <dark_gray>·</dark_gray>  " + Numbers.shortFormat(g.points) + " points");
        long next = c.nextLevelPoints(g.points);
        if (next > 0) head.add("<dark_gray>Next level at " + Numbers.shortFormat(next));
        head.add("<gray>This week <white>" + Numbers.shortFormat(g.weeklyPoints) + "</white> pts  <dark_gray>·</dark_gray>  rank #" + weeklyRank(g));
        head.add("<gray>Crew <white>" + g.members.size() + "/" + c.maxMembers(g));
        head.add("");
        head.add("<gray>Upkeep <white>" + eco.format(Currency.CREDITS, c.upkeep(g)) + "</white><gray> every " + c.upkeepIntervalHours + "h");
        if (g.perksSuspended) head.add("<red>Perks offline: the bank couldn't pay upkeep.");
        if (!gangs.territories().isEmpty()) {
            List<String> held = new ArrayList<>();
            for (var e : gangs.territories().entrySet()) if (e.getValue().equals(g.id)) held.add(c.territoryName(e.getKey()));
            if (!held.isEmpty()) head.add("<gray>Turf <#c8743a>" + String.join(", ", held));
        }
        if (!g.motd.isBlank()) {
            head.add("");
            head.add("<white>" + Text.MM.escapeTags(g.motd));
        }
        inv.setItem(SLOT_HEADER, icon(Material.WHITE_BANNER, "ui/rank", "<dark_gray>[" + g.coloredTag() + "<dark_gray>]</dark_gray> <white><b>" + g.name, head));

        // Bank
        UUID id = p.getUniqueId();
        List<String> bank = new ArrayList<>();
        bank.add("<gray>Balance " + eco.format(Currency.CREDITS, g.bank) + "<dark_gray> / " + Numbers.shortFormat(c.bankCapacity(g)));
        bank.add("<gray>You can withdraw " + eco.format(Currency.CREDITS, g.rank(id).atLeast(c.rankWithdraw) ? gangs.withdrawable(g, id) : Math.min(gangs.parkedBy(g, id), g.bank)));
        long parked = gangs.parkedBy(g, id);
        if (parked > 0) bank.add("<gray>Parked by you this run <white>" + Numbers.shortFormat(parked));
        bank.add("");
        bank.add("<#8a8070>Prestige lock: what you deposit is parked");
        bank.add("<#8a8070>under your name for this run. From Grade " + c.depositLockGrade);
        bank.add("<#8a8070>it's locked, and prestiging wipes it with");
        bank.add("<#8a8070>your wallet. Gang spending isn't affected.");
        bank.add("");
        bank.add("<#c8743a>/gang deposit <amount|all>");
        bank.add("<#c8743a>/gang withdraw <amount|all>");
        inv.setItem(SLOT_BANK, icon(Material.GOLD_NUGGET, "currency/credit", "<gold>Gang Bank", bank));

        // Upgrades
        int i = 0;
        boolean canBuy = g.rank(id).atLeast(c.rankUpgrade);
        for (GangsConfig.UpgradeDef d : c.upgrades.values()) {
            if (i >= UPGRADE_SLOTS.length) break;
            int lvl = g.upgrade(d.id());
            List<String> lore = new ArrayList<>();
            lore.add("<#8a8070>" + d.description());
            lore.add("");
            lore.add("<gray>Level <white>" + lvl + "</white><dark_gray> / " + d.maxLevel());
            if (lvl < d.maxLevel()) {
                long cost = d.cost(lvl + 1);
                int need = d.minLevelFor(lvl + 1);
                lore.add("<gray>Next costs " + (g.bank >= cost ? "<gold>" : "<red>") + Numbers.shortFormat(cost) + " Credits</gray> <dark_gray>(bank)");
                if (g.level() < need) lore.add("<red>Needs gang level " + need);
                lore.add("");
                lore.add(canBuy ? "<#c8743a>Click</#c8743a> <gray>to buy" : "<dark_gray>" + c.rankUpgrade.display + "s and up can buy");
            } else {
                lore.add("<gold><b>MAXED");
            }
            ItemStack it = icon(d.icon(), null, "<#c8743a>" + d.name() + (lvl > 0 ? " <white>" + lvl : ""), lore);
            inv.setItem(UPGRADE_SLOTS[i], it);
            h.upgradeAt.put(UPGRADE_SLOTS[i], d.id());
            i++;
        }

        // Leaderboard
        List<String> top = new ArrayList<>();
        top.add("<gray>This week:");
        List<Gang> ranked = gangs.top(true);
        for (int r = 0; r < Math.min(5, ranked.size()); r++) {
            Gang o = ranked.get(r);
            top.add("<gray>#" + (r + 1) + " [" + o.coloredTag() + "<gray>] <white>" + o.name + "</white> <dark_gray>" + Numbers.shortFormat(o.weeklyPoints));
        }
        top.add("");
        long left = gangs.nextWeekStart() - System.currentTimeMillis();
        top.add("<gray>Resets in <white>" + (left / 86_400_000L) + "d " + (left / 3_600_000L % 24) + "h");
        top.add("<dark_gray>/gang top · /gang top level");
        inv.setItem(SLOT_TOP, icon(Material.GOLDEN_HELMET, "ui/prestige", "<gold>Crew Standings", top));

        // Contracts
        for (int s : CONTRACT_SLOTS) inv.setItem(s, icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "ui/panel_rust", "<dark_gray>No contract", null));
        for (int k = 0; k < Math.min(g.contracts.size(), CONTRACT_SLOTS.length); k++) {
            Gang.Contract ct = g.contracts.get(k);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + gangs.objectives().getOrDefault(ct.objective, ct.objective) + ": <white>"
                    + Numbers.shortFormat(ct.progress) + "</white><dark_gray> / " + Numbers.shortFormat(ct.target));
            lore.add(bar(ct.progress, ct.target));
            lore.add("");
            lore.add("<gray>Reward <white>" + Numbers.shortFormat(ct.points) + "</white> points"
                    + (ct.credits > 0 ? " + " + eco.format(Currency.CREDITS, ct.credits) : ""));
            lore.add("<dark_gray>Weekly. New contracts at the reset.");
            ItemStack it = icon(ct.done ? Material.FILLED_MAP : Material.MAP, "ui/info", (ct.done ? "<green>✔ " : "<#c8743a>") + ct.name, lore);
            inv.setItem(CONTRACT_SLOTS[k], it);
        }

        // Members
        int slot = MEMBERS_FROM;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        List<Map.Entry<String, Gang.Member>> roster = new ArrayList<>(g.members.entrySet());
        roster.sort((a, b) -> b.getValue().rank.ordinal() - a.getValue().rank.ordinal());
        for (Map.Entry<String, Gang.Member> e : roster) {
            if (slot > MEMBERS_TO) break;
            Gang.Member m = e.getValue();
            UUID mid = UUID.fromString(e.getKey());
            boolean online = Bukkit.getPlayer(mid) != null;
            List<String> lore = new ArrayList<>();
            lore.add(m.rank.colored());
            lore.add("<gray>Grade <white>" + m.grade);
            lore.add("<gray>Points <white>" + Numbers.shortFormat(m.pointsEarned) + "</white> <dark_gray>(" + Numbers.shortFormat(m.weeklyPoints) + " this week)");
            lore.add("<gray>Deposited <white>" + Numbers.shortFormat(m.deposited));
            lore.add("<dark_gray>Joined " + df.format(new Date(m.joined)));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setPlayerProfile(Bukkit.createProfile(mid, m.name));
            sm.displayName(Text.item((online ? "<green>● " : "<dark_gray>● ") + "<white>" + m.name));
            sm.lore(Text.lore(lore));
            head.setItemMeta(sm);
            inv.setItem(slot++, head);
        }

        boolean chat = gangs.chatToggled(id);
        inv.setItem(SLOT_CHAT, icon(chat ? Material.LIME_DYE : Material.GRAY_DYE, "ui/info", "<white>Crew chat: " + (chat ? "<green>on" : "<gray>off"),
                List.of("<gray>Click to send your chat to the crew.", "<dark_gray>Or /gc <message> for one line.")));
        List<String> log = new ArrayList<>();
        SimpleDateFormat tf = new SimpleDateFormat("MM-dd HH:mm");
        for (int k = 0; k < Math.min(10, g.log.size()); k++) {
            Gang.LogEntry le = g.log.get(k);
            log.add("<dark_gray>" + tf.format(new Date(le.at)) + " <gray>" + le.who + " " + le.what + " "
                    + (le.amount >= 0 ? "<green>+" : "<red>") + Numbers.shortFormat(le.amount));
        }
        if (log.isEmpty()) log.add("<dark_gray>Nothing yet.");
        inv.setItem(SLOT_LOG, icon(Material.PAPER, "ui/info", "<white>Bank Log", log));
        inv.setItem(SLOT_CLOSE, icon(Material.BARRIER, "ui/close", "<red>Close", null));
    }

    private int weeklyRank(Gang g) {
        List<Gang> ranked = gangs.top(true);
        return ranked.indexOf(g) + 1;
    }

    private static String bar(long progress, long target) {
        int filled = target <= 0 ? 20 : (int) Math.min(20, progress * 20 / target);
        return "<#c8743a>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(20 - filled);
    }

    static ItemStack icon(Material mat, String model, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore != null && !lore.isEmpty()) meta.lore(Text.lore(lore));
        if (model != null) meta.setItemModel(new NamespacedKey("spacepunk", model));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (slot == SLOT_CLOSE) { p.closeInventory(); return; }
        if (slot == SLOT_CHAT) gangs.toggleChat(p);
        else if (h.upgradeAt.containsKey(slot)) gangs.buyUpgrade(p, h.upgradeAt.get(slot));
        else return;
        Gang g = gangs.gangFor(p.getUniqueId());
        if (g == null) { p.closeInventory(); return; }
        h.upgradeAt.clear();
        render(p, g, h);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
