package dev.spacepunk.prison.trade;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /trade <player>              send a trade request (or accept theirs)
 * /trade accept|deny <player>
 * /trade toggle                stop or allow trade requests
 * /trade history               your recent trades
 * /trade locks                 your prestige trade locks
 * /trade admin log [player] | reload | cancel <player>
 */
final class TradeCommand implements BasicCommand {
    private static final String ADMIN = "spacepunk.admin";
    private final TradeModule module;

    TradeCommand(TradeModule module) { this.module = module; }

    @Override
    public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
        CommandSender sender = src.getSender();
        TradeManager t = module.manager();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            admin(sender, args);
            return;
        }
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<gray>/trade admin <log [player]|cancel <player>|reload>");
            return;
        }
        switch (sub) {
            case "" , "help" -> help(p);
            case "accept" -> {
                if (args.length < 2) { Text.send(p, "<gray>/trade accept <player>"); return; }
                Player r = Bukkit.getPlayerExact(args[1]);
                if (r == null) { Text.send(p, "<red>" + args[1] + " isn't online."); return; }
                t.accept(p, r);
            }
            case "deny", "decline" -> {
                if (args.length < 2) { Text.send(p, "<gray>/trade deny <player>"); return; }
                t.deny(p, args[1]);
            }
            case "toggle" -> Text.send(p, t.toggleBlocking(p)
                    ? "<gray>Trade requests are now <red>off</red>. <white>/trade toggle</white> turns them back on."
                    : "<gray>Trade requests are now <green>on</green>.");
            case "history", "log" -> history(p, t.history(p.getUniqueId(), 10), "Your recent trades");
            case "locks", "lock" -> locks(p, t);
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { Text.send(p, "<red>" + args[0] + " isn't online."); return; }
                t.request(p, target);
            }
        }
    }

    private void locks(Player p, TradeManager t) {
        TradeConfig c = t.cfg();
        EconomyService eco = Services.get(EconomyService.class);
        List<String> lines = new ArrayList<>();
        lines.add("<#c8743a>Your trade prestige locks");
        int grade = Services.find(dev.spacepunk.prison.api.ProgressionService.class).map(s -> s.grade(p.getUniqueId())).orElse(1);
        if (c.creditLockGrade > 0) {
            lines.add(grade >= c.creditLockGrade
                    ? "<red>Grade " + grade + ": you can't put Credits in trades until you prestige."
                    : "<gray>From Grade " + c.creditLockGrade + " you can't put Credits in trades (you're Grade " + grade + ").");
        }
        var mine = t.store().state().returnLocks.get(p.getUniqueId().toString());
        int shown = 0;
        if (mine != null) {
            for (var e : mine.entrySet()) {
                TradeStore.ReturnLock l = t.returnLock(p.getUniqueId(), UUID.fromString(e.getKey()));
                if (l == null) continue;
                shown++;
                lines.add("<gray>Can't send Credits to <white>" + l.senderName + "</white> for "
                        + TradeMenu.duration(l.until - System.currentTimeMillis()) + " <dark_gray>(they sent you "
                        + eco.format(Currency.CREDITS, l.amount) + "<dark_gray> and prestiged)");
            }
        }
        if (shown == 0) lines.add("<gray>No return locks.");
        Text.send(p, String.join("\n", lines));
    }

    private static void history(CommandSender to, List<TradeRecord> list, String title) {
        if (list.isEmpty()) { Text.send(to, "<gray>No trades on record."); return; }
        EconomyService eco = Services.get(EconomyService.class);
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d HH:mm", Locale.ROOT);
        StringBuilder sb = new StringBuilder("<#c8743a>" + title + "</#c8743a>");
        for (TradeRecord r : list) {
            sb.append("\n<dark_gray>#").append(r.id).append(" ").append(fmt.format(new Date(r.time))).append("</dark_gray> ")
                    .append("<white>").append(r.aName).append("</white> <gray>gave ").append(gave(eco, r.aCredits, r.aItems))
                    .append(" <dark_gray>⇄</dark_gray> <white>").append(r.bName).append("</white> <gray>gave ").append(gave(eco, r.bCredits, r.bItems));
        }
        Text.send(to, sb.toString());
    }

    private static String gave(EconomyService eco, long credits, List<String> items) {
        List<String> parts = new ArrayList<>();
        if (credits > 0) parts.add(eco.format(Currency.CREDITS, credits));
        if (items.size() <= 3) parts.addAll(items);
        else {
            parts.addAll(items.subList(0, 2));
            parts.add("<gray>" + (items.size() - 2) + " more stacks");
        }
        return parts.isEmpty() ? "<dark_gray>nothing" : String.join("<gray>, ", parts);
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) { Text.send(sender, "<red>No permission."); return; }
        TradeManager t = module.manager();
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                module.reload();
                Text.send(sender, "<gray>trade.yml reloaded.");
            }
            case "log" -> {
                if (args.length < 3) {
                    history(sender, t.history(null, 15), "Recent trades (full log: trades.log)");
                    return;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[2]);
                history(sender, t.history(op.getUniqueId(), 15), "Trades of " + args[2]);
            }
            case "cancel" -> {
                if (args.length < 3) { Text.send(sender, "<gray>/trade admin cancel <player>"); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                TradeSession s = target == null ? null : t.session(target.getUniqueId());
                if (s == null) { Text.send(sender, "<red>They aren't trading."); return; }
                t.cancel(s, "<red>An admin cancelled the trade. <gray>Your items are back.");
                Text.send(sender, "<gray>Cancelled.");
            }
            default -> Text.send(sender, "<gray>/trade admin log [player]\n/trade admin cancel <player>\n/trade admin reload");
        }
    }

    private void help(Player p) {
        Text.send(p, """
                <#c8743a>Trading</#c8743a>
                <white>/trade <player></white> <gray>ask someone to trade (items and Credits)
                <white>/trade accept|deny <player></white> <gray>answer a request
                <white>/trade history</white> <gray>your recent trades · <white>/trade locks</white> <gray>prestige locks
                <white>/trade toggle</white> <gray>turn trade requests off or on""");
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
        CommandSender sender = src.getSender();
        List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> !(sender instanceof Player p) || !p.getName().equals(n)).toList();
        if (args.length <= 1) {
            List<String> out = new ArrayList<>(names);
            out.addAll(List.of("accept", "deny", "history", "locks", "toggle"));
            if (sender.hasPermission(ADMIN)) out.add("admin");
            String pre = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(pre)).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("accept") || sub.equals("deny"))) return names;
        if (args.length == 2 && sub.equals("admin") && sender.hasPermission(ADMIN)) return List.of("log", "cancel", "reload");
        if (args.length == 3 && sub.equals("admin")) return names;
        return List.of();
    }
}
