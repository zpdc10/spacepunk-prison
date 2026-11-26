package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.ContrabandSmuggledEvent;
import dev.spacepunk.prison.undercroft.Contraband.Id;
import dev.spacepunk.prison.undercroft.Contraband.Kind;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** The only way out: the guard checkpoint. Contraband gets scanned; a Smuggler's Lining hides some; Credits buy a blind eye. */
final class Checkpoint {
    private final UndercroftManager uc;
    /** Players we are teleporting out right now (the teleport guard lets them through). */
    final Set<UUID> leaving = new HashSet<>();

    Checkpoint(UndercroftManager uc) { this.uc = uc; }

    private record Stack(int slot, ItemStack item, long value) {}

    private List<Stack> contraband(Player p) {
        List<Stack> out = new ArrayList<>();
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            if (!uc.items.isContraband(c[i])) continue;
            out.add(new Stack(i, c[i], value(p, c[i])));
        }
        out.sort(Comparator.comparingLong(Stack::value).reversed());
        return out;
    }

    /** Fence value of a whole stack for this player. */
    long value(Player p, ItemStack it) {
        Id id = uc.items.read(it);
        if (id == null) return 0;
        if (id.kind() == Kind.TAG) return uc.items.tagValue(it);
        return uc.scale(p, uc.items.baseValue(id)) * it.getAmount();
    }

    private int linings(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (uc.items.is(it, Kind.TOOL, "lining")) n += it.getAmount();
        return n;
    }

    long bribe(Player p, List<Stack> stacks) {
        long v = stacks.stream().mapToLong(Stack::value).sum();
        return uc.scale(p, uc.cfg().bribeBase) + Math.round(v * uc.cfg().bribeShare);
    }

    /** A player at the checkpoint wants out (gate terminal, /undercroft leave, or a teleport we stopped). */
    void request(Player p, Location destination) {
        int ring = uc.world.ringAt(p.getLocation());
        if (ring != 0) {
            Text.send(p, "<red>The only way out is through the " + uc.cfg().checkpointName + ". Head west.");
            return;
        }
        long tag = uc.tagLeft(p.getUniqueId());
        if (tag > 0) {
            Text.send(p, "<red>The guards won't open the gate mid-firefight. Wait <white>" + (tag / 1000 + 1) + "s</white>.");
            return;
        }
        List<Stack> stacks = contraband(p);
        if (stacks.isEmpty()) {
            Text.send(p, "<gray>The scanner hums. <white>Clean.</white> \"Move along, inmate.\"");
            exit(p, destination);
            return;
        }
        int items = stacks.stream().mapToInt(s -> s.item().getAmount()).sum();
        int hidden = Math.min(stacks.size(), linings(p) * uc.cfg().liningHides);
        long bribe = bribe(p, stacks);
        Menu m = new Menu(3, "<#3a3028>" + Text.MM.stripTags(uc.cfg().checkpointName).toUpperCase());
        m.set(4, Menu.icon(Material.LODESTONE, "ui/info", "<#c8743a>Scanner gate", List.of(
                "<gray>You're carrying <white>" + items + "</white> contraband in <white>" + stacks.size() + "</white> stacks.",
                "<gray>Smuggler's Lining hides <white>" + hidden + "</white> of those stacks.",
                "<gray>Each other stack has a <white>" + Math.round(uc.cfg().detectChance * 100) + "%</white> chance",
                "<gray>to be found and confiscated.")), null);
        m.set(11, Menu.icon(Material.LIME_DYE, "ui/confirm", "<green>Walk through the scanner", List.of(
                "<gray>Take your chances.")), e -> { p.closeInventory(); scan(p, destination, false); });
        m.set(13, Menu.icon(Material.GOLD_NUGGET, "currency/credit", "<gold>Bribe the guard", List.of(
                "<gray>\"I didn't see anything.\"", "", "<gray>Costs <gold>" + Numbers.shortFormat(bribe) + " Credits",
                "<gray>and everything goes through.")), e -> {
            p.closeInventory();
            EconomyService eco = Services.find(EconomyService.class).orElse(null);
            if (eco == null || !eco.take(p.getUniqueId(), Currency.CREDITS, bribe, "undercroft:bribe")) {
                Text.send(p, "<red>The guard looks at your wallet and laughs.");
                return;
            }
            scan(p, destination, true);
        });
        m.set(15, Menu.icon(Material.BARRIER, "ui/cancel", "<gray>Stay in the Undercroft", List.of()), e -> p.closeInventory());
        m.open(p);
    }

    private void scan(Player p, Location destination, boolean bribed) {
        if (uc.world.ringAt(p.getLocation()) != 0) return;
        List<Stack> stacks = contraband(p);
        int hidden = bribed ? stacks.size() : Math.min(stacks.size(), linings(p) * uc.cfg().liningHides);
        int out = 0, taken = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < stacks.size(); i++) {
            Stack s = stacks.get(i);
            if (i >= hidden && r.nextDouble() < uc.cfg().detectChance) {
                taken += s.item().getAmount();
                p.getInventory().setItem(s.slot(), null);
            } else {
                out += s.item().getAmount();
            }
        }
        if (bribed) Text.send(p, "<gray>The guard pockets the Credits and looks the other way.");
        else if (taken == 0) Text.send(p, "<gray>The scanner beeps once... and lets you through. <green>Nothing found.");
        else Text.send(p, "<red>The scanner shrieks. Guards confiscate <white>" + taken + "</white> contraband."
                + (out > 0 ? " <gray>You get <white>" + out + "</white> out." : ""));
        p.playSound(Sound.sound(Key.key(taken > 0 ? "block.note_block.bass" : "block.note_block.chime"), Sound.Source.PLAYER, 0.8f, taken > 0 ? 0.6f : 1.4f));
        if (out > 0 || taken > 0) {
            PlayerData d = uc.data(p);
            if (d != null && out > 0) { d.smuggled += out; uc.save(p); }
            uc.plugin().getServer().getPluginManager().callEvent(new ContrabandSmuggledEvent(p, out, taken, bribed));
            int smuggled = out;
            if (smuggled > 0) Services.find(dev.spacepunk.prison.api.GangService.class)
                    .ifPresent(g -> g.addContractProgress(p.getUniqueId(), "contraband_smuggled", smuggled));
        }
        exit(p, destination);
    }

    void exit(Player p, Location destination) {
        Location to = destination != null && !uc.world.isWorld(destination.getWorld()) ? destination : uc.exitLocation(p);
        leaving.add(p.getUniqueId());
        p.teleportAsync(to).whenComplete((ok, err) -> leaving.remove(p.getUniqueId()));
    }
}
