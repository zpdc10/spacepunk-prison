package dev.spacepunk.prison.auction;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /ah                              browse the Black Market
 * /ah sell <price> [hours]         list the held stack as buy-it-now
 * /ah auction <start bid> [hours]  list the held stack as a timed auction
 * /ah bid <id> <amount>            bid a custom amount
 * /ah mine | claims
 * /ah admin reload | remove <id>
 */
final class AuctionCommand implements BasicCommand {
    private static final String ADMIN = "spacepunk.admin";
    private final AuctionModule module;

    AuctionCommand(AuctionModule module) { this.module = module; }

    @Override
    public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
        CommandSender sender = src.getSender();
        AuctionManager ah = module.manager();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            admin(sender, args);
            return;
        }
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<gray>/ah admin <reload|remove <id>>");
            return;
        }
        switch (sub) {
            case "" , "browse" -> module.menu().open(p, AuctionMenu.View.BROWSE);
            case "mine", "stalls", "listings" -> module.menu().open(p, AuctionMenu.View.MINE);
            case "claims", "claim", "collect" -> module.menu().open(p, AuctionMenu.View.CLAIMS);
            case "sell", "bin", "auction" -> list(p, ah, !sub.equals("sell") && !sub.equals("bin"), args);
            case "bid" -> {
                if (args.length < 3) {
                    Text.send(p, "<gray>/ah bid <listing id> <amount>");
                    return;
                }
                long id, amount;
                try {
                    id = Long.parseLong(args[1].replace("#", ""));
                    amount = Numbers.parse(args[2]);
                } catch (NumberFormatException e) {
                    Text.send(p, "<red>Bad number.");
                    return;
                }
                module.menu().report(p, ah.bid(p, id, amount), amount);
                module.menu().refreshAll();
            }
            default -> help(p, ah);
        }
    }

    private void list(Player p, AuctionManager ah, boolean auction, String[] args) {
        AuctionConfig c = ah.cfg();
        if (args.length < 2) {
            Text.send(p, "<gray>Hold the item, then <white>/ah " + (auction ? "auction <start bid>" : "sell <price>") + " [hours]");
            return;
        }
        long price;
        try {
            price = Numbers.parse(args[1]);
        } catch (NumberFormatException e) {
            Text.send(p, "<red>Bad price. Examples: 500, 12.5k, 3M");
            return;
        }
        int hours = auction ? c.defaultAuctionHours : c.defaultBinHours;
        if (args.length >= 3) {
            try {
                hours = Integer.parseInt(args[2].toLowerCase(Locale.ROOT).replace("h", ""));
            } catch (NumberFormatException e) {
                Text.send(p, "<red>Bad duration.");
                return;
            }
        }
        EconomyService eco = Services.get(EconomyService.class);
        long fee = c.listingFee(Math.max(price, 0), hours);
        String described = AuctionManager.describe(p.getInventory().getItemInMainHand());
        switch (ah.list(p, auction, price, hours)) {
            case OK -> {
                Text.send(p, "<green>Listed</green> " + described + " <gray>" + (auction ? "for auction from " : "at ")
                        + eco.format(Currency.CREDITS, price) + " <gray>for " + hours + "h. Fee paid: "
                        + eco.format(Currency.CREDITS, fee) + "<gray>.");
                module.menu().refreshAll();
            }
            case NOTHING_IN_HAND -> Text.send(p, "<red>Hold the item you want to sell.");
            case NOT_ALLOWED -> Text.send(p, "<red>That item can't be sold on the Black Market.");
            case BAD_PRICE -> Text.send(p, "<red>Price must be between " + Numbers.shortFormat(c.minPrice) + " and "
                    + Numbers.shortFormat(c.maxPrice) + ".");
            case BAD_DURATION -> Text.send(p, "<red>Duration must be one of: " + String.join(", ",
                    c.durations.keySet().stream().map(h -> h + "h").toList()));
            case TOO_MANY -> Text.send(p, "<red>You already have " + ah.maxListings(p.getUniqueId())
                    + " listings, your limit. <gray>Each prestige adds one more stall.");
            case CLAIMS_FULL -> Text.send(p, "<red>Your collection bin is full. <gray>Empty it with <white>/ah claims</white> first.");
            case TOO_POOR -> Text.send(p, "<red>You can't afford the listing fee of " + eco.format(Currency.CREDITS, fee) + "<red>.");
            case NOT_LOADED -> Text.send(p, "<red>Your profile is still loading.");
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            Text.send(sender, "<red>No permission.");
            return;
        }
        AuctionManager ah = module.manager();
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                module.reload();
                Text.send(sender, "<gray>auction.yml reloaded.");
            }
            case "remove" -> {
                if (args.length < 3) { Text.send(sender, "<gray>/ah admin remove <id>"); return; }
                try {
                    long id = Long.parseLong(args[2].replace("#", ""));
                    var r = ah.cancel(sender instanceof Player p ? p : null, id, true);
                    Text.send(sender, r == AuctionManager.CancelResult.OK ? "<gray>Removed #" + id + " (item returned, bids refunded)." : "<red>No such listing.");
                    module.menu().refreshAll();
                } catch (NumberFormatException e) {
                    Text.send(sender, "<red>Bad id.");
                }
            }
            default -> Text.send(sender, "<gray>/ah admin reload\n/ah admin remove <id>");
        }
    }

    private void help(Player p, AuctionManager ah) {
        Text.send(p, """
                <#c8743a>Black Market</#c8743a>
                <white>/ah</white> <gray>browse listings
                <white>/ah sell <price> [hours]</white> <gray>sell what you're holding, buy-it-now
                <white>/ah auction <start bid> [hours]</white> <gray>timed auction
                <white>/ah bid <id> <amount></white> <gray>bid a custom amount
                <white>/ah mine</white> <gray>your listings and bids · <white>/ah claims</white> <gray>collection bin""");
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
        AuctionConfig c = module.manager().cfg();
        if (args.length <= 1) {
            List<String> out = new ArrayList<>(List.of("sell", "auction", "bid", "mine", "claims"));
            if (src.getSender().hasPermission(ADMIN)) out.add("admin");
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("admin") && src.getSender().hasPermission(ADMIN)) return List.of("reload", "remove");
        if (args.length == 2 && (sub.equals("sell") || sub.equals("auction"))) return List.of("1000", "10k", "1M");
        if (args.length == 3 && (sub.equals("sell") || sub.equals("auction"))) {
            return c.durations.keySet().stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
