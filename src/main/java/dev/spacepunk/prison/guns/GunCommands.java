package dev.spacepunk.prison.guns;

import dev.spacepunk.prison.guns.GunsConfig.AmmoDef;
import dev.spacepunk.prison.guns.GunsConfig.GunDef;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /gunsmith opens the shop. /gun admin give|ammo|parts|reload for staff. */
final class GunCommands {
    private static final String ADMIN = "spacepunk.admin";
    private final GunsModule module;

    GunCommands(GunsModule module) { this.module = module; }

    BasicCommand gunsmith() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (src.getSender() instanceof Player p) module.menu().open(p);
                else Text.send(src.getSender(), "<red>Players only.");
            }
        };
    }

    BasicCommand gun() {
        return new BasicCommand() {
            @Override
            public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                if (args.length == 0 || !args[0].equalsIgnoreCase("admin") || !s.hasPermission(ADMIN)) {
                    Text.send(s, "<gray>Guns fire with <white>right-click</white>, reload with <white>Q</white> or <white>F</white>, "
                            + "and scope or steady with <white>sneak</white>. They only fire in PvP zones (the Undercroft). "
                            + "Buy them at the <white>/gunsmith</white>.");
                    return;
                }
                GunManager guns = module.guns();
                if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                    module.reload();
                    Text.send(s, "<gray>guns.yml reloaded: " + guns.cfg().guns.size() + " guns.");
                    return;
                }
                if (args.length < 3) { usage(s); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { Text.send(s, "<red>Player not online."); return; }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "give" -> {
                        GunDef g = args.length > 3 ? guns.cfg().guns.get(args[3].toLowerCase(Locale.ROOT)) : null;
                        if (g == null) { Text.send(s, "<red>Guns: " + String.join(", ", guns.cfg().guns.keySet())); return; }
                        give(target, guns.items().gun(g, g.magazine()));
                        Text.send(s, "<gray>Gave " + target.getName() + " a " + g.name() + "<gray>.");
                    }
                    case "ammo" -> {
                        AmmoDef a = args.length > 3 ? guns.cfg().ammo.get(args[3].toLowerCase(Locale.ROOT)) : null;
                        if (a == null) { Text.send(s, "<red>Ammo: " + String.join(", ", guns.cfg().ammo.keySet())); return; }
                        int n = args.length > 4 ? parse(args[4], a.pack()) : a.pack();
                        while (n > 0) { int k = Math.min(99, n); give(target, guns.items().ammo(a, k)); n -= k; }
                        Text.send(s, "<gray>Gave " + target.getName() + " " + a.name() + "<gray>.");
                    }
                    case "parts" -> {
                        int n = args.length > 3 ? parse(args[3], 1) : 1;
                        while (n > 0) { int k = Math.min(99, n); give(target, guns.items().parts(k)); n -= k; }
                        Text.send(s, "<gray>Gave " + target.getName() + " gun parts.");
                    }
                    default -> usage(s);
                }
            }

            @Override
            public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!src.getSender().hasPermission(ADMIN)) return List.of();
                return switch (args.length) {
                    case 0, 1 -> List.of("admin");
                    case 2 -> List.of("give", "ammo", "parts", "reload");
                    case 3 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    case 4 -> switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "give" -> List.copyOf(module.guns().cfg().guns.keySet());
                        case "ammo" -> List.copyOf(module.guns().cfg().ammo.keySet());
                        default -> List.of();
                    };
                    default -> List.of();
                };
            }
        };
    }

    private static void usage(CommandSender s) {
        Text.send(s, """
                <gray>/gun admin give <player> <gun>
                /gun admin ammo <player> <type> [amount]
                /gun admin parts <player> [amount]
                /gun admin reload""");
    }

    private static int parse(String s, int def) {
        try { return Math.max(1, Math.min(6400, Integer.parseInt(s))); } catch (NumberFormatException e) { return def; }
    }

    private static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }
}
