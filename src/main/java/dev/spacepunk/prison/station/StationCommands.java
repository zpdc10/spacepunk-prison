package dev.spacepunk.prison.station;

import dev.spacepunk.prison.api.WarpService;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** /hub, /warp ID, /warps, /stationadmin. */
final class StationCommands {
    private static final String ADMIN = "spacepunk.admin";
    private static final List<String> SUBS = List.of("build", "setspawn", "setwarp", "delwarp", "warps", "reload");
    private static final List<String> PUBLIC = List.of(WarpService.PLANETSIDE, WarpService.DANGER,
            WarpService.PARKOUR, WarpService.STATION);

    private final StationModule module;

    StationCommands(StationModule module) { this.module = module; }

    BasicCommand hub() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) { Text.send(src.getSender(), "<red>Players only."); return; }
                if (module.warps().hub() == null) { Text.send(p, "<red>The station hub has not been built yet."); return; }
                module.warps().warp(p, "hub");
            }
        };
    }

    BasicCommand warp() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) { Text.send(src.getSender(), "<red>Players only."); return; }
                if (args.length == 0) { openBoard(p); return; }
                module.warps().warp(p, args[0]);
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length > 1) return List.of();
                String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                return module.warps().all().stream().filter(w -> !w.category().equals(WarpService.PRIVATE) || w.id().equals("workshop"))
                        .map(WarpService.Warp::id).filter(s -> s.startsWith(prefix)).toList();
            }
        };
    }

    BasicCommand warps() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) { Text.send(src.getSender(), "<red>Players only."); return; }
                openBoard(p);
            }
        };
    }

    private void openBoard(Player p) { module.board().open(p, "Shuttle Board", PUBLIC); }

    BasicCommand admin() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "build" -> {
                        if (module.build(() -> Text.send(s, "<green>Station hub built.")))
                            Text.send(s, "<gray>Building the station hub...");
                        else Text.send(s, "<red>A build is already running, or the station world could not be loaded.");
                    }
                    case "setspawn" -> {
                        if (!(s instanceof Player p) || !p.getWorld().getName().equals(module.worldName())) {
                            Text.send(s, "<red>Stand in the station world.");
                            return;
                        }
                        module.setSpawn(p.getLocation());
                        Text.send(s, "<green>Hub spawn set.");
                    }
                    case "setwarp" -> {
                        if (!(s instanceof Player p) || args.length < 2) {
                            Text.send(s, "<red>/stationadmin setwarp ID [category] [display name]");
                            return;
                        }
                        String id = args[1].toLowerCase(Locale.ROOT);
                        String cat = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : WarpService.PLANETSIDE;
                        String name = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : id;
                        module.saveAdminWarp(id, name, cat, p.getLocation());
                        Text.send(s, "<green>Warp <white>" + id + "</white> set here (" + cat + ").");
                    }
                    case "delwarp" -> {
                        if (args.length < 2) { Text.send(s, "<red>/stationadmin delwarp ID"); return; }
                        Text.send(s, module.deleteAdminWarp(args[1].toLowerCase(Locale.ROOT))
                                ? "<green>Warp removed." : "<red>No admin warp with that id (code warps can't be removed).");
                    }
                    case "warps" -> {
                        StringBuilder sb = new StringBuilder("<gray>Warps:");
                        for (WarpService.Warp w : module.warps().all()) {
                            sb.append("\n  <white>").append(w.id()).append(" <dark_gray>").append(w.category())
                                    .append(" <gray>").append(w.displayName())
                                    .append(module.adminWarpIds().contains(w.id()) ? " <dark_gray>(admin)" : "");
                        }
                        Text.send(s, sb.toString());
                    }
                    case "reload" -> {
                        module.reload();
                        Text.send(s, "<green>Station config reloaded.");
                    }
                    default -> Text.send(s, """
                            <gray>/stationadmin <white>build</white> <dark_gray>- (re)build the hub from code
                            <gray>/stationadmin <white>setspawn</white> <dark_gray>- move the hub spawn to you
                            <gray>/stationadmin <white>setwarp ID [category] [name]</white> <dark_gray>- add a warp here
                            <gray>/stationadmin <white>delwarp ID</white> | <white>warps</white> | <white>reload</white>""");
                }
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
                if (args.length <= 1) return SUBS.stream().filter(x -> x.startsWith(prefix)).toList();
                if (args.length == 2 && args[0].equalsIgnoreCase("delwarp"))
                    return module.adminWarpIds().stream().filter(x -> x.startsWith(prefix)).toList();
                if (args.length == 3 && args[0].equalsIgnoreCase("setwarp"))
                    return Stream.of(WarpService.PLANETSIDE, WarpService.DANGER, WarpService.PARKOUR, WarpService.STATION, WarpService.PRIVATE)
                            .filter(x -> x.startsWith(prefix)).toList();
                return List.of();
            }

            @Override public String permission() { return ADMIN; }
        };
    }
}
