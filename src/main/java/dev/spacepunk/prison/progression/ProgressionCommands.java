package dev.spacepunk.prison.progression;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** /rankup, /prestige, /sell, /cargo, /autosell, /grades and the admin /progression command. */
final class ProgressionCommands {
    private final ProgressionManager progression;
    private final CargoHold cargo;
    private final CargoMenu menu;
    private final EconomyService economy;
    private final ProfileService profiles;
    private final Runnable reload;
    private final Map<UUID, Long> prestigeConfirm = new HashMap<>();

    ProgressionCommands(ProgressionManager progression, CargoHold cargo, CargoMenu menu, EconomyService economy,
                        ProfileService profiles, Runnable reload) {
        this.progression = progression;
        this.cargo = cargo;
        this.menu = menu;
        this.economy = economy;
        this.profiles = profiles;
        this.reload = reload;
    }

    /** A command only players can run. */
    private static BasicCommand playerCommand(PlayerExec exec, List<String> suggestions) {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) {
                    Text.send(src.getSender(), "<red>Players only.");
                    return;
                }
                exec.run(p, args);
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                return args.length <= 1 ? suggestions : List.of();
            }
        };
    }

    private interface PlayerExec { void run(Player p, String[] args); }

    BasicCommand rankup() {
        return playerCommand((p, args) -> {
            boolean max = args.length > 0 && args[0].equalsIgnoreCase("max");
            if (max) {
                int before = progression.grade(p.getUniqueId());
                int gained = progression.rankUpMax(p);
                if (gained > 0) {
                    Text.send(p, "<green>Ranked up " + gained + (gained == 1 ? " grade" : " grades") + ":</green> "
                            + progression.gradeLabel(before) + " <dark_gray>-></dark_gray> " + progression.gradeLabel(before + gained));
                    return;
                }
            } else {
                int before = progression.grade(p.getUniqueId());
                long cost = progression.nextGradeCost(p.getUniqueId());
                switch (progression.rankUp(p)) {
                    case OK -> {
                        Text.send(p, "<green>Clearance upgraded</green> for " + economy.format(Currency.CREDITS, cost)
                                + "<gray>: " + progression.gradeLabel(before + 1));
                        return;
                    }
                    case NOT_LOADED -> { Text.send(p, "<red>Your profile is still loading."); return; }
                    default -> {}
                }
            }
            explainCantRankUp(p);
        }, List.of("max"));
    }

    private void explainCantRankUp(Player p) {
        UUID id = p.getUniqueId();
        if (progression.grade(id) >= ProgressionService.MAX_GRADE) {
            Text.send(p, "<light_purple>You're at maximum clearance.</light_purple> <gray>Use <white>/prestige</white>.");
            return;
        }
        long cost = progression.nextGradeCost(id);
        long bal = economy.balance(id, Currency.CREDITS);
        Text.send(p, "<red>Not enough Credits.</red> <gray>Next grade costs " + economy.format(Currency.CREDITS, cost)
                + "<gray>, you need " + economy.format(Currency.CREDITS, cost - bal) + "<gray> more.");
    }

    BasicCommand prestige() {
        return playerCommand((p, args) -> {
            UUID id = p.getUniqueId();
            ProgressionConfig c = progression.cfg();
            switch (progression.canPrestige(p)) {
                case NOT_LOADED -> { Text.send(p, "<red>Your profile is still loading."); return; }
                case MAX_PRESTIGE -> { Text.send(p, "<light_purple>You've reached the highest prestige."); return; }
                case NOT_MAX_GRADE -> {
                    Text.send(p, "<red>Prestige unlocks at " + progression.gradeLabel(ProgressionService.MAX_GRADE)
                            + "<red>.</red> <gray>You're at " + progression.gradeLabel(progression.grade(id)) + "<gray>.");
                    return;
                }
                case OK -> {}
            }
            boolean confirming = args.length > 0 && args[0].equalsIgnoreCase("confirm");
            Long until = prestigeConfirm.get(id);
            if (!confirming || until == null || until < System.currentTimeMillis()) {
                int next = progression.prestige(id) + 1;
                long earned = progression.runEarnings(id);
                double mult = c.earningsMultiplier(next, earned);
                long capCredits = ProgressionConfig.clamp(c.runCost(next - 1) * c.earningsCap);
                prestigeConfirm.put(id, System.currentTimeMillis() + c.confirmSeconds * 1000L);
                Text.send(p, "<light_purple><bold>Prestige " + next + "</bold></light_purple>\n"
                        + "<gray>  Token cache: " + economy.format(Currency.TOKENS, c.tokenCache(next))
                        + "<gray> x<white>" + String.format("%.2f", mult) + "</white> for the "
                        + economy.format(Currency.CREDITS, earned) + "<gray> earned this run"
                        + " <dark_gray>(stops growing at " + Numbers.shortFormat(capCredits) + ")</dark_gray>\n"
                        + "<gray>  You get: " + economy.format(Currency.TOKENS, c.tokensFor(next, earned))
                        + "<gray>, " + economy.format(Currency.SHARDS, c.shardsFor(next))
                        + "<gray>, sell bonus <green>+" + Math.round(c.bonusPerPrestige * 100) + "%</green> forever\n"
                        + "<gray>  You keep: your drill, Tokens, Shards and cargo upgrades\n"
                        + "<red>  You lose: your grade (back to 1), all Credits, and ore in your cargo hold (<white>/sell</white> first so it counts)\n"
                        + "<gray>  Rank-ups will cost <white>x" + String.format("%.2f", 1 + c.costPerPrestige * next) + "</white> base.\n"
                        + "<yellow>Type <white>/prestige confirm</white> within " + c.confirmSeconds + " seconds.");
                return;
            }
            prestigeConfirm.remove(id);
            if (progression.prestige(p) == ProgressionManager.PrestigeResult.OK) {
                Text.send(p, "<light_purple>Prestige complete.</light_purple> <gray>You're now Prestige "
                        + progression.prestige(id) + ". Welcome back to " + progression.gradeLabel(1) + "<gray>.");
            }
        }, List.of("confirm"));
    }

    BasicCommand sell() {
        return playerCommand((p, args) -> reportSell(p, cargo.sellAll(p, "command"), economy), List.of());
    }

    BasicCommand cargo() {
        return playerCommand((p, args) -> {
            if (args.length > 0 && args[0].equalsIgnoreCase("upgrade")) {
                reportUpgrade(p, cargo.upgrade(p), cargo, progression, economy);
                return;
            }
            menu.open(p);
        }, List.of("upgrade"));
    }

    BasicCommand autosell() {
        return playerCommand((p, args) -> reportAutoSell(p, cargo.autoSell(p), progression, economy), List.of());
    }

    BasicCommand grades() {
        return playerCommand((p, args) -> {
            UUID id = p.getUniqueId();
            int cur = progression.grade(id);
            int pres = progression.prestige(id);
            ProgressionConfig c = progression.cfg();
            StringBuilder sb = new StringBuilder("<#c8743a>Clearance grades</#c8743a>"
                    + (pres > 0 ? " <dark_gray>(</dark_gray><light_purple>Prestige " + pres + "</light_purple><dark_gray>)</dark_gray>" : ""));
            int lastDeck = -1;
            for (int g = 1; g <= ProgressionService.MAX_GRADE; g++) {
                if (c.deckIndex(g) != lastDeck) {
                    lastDeck = c.deckIndex(g);
                    sb.append("\n ").append(c.deckName(g)).append("<reset>");
                }
                String mark = g < cur ? "<green>✔" : g == cur ? "<yellow>▶" : "<dark_gray>·";
                long cost = g == 1 ? 0 : c.gradeCost(g - 1, pres);
                sb.append("\n   ").append(mark).append(" <white>").append(c.gradeName(g)).append("</white>");
                if (g > 1) sb.append(" <dark_gray>- </dark_gray><gold>").append(Numbers.shortFormat(cost)).append("</gold>");
            }
            Text.send(p, sb.toString());
        }, List.of());
    }

    // ---- Admin ----

    BasicCommand admin() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                if (args.length == 0) {
                    Text.send(s, "<gray>/progression <setgrade|setprestige> <player> <n>\n"
                            + "<gray>/progression cargolevel <player> <n>\n"
                            + "<gray>/progression info <player>\n"
                            + "<gray>/progression reload");
                    return;
                }
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("reload")) {
                    reload.run();
                    for (Player p : Bukkit.getOnlinePlayers()) progression.refreshTab(p);
                    Text.send(s, "<green>progression.yml reloaded.");
                    return;
                }
                if (args.length < 2) { Text.send(s, "<red>Name a player."); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                PlayerProfile prof = target == null ? null : profiles.get(target.getUniqueId()).orElse(null);
                if (prof == null) { Text.send(s, "<red>Player not online or still loading."); return; }
                if (sub.equals("info")) {
                    CargoStore.Data d = cargo.store().get(target.getUniqueId());
                    Text.send(s, "<white>" + target.getName() + "</white><gray>: " + progression.gradeLabel(prof.grade())
                            + "<gray>, prestige " + prof.prestige()
                            + ", sell x" + String.format("%.2f", progression.sellMultiplier(target.getUniqueId()))
                            + (d == null ? "" : ", cargo L" + d.level + " " + Numbers.shortFormat(d.used) + "/" + Numbers.shortFormat(cargo.capacity(d))
                            + ", auto-sell " + (d.autoSellUnlocked ? (d.autoSell ? "on" : "off") : "locked")
                            + ", earned this run " + Numbers.shortFormat(d.runEarnings)));
                    return;
                }
                if (args.length < 3) { Text.send(s, "<red>Give a number."); return; }
                int n;
                try {
                    n = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    Text.send(s, "<red>Not a number: " + args[2]);
                    return;
                }
                switch (sub) {
                    case "setgrade" -> progression.setGrade(target, prof, n, prof.prestige());
                    case "setprestige" -> progression.setGrade(target, prof, prof.grade(), n);
                    case "cargolevel" -> {
                        CargoStore.Data d = cargo.store().get(target.getUniqueId());
                        if (d == null) { Text.send(s, "<red>Cargo not loaded."); return; }
                        d.level = Math.clamp(n, 0, progression.cfg().cargoMaxLevel);
                        cargo.store().markDirty(target.getUniqueId());
                    }
                    default -> { Text.send(s, "<red>Unknown subcommand."); return; }
                }
                Text.send(s, "<green>Done.");
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                return switch (args.length) {
                    case 0, 1 -> List.of("setgrade", "setprestige", "cargolevel", "info", "reload");
                    case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    default -> List.of();
                };
            }

            @Override public String permission() { return "spacepunk.admin"; }
        };
    }

    // ---- Shared feedback (also used by the menu) ----

    static void reportSell(Player p, CargoHold.SellResult r, EconomyService economy) {
        if (r.units() <= 0) {
            Text.send(p, "<gray>Your cargo hold is empty.");
            return;
        }
        Text.send(p, "<gray>Sold <white>" + Numbers.shortFormat(r.units()) + "</white> ore for " + economy.format(Currency.CREDITS, r.credits()) + "<gray>.");
    }

    static void reportUpgrade(Player p, CargoHold.UpgradeResult r, CargoHold cargo, ProgressionManager progression, EconomyService economy) {
        CargoStore.Data d = cargo.store().get(p.getUniqueId());
        switch (r) {
            case OK -> Text.send(p, "<green>Cargo hold expanded</green> <gray>to level " + d.level + " ("
                    + Numbers.shortFormat(cargo.capacity(d)) + " capacity).");
            case MAXED -> Text.send(p, "<gray>Your cargo hold is fully upgraded.");
            case TOO_POOR -> Text.send(p, "<red>Not enough Credits.</red> <gray>The next expansion costs "
                    + economy.format(Currency.CREDITS, progression.cfg().cargoUpgradeCost(d.level)) + "<gray>.");
            case NOT_LOADED -> Text.send(p, "<red>Your profile is still loading.");
        }
    }

    static void reportAutoSell(Player p, CargoHold.AutoSellResult r, ProgressionManager progression, EconomyService economy) {
        ProgressionConfig c = progression.cfg();
        Consumer<String> say = m -> Text.send(p, m);
        switch (r) {
            case UNLOCKED -> say.accept("<green>Auto-Seller installed and switched on.</green> <gray>Mined ore now sells instantly at "
                    + Math.round(c.autoSellRate * 100) + "%. Toggle with <white>/autosell</white>.");
            case ENABLED -> say.accept("<gray>Auto-Seller <green>on</green>.");
            case DISABLED -> say.accept("<gray>Auto-Seller <red>off</red>. Ore goes to your cargo hold.");
            case LOW_GRADE -> say.accept("<red>The Auto-Seller needs</red> " + progression.gradeLabel(c.autoSellGrade) + "<red>.");
            case TOO_POOR -> say.accept("<red>The Auto-Seller costs</red> " + economy.format(Currency.CREDITS, c.autoSellCost) + "<red>.");
            case NOT_LOADED -> say.accept("<red>Your profile is still loading.");
        }
    }
}
