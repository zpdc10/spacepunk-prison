package dev.spacepunk.prison.quests;

import dev.spacepunk.prison.api.ObjectiveTypes;
import dev.spacepunk.prison.quests.QuestConfig.Chapter;
import dev.spacepunk.prison.quests.QuestConfig.QuestDef;
import dev.spacepunk.prison.quests.QuestConfig.RewardDef;
import dev.spacepunk.prison.quests.QuestData.Active;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** The quest log: story, tutorials, contracts and the monthly track. Vanilla items, pack icons where they exist. */
final class QuestMenu implements Listener {
    enum Tab { STORY, TUTORIALS, CONTRACTS, TRACK }

    private static final int SIZE = 54;
    private static final int[] GRID = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_PREV = 45, SLOT_CLOSE = 49, SLOT_NEXT = 53;

    private final QuestManager quests;

    QuestMenu(QuestManager quests) { this.quests = quests; }

    static final class Holder implements InventoryHolder {
        final UUID owner;
        Tab tab;
        int page;
        Inventory inventory;
        final Map<Integer, Runnable> actions = new HashMap<>();

        Holder(UUID owner, Tab tab, int page) {
            this.owner = owner;
            this.tab = tab;
            this.page = page;
        }

        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    void open(Player p, Tab tab) { open(p, tab, -1); }

    void open(Player p, Tab tab, int page) {
        QuestData d = quests.data(p.getUniqueId());
        if (d == null) {
            Text.send(p, "<red>Your quest log is still loading.");
            return;
        }
        Holder h = new Holder(p.getUniqueId(), tab, page);
        h.inventory = Bukkit.createInventory(h, SIZE, Text.mm("<#3a3028>QUEST LOG"));
        render(p, h, d);
        p.openInventory(h.inventory);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
    }

    private void rerender(Player p, Holder h) {
        QuestData d = quests.data(p.getUniqueId());
        if (d == null) { p.closeInventory(); return; }
        render(p, h, d);
    }

    private void render(Player p, Holder h, QuestData d) {
        Inventory inv = h.inventory;
        inv.clear();
        h.actions.clear();
        ItemStack filler = filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);
        tab(p, h, 1, Tab.STORY, Material.WRITABLE_BOOK, "<#c8743a>Story", "<gray>" + quests.cfg().storyTitle);
        tab(p, h, 3, Tab.TUTORIALS, Material.COMPASS, "<#8fd3ff>Tutorials", "<gray>Learn each station system");
        tab(p, h, 5, Tab.CONTRACTS, Material.CLOCK, "<gold>Contracts", "<gray>Daily and weekly jobs");
        tab(p, h, 7, Tab.TRACK, Material.NETHER_STAR, "<yellow>" + quests.cfg().trackName, "<gray>Monthly rewards for contract points");
        switch (h.tab) {
            case STORY -> renderStory(p, h, d);
            case TUTORIALS -> renderTutorials(p, h, d);
            case CONTRACTS -> renderContracts(p, h, d);
            case TRACK -> renderTrack(p, h, d);
        }
        set(h, SLOT_CLOSE, icon(Material.BARRIER, "ui/close", "<red>Close", List.of(), false), p::closeInventory);
        if (h.tab == Tab.TUTORIALS) {
            set(h, 47, icon(Material.WRITTEN_BOOK, "ui/info", "<#e0b354>Station Guide", List.of(
                    "<gray>How every system works, step by step.", "", "<white>Click</white> <gray>to open <white>/guide"), false), () -> {
                p.closeInventory();
                p.performCommand("guide");
            });
        }
    }

    private void tab(Player p, Holder h, int slot, Tab tab, Material m, String name, String desc) {
        boolean on = h.tab == tab;
        set(h, slot, icon(m, null, (on ? "<bold>" : "") + name, List.of(desc, on ? "<dark_gray>Viewing" : "<yellow>Click to open"), on),
                () -> { h.tab = tab; h.page = -1; rerender(p, h); click(p); });
    }

    // ------------------------------------------------------------------ story

