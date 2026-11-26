package dev.spacepunk.prison.mines;

import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** /mine [id|grade], /mines, and /mineadmin. */
final class MineCommands {
    private static final String ADMIN = "spacepunk.admin";
    private static final List<String> SUBS = List.of("list", "info", "generate", "pos1", "pos2", "define", "create",
            "setspawn", "remove", "reset", "reload");

    private final MinesModule module;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    MineCommands(MinesModule module) {
        this.module = module;
    }

    private MineManager mgr() { return module.manager(); }

    private Stream<String> mineIds() { return mgr().mines().stream().map(Mine::id); }

    // ------------------------------------------------------------------ player commands

    BasicCommand mine() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) { Text.send(src.getSender(), "<red>Players only."); return; }
                if (args.length > 0 && args[0].equalsIgnoreCase("list")) { sendList(p); return; }
                Optional<Mine> target = args.length == 0 ? mgr().bestFor(p) : mgr().resolve(args[0]);
                if (target.isEmpty()) {
                    Text.send(p, args.length == 0 ? "<red>No mine is open to you yet." : "<red>No mine called <white>" + args[0] + "</white>. Try <white>/mines</white>.");
                    return;
                }
                Mine m = target.get();
                if (!m.placed()) { Text.send(p, "<red>" + m.displayName() + " has not been built yet."); return; }
                if (!mgr().canAccess(p, m)) {
                    Text.send(p, "<red>" + m.displayName() + " needs <white>Grade " + m.requiredGrade() + "</white>.");
                    return;
                }
                p.teleportAsync(m.spawn());
                Text.send(p, "<gray>Transit to " + m.color() + m.displayName() + " <dark_gray>(" + m.deckName() + ")");
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length > 1) return List.of();
                String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                return Stream.concat(Stream.of("list"), mineIds()).filter(s -> s.startsWith(prefix)).toList();
            }
        };
    }

    BasicCommand list() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                sendList(src.getSender());
            }
        };
    }

    private void sendList(CommandSender to) {
        StringBuilder sb = new StringBuilder("<gray>Mines <dark_gray>(click to travel)");
        String deck = null;
        for (Mine m : mgr().mines()) {
            if (!m.deckName().equals(deck)) {
                deck = m.deckName();
                sb.append("\n").append(m.color()).append("<bold>").append(deck).append("</bold>");
            }
            boolean open = !(to instanceof Player p) || mgr().canAccess(p, m);
            String status = !m.placed() ? "<dark_gray>not built" : open ? "<green>open" : "<red>locked";
            String line = "<gray>G" + m.requiredGrade() + " <white>" + m.displayName() + " <dark_gray>- " + status
                    + (m.placed() ? " <dark_gray>- <gray>" + Math.round(m.fillRatio() * 100) + "% ore" : "");
            if (open && m.placed()) line = "<click:run_command:'/mine " + m.id() + "'><hover:show_text:'<gray>/mine " + m.id() + "'>" + line + "</hover></click>";
            sb.append("\n  ").append(line);
        }
        Text.send(to, sb.toString());
    }

    // ------------------------------------------------------------------ admin

    BasicCommand admin() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                CommandSender s = src.getSender();
                if (args.length == 0) { help(s); return; }
                String sub = args[0].toLowerCase(Locale.ROOT);
                try {
                    switch (sub) {
                        case "list" -> adminList(s);
                        case "info" -> info(s, need(args, 1));
                        case "generate" -> generate(s, args.length > 1 ? args[1] : "all");
                        case "pos1", "pos2" -> pos(s, sub.equals("pos1") ? 0 : 1);
                        case "define" -> define(s, need(args, 1));
                        case "create" -> create(s, need(args, 1), need(args, 2), args.length > 3 ? args[3] : null);
                        case "setspawn" -> setSpawn(s, need(args, 1));
                        case "remove" -> remove(s, need(args, 1));
                        case "reset" -> reset(s, need(args, 1));
                        case "reload" -> { module.reload(); Text.send(s, "<green>Mines reloaded and refilling."); }
                        default -> help(s);
                    }
                } catch (UsageException e) {
                    Text.send(s, "<red>" + e.getMessage());
                }
            }

            @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (args.length <= 1) {
                    String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                    return SUBS.stream().filter(x -> x.startsWith(prefix)).toList();
                }
                String sub = args[0].toLowerCase(Locale.ROOT);
                String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
                if (args.length == 2 && List.of("info", "define", "setspawn", "remove").contains(sub)) {
                    return mineIds().filter(x -> x.startsWith(prefix)).toList();
                }
                if (args.length == 2 && List.of("reset", "generate").contains(sub)) {
                    return Stream.concat(Stream.of("all"), mineIds()).filter(x -> x.startsWith(prefix)).toList();
                }
                if (args.length == 4 && sub.equals("create")) return mineIds().filter(x -> x.startsWith(prefix)).toList();
                return List.of();
            }

            @Override public String permission() { return ADMIN; }
        };
    }

    private void help(CommandSender s) {
        Text.send(s, """
                <gray>/mineadmin <white>list</white> | <white>info ID</white>
                <gray>/mineadmin <white>generate [all|ID]</white> <dark_gray>- build mines in the void world (auto layout)
                <gray>/mineadmin <white>pos1</white> | <white>pos2</white> <dark_gray>- select corners (the block you look at)
                <gray>/mineadmin <white>define ID</white> <dark_gray>- place a configured mine on the selection
                <gray>/mineadmin <white>create ID GRADE [COPY_BLOCKS_FROM]</white> <dark_gray>- new mine on the selection
                <gray>/mineadmin <white>setspawn ID</white> | <white>remove ID</white> | <white>reset ID|all</white> | <white>reload</white>""");
    }

    private void adminList(CommandSender s) {
        StringBuilder sb = new StringBuilder("<gray>" + mgr().mines().size() + " mines, refiller: " + mgr().refiller().name());
        for (Mine m : mgr().mines()) {
            Region r = m.region();
            sb.append("\n  <white>").append(m.id()).append(" <gray>G").append(m.requiredGrade()).append(" ");
            if (r == null) sb.append("<dark_gray>not placed");
            else sb.append("<gray>").append(r.world()).append(" ").append(r.minX()).append(",").append(r.minY()).append(",").append(r.minZ())
                    .append(" <dark_gray>").append(Numbers.shortFormat(r.volume())).append(" blocks <gray>")
                    .append(Math.round(m.fillRatio() * 100)).append("%").append(m.refilling() ? " <yellow>refilling" : "");
        }
        Text.send(s, sb.toString());
    }

    private void info(CommandSender s, String id) throws UsageException {
        Mine m = find(id);
        StringBuilder sb = new StringBuilder("<white>" + m.displayName() + " <dark_gray>(" + m.id() + ")"
                + "\n  <gray>Deck: " + m.color() + m.deckName() + " <gray>Grade: <white>" + m.requiredGrade()
                + (m.def().custom() ? " <dark_gray>(custom)" : ""));
        Region r = m.region();
        if (r != null) {
            sb.append("\n  <gray>Region: ").append(r.world()).append(" ").append(r.minX()).append(",").append(r.minY()).append(",").append(r.minZ())
                    .append(" to ").append(r.maxX()).append(",").append(r.maxY()).append(",").append(r.maxZ())
                    .append(" <dark_gray>(").append(r.sizeX()).append("x").append(r.sizeY()).append("x").append(r.sizeZ()).append(")")
                    .append("\n  <gray>Ore left: <white>").append(Numbers.shortFormat(m.remaining())).append("/").append(Numbers.shortFormat(r.volume()))
                    .append(" <gray>(").append(Math.round(m.fillRatio() * 100)).append("%)");
        } else {
            sb.append("\n  <dark_gray>Not placed.");
        }
        double total = m.mix().total();
        sb.append("\n  <gray>Blocks:");
        for (Map.Entry<Material, Double> e : m.mix().weights().entrySet()) {
            sb.append("\n    <white>").append(e.getKey().name().toLowerCase(Locale.ROOT)).append(" <gray>")
                    .append(String.format(Locale.ROOT, "%.1f%%", e.getValue() * 100 / total));
        }
        Text.send(s, sb.toString());
    }

    private void generate(CommandSender s, String which) throws UsageException {
        List<Mine> targets = new ArrayList<>();
        if (which.equalsIgnoreCase("all")) targets.addAll(mgr().mines());
        else targets.add(find(which));
        targets.removeIf(m -> m.def().custom());
        if (targets.isEmpty()) throw new UsageException("Nothing to generate (custom mines are placed with create/define).");
        List<Mine> done = module.generate(targets);
        if (done.isEmpty()) throw new UsageException("Could not load the mines world.");
        Text.send(s, "<green>Built " + done.size() + " mine(s) in <white>" + mgr().config().settings().world()
                + "</white>. Refilling now. Use <white>/mine " + done.get(0).id() + "</white> to visit.");
    }

    private void pos(CommandSender s, int index) throws UsageException {
        Player p = player(s);
        Block target = p.getTargetBlockExact(8);
        Location l = target != null ? target.getLocation() : p.getLocation().getBlock().getLocation();
        selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2])[index] = l;
        Text.send(p, "<gray>Corner " + (index + 1) + " set to <white>" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
    }

    private Region selection(Player p, Mine exclude) throws UsageException {
        Location[] sel = selections.get(p.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null) throw new UsageException("Set both corners first with /mineadmin pos1 and pos2.");
        if (sel[0].getWorld() != sel[1].getWorld() || sel[0].getWorld() == null) throw new UsageException("Both corners must be in the same world.");
        Region r = Region.of(sel[0].getWorld().getName(), sel[0].getBlockX(), sel[0].getBlockY(), sel[0].getBlockZ(),
                sel[1].getBlockX(), sel[1].getBlockY(), sel[1].getBlockZ());
        long max = mgr().config().settings().maxVolume();
        if (r.volume() > max) throw new UsageException("Selection is " + r.volume() + " blocks; the limit is " + max + " (limits.max-volume).");
        for (Mine other : mgr().mines()) {
            Region o = other.region();
            if (other != exclude && o != null && o.world().equals(r.world()) && o.minX() <= r.maxX() && o.maxX() >= r.minX()
                    && o.minY() <= r.maxY() && o.maxY() >= r.minY() && o.minZ() <= r.maxZ() && o.maxZ() >= r.minZ()) {
                throw new UsageException("Selection overlaps mine " + other.id() + ". Remove it first.");
            }
        }
        return r;
    }

    private void define(CommandSender s, String id) throws UsageException {
        Player p = player(s);
        Mine m = find(id);
        Region r = selection(p, m);
        mgr().place(m, r, null);
        Text.send(p, "<green>" + m.displayName() + " placed (" + r.volume() + " blocks) and refilling. Set a spawn with /mineadmin setspawn " + m.id() + ".");
    }

    private void create(CommandSender s, String rawId, String gradeArg, String copyFrom) throws UsageException {
        Player p = player(s);
        String id = rawId.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,32}")) throw new UsageException("Ids use a-z, 0-9, - and _.");
        if (mgr().byId(id).isPresent()) throw new UsageException("A mine called " + id + " already exists; use define to move it.");
        final int grade = parseGrade(gradeArg);
        Mine template = copyFrom != null ? find(copyFrom)
                : mgr().mines().stream().filter(m -> m.requiredGrade() <= grade).reduce((a, b) -> b)
                .orElseGet(() -> mgr().mines().stream().findFirst().orElse(null));
        Region r = selection(p, null);
        MinesConfig.MineDef def = new MinesConfig.MineDef(id, id, template == null ? "" : template.def().deck(),
                Math.max(1, grade), template == null ? new BlockMix(Map.of(Material.STONE, 1.0)) : template.mix(), true);
        Mine m = mgr().addCustom(def);
        mgr().place(m, r, null);
        Text.send(p, "<green>Created mine " + id + " (grade " + grade + ", blocks from " + (template == null ? "stone" : template.id())
                + "). Edit its blocks under custom." + id + " in mines-data.yml, then /mineadmin reload.");
    }

    private void setSpawn(CommandSender s, String id) throws UsageException {
        Player p = player(s);
        Mine m = find(id);
        if (m.region() == null) throw new UsageException("Place the mine first.");
        mgr().setSpawn(m, p.getLocation());
        Text.send(p, "<green>Spawn for " + m.displayName() + " set here.");
    }

    private void remove(CommandSender s, String id) throws UsageException {
        Mine m = find(id);
        mgr().unplace(m);
        Text.send(s, "<green>" + m.id() + (m.def().custom() ? " deleted." : " unplaced (its blocks stay in the world)."));
    }

    private void reset(CommandSender s, String which) throws UsageException {
        if (which.equalsIgnoreCase("all")) {
            mgr().refillAll();
            Text.send(s, "<green>Refilling all placed mines.");
            return;
        }
        Mine m = find(which);
        if (!m.placed()) throw new UsageException(m.id() + " is not placed.");
        mgr().refillNow(m);
        Text.send(s, "<green>Refilling " + m.displayName() + ".");
    }

    // ------------------------------------------------------------------ helpers

    private Mine find(String id) throws UsageException {
        return mgr().resolve(id).orElseThrow(() -> new UsageException("No mine called " + id + "."));
    }

    private static int parseGrade(String arg) throws UsageException {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            throw new UsageException("Grade must be a number.");
        }
    }

    private static Player player(CommandSender s) throws UsageException {
        if (s instanceof Player p) return p;
        throw new UsageException("Run this in game.");
    }

    private static String need(String[] args, int i) throws UsageException {
        if (args.length <= i) throw new UsageException("Missing argument. /mineadmin for help.");
        return args[i];
    }

    private static final class UsageException extends Exception {
        UsageException(String msg) { super(msg); }
    }
}
