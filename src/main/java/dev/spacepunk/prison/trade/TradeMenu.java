package dev.spacepunk.prison.trade;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The trade window. Each player sees their own offer on the left and the partner's on the right.
 *
 *   cols 0-3, rows 0-3  your items (click an item in your inventory to put it up, click here to take it back)
 *   col 4               divider / countdown
 *   cols 5-8, rows 0-3  their items
 *   36 your Credits (click, then type an amount in chat) · 44 their Credits
 *   45 cancel · 47 your accept · 49 how it works · 51 their status
 */
final class TradeMenu implements Listener {
    static final int SLOT_MY_CREDITS = 36, SLOT_THEIR_CREDITS = 44;
    static final int SLOT_CANCEL = 45, SLOT_ACCEPT = 47, SLOT_INFO = 49, SLOT_THEIR_STATUS = 51;

    private final Plugin plugin;
    private final TradeManager trades;
    private boolean useModels = true;

    TradeMenu(Plugin plugin, TradeManager trades) {
        this.plugin = plugin;
        this.trades = trades;
    }

    void useModels(boolean b) { this.useModels = b; }

    private static EconomyService eco() { return Services.get(EconomyService.class); }

    static int mySlot(int i) { return (i / 4) * 9 + (i % 4); }

    static int theirSlot(int i) { return (i / 4) * 9 + 5 + (i % 4); }

    /** Index into my items for a top-inventory slot, or -1. */
    static int myIndex(int slot) {
        int row = slot / 9, col = slot % 9;
        return row < 4 && col < 4 ? row * 4 + col : -1;
    }

    void open(Player p, TradeSession.Side side) {
        side.inventory = Bukkit.createInventory(side, 54, Text.mm("<dark_gray>Trade · " + side.other().name));
        renderSide(side);
        p.openInventory(side.inventory);
    }

    /** After typing a Credits amount: show the same window again. */
    void reopen(Player p, TradeSession.Side side) {
        side.typing = false;
        if (side.session.over) return;
        renderSide(side);
        p.openInventory(side.inventory);
    }

    void render(TradeSession s) {
        renderSide(s.a);
        renderSide(s.b);
    }

    private void renderSide(TradeSession.Side me) {
        Inventory inv = me.inventory;
        if (inv == null) return;
        TradeSession s = me.session;
        TradeSession.Side them = me.other();
        inv.clear();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, "ui/panel", " ", List.of());
        ItemMeta fm = filler.getItemMeta();
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        for (int i = 36; i < 54; i++) inv.setItem(i, filler);

        for (int i = 0; i < me.items.size(); i++) inv.setItem(mySlot(i), withHint(me.items.get(i)));
        for (int i = 0; i < them.items.size(); i++) inv.setItem(theirSlot(i), them.items.get(i).clone());

        // Divider column: countdown once both accepted.
        for (int row = 0; row < 5; row++) {
            ItemStack d;
            if (s.countdown > 0) {
                d = icon(Material.LIME_STAINED_GLASS_PANE, "ui/confirm", "<green>Trading in " + s.countdown + "...",
                        List.of("<gray>Any change cancels the countdown."));
                d.setAmount(Math.max(1, Math.min(64, s.countdown)));
            } else {
                d = icon(Material.BLACK_STAINED_GLASS_PANE, "ui/divider", "<dark_gray>" + me.name + " ← · → " + them.name, List.of());
            }
            inv.setItem(row * 9 + 4, d);
        }

        String lock = trades.creditLock(me.id, them.id, them.name);
        List<String> myCredLore = new ArrayList<>();
        myCredLore.add("<gray>Offering: " + eco().format(Currency.CREDITS, me.credits));
        myCredLore.add("<gray>Wallet: " + eco().format(Currency.CREDITS, eco().balance(me.id, Currency.CREDITS)));
        myCredLore.add("");
        if (lock != null) {
            myCredLore.add("<red>Prestige-locked for this trade");
            myCredLore.add("<dark_gray>Hover the book for why.");
        } else {
            myCredLore.add("<#c8743a>Click<gray>, then type an amount in chat");
            myCredLore.add("<dark_gray>(500, 12.5k, 3M, or 0 to clear)");
        }
        inv.setItem(SLOT_MY_CREDITS, icon(lock != null ? Material.IRON_NUGGET : Material.GOLD_NUGGET, "currency/credit",
                "<gold>Your Credits", myCredLore));
        inv.setItem(SLOT_THEIR_CREDITS, icon(Material.GOLD_NUGGET, "currency/credit", "<gold>" + them.name + "'s Credits",
                List.of("<gray>Offering: " + eco().format(Currency.CREDITS, them.credits))));