    private void renderStory(Player p, Holder h, QuestData d) {
        QuestConfig c = quests.cfg();
        if (c.chapters.isEmpty()) return;
        QuestDef next = quests.nextStoryStep(d);
        int current = 0;
        for (int i = 0; i < c.chapters.size(); i++) if (next != null && c.chapters.get(i).id().equals(next.chapter())) current = i;
        if (next == null) current = c.chapters.size() - 1;
        int shown = h.page < 0 ? current : Math.min(h.page, c.chapters.size() - 1);
        int[] chapterSlots = {11, 12, 13, 14, 15};
        for (int i = 0; i < c.chapters.size() && i < chapterSlots.length; i++) {
            Chapter ch = c.chapters.get(i);
            boolean finished = ch.steps().stream().allMatch(s -> d.done.contains(s.id()));
            boolean started = ch.steps().stream().anyMatch(s -> d.done.contains(s.id()) || d.story.containsKey(s.id()));
            Material m = finished ? Material.ENCHANTED_BOOK : started ? material(ch.icon(), Material.WRITABLE_BOOK) : Material.BOOK;
            List<String> lore = new ArrayList<>();
            QuestConfig.Character who = c.character(ch.character());
            if (who != null) lore.add("<gray>With " + who.display());
            lore.add(finished ? "<green>Complete" : started ? "<yellow>In progress" : "<dark_gray>Unlocks at Grade " + ch.minGrade());
            int doneCount = (int) ch.steps().stream().filter(s -> d.done.contains(s.id())).count();
            lore.add("<gray>" + doneCount + "/" + ch.steps().size() + " steps");
            final int idx = i;
            set(h, chapterSlots[i], icon(m, null, (i == shown ? "<bold>" : "") + "<#c8743a>" + ch.name(), lore, i == shown),
                    () -> { h.page = idx; rerender(p, h); click(p); });
        }
        Chapter ch = c.chapters.get(shown);
        QuestConfig.Character who = c.character(ch.character());
        for (int i = 0; i < ch.steps().size() && i < GRID.length; i++) {
            QuestDef step = ch.steps().get(i);
            Active a = d.story.get(step.id());
            if (d.done.contains(step.id())) {
                List<String> lore = new ArrayList<>();
                lore.add("<green>Complete");
                lore.add("<yellow>Click to replay the log");
                set(h, GRID[i], icon(Material.FILLED_MAP, null, "<green>" + step.name(), lore, false), () -> {
                    p.closeInventory();
                    Text.send(p, "<#c8743a>" + step.name());
                    for (String line : step.intro()) quests.say(p, who, line);
                    for (String line : step.outro()) quests.say(p, who, line);
                });
            } else if (a != null) {
                set(h, GRID[i], activeIcon(a, d, true), () -> activeClick(p, h, a, QuestKind.STORY, d));
            } else if (step == quests.nextStoryStep(d)) {
                List<String> lore = List.of("<red>Needs Grade " + step.minGrade() + " to continue.");
                set(h, GRID[i], icon(Material.MAP, null, "<gray>" + step.name(), lore, false), null);
            } else {
                set(h, GRID[i], icon(Material.PAPER, null, "<dark_gray>???", List.of("<dark_gray>Not reached yet"), false), null);
            }
        }
    }

    // ------------------------------------------------------------------ tutorials

    private void renderTutorials(Player p, Holder h, QuestData d) {
        QuestConfig c = quests.cfg();
        QuestManager.Ctx ctx = quests.ctx(p, d);
        List<QuestDef> list = new ArrayList<>();
        for (QuestDef t : c.tutorials.values()) {
            boolean available = t.requires().stream().allMatch(quests::hasObjectiveType);
            if (available || d.done.contains(t.id())) list.add(t);
        }
        int pages = Math.max(1, (list.size() + GRID.length - 1) / GRID.length);
        int page = Math.max(0, Math.min(pages - 1, h.page));
        pager(p, h, page, pages);
        for (int i = 0; i < GRID.length; i++) {
            int idx = page * GRID.length + i;
            if (idx >= list.size()) break;
            QuestDef t = list.get(idx);
            Active a = d.tutorials.get(t.id());
            Material base = material(t.icon(), Material.BOOK);
            if (d.done.contains(t.id())) {
                set(h, GRID[i], icon(base, null, "<green>" + t.name(), List.of("<green>Complete"), false), null);
            } else if (a != null) {
                set(h, GRID[i], activeIcon(a, d, true), () -> activeClick(p, h, a, QuestKind.TUTORIAL, d));
            } else {
                List<String> lore = new ArrayList<>();
                if (ctx.grade() < t.minGrade()) lore.add("<red>Unlocks at Grade " + t.minGrade());
                if (ctx.prestige() < t.minPrestige()) lore.add("<red>Unlocks at Prestige " + t.minPrestige());
                if (!t.after().isEmpty() && !d.done.contains(t.after())) {
                    QuestDef before = c.tutorials.get(t.after());
                    lore.add("<red>Finish " + (before == null ? t.after() : before.name()) + " first");
                }
                set(h, GRID[i], icon(Material.GRAY_DYE, null, "<gray>" + t.name(), lore, false), null);
            }
        }
    }

