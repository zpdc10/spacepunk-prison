package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.api.GunService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /rig [drill | rod | gun [gun id]]: opens a module rig. "gun" alone opens the held gun's rig (or the first one). */
final class RigCommand implements BasicCommand {
    private final DrillManager drills;

    RigCommand(DrillManager drills) { this.drills = drills; }

    @Override
    public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
        if (!(src.getSender() instanceof Player p)) {
            Text.send(src.getSender(), "<red>Players only.");
            return;
        }
        String which = args.length == 0 ? "drill" : args[0].toLowerCase(Locale.ROOT);
        String key = switch (which) {
            case "rod", "fishing" -> DrillManager.RIG_ROD;
            case "gun", "guns", "weapon" -> {
                String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : heldGun(p);
                if (id == null && !drills.cfg().rigGuns().isEmpty()) id = drills.cfg().rigGuns().getFirst();
                yield id == null ? null : DrillManager.gunRigKey(id);
            }
            default -> DrillManager.RIG_DRILL;
        };
        if (key == null || !drills.validRig(key)) {
            Text.send(p, "<red>Unknown rig. Try <white>/rig</white>, <white>/rig rod</white> or <white>/rig gun "
                    + String.join("|", drills.cfg().rigGuns()) + "</white>.");
            return;
        }
        drills.openRig(p, key);
    }

    private static String heldGun(Player p) {
        GunService guns = Services.find(GunService.class).orElse(null);
        return guns == null ? null : guns.gunId(p.getInventory().getItemInMainHand()).orElse(null);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack src, String @NotNull [] args) {
        if (args.length <= 1) return List.of("drill", "rod", "gun");
        if (args.length == 2 && args[0].equalsIgnoreCase("gun")) return new ArrayList<>(drills.cfg().rigGuns());
        return List.of();
    }
}
