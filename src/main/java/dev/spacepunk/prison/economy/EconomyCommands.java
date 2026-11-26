package dev.spacepunk.prison.economy;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /balance [player] and /eco <give|take|set> <player> <currency> <amount>. */
public final class EconomyCommands {
    private EconomyCommands() {}

    public static BasicCommand balance(EconomyService eco) {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender sender = src.getSender();
                Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player p ? p : null);
                if (target == null) { Text.send(sender, "<red>Player not found."); return; }
                StringBuilder sb = new StringBuilder("<gray>" + target.getName() + "'s balances:");
                for (Currency c : Currency.values()) sb.append("\n  ").append(eco.format(c, eco.balance(target.getUniqueId(), c)));
                Text.send(sender, sb.toString());
            }
        };
    }

    public static BasicCommand eco(EconomyService eco) {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender sender = src.getSender();
                if (args.length < 4) { Text.send(sender, "<gray>/eco <give|take|set> <player> <credits|tokens|shards> <amount>"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Text.send(sender, "<red>Player not online."); return; }
                Currency c;
                long amount;
                try {
                    c = Currency.valueOf(args[2].toUpperCase(Locale.ROOT));
                    amount = Numbers.parse(args[3]);
                } catch (IllegalArgumentException e) {
                    Text.send(sender, "<red>Bad currency or amount.");
                    return;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "give" -> eco.give(target.getUniqueId(), c, amount, "admin");
                    case "take" -> { if (!eco.take(target.getUniqueId(), c, amount, "admin")) { Text.send(sender, "<red>Not enough balance."); return; } }
                    case "set" -> eco.set(target.getUniqueId(), c, amount);
                    default -> { Text.send(sender, "<red>Unknown action."); return; }
                }
                Text.send(sender, "<gray>Done. " + target.getName() + " now has " + eco.format(c, eco.balance(target.getUniqueId(), c)));
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                return switch (args.length) {
                    case 0, 1 -> List.of("give", "take", "set");
                    case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    case 3 -> Arrays.stream(Currency.values()).map(x -> x.name().toLowerCase(Locale.ROOT)).toList();
                    default -> List.of();
                };
            }

            @Override public String permission() { return "spacepunk.admin"; }
        };
    }
}