    // ------------------------------------------------------------------ contracts

    private void renderContracts(Player p, Holder h, QuestData d) {
        QuestConfig c = quests.cfg();
        set(h, 19, icon(Material.CLOCK, null, "<gold><bold>Daily contracts", List.of(
                "<gray>New ones in <white>" + quests.resetIn(QuestKind.DAILY),
                d.dailyBonus ? "<green>All-dailies bonus collected" : "<gray>Finish all " + d.daily.size() + " for a bonus"), false), null);
        for (int i = 0; i < d.daily.size() && i < 4; i++) {
            Active a = d.daily.get(i);
            set(h, 20 + i, activeIcon(a, d, false), () -> activeClick(p, h, a, QuestKind.DAILY, d));
        }
        set(h, 28, icon(Material.RECOVERY_COMPASS, null, "<gold><bold>Weekly contracts", List.of(
                "<gray>New ones in <white>" + quests.resetIn(QuestKind.WEEKLY)), false), null);
        for (int i = 0; i < d.weekly.size() && i < 4; i++) {
            Active a = d.weekly.get(i);
            set(h, 29 + i, activeIcon(a, d, false), () -> activeClick(p, h, a, QuestKind.WEEKLY, d));
        }
        QuestConfig.TrackTier next = c.tiers.stream().filter(t -> t.points() > d.points).findFirst().orElse(null);
        long claimable = c.tiers.stream().filter(t -> t.points() <= d.points && !d.claimed.contains(t.index())).count();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Points this month: <yellow>" + d.points);
        lore.add(next == null ? "<green>Track complete!" : "<gray>Next tier at <yellow>" + next.points());
        if (claimable > 0) lore.add("<green>" + claimable + " reward(s) to claim");
        lore.add("<gray>Resets in <white>" + quests.resetInMonth());
        lore.add("<yellow>Click to open the track");
        set(h, 25, icon(Material.NETHER_STAR, null, "<yellow>" + c.trackName, lore, claimable > 0),
                () -> { h.tab = Tab.TRACK; h.page = -1; rerender(p, h); click(p); });
    }

    // ------------------------------------------------------------------ monthly track

    private void renderTrack(Player p, Holder h, QuestData d) {
        QuestConfig c = quests.cfg();
        int pages = Math.max(1, (c.tiers.size() + GRID.length - 1) / GRID.length);
        int firstOpen = 0;
        for (QuestConfig.TrackTier t : c.tiers) {
            if (t.points() <= d.points && !d.claimed.contains(t.index())) { firstOpen = t.index(); break; }
            if (t.points() > d.points) { firstOpen = t.index(); break; }
        }
        int page = h.page < 0 ? firstOpen / GRID.length : Math.max(0, Math.min(pages - 1, h.page));
        h.page = page;
        pager(p, h, page, pages);
        set(h, 4, icon(Material.NETHER_STAR, null, "<yellow><bold>" + c.trackName, List.of(
                "<gray>Points this month: <yellow>" + d.points + "<gray>/" + c.maxPoints(),
                "<gray>Earn points from daily and weekly contracts.",
                "<gray>Resets in <white>" + quests.resetInMonth()), false), null);
        QuestManager.Ctx ctx = quests.ctx(p, d);
        for (int i = 0; i < GRID.length; i++) {
            int idx = page * GRID.length + i;
            if (idx >= c.tiers.size()) break;
            QuestConfig.TrackTier t = c.tiers.get(idx);
            boolean claimed = d.claimed.contains(idx), ready = d.points >= t.points();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Needs <yellow>" + t.points() + "<gray> points");
            lore.addAll(rewardLines(t.rewards(), ctx));
            lore.add(claimed ? "<green>Claimed" : ready ? "<yellow><bold>Click to claim" : "<dark_gray>Locked");
            Material m = claimed ? Material.LIME_STAINED_GLASS_PANE : ready ? material(t.icon(), Material.CHEST)
                    : Material.GRAY_STAINED_GLASS_PANE;
            set(h, GRID[i], icon(m, null, (ready ? "<yellow>" : "<gray>") + "Tier " + (idx + 1), lore, ready && !claimed),
                    claimed || !ready ? null : () -> {
                        Text.send(p, quests.claimTier(p, idx));
                        rerender(p, h);
                    });
        }
    }

