package dev.spacepunk.prison.workshop;

import dev.spacepunk.prison.util.Text;
import dev.spacepunk.prison.workshop.PlotRegistry.Plot;
import dev.spacepunk.prison.workshop.WorkshopConfig.MachineDef;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /workshop (alias /ws, /bay) for players and /wsadmin for staff. */
final class WorkshopCommands {
    private final WorkshopManager ws;
    private final WorkshopMenus menus;
    private final Runnable reload;

    WorkshopCommands(WorkshopManager ws, WorkshopMenus menus, Runnable reload) {
        this.ws = ws;
        this.menus = menus;
        this.reload = reload;
    }

    private static final List<String> SUBS = List.of("menu", "visit", "open", "close", "crew", "locker", "market", "shop", "help");

    BasicCommand workshop() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) {
                    Text.send(src.getSender(), "<red>Players only.");
                    return;
                }
                if (args.length == 0) {
                    ws.enter(p);
                    return;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "menu", "terminal" -> menus.main(p);
                    case "locker" -> menus.locker(p, 0);
                    case "market", "trade", "sell" -> menus.market(p, 0);
                    case "shop" -> menus.shop(p);
                    case "open" -> ws.setOpen(p, true);
                    case "close", "seal" -> ws.setOpen(p, false);
                    case "visit" -> {
                        if (args.length < 2) {
                            Text.send(p, "<gray>/ws visit <player>");
                            return;
                        }
                        Plot plot = ws.plots.byName(args[1]);
                        if (plot == null) {
                            Text.send(p, "<red>" + args[1] + " has no hull bay.");
                            return;
                        }
                        if (plot.ownerId().equals(p.getUniqueId())) ws.enter(p);
                        else ws.visit(p, plot);
                    }
                    case "crew" -> {
                        if (args.length < 3 || !(args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                            Text.send(p, "<gray>/ws crew add|remove <player>");
                            return;
                        }
                        OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(args[2]);
                        if (o == null) {
                            Text.send(p, "<red>Unknown player " + args[2] + ".");
                            return;
                        }
                        ws.crew(p, o.getUniqueId(), o.getName() == null ? args[2] : o.getName(), args[1].equalsIgnoreCase("add"));
                    }
                    default -> help(p);
                }
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length <= 1) return SUBS;
                if (args.length == 2 && args[0].equalsIgnoreCase("crew")) return List.of("add", "remove");
                if ((args.length == 2 && args[0].equalsIgnoreCase("visit")) || (args.length == 3 && args[0].equalsIgnoreCase("crew"))) {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                }
                return List.of();
            }
        };
    }

    private static void help(CommandSender s) {
        Text.send(s, """
                <#c8743a>Hull bay</#c8743a> <gray>(your private workshop)
                <white>/ws</white> <gray>go to your bay  <white>/ws menu</white> <gray>terminal
                <white>/ws locker</white> <gray>Bay Locker  <white>/ws market</white> <gray>Trade Terminal  <white>/ws shop</white> <gray>Machine Shop
                <white>/ws open</white><gray>|<white>close</white> <gray>visitors  <white>/ws visit <player></white>
                <white>/ws crew add|remove <player></white> <gray>let someone build with you""");
    }

    BasicCommand admin() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                if (args.length == 0) {
                    Text.send(s, "<gray>/wsadmin reload | give <player> <machine> [mark] [amount] | conveyors <player> [amount] | goods <player> <good> <amount> | tp <player>");
                    return;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "reload" -> {
                        reload.run();
                        Text.send(s, "<gray>Workshop config reloaded: " + ws.cfg().machines.size() + " machines, " + ws.cfg().goods.size() + " goods.");
                    }
                    case "give" -> {
                        Player t = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                        MachineDef def = args.length > 2 ? ws.cfg().machine(args[2].toLowerCase(Locale.ROOT)) : null;
                        if (t == null || def == null) {
                            Text.send(s, "<red>/wsadmin give <online player> <" + String.join("|", ws.cfg().machines.keySet()) + "> [mark 1-" + ws.cfg().tiers.size() + "] [amount]");
                            return;
                        }
                        int tier = args.length > 3 ? parse(args[3], 1) - 1 : 0;
                        int amount = args.length > 4 ? parse(args[4], 1) : 1;
                        WorkshopManager.give(t, ws.items.machine(def, Math.max(0, Math.min(ws.cfg().tiers.size() - 1, tier)), amount));
                        Text.send(s, "<gray>Gave " + t.getName() + " " + amount + "x " + def.name() + ".");
                    }
                    case "conveyors" -> {
                        Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                        if (t == null) {
                            Text.send(s, "<red>/wsadmin conveyors <online player> [amount]");
                            return;
                        }
                        int n = args.length > 2 ? parse(args[2], 16) : 16;
                        WorkshopManager.give(t, ws.items.conveyor(Math.min(64, n)));
                    }
                    case "goods" -> {
                        Player t = args.length > 3 ? Bukkit.getPlayerExact(args[1]) : null;
                        if (t == null) {
                            Text.send(s, "<red>/wsadmin goods <online player> <good id> <amount>");
                            return;
                        }
                        String id = WorkshopConfig.goodId(args[2]);
                        long added = ws.lockerGive(t.getUniqueId(), id, parse(args[3], 1));
                        Text.send(s, "<gray>Put " + added + "x " + ws.items.name(id) + " in " + t.getName() + "'s Bay Locker.");
                    }
                    case "tp" -> {
                        if (!(s instanceof Player p) || args.length < 2) {
                            Text.send(s, "<red>/wsadmin tp <player>");
                            return;
                        }
                        Plot plot = ws.plots.byName(args[1]);
                        if (plot == null || !plot.built) {
                            Text.send(s, "<red>No built bay for " + args[1] + ".");
                            return;
                        }
                        p.teleportAsync(ws.world.spawn(plot));
                    }
                    default -> Text.send(s, "<red>Unknown subcommand.");
                }
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length <= 1) return List.of("reload", "give", "conveyors", "goods", "tp");
                if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                if (args.length == 3 && args[0].equalsIgnoreCase("give")) return List.copyOf(ws.cfg().machines.keySet());
                if (args.length == 3 && args[0].equalsIgnoreCase("goods")) {
                    List<String> ids = new ArrayList<>();
                    for (String id : ws.cfg().goods.keySet()) ids.add(id.substring(WorkshopConfig.NS.length()));
                    return ids;
                }
                return List.of();
            }

            @Override public String permission() { return "spacepunk.admin"; }
        };
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
