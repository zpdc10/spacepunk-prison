package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /undercroft (enter, leave, stats, admin) and /fence. */
final class UndercroftCommands {
    private static final String ADMIN = "spacepunk.admin";
    private final UndercroftModule module;

    UndercroftCommands(UndercroftModule module) { this.module = module; }

    BasicCommand fence() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (src.getSender() instanceof Player p) module.fence().open(p);
            }
        };
    }

    BasicCommand undercroft() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                UndercroftManager uc = module.manager();
                String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("admin")) { admin(s, args); return; }
                if (!(s instanceof Player p)) { Text.send(s, "<gray>/undercroft admin ..."); return; }
                switch (sub) {
                    case "", "enter", "go" -> {
                        if (uc.inUndercroft(p.getLocation())) { stats(p); return; }
                        module.enter(p);
                    }
                    case "leave", "exit" -> module.checkpoint().request(p, null);
                    case "stats", "info" -> stats(p);
                    default -> Text.send(p, """
                            <gray>/undercroft <dark_gray>-</dark_gray> go down to the Undercroft (PvP zone)
                            /undercroft leave <dark_gray>-</dark_gray> go through the checkpoint scanner
                            /undercroft stats <dark_gray>-</dark_gray> your kills, bounty and stim
                            /fence <dark_gray>-</dark_gray> sell smuggled goods (outside the Undercroft)""");
                }
            }

            @Override
            public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                boolean admin = src.getSender().hasPermission(ADMIN);
                if (args.length <= 1) return admin ? List.of("leave", "stats", "admin") : List.of("leave", "stats");
                if (!admin || !args[0].equalsIgnoreCase("admin")) return List.of();
                return switch (args.length) {
                    case 2 -> List.of("generate", "regrow", "reload", "drop", "give", "lab", "tp");
                    case 3 -> args[1].equalsIgnoreCase("give") ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                            : args[1].equalsIgnoreCase("lab") ? List.copyOf(module.manager().cfg().labs.keySet()) : List.of();
                    case 4 -> args[1].equalsIgnoreCase("give") ? module.manager().items.allIds() : List.of();
                    default -> List.of();
                };
            }
        };
    }

    private void stats(Player p) {
        UndercroftManager uc = module.manager();
        PlayerData d = uc.data(p);
        if (d == null) { Text.send(p, "<red>Still loading."); return; }
        Text.send(p, "<gray>Undercroft record: <white>" + d.kills + "</white> kills, <white>" + d.deaths + "</white> deaths, streak <white>"
                + d.streak + "</white>, bounty on you <gold>" + Numbers.shortFormat(d.bounty) + "</gold>, smuggled <white>" + d.smuggled + "</white> items.");
        if (d.stim != null) Text.send(p, "<gray>On <white>" + d.stim + "</white>" + (d.comedown ? " (comedown)" : "") + ", "
                + Contraband.mmss(d.remaining) + " left.");
    }

    private void admin(CommandSender s, String[] args) {
        if (!s.hasPermission(ADMIN)) { Text.send(s, "<red>No permission."); return; }
        UndercroftManager uc = module.manager();
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "generate" -> {
                Text.send(s, "<gray>Building the Undercroft...");
                module.generate();
                Text.send(s, "<gray>Done: " + uc.store.veins.size() + " ore blocks, " + uc.cfg().labs.size() + " labs.");
            }
            case "regrow" -> { uc.world.regrowAll(uc.store); Text.send(s, "<gray>Every hot ore vein regrown."); }
            case "reload" -> { module.reload(); Text.send(s, "<gray>undercroft.yml reloaded."); }
            case "drop" -> {
                int ring = args.length > 2 ? parse(args[2], 1) : 1;
                Text.send(s, module.drops().launch(ring) ? "<gray>Supply drop launched into ring " + ring + "." : "<red>No landing spot found.");
            }
            case "tp" -> {
                if (!(s instanceof Player p)) return;
                World w = uc.world.loadOrCreate();
                if (w != null) p.teleport(uc.world.checkpointSpawn(w));
            }
            case "give" -> {
                if (args.length < 4) { Text.send(s, "<gray>/undercroft admin give <player> <item> [amount]"); return; }
                Player t = Bukkit.getPlayerExact(args[2]);
                if (t == null) { Text.send(s, "<red>Player not online."); return; }
                ItemStack it = uc.items.create(args[3].toLowerCase(Locale.ROOT), args.length > 4 ? parse(args[4], 1) : 1);
                if (it == null) { Text.send(s, "<red>Items: " + String.join(", ", uc.items.allIds())); return; }
                uc.give(t, it);
                Text.send(s, "<gray>Given.");
            }
            case "lab" -> {
                if (args.length < 4) { Text.send(s, "<gray>/undercroft admin lab <lab> <controllerId|none> [name]"); return; }
                String id = args[3].equalsIgnoreCase("none") ? null : args[3];
                String name = args.length > 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)) : id;
                uc.setLabController(args[2], id, name, true);
                Text.send(s, "<gray>Lab controller set.");
            }
            default -> Text.send(s, """
                    <gray>/undercroft admin generate <dark_gray>-</dark_gray> build (or rebuild) the zone
                    /undercroft admin regrow | reload | tp
                    /undercroft admin drop [ring]
                    /undercroft admin give <player> <item> [amount]
                    /undercroft admin lab <lab> <controllerId|none> [name]""");
        }
    }

    private static int parse(String s, int def) {
        try { return Math.max(1, Math.min(6400, Integer.parseInt(s))); } catch (NumberFormatException e) { return def; }
    }
}
