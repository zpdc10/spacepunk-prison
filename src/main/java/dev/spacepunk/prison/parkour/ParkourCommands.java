package dev.spacepunk.prison.parkour;

import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** /parkour [leave|cp|go ID] and /parkouradmin. */
final class ParkourCommands {
    private static final String ADMIN = "spacepunk.admin";
    private final ParkourModule module;

    ParkourCommands(ParkourModule module) { this.module = module; }

    private ParkourManager mgr() { return module.manager(); }

    private Stream<String> mineIds() {
        return Services.find(MineService.class).map(ms -> ms.mines().stream().map(MineService.MineInfo::id).toList())
                .orElse(List.of()).stream();
    }

    private Optional<? extends MineService.MineInfo> mine(String id) {
        return Services.find(MineService.class).flatMap(ms -> ms.mines().stream()
                .filter(m -> m.id().equalsIgnoreCase(id) || String.valueOf(m.requiredGrade()).equals(id)).findFirst());
    }

    /** Teleports a player to a course start if they may run it. */
    void go(Player p, String id) {
        Course c = mgr().courses().get(id);
        if (c == null) { Text.send(p, "<red>No run built for " + id + "."); return; }
        if (!mgr().canRun(p, c)) { Text.send(p, "<red>That run needs Grade " + c.grade + "."); return; }
        World w = c.bukkitWorld();
        if (w == null) { Text.send(p, "<red>That run's world is not loaded."); return; }
        p.teleportAsync(c.respawns.get(0).at(w));
        Text.send(p, "<gray>Maintenance run: " + ParkourManager.deckColor(c.grade) + mgr().mineName(id)
                + " <dark_gray>- <gray>step on the plate to start.");
    }

    BasicCommand player() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) { Text.send(src.getSender(), "<red>Players only."); return; }
                if (args.length == 0) { module.menu().open(p); return; }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "leave", "stop", "quit" -> {
                        if (mgr().run(p.getUniqueId()) == null) Text.send(p, "<gray>You are not on a run.");
                        else mgr().endRun(p, "<gray>Run stopped.");
                    }
                    case "cp", "checkpoint", "reset" -> {
                        ParkourManager.Run run = mgr().run(p.getUniqueId());
                        Course c = run == null ? null : mgr().courses().get(run.course);
                        if (c == null) Text.send(p, "<gray>You are not on a run.");
                        else mgr().respawn(p, c, run);
                    }
                    case "go", "tp" -> {
                        if (args.length < 2) { Text.send(p, "<red>/parkour go MINE"); return; }
                        Optional<? extends MineService.MineInfo> m = mine(args[1]);
                        go(p, m.map(MineService.MineInfo::id).orElse(args[1].toLowerCase(Locale.ROOT)));
                    }
                    default -> module.menu().open(p);
                }
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
                if (args.length <= 1) return Stream.of("leave", "cp", "go").filter(s -> s.startsWith(prefix)).toList();
                if (args.length == 2 && args[0].equalsIgnoreCase("go")) {
                    return mgr().courses().keySet().stream().filter(s -> s.startsWith(prefix)).toList();
                }
                return List.of();
            }
        };
    }

    BasicCommand admin() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                if (args.length == 0) { help(s); return; }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "generate" -> generate(s, args.length > 1 ? args[1] : "all", args.length > 2 ? args[2] : "0");
                    case "clear" -> {
                        String which = args.length > 1 ? args[1] : "";
                        if (which.isEmpty()) { Text.send(s, "<red>/parkouradmin clear all|MINE"); return; }
                        int n = 0;
                        for (String id : which.equalsIgnoreCase("all") ? List.copyOf(mgr().courses().keySet()) : List.of(which.toLowerCase(Locale.ROOT))) {
                            if (mgr().clear(id)) n++;
                        }
                        Text.send(s, "<green>Removed " + n + " run(s).");
                    }
                    case "reload" -> {
                        module.reload();
                        Text.send(s, "<green>parkour.yml reloaded. Regenerate to apply layout changes; rewards apply now.");
                    }
                    case "list", "info" -> {
                        StringBuilder sb = new StringBuilder("<gray>" + mgr().courses().size() + " runs built");
                        for (Course c : mgr().courses().values()) {
                            sb.append("\n  <white>").append(c.mineId).append(" <gray>G").append(c.grade).append(" ").append(c.jumps)
                                    .append("/").append(mgr().cfg().jumps(c.grade)).append(" jumps, ").append(c.checkpoints.size()).append(" CP, start ").append(c.start)
                                    .append(mgr().record(c.mineId).map(r -> " <gold>" + ParkourManager.time(r.millis()) + " " + r.name()).orElse(""));
                        }
                        Text.send(s, sb.toString());
                    }
                    default -> help(s);
                }
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
                if (args.length <= 1) return Stream.of("generate", "clear", "list", "reload").filter(x -> x.startsWith(prefix)).toList();
                if (args.length == 2 && List.of("generate", "clear").contains(args[0].toLowerCase(Locale.ROOT))) {
                    return Stream.concat(Stream.of("all", "short"), mineIds()).filter(x -> x.startsWith(prefix)).toList();
                }
                return List.of();
            }

            @Override public String permission() { return ADMIN; }
        };
    }

    private void generate(CommandSender s, String which, String seedArg) {
        MineService ms = Services.find(MineService.class).orElse(null);
        if (ms == null) { Text.send(s, "<red>The mines module is not running."); return; }
        long seed;
        try {
            seed = Long.parseLong(seedArg);
        } catch (NumberFormatException e) {
            seed = seedArg.hashCode();
        }
        List<MineService.MineInfo> targets = new ArrayList<>();
        if (which.equalsIgnoreCase("all")) targets.addAll(ms.mines());
        else if (which.equalsIgnoreCase("short")) {
            // Rebuild only runs that came out with fewer jumps than their grade calls for.
            for (MineService.MineInfo m : ms.mines()) {
                Course c = mgr().courses().get(m.id());
                if (c != null && c.jumps < mgr().cfg().jumps(c.grade)) targets.add(m);
            }
            if (targets.isEmpty()) { Text.send(s, "<green>Every built run already has its full length."); return; }
        }
        else mine(which).ifPresent(targets::add);
        if (targets.isEmpty()) { Text.send(s, "<red>No mine called " + which + "."); return; }
        int built = 0;
        List<String> errors = new ArrayList<>();
        for (MineService.MineInfo m : targets) {
            String err = mgr().generate(m, seed);
            if (err == null) built++;
            else errors.add(err);
        }
        StringBuilder shortRuns = new StringBuilder();
        for (MineService.MineInfo m : targets) {
            Course c = mgr().courses().get(m.id());
            if (c != null && c.jumps < mgr().cfg().jumps(c.grade)) {
                shortRuns.append(" ").append(m.id()).append(" (").append(c.jumps).append("/").append(mgr().cfg().jumps(c.grade)).append(")");
            }
        }
        Text.send(s, "<green>Built " + built + " maintenance run(s)." + (errors.isEmpty() ? "" : " <red>" + String.join(" ", errors))
                + (shortRuns.isEmpty() ? "" : " <yellow>Still short:" + shortRuns + ". Try generate MINE with another seed."));
    }

    private static void help(CommandSender s) {
        Text.send(s, """
                <gray>/parkouradmin <white>generate [all|short|MINE] [SEED]</white> <dark_gray>- build runs north of each mine's spawn (rebuilds; short = only runs below full length)
                <gray>/parkouradmin <white>clear all|MINE</white> <dark_gray>- remove runs and restore the rim
                <gray>/parkouradmin <white>list</white> | <white>reload</white>""");
    }
}
