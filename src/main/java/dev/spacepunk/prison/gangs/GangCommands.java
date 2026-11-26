package dev.spacepunk.prison.gangs;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /gang and /gc. */
final class GangCommands {
    private static final List<String> SUBS = List.of("create", "invite", "accept", "deny", "leave", "kick", "promote", "demote",
            "transfer", "disband", "info", "top", "bank", "deposit", "withdraw", "upgrade", "contracts", "chat", "color", "motd", "help");
    private static final List<String> ADMIN = List.of("reload", "disband", "setbank", "addpoints", "weeklyreset", "territory");

    private final GangManager gangs;
    private final GangMenu menu;
    private final Runnable reload;

    GangCommands(GangManager gangs, GangMenu menu, Runnable reload) {
        this.gangs = gangs;
        this.menu = menu;
        this.reload = reload;
    }

    BasicCommand gang() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender sender = src.getSender();
                if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
                    admin(sender, Arrays.copyOfRange(args, 1, args.length));
                    return;
                }
                if (!(sender instanceof Player p)) {
                    Text.send(sender, "<gray>/gang admin ...");
                    return;
                }
                if (args.length == 0) {
                    if (gangs.gangFor(p.getUniqueId()) != null) menu.open(p);
                    else help(p);
                    return;
                }
                String sub = args[0].toLowerCase(Locale.ROOT);
                String a1 = args.length > 1 ? args[1] : null;
                switch (sub) {
                    case "create" -> {
                        if (args.length < 3) { Text.send(p, "<gray>/gang create <name> <tag>"); return; }
                        gangs.create(p, args[1], args[2]);
                    }
                    case "invite" -> {
                        if (a1 == null) { Text.send(p, "<gray>/gang invite <player>"); return; }
                        Player t = Bukkit.getPlayerExact(a1);
                        if (t == null) { Text.send(p, "<red>" + a1 + " isn't online."); return; }
                        gangs.invite(p, t);
                    }
                    case "accept", "join" -> gangs.accept(p, a1);
                    case "deny" -> gangs.deny(p, a1);
                    case "leave" -> gangs.leave(p);
                    case "kick" -> { if (a1 == null) Text.send(p, "<gray>/gang kick <player>"); else gangs.kick(p, a1); }
                    case "promote" -> { if (a1 == null) Text.send(p, "<gray>/gang promote <player>"); else gangs.promote(p, a1, true); }
                    case "demote" -> { if (a1 == null) Text.send(p, "<gray>/gang demote <player>"); else gangs.promote(p, a1, false); }
                    case "transfer" -> { if (a1 == null) Text.send(p, "<gray>/gang transfer <player>"); else gangs.transfer(p, a1); }
                    case "disband" -> gangs.disband(p, "confirm".equalsIgnoreCase(a1));
                    case "info" -> info(p, a1);
                    case "top" -> top(p, a1 != null && a1.toLowerCase(Locale.ROOT).startsWith("l"));
                    case "bank", "log" -> bank(p);
                    case "deposit", "d" -> gangs.deposit(p, a1);
                    case "withdraw", "w" -> gangs.withdraw(p, a1);
                    case "upgrade", "upgrades" -> {
                        if (a1 == null) menu.open(p);
                        else gangs.buyUpgrade(p, a1);
                    }
                    case "contracts", "menu" -> menu.open(p);
                    case "chat", "c" -> {
                        if (args.length > 1) gangs.chat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                        else gangs.toggleChat(p);
                    }
                    case "color", "colour" -> {
                        if (a1 == null) Text.send(p, "<gray>/gang color <" + String.join("|", gangs.cfg().colors.keySet()) + ">");
                        else gangs.setColor(p, a1);
                    }
                    case "motd", "notice" -> {
                        if (a1 == null) Text.send(p, "<gray>/gang motd <text>");
                        else gangs.setMotd(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                    }
                    default -> help(p);
                }
            }

            @Override
            public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                if (args.length <= 1) {
                    List<String> out = new ArrayList<>(SUBS);
                    if (s.hasPermission("spacepunk.admin")) out.add("admin");
                    return filter(out, args.length == 0 ? "" : args[0]);
                }
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (args.length == 2) {
                    String pre = args[1];
                    switch (sub) {
                        case "invite" -> { return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), pre); }
                        case "kick", "promote", "demote", "transfer" -> {
                            if (s instanceof Player p) {
                                Gang g = gangs.gangFor(p.getUniqueId());
                                if (g != null) return filter(g.members.values().stream().map(m -> m.name).toList(), pre);
                            }
                            return List.of();
                        }
                        case "accept", "deny", "info" -> { return filter(gangs.all().stream().map(g -> g.name).toList(), pre); }
                        case "top" -> { return filter(List.of("weekly", "level"), pre); }
                        case "deposit", "withdraw" -> { return filter(List.of("all", "10K", "100K", "1M"), pre); }
                        case "upgrade" -> { return filter(new ArrayList<>(gangs.cfg().upgrades.keySet()), pre); }
                        case "color" -> { return filter(new ArrayList<>(gangs.cfg().colors.keySet()), pre); }
                        case "disband" -> { return filter(List.of("confirm"), pre); }
                        case "admin" -> { return s.hasPermission("spacepunk.admin") ? filter(ADMIN, pre) : List.of(); }
                        default -> { return List.of(); }
                    }
                }
                if (sub.equals("admin") && s.hasPermission("spacepunk.admin") && args.length == 3) {
                    String a = args[1].toLowerCase(Locale.ROOT);
                    if (a.equals("territory")) return filter(new ArrayList<>(gangs.cfg().territories.keySet()), args[2]);
                    if (List.of("disband", "setbank", "addpoints").contains(a)) return filter(gangs.all().stream().map(g -> g.name).toList(), args[2]);
                }
                if (sub.equals("admin") && args.length == 4 && args[1].equalsIgnoreCase("territory")) {
                    List<String> names = new ArrayList<>(gangs.all().stream().map(g -> g.name).toList());
                    names.add("none");
                    return filter(names, args[3]);
                }
                return List.of();
            }
        };
    }

    BasicCommand gc() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) return;
                if (args.length == 0) { gangs.toggleChat(p); return; }
                gangs.chat(p, String.join(" ", args));
            }
        };
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String pre = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(pre)).toList();
    }

    private void help(Player p) {
        GangsConfig c = gangs.cfg();
        Text.send(p, "<#c8743a><b>CREWS</b> <gray>(unlock at Prestige " + c.createPrestige + ")");
        String[][] lines = {
                {"/gang create <name> <tag>", "found a crew (" + Numbers.shortFormat(c.createCost) + " Credits)"},
                {"/gang", "crew terminal: bank, upgrades, contracts, roster"},
                {"/gang invite|kick|promote|demote <player>", "manage the crew"},
                {"/gang accept|deny [gang]", "answer an invite"},
                {"/gang deposit|withdraw <amount|all>", "gang bank (Credits)"},
                {"/gang upgrade <id>", "buy an upgrade from the bank"},
                {"/gc <message>", "crew chat (/gang chat toggles it)"},
                {"/gang info [gang] · /gang top [level]", "look up crews"},
                {"/gang leave · transfer · disband · color · motd", ""}};
        for (String[] l : lines) {
            p.sendMessage(Text.mm("  <white>" + Text.MM.escapeTags(l[0]) + (l[1].isEmpty() ? "" : " <dark_gray>- <gray>" + l[1])));
        }
    }

    private void info(Player p, String name) {
        Gang g = name == null ? gangs.gangFor(p.getUniqueId()) : gangs.byName(name);
        if (g == null) { Text.send(p, name == null ? "<red>You're not in a gang." : "<red>No gang called " + Text.MM.escapeTags(name) + "."); return; }
        EconomyService eco = Services.get(EconomyService.class);
        GangsConfig c = gangs.cfg();
        Text.send(p, "<dark_gray>[" + g.coloredTag() + "<dark_gray>]</dark_gray> <white><b>" + g.name + "</b>  <gray>level <white>" + g.level()
                + "</white>  <dark_gray>·</dark_gray>  " + Numbers.shortFormat(g.points) + " pts <dark_gray>(" + Numbers.shortFormat(g.weeklyPoints) + " this week)");
        StringBuilder roster = new StringBuilder();
        for (Map.Entry<String, Gang.Member> e : g.members.entrySet()) {
            boolean online = Bukkit.getPlayer(UUID.fromString(e.getKey())) != null;
            if (!roster.isEmpty()) roster.append("<dark_gray>, ");
            roster.append(e.getValue().rank.color).append(online ? "" : "<i>").append(e.getValue().name).append(online ? "" : "</i>").append("<reset>");
        }
        p.sendMessage(Text.mm("  <gray>Crew (" + g.members.size() + "/" + c.maxMembers(g) + "): " + roster));
        if (gangs.gangFor(p.getUniqueId()) == g) p.sendMessage(Text.mm("  <gray>Bank " + eco.format(Currency.CREDITS, g.bank)));
        List<String> turf = new ArrayList<>();
        for (var e : gangs.territories().entrySet()) if (e.getValue().equals(g.id)) turf.add(c.territoryName(e.getKey()));
        if (!turf.isEmpty()) p.sendMessage(Text.mm("  <gray>Turf <#c8743a>" + String.join(", ", turf)));
    }

    private void top(Player p, boolean level) {
        List<Gang> list = gangs.top(!level);
        Text.send(p, "<#c8743a><b>" + (level ? "TOP CREWS" : "THIS WEEK'S CREWS") + "</b>");
        if (list.isEmpty()) { p.sendMessage(Text.mm("  <dark_gray>No crews yet.")); return; }
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Gang g = list.get(i);
            p.sendMessage(Text.mm("  <gray>#" + (i + 1) + " [" + g.coloredTag() + "<gray>] <white>" + g.name + "</white> <dark_gray>·</dark_gray> "
                    + (level ? "level " + g.level() + " (" + Numbers.shortFormat(g.points) + " pts)" : Numbers.shortFormat(g.weeklyPoints) + " pts")));
        }
    }

    private void bank(Player p) {
        Gang g = gangs.requireGang(p);
        if (g == null) return;
        EconomyService eco = Services.get(EconomyService.class);
        GangsConfig c = gangs.cfg();
        UUID id = p.getUniqueId();
        Text.send(p, "<gold>Gang bank " + eco.format(Currency.CREDITS, g.bank) + " <dark_gray>/ " + Numbers.shortFormat(c.bankCapacity(g)));
        long can = g.rank(id).atLeast(c.rankWithdraw) ? gangs.withdrawable(g, id) : Math.min(gangs.parkedBy(g, id), g.bank);
        p.sendMessage(Text.mm("  <gray>You can withdraw " + eco.format(Currency.CREDITS, can)
                + "<gray>, parked by you this run: <white>" + Numbers.shortFormat(gangs.parkedBy(g, id))));
        p.sendMessage(Text.mm("  <dark_gray>Deposits are parked under your name for this run. From Grade " + c.depositLockGrade
                + " they're locked and prestiging wipes them."));
        for (int i = 0; i < Math.min(8, g.log.size()); i++) {
            Gang.LogEntry le = g.log.get(i);
            p.sendMessage(Text.mm("  <dark_gray>" + new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date(le.at))
                    + " <gray>" + le.who + " " + le.what + " " + (le.amount >= 0 ? "<green>+" : "<red>") + Numbers.shortFormat(le.amount)));
        }
    }

    private void admin(CommandSender s, String[] args) {
        if (!s.hasPermission("spacepunk.admin")) { Text.send(s, "<red>No permission."); return; }
        if (args.length == 0) { Text.send(s, "<gray>/gang admin " + String.join("|", ADMIN)); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { reload.run(); Text.send(s, "<gray>gangs.yml reloaded."); }
            case "weeklyreset" -> { gangs.weeklyReset(); Text.send(s, "<gray>Weekly reset done."); }
            case "disband" -> {
                Gang g = args.length > 1 ? gangs.byName(args[1]) : null;
                if (g == null) { Text.send(s, "<red>/gang admin disband <gang>"); return; }
                gangs.disbandNow(g, "staff");
                Text.send(s, "<gray>Disbanded " + g.name + ".");
            }
            case "setbank" -> {
                Gang g = args.length > 2 ? gangs.byName(args[1]) : null;
                if (g == null) { Text.send(s, "<red>/gang admin setbank <gang> <amount>"); return; }
                try {
                    g.bank = Math.max(0, Numbers.parse(args[2]));
                    g.parked.clear();
                    gangs.markDirty();
                    Text.send(s, "<gray>" + g.name + " bank set to " + Numbers.shortFormat(g.bank) + " (parked deposits cleared).");
                } catch (NumberFormatException e) {
                    Text.send(s, "<red>Bad amount.");
                }
            }
            case "addpoints" -> {
                Gang g = args.length > 2 ? gangs.byName(args[1]) : null;
                if (g == null) { Text.send(s, "<red>/gang admin addpoints <gang> <points>"); return; }
                try {
                    gangs.adminPoints(g, Numbers.parse(args[2]));
                    Text.send(s, "<gray>" + g.name + " now has " + Numbers.shortFormat(g.points) + " points (level " + g.level() + ").");
                } catch (NumberFormatException e) {
                    Text.send(s, "<red>Bad number.");
                }
            }
            case "territory" -> {
                if (args.length < 3) { Text.send(s, "<red>/gang admin territory <id> <gang|none>"); return; }
                Gang g = args[2].equalsIgnoreCase("none") ? null : gangs.byName(args[2]);
                if (g == null && !args[2].equalsIgnoreCase("none")) { Text.send(s, "<red>No gang " + args[2]); return; }
                gangs.setTerritoryOwner(args[1], g == null ? null : g.id);
                Text.send(s, "<gray>Territory " + args[1] + " -> " + (g == null ? "nobody" : g.name));
            }
            default -> Text.send(s, "<gray>/gang admin " + String.join("|", ADMIN));
        }
    }
}
