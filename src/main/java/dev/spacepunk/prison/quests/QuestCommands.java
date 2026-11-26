package dev.spacepunk.prison.quests;

import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** /quests [tab], /contracts, /questadmin. */
final class QuestCommands {
    private static final String ADMIN = "spacepunk.admin";
    private static final List<String> TABS = List.of("story", "tutorials", "contracts", "track");
    private static final List<String> SUBS = List.of("reload", "reset", "complete", "points", "reroll", "npc", "types", "info");

    private final QuestsModule module;

    QuestCommands(QuestsModule module) { this.module = module; }

    BasicCommand quests() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) { Text.send(src.getSender(), "<red>Players only."); return; }
                QuestMenu.Tab tab = QuestMenu.Tab.STORY;
                if (args.length > 0) {
                    tab = switch (args[0].toLowerCase(Locale.ROOT)) {
                        case "tutorials", "tutorial", "tut" -> QuestMenu.Tab.TUTORIALS;
                        case "contracts", "contract", "daily", "weekly" -> QuestMenu.Tab.CONTRACTS;
                        case "track", "pass", "monthly" -> QuestMenu.Tab.TRACK;
                        default -> QuestMenu.Tab.STORY;
                    };
                }
                module.menu().open(p, tab);
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                return args.length > 1 ? List.of() : TABS.stream().filter(t -> t.startsWith(prefix)).toList();
            }
        };
    }

    BasicCommand contracts() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) { Text.send(src.getSender(), "<red>Players only."); return; }
                module.menu().open(p, QuestMenu.Tab.CONTRACTS);
            }
        };
    }

    BasicCommand admin() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                QuestManager q = module.manager();
                if (args.length == 0) { help(s); return; }
                String sub = args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "reload" -> {
                        module.reload();
                        Text.send(s, "<green>quests.yml reloaded. New numbers apply to quests and contracts as they start.");
                    }
                    case "types" -> Text.send(s, "<gray>Registered objective types: <white>" + String.join(", ", module.typeIds()));
                    case "reset" -> {
                        Player t = target(s, args, 1);
                        if (t == null) return;
                        String what = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "all";
                        q.reset(t, what);
                        Text.send(s, "<green>Reset " + what + " quests for " + t.getName() + ".");
                    }
                    case "reroll" -> {
                        Player t = target(s, args, 1);
                        if (t == null) return;
                        q.reroll(t);
                        Text.send(s, "<green>Rolled new daily and weekly contracts for " + t.getName() + ".");
                    }
                    case "complete" -> {
                        Player t = target(s, args, 1);
                        if (t == null) return;
                        if (args.length < 3) { Text.send(s, "<red>/questadmin complete PLAYER story:ID|tutorial:ID|daily:N|weekly:N"); return; }
                        Text.send(s, q.forceComplete(t, args[2].toLowerCase(Locale.ROOT)) ? "<green>Completed." : "<red>No such active quest.");
                    }
                    case "points" -> {
                        Player t = target(s, args, 1);
                        if (t == null) return;
                        QuestData d = q.data(t.getUniqueId());
                        if (d == null || args.length < 3) { Text.send(s, "<red>/questadmin points PLAYER AMOUNT"); return; }
                        try {
                            d.points = Math.max(0, Long.parseLong(args[2]));
                        } catch (NumberFormatException e) {
                            Text.send(s, "<red>Bad number.");
                            return;
                        }
                        q.markDirty(t.getUniqueId());
                        Text.send(s, "<green>" + t.getName() + " now has " + d.points + " contract points.");
                    }
                    case "info" -> {
                        Player t = target(s, args, 1);
                        if (t == null) return;
                        QuestData d = q.data(t.getUniqueId());
                        if (d == null) { Text.send(s, "<red>Still loading."); return; }
                        StringBuilder sb = new StringBuilder("<white>" + t.getName() + "<gray>: " + d.done.size() + " finished, "
                                + d.points + " points, " + d.completedContracts + " contracts done");
                        for (QuestData.Active a : QuestManager.all(d)) {
                            sb.append("\n  <white>").append(QuestManager.trackKey(QuestManager.kindOf(a), a, d)).append(" <gray>")
                                    .append(a.name).append(" ").append(Math.round(a.fraction() * 100)).append("%").append(a.done ? " <green>done" : "");
                        }
                        Text.send(s, sb.toString());
                    }
                    case "npc" -> {
                        if (!(s instanceof Player p)) { Text.send(s, "<red>Run this in game."); return; }
                        if (args.length < 2) {
                            Text.send(s, "<red>/questadmin npc CHARACTER|board|remove <gray>Characters: "
                                    + String.join(", ", q.cfg().characters.keySet()));
                            return;
                        }
                        String c = args[1].toLowerCase(Locale.ROOT);
                        if (c.equals("remove")) {
                            Text.send(s, "<green>Removed " + module.npcs().removeNear(p.getLocation(), 3) + " quest NPC(s) near you.");
                        } else if (module.npcs().spawn(p.getLocation(), c)) {
                            Text.send(s, "<green>Placed " + c + " here. Right-click it to open the quest log.");
                        } else {
                            Text.send(s, "<red>Unknown character. Options: board, " + String.join(", ", q.cfg().characters.keySet()));
                        }
                    }
                    default -> help(s);
                }
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length <= 1) {
                    String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                    return SUBS.stream().filter(x -> x.startsWith(prefix)).toList();
                }
                String sub = args[0].toLowerCase(Locale.ROOT), prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
                if (args.length == 2 && sub.equals("npc")) {
                    List<String> opts = new ArrayList<>(List.of("board", "remove"));
                    opts.addAll(module.manager().cfg().characters.keySet());
                    return opts.stream().filter(x -> x.startsWith(prefix)).toList();
                }
                if (args.length == 2 && List.of("reset", "reroll", "complete", "points", "info").contains(sub)) {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
                }
                if (args.length == 3 && sub.equals("reset")) {
                    return Stream.of("all", "story", "tutorials", "contracts", "track").filter(x -> x.startsWith(prefix)).toList();
                }
                return List.of();
            }

            @Override public String permission() { return ADMIN; }
        };
    }

    private static Player target(CommandSender s, String[] args, int i) {
        if (args.length <= i) { Text.send(s, "<red>Name a player."); return null; }
        Player t = Bukkit.getPlayerExact(args[i]);
        if (t == null) Text.send(s, "<red>Player not online.");
        return t;
    }

    private static void help(CommandSender s) {
        Text.send(s, """
                <gray>/questadmin <white>reload</white> | <white>types</white> | <white>info PLAYER</white>
                <gray>/questadmin <white>reset PLAYER [all|story|tutorials|contracts|track]</white>
                <gray>/questadmin <white>complete PLAYER KEY</white> | <white>points PLAYER N</white> | <white>reroll PLAYER</white>
                <gray>/questadmin <white>npc CHARACTER|board|remove</white> <dark_gray>- place quest NPCs where you stand""");
    }
}