        inv.setItem(SLOT_CANCEL, icon(Material.BARRIER, "ui/cancel", "<red>Cancel trade", List.of("<gray>Everything goes back to its owner.")));
        if (me.accepted) {
            inv.setItem(SLOT_ACCEPT, icon(Material.LIME_STAINED_GLASS_PANE, "ui/confirm", "<green>You accepted",
                    List.of(them.accepted ? "<gray>Both accepted. Trading soon." : "<gray>Waiting for " + them.name + ".", "", "<#c8743a>Click to take it back")));
        } else {
            inv.setItem(SLOT_ACCEPT, icon(Material.RED_STAINED_GLASS_PANE, "ui/locked", "<#c8743a>Accept",
                    List.of("<gray>You get: " + TradeManager.summary(them), "<gray>You give: " + TradeManager.summary(me), "",
                            "<#c8743a>Click when you're happy with the deal")));
        }
        inv.setItem(SLOT_THEIR_STATUS, them.accepted
                ? icon(Material.LIME_DYE, "ui/confirm", "<green>" + them.name + " accepted", List.of())
                : icon(Material.GRAY_DYE, "ui/info", "<gray>" + them.name + " hasn't accepted", List.of(them.typing ? "<dark_gray>(typing an amount)" : "")));

        List<String> info = new ArrayList<>(List.of(
                "<gray>Click items in your inventory to put",
                "<gray>them up (right-click: one at a time).",
                "<gray>Click your items above to take them back.",
                "",
                "<gray>When both accept, a " + trades.cfg().countdownSeconds + "s countdown runs.",
                "<gray>Any change resets both accepts.",
                "",
                "<dark_gray>Traded Credits never count as income.",
                "<dark_gray>Drills and rods are soulbound."));
        if (trades.cfg().creditLockGrade > 0) {
            info.add("<dark_gray>From Grade " + trades.cfg().creditLockGrade + " you can't send Credits,");
            info.add("<dark_gray>and Credits sent to you by someone who");
            info.add("<dark_gray>then prestiged can't go back to them.");
        }
        if (lock != null) {
            info.add("");
            info.addAll(wrap(lock));
        }
        inv.setItem(SLOT_INFO, icon(Material.BOOK, "ui/info", "<#c8743a>How trading works", info));
    }

    private static List<String> wrap(String mini) {
        String plain = plain(mini);
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : plain.split(" ")) {
            if (line.length() + w.length() > 38) {
                out.add("<red>" + line.toString().trim());
                line.setLength(0);
            }
            line.append(w).append(' ');
        }
        if (!line.isEmpty()) out.add("<red>" + line.toString().trim());
        return out;
    }

    private ItemStack withHint(ItemStack item) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return copy;
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.item(""));
        lore.add(Text.item("<dark_gray>Click to take back"));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    private ItemStack icon(Material mat, String model, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        List<String> l = lore == null ? List.of() : lore.stream().filter(x -> x != null).toList();
        if (!l.isEmpty() && !(l.size() == 1 && l.get(0).isEmpty())) meta.lore(Text.lore(l));
        if (model != null && useModels) meta.setItemModel(new NamespacedKey("spacepunk", model));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    // =====================================================================================
    // Clicks
    // =====================================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof TradeSession.Side me)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (me.session.over || e.getClickedInventory() == null) return;
        ClickType click = e.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;

        if (e.getClickedInventory() == e.getView().getBottomInventory()) {
            ItemStack current = e.getCurrentItem();
            if (current == null || current.getType().isAir()) return;
            ItemStack moving = current.clone();
            if (click == ClickType.RIGHT) moving.setAmount(1);
            switch (trades.offer(me, moving)) {
                case OK -> {
                    int left = current.getAmount() - moving.getAmount();
                    if (left <= 0) e.getClickedInventory().setItem(e.getSlot(), null);
                    else {
                        ItemStack rest = current.clone();
                        rest.setAmount(left);
                        e.getClickedInventory().setItem(e.getSlot(), rest);
                    }
                }
                case NOT_ALLOWED -> Text.send(p, "<red>That item can't be traded.");
                case FULL -> Text.send(p, "<red>Your side of the table is full (" + TradeSession.MAX_ITEMS + " stacks).");
            }
            return;
        }

        int slot = e.getRawSlot();
        int idx = myIndex(slot);
        if (idx >= 0) {
            if (idx < me.items.size()) trades.withdraw(p, me, idx, click == ClickType.RIGHT ? 1 : Integer.MAX_VALUE);
            return;
        }
        switch (slot) {
            case SLOT_CANCEL -> trades.cancel(me.session,
                    me == me.session.a ? "<gray>You cancelled the trade." : "<gray>" + me.name + " cancelled the trade. <gray>Your items are back.",
                    me == me.session.b ? "<gray>You cancelled the trade." : "<gray>" + me.name + " cancelled the trade. <gray>Your items are back.");
            case SLOT_ACCEPT -> trades.toggleAccept(me);
            case SLOT_MY_CREDITS -> {
                String lock = trades.creditLock(me.id, me.other().id, me.other().name);
                if (lock != null) {
                    Text.send(p, lock);
                    return;
                }
                me.typing = true;
                me.typingUntil = System.currentTimeMillis() + trades.cfg().inputSeconds * 1000L;
                p.closeInventory();
                Text.send(p, "<#c8743a>Type how many Credits to offer</#c8743a> <gray>(e.g. 500, 12.5k, 3M, 0 to clear, or <white>cancel</white>)."
                        + " You have " + eco().format(Currency.CREDITS, eco().balance(me.id, Currency.CREDITS)) + "<gray>.");
                render(me.session);
            }
            default -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof TradeSession.Side) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof TradeSession.Side me)) return;
        if (me.session.over || me.typing) return;
        // Closing the window (Esc) cancels the trade, one tick later so we're not inside the close event.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (me.session.over || me.typing) return;
            Player p = Bukkit.getPlayer(me.id);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() == me) return;
            trades.cancel(me.session,
                    me == me.session.a ? "<gray>You closed the trade window; trade cancelled." : "<gray>" + me.name + " closed the trade window. <gray>Trade cancelled, your items are back.",
                    me == me.session.b ? "<gray>You closed the trade window; trade cancelled." : "<gray>" + me.name + " closed the trade window. <gray>Trade cancelled, your items are back.");
        });
    }

    /** Credits amount typed in chat. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        TradeSession.Side me = typingSide(e.getPlayer());
        if (me == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> typed(e.getPlayer(), msg));
    }

    /** Called on the async chat thread: only reads. A stale answer is re-checked on the main thread. */
    private TradeSession.Side typingSide(Player p) {
        TradeSession s = trades.session(p.getUniqueId());
        if (s == null) return null;
        TradeSession.Side side = s.side(p.getUniqueId());
        return side != null && side.typing ? side : null;
    }

    void typed(Player p, String msg) {
        TradeSession s = trades.session(p.getUniqueId());
        TradeSession.Side me = s == null ? null : s.side(p.getUniqueId());
        if (me == null || !me.typing || s.over) return;
        if (!msg.equalsIgnoreCase("cancel")) {
            long amount;
            try {
                amount = msg.equalsIgnoreCase("all") ? eco().balance(me.id, Currency.CREDITS) : Numbers.parse(msg.replace(",", ""));
            } catch (NumberFormatException ex) {
                Text.send(p, "<red>That's not an amount. <gray>Examples: 500, 12.5k, 3M.");
                reopen(p, me);
                return;
            }
            String err = trades.setCredits(me, amount);
            if (err != null) Text.send(p, err);
        }
        reopen(p, me);
        render(s);
    }

    // =====================================================================================
    // Text helpers
    // =====================================================================================

    static String describe(ItemStack item) {
        if (item == null) return "<dark_gray>an item";
        String name = Text.MM.serialize(item.effectiveName());
        return (item.getAmount() > 1 ? "<white>" + item.getAmount() + "x</white> " : "") + name;
    }

    static String plain(String mini) { return PlainTextComponentSerializer.plainText().serialize(Text.mm(mini)); }

    static String duration(long ms) {
        if (ms <= 0) return "a moment";
        long s = ms / 1000;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return Math.max(1, m) + "m";
    }

    static String ago(long time) {
        long ms = System.currentTimeMillis() - time;
        return duration(ms).toLowerCase(Locale.ROOT) + " ago";
    }
}
