package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.drill.DrillConfig.EnchantDef;
import dev.spacepunk.prison.drill.DrillConfig.FrameDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;
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

/**
 * /drill            open the drill bay (and return a missing drill)
 * /drill admin ...  give module, set frame/level/heat, reset, reload
 */
public final class DrillCommand implements BasicCommand {
    private static final String ADMIN = "spacepunk.admin";
    private final DrillModule module;

    public DrillCommand(DrillModule module) { this.module = module; }

    @Override
    public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
        CommandSender sender = src.getSender();
        DrillManager drills = module.drills();
        if (args.length == 0 || !args[0].equalsIgnoreCase("admin")) {
            if (!(sender instanceof Player p)) {
                Text.send(sender, "<gray>/drill admin <module|frame|level|heat|give|reset|reload> ...");
                return;
            }
            if (drills.data(p.getUniqueId()) == null) {
                Text.send(p, "<red>Your drill is still loading.");
                return;
            }
            if (drills.ensureDrill(p)) Text.send(p, "<gray>Your drill was returned to your inventory.");
            module.menu().open(p);
            return;
        }
        if (!sender.hasPermission(ADMIN)) {
            Text.send(sender, "<red>No permission.");
            return;
        }
        if (args.length < 2) {
            usage(sender);
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            module.reload();
            Text.send(sender, "<gray>drill.yml reloaded.");
            return;
        }
        if (args.length < 3) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null || drills.data(target.getUniqueId()) == null) {
            Text.send(sender, "<red>Player not online (or still loading).");
            return;
        }
        DrillConfig cfg = drills.cfg();
        DrillData d = drills.data(target.getUniqueId());
        switch (sub) {
            case "give" -> {
                boolean given = drills.ensureDrill(target);
                Text.send(sender, given ? "<gray>Gave " + target.getName() + " their drill." : "<gray>" + target.getName() + " already has their drill (refreshed).");
            }
            case "module" -> {
                if (args.length < 4) { usage(sender); return; }
                ModuleDef m = cfg.module(args[3].toLowerCase(Locale.ROOT));
                if (m == null) { Text.send(sender, "<red>Unknown module. Options: " + String.join(", ", cfg.modules.keySet())); return; }
                int amount = 1;
                if (args.length >= 5) {
                    try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[4]))); } catch (NumberFormatException ignored) { }
                }
                for (int i = 0; i < amount; i++) drills.give(target, drills.items().module(m));
                Text.send(sender, "<gray>Gave " + target.getName() + " " + amount + "x " + m.coloredName());
            }
            case "frame" -> {
                if (args.length < 4) { usage(sender); return; }
                FrameDef f = cfg.frames.stream().filter(x -> x.id().equalsIgnoreCase(args[3])).findFirst().orElse(null);
                if (f == null) { Text.send(sender, "<red>Unknown frame. Options: " + String.join(", ", cfg.frames.stream().map(FrameDef::id).toList())); return; }
                drills.setFrame(target, f);
                Text.send(sender, "<gray>Set " + target.getName() + "'s frame to " + f.coloredName());
            }
            case "level" -> {
                if (args.length < 5) { usage(sender); return; }
                Enchant e = Enchant.byId(args[3]);
                if (e == null) { Text.send(sender, "<red>Unknown enchant."); return; }
                int lvl;
                try { lvl = Integer.parseInt(args[4]); } catch (NumberFormatException ex) { Text.send(sender, "<red>Bad level."); return; }
                EnchantDef def = cfg.enchant(e);
                int cap = def.cap(cfg.frame(d.frame));
                lvl = Math.max(0, Math.min(cap, lvl));
                d.level(e, lvl);
                drills.changed(target);
                Text.send(sender, "<gray>Set " + target.getName() + "'s " + def.coloredName() + " <gray>to " + lvl + " (frame cap " + cap + ")");
            }
            case "heat" -> {
                if (args.length < 4) { usage(sender); return; }
                try {
                    module.heat().set(target, Double.parseDouble(args[3]));
                } catch (NumberFormatException ex) {
                    Text.send(sender, "<red>Bad number.");
                    return;
                }
                Text.send(sender, "<gray>Set heat.");
            }
            case "reset" -> {
                d.frame = cfg.firstFrame().id();
                d.levels.clear();
                d.sockets.clear();
                drills.changed(target);
                Text.send(sender, "<gray>Reset " + target.getName() + "'s drill (modules in sockets were deleted).");
            }
            default -> {
                usage(sender);
                return;
            }
        }
        module.menu().refreshIfOpen(target);
    }

    private static void usage(CommandSender s) {
        Text.send(s, """
                <gray>/drill admin give <player>
                /drill admin module <player> <module> [amount]
                /drill admin frame <player> <frame>
                /drill admin level <player> <enchant> <level>
                /drill admin heat <player> <amount>
                /drill admin reset <player>
                /drill admin reload""");
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
        if (!src.getSender().hasPermission(ADMIN)) return List.of();
        DrillConfig cfg = module.drills().cfg();
        return switch (args.length) {
            case 0, 1 -> List.of("admin");
            case 2 -> List.of("give", "module", "frame", "level", "heat", "reset", "reload");
            case 3 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            case 4 -> switch (args[1].toLowerCase(Locale.ROOT)) {
                case "module" -> List.copyOf(cfg.modules.keySet());
                case "frame" -> cfg.frames.stream().map(FrameDef::id).toList();
                case "level" -> Arrays.stream(Enchant.values()).map(Enchant::id).toList();
                default -> List.of();
            };
            default -> List.of();
        };
    }
}
