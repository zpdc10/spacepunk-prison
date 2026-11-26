package dev.spacepunk.prison.raredrops;

import dev.spacepunk.prison.raredrops.RareConfig.Drop;
import dev.spacepunk.prison.raredrops.RareConfig.Tier;
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

/**
 * /salvage                     open the salvage bay (collections, assembly, stash)
 * /salvage admin give|roll|reset|reload ...
 */
final class SalvageCommand implements BasicCommand {
    private static final String ADMIN = "spacepunk.admin";
    private final RareDropsModule module;

    SalvageCommand(RareDropsModule module) { this.module = module; }

    @Override
    public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
        CommandSender sender = src.getSender();
        RareDropsManager rare = module.manager();
        if (args.length == 0 || !args[0].equalsIgnoreCase("admin")) {
            if (!(sender instanceof Player p)) {
                usage(sender);
                return;
            }
            SalvageMenu.View view = SalvageMenu.View.MAIN;
            if (args.length > 0 && args[0].equalsIgnoreCase("collections")) view = SalvageMenu.View.COLLECTIONS;
            if (args.length > 0 && args[0].equalsIgnoreCase("assembly")) view = SalvageMenu.View.ASSEMBLY;
            module.menu().open(p, view);
            return;
        }
        if (!sender.hasPermission(ADMIN)) {
            Text.send(sender, "<red>No permission.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            module.reload();
            Text.send(sender, "<gray>raredrops.yml reloaded.");
            return;
        }
        if (args.length < 3) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        RareData d = target == null ? null : rare.data(target.getUniqueId());
        if (d == null) {
            Text.send(sender, "<red>Player not online (or still loading).");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (args.length < 4) { usage(sender); return; }
                Drop drop = rare.cfg().drop(args[3]);
                if (drop == null) { Text.send(sender, "<red>Unknown drop. Options: " + String.join(", ", rare.cfg().drops.keySet())); return; }
                int amount = 1;
                if (args.length >= 5) {
                    try { amount = Math.max(1, Math.min(640, Integer.parseInt(args[4]))); } catch (NumberFormatException ignored) { }
                }
                rare.give(target, d, drop, amount);
                rare.save(target.getUniqueId());
                Text.send(sender, "<gray>Gave " + target.getName() + " " + amount + "x " + drop.coloredName());
            }
            case "roll" -> {
                Tier tier = args.length >= 4 ? rare.cfg().tier(args[3]) : null;
                int deck = 1;
                if (args.length >= 5) {
                    try { deck = Math.max(1, Math.min(5, Integer.parseInt(args[4]))); } catch (NumberFormatException ignored) { }
                }
                Drop drop = rare.pick(deck, tier);
                if (drop == null) { Text.send(sender, "<red>Nothing can drop there."); return; }
                rare.award(target, d, drop, true);
                Text.send(sender, "<gray>Rolled " + drop.coloredName() + " <gray>for " + target.getName());
            }
            case "reset" -> {
                d.catalogued.clear();
                d.setsClaimed.clear();
                d.found.clear();
                d.stash.clear();
                d.totalFinds = d.cachesOpened = d.modulesAssembled = 0;
                rare.save(target.getUniqueId());
                Text.send(sender, "<gray>Reset " + target.getName() + "'s salvage record and collections.");
            }
            default -> usage(sender);
        }
    }

    private static void usage(CommandSender s) {
        Text.send(s, """
                <gray>/salvage [collections|assembly]
                /salvage admin give <player> <drop> [amount]
                /salvage admin roll <player> [tier] [deck]
                /salvage admin reset <player>
                /salvage admin reload""");
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
        boolean admin = src.getSender().hasPermission(ADMIN);
        RareConfig cfg = module.manager().cfg();
        return switch (args.length) {
            case 0, 1 -> {
                List<String> out = new ArrayList<>(List.of("collections", "assembly"));
                if (admin) out.add("admin");
                yield out;
            }
            case 2 -> admin ? List.of("give", "roll", "reset", "reload") : List.of();
            case 3 -> admin ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
            case 4 -> !admin ? List.of() : switch (args[1].toLowerCase(Locale.ROOT)) {
                case "give" -> List.copyOf(cfg.drops.keySet());
                case "roll" -> List.copyOf(cfg.tiers.keySet());
                default -> List.of();
            };
            default -> List.of();
        };
    }
}
