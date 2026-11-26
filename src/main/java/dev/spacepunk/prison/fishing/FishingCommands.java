package dev.spacepunk.prison.fishing;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.fishing.FishingConfig.Area;
import dev.spacepunk.prison.fishing.FishingConfig.BaitDef;
import dev.spacepunk.prison.fishing.FishingConfig.FishDef;
import dev.spacepunk.prison.fishing.FishingConfig.LureDef;
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

/** /fish, /rod, /waters and /fishadmin. */
final class FishingCommands {
    private static final String ADMIN = "spacepunk.admin";
    private final FishingModule module;

    FishingCommands(FishingModule module) { this.module = module; }

    private static List<String> filter(Collection<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    BasicCommand fish() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) {
                    Text.send(src.getSender(), "<gray>Players only. Admins: /fishadmin");
                    return;
                }
                String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                FishingManager m = module.manager();
                switch (sub) {
                    case "" -> module.menus().open(p, "main");
                    case "sell" -> {
                        FishingManager.Sale s = m.sellAll(p, false, false);
                        if (s.units() == 0) Text.send(p, "<gray>You have no fish or junk to sell (trophies sell only in the market menu).");
                        else Text.send(p, "<gray>Sold " + s.units() + " items for "
                                + m.economy().map(e -> e.format(Currency.CREDITS, s.credits())).orElse(s.credits() + " Credits"));
                    }
                    case "gut" -> {
                        FishingManager.Sale s = m.sellAll(p, true, false);
                        if (s.units() == 0) Text.send(p, "<gray>You have no fish to gut.");
                        else Text.send(p, "<gray>Gutted " + s.units() + " fish for " + m.formatScales(s.scales()));
                    }
                    case "rod" -> module.menus().open(p, "rod");
                    case "bait" -> module.menus().open(p, "bait");
                    case "market" -> module.menus().open(p, "market");
                    case "collection", "log" -> module.menus().open(p, "collection");
                    case "top", "leaderboard" -> module.menus().open(p, "top");
                    case "waters", "travel" -> module.menus().open(p, "waters");
                    case "help" -> help(p);
                    default -> help(p);
                }
            }

            @Override
            public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length <= 1) return filter(List.of("sell", "gut", "rod", "bait", "market", "collection", "top", "waters", "help"),
                        args.length == 0 ? "" : args[0]);
                return List.of();
            }
        };
    }

    private void help(Player p) {
        Text.send(p, """
                <#5fb4c8>Fishing</#5fb4c8> <gray>· the station's flooded decks
                <gray>/waters</gray> <dark_gray>travel to a pool (one per deck, unlocked by grade)
                <gray>/fish</gray> <dark_gray>market, bait shop, collection, leaderboards
                <gray>/fish sell</gray> <dark_gray>/</dark_gray> <gray>/fish gut</gray> <dark_gray>sell fish for Credits or gut them for Scales
                <gray>/rod</gray> <dark_gray>enchants, lures, bait and rod tiers (or shift + left-click your rod)
                <dark_gray>Right-click when it bites. If it fights, your XP bar is the line: hold SNEAK to raise it, keep it in the middle.""");
    }

    BasicCommand rod() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) return;
                if (module.manager().data(p.getUniqueId()) == null) {
                    Text.send(p, "<red>Your fishing data is still loading.");
                    return;
                }
                if (module.manager().ensureRod(p)) Text.send(p, "<gray>Your rod was returned to your inventory.");
                module.menus().open(p, "rod");
            }
        };
    }

    BasicCommand waters() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) return;
                if (args.length == 0) {
                    module.menus().open(p, "waters");
                    return;
                }
                String id = args[0].toLowerCase(Locale.ROOT);
                if (id.equals("hub") || id.equals("market")) {
                    module.travelHub(p);
                    return;
                }
                Area a = module.cfg().areas.get(id);
                if (a == null) {
                    Text.send(p, "<red>Unknown pool. Options: hub, " + String.join(", ", module.cfg().areas.keySet()));
                    return;
                }
                module.travel(p, a);
            }

            @Override
            public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length > 1) return List.of();
                List<String> o = new ArrayList<>(module.cfg().areas.keySet());
                o.add(0, "hub");
                return filter(o, args.length == 0 ? "" : args[0]);
            }
        };
    }

    BasicCommand admin() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                if (!s.hasPermission(ADMIN)) {
                    Text.send(s, "<red>No permission.");
                    return;
                }
                String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "build" -> {
                        module.rebuild();
                        Text.send(s, "<gray>Rebuilt the station waters (hub and " + module.cfg().areas.size() + " pool modules).");
                    }
                    case "reload" -> {
                        module.reload();
                        Text.send(s, "<gray>fishing.yml reloaded. Run /fishadmin build if you changed the layout.");
                    }
                    case "resetweek" -> {
                        module.leaderboard().clearWeek();
                        Text.send(s, "<gray>Cleared this week's biggest-catch board (no rewards paid).");
                    }
                    case "give" -> give(s, args);
                    default -> Text.send(s, """
                            <gray>/fishadmin build</gray> <dark_gray>rebuild the waters world layout
                            <gray>/fishadmin reload</gray> <dark_gray>re-read fishing.yml
                            <gray>/fishadmin resetweek</gray> <dark_gray>clear the weekly board
                            <gray>/fishadmin give <player> fish <id> [amount]
                            <gray>/fishadmin give <player> lure <id>
                            <gray>/fishadmin give <player> bait <id> <amount>
                            <gray>/fishadmin give <player> scales|xp <amount>
                            <gray>/fishadmin give <player> rod""");
                }
            }

            @Override
            public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!src.getSender().hasPermission(ADMIN)) return List.of();
                if (args.length <= 1) return filter(List.of("build", "reload", "resetweek", "give"), args.length == 0 ? "" : args[0]);
                if (!args[0].equalsIgnoreCase("give")) return List.of();
                if (args.length == 2) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
                if (args.length == 3) return filter(List.of("fish", "lure", "bait", "scales", "xp", "rod"), args[2]);
                if (args.length == 4) {
                    return switch (args[2].toLowerCase(Locale.ROOT)) {
                        case "fish" -> filter(module.cfg().fish.keySet(), args[3]);
                        case "lure" -> filter(module.cfg().lures.keySet(), args[3]);
                        case "bait" -> filter(module.cfg().baits.keySet(), args[3]);
                        default -> List.of();
                    };
                }
                return List.of();
            }
        };
    }

    private void give(CommandSender s, String[] args) {
        if (args.length < 3) {
            Text.send(s, "<red>/fishadmin give <player> <fish|lure|bait|scales|xp|rod> ...");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        FishingManager m = module.manager();
        FishingData d = t == null ? null : m.data(t.getUniqueId());
        if (d == null) {
            Text.send(s, "<red>Player not online (or still loading).");
            return;
        }
        String what = args[2].toLowerCase(Locale.ROOT);
        String id = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "";
        long amount = 1;
        String amountArg = what.equals("scales") || what.equals("xp") ? id : (args.length > 4 ? args[4] : "1");
        try {
            amount = Math.max(1, Long.parseLong(amountArg.isEmpty() ? "1" : amountArg));
        } catch (NumberFormatException e) {
            Text.send(s, "<red>Bad amount: " + amountArg);
            return;
        }
        switch (what) {
            case "fish" -> {
                FishDef f = module.cfg().fish.get(id);
                if (f == null) {
                    Text.send(s, "<red>Unknown fish.");
                    return;
                }
                if ("legendary".equals(f.rarity())) {
                    for (int i = 0; i < Math.min(9, amount); i++) m.give(t, d, m.items().trophy(f, f.maxKg(), t.getName()), "trophy:" + f.id() + "@" + f.maxKg(), 1);
                } else {
                    m.give(t, d, m.items().fish(f, (int) Math.min(64, amount)), "fish:" + f.id(), (int) Math.min(64, amount));
                }
            }
            case "lure" -> {
                LureDef l = module.cfg().lures.get(id);
                if (l == null) {
                    Text.send(s, "<red>Unknown lure.");
                    return;
                }
                m.give(t, d, m.items().lure(l), "lure:" + l.id(), 1);
            }
            case "bait" -> {
                BaitDef b = module.cfg().baits.get(id);
                if (b == null) {
                    Text.send(s, "<red>Unknown bait.");
                    return;
                }
                d.bait.merge(b.id(), (int) Math.min(100000, amount), Integer::sum);
                if (d.activeBait == null) d.activeBait = b.id();
            }
            case "scales" -> d.scales += amount;
            case "xp" -> m.addXp(t, d, amount);
            case "rod" -> m.ensureRod(t);
            default -> {
                Text.send(s, "<red>Unknown item type.");
                return;
            }
        }
        m.changed(t);
        Text.send(s, "<gray>Gave " + t.getName() + " " + what + (id.isEmpty() || what.equals("scales") || what.equals("xp") ? "" : " " + id)
                + " x" + amount + ".");
    }
}