    private static List<String> rewardLines(RewardDef r, QuestManager.Ctx ctx) {
        List<String> out = new ArrayList<>();
        long cr = r.credits().resolve(ctx.grade(), ctx.rankCost()), tk = r.tokens().resolve(ctx.grade(), ctx.rankCost()),
                sh = r.shards().resolve(ctx.grade(), ctx.rankCost());
        if (cr > 0) out.add("<dark_gray>▸ <gold>" + Numbers.shortFormat(cr) + " Credits");
        if (tk > 0) out.add("<dark_gray>▸ <aqua>" + Numbers.shortFormat(tk) + " Tokens");
        if (sh > 0) out.add("<dark_gray>▸ <light_purple>" + Numbers.shortFormat(sh) + " Shards");
        for (QuestConfig.CommandReward c : r.commands()) if (!c.text().isEmpty()) out.add("<dark_gray>▸ " + c.text());
        return out;
    }

    // ------------------------------------------------------------------ shared pieces

    private ItemStack activeIcon(Active a, QuestData d, boolean quest) {
        QuestKind kind = QuestManager.kindOf(a);
        boolean tracked = a == quests.trackedQuest(d);
        List<String> lore = new ArrayList<>();
        for (int i = 0; i < a.texts.length; i++) {
            boolean ok = a.have[i] >= a.need[i];
            lore.add((ok ? "<green>✔ " : "<dark_gray>▸ <gray>") + a.texts[i]);
            if (!ok) {
                double f = a.need[i] <= 0 ? 1 : (double) a.have[i] / a.need[i];
                lore.add("   " + QuestManager.bar(f) + " <gray>" + Numbers.shortFormat(a.have[i]) + "/" + Numbers.shortFormat(a.need[i]));
            }
        }
        String rewards = quests.rewardText(a);
        if (!rewards.isEmpty()) lore.add("<gray>Reward: " + rewards);
        if (a.done) lore.add("<green><bold>Done");
        else {
            if (QuestManager.hasDelivery(a)) lore.add("<yellow>Click to hand in cargo");
            lore.add(tracked ? "<aqua>Shown on your sidebar" : "<yellow>" + (QuestManager.hasDelivery(a) ? "Right-click" : "Click") + " to track");
        }
        Material m = a.done ? Material.LIME_DYE : switch (kind) {
            case STORY -> Material.WRITABLE_BOOK;
            case TUTORIAL -> Material.BOOK;
            case DAILY -> Material.PAPER;
            case WEEKLY -> Material.MAP;
        };
        QuestDef def = quests.cfg().byKey(kind, a.id);
        if (!a.done && def != null && !def.icon().isEmpty()) m = material(def.icon(), m);
        String color = a.done ? "<green>" : quest ? "<white>" : "<gold>";
        return icon(m, null, color + a.name, lore, tracked && !a.done);
    }

    private void activeClick(Player p, Holder h, Active a, QuestKind kind, QuestData d) {
        if (a.done) return;
        boolean right = lastClickRight;
        if (QuestManager.hasDelivery(a) && !right) {
            long n = quests.deliver(p, a);
            Text.send(p, n > 0 ? "<green>Handed in " + Numbers.shortFormat(n) + " from your cargo hold."
                    : "<red>Nothing to hand in. Mine it and keep it in your cargo hold (don't sell it).");
        } else {
            quests.track(p, QuestManager.trackKey(kind, a, d));
            click(p);
        }
        rerender(p, h);
    }

    private boolean lastClickRight;

    private void pager(Player p, Holder h, int page, int pages) {
        if (page > 0) set(h, SLOT_PREV, icon(Material.ARROW, "ui/previous", "<gray>Previous page", List.of(), false),
                () -> { h.page = page - 1; rerender(p, h); click(p); });
        if (page < pages - 1) set(h, SLOT_NEXT, icon(Material.ARROW, "ui/next", "<gray>Next page", List.of(), false),
                () -> { h.page = page + 1; rerender(p, h); click(p); });
    }

    private static void set(Holder h, int slot, ItemStack item, Runnable action) {
        h.inventory.setItem(slot, item);
        if (action != null) h.actions.put(slot, action);
    }

    private static void click(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f); }

    static Material material(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return m == null || !m.isItem() ? fallback : m;
    }

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("spacepunk", "ui/panel"));
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack icon(Material m, String model, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        meta.lore(Text.lore(lore));
        if (model != null) meta.setItemModel(new NamespacedKey("spacepunk", model));
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(h.owner)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Runnable r = h.actions.get(e.getRawSlot());
        if (r == null) return;
        lastClickRight = e.isRightClick();
        r.run();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    static boolean isDelivery(String type) { return ObjectiveTypes.DELIVER_CARGO.equals(type); }
}
