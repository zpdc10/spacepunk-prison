package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.BountyTurnInEvent;
import dev.spacepunk.prison.undercroft.Contraband.Id;
import dev.spacepunk.prison.undercroft.Contraband.Kind;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The fence: the black-market buyer for Undercroft goods. Only deals outside the Undercroft, so everything has to be
 * smuggled out first. Payouts are server income (reason income:undercroft...), so they count toward prestige.
 */
final class Fence {
    private final UndercroftManager uc;

    Fence(UndercroftManager uc) { this.uc = uc; }

    private double sellMult(Player p) {
        return Services.find(ProgressionService.class).map(s -> s.sellMultiplier(p.getUniqueId())).orElse(1.0)
                * uc.stims.sellMultiplier(p);
    }

    private long total(Player p, Kind kind) {
        long v = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            Id id = uc.items.read(it);
            if (id == null || id.kind() != kind) continue;
            v += kind == Kind.TAG ? uc.items.tagValue(it) : uc.scale(p, uc.items.baseValue(id)) * it.getAmount();
        }
        return kind == Kind.TAG ? v : Math.round(v * sellMult(p));
    }

    private int count(Player p, Kind kind) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            Id id = uc.items.read(it);
            if (id != null && id.kind() == kind) n += it.getAmount();
        }
        return n;
    }

    private void remove(Player p, Kind kind) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            Id id = uc.items.read(c[i]);
            if (id != null && id.kind() == kind) p.getInventory().setItem(i, null);
        }
    }

    void open(Player p) {
        if (uc.inUndercroft(p.getLocation())) {
            Text.send(p, "<red>The fence doesn't come down here. Smuggle it out first.");
            return;
        }
        Menu m = new Menu(3, "<#3a3028>THE FENCE");
        long ore = total(p, Kind.HOT_ORE), tags = total(p, Kind.TAG), stims = total(p, Kind.STIM);
        int oreN = count(p, Kind.HOT_ORE), tagN = count(p, Kind.TAG), stimN = count(p, Kind.STIM);
        long lining = uc.scale(p, uc.cfg().liningPrice);
        m.set(4, Menu.icon(Material.PAPER, "ui/info", "<#c8743a>The fence", List.of(
                "<#8a8070><i>\"No names, no serials, no questions.\"", "",
                "<gray>Buys hot ore, bounty tags and stims.", "<gray>Prices grow with your grade.")), null);
        m.set(10, Menu.icon(Material.RAW_GOLD, "ui/sell", "<#ff9a40>Sell hot ore", List.of(
                "<gray>You have <white>" + oreN + "</white> hot ore.", "<gray>Pays <gold>" + Numbers.shortFormat(ore) + " Credits")),
                e -> sell(p, Kind.HOT_ORE, "hot ore"));
        m.set(12, Menu.icon(Material.NAME_TAG, "ui/rank", "<#d6a640>Turn in bounty tags", List.of(
                "<gray>You have <white>" + tagN + "</white> tags.", "<gray>Pays <gold>" + Numbers.shortFormat(tags) + " Credits",
                "<gray>plus <white>" + tagN * uc.cfg().pointsPerTag + "</white> gang points.")), e -> turnIn(p));
        m.set(14, Menu.icon(Material.HONEY_BOTTLE, "ui/sell", "<#c070ff>Sell stims", List.of(
                "<gray>You have <white>" + stimN + "</white> doses.", "<gray>Pays <gold>" + Numbers.shortFormat(stims) + " Credits")),
                e -> sell(p, Kind.STIM, "stims"));
        m.set(16, Menu.withLore(uc.items.lining(1), List.of("", "<gray>Buy for <gold>" + Numbers.shortFormat(lining) + " Credits")),
                e -> {
                    EconomyService eco = Services.find(EconomyService.class).orElse(null);
                    if (eco == null || !eco.take(p.getUniqueId(), Currency.CREDITS, lining, "fence:lining")) {
                        Text.send(p, "<red>Not enough Credits.");
                        return;
                    }
                    uc.give(p, uc.items.lining(1));
                    Text.send(p, "<gray>The fence slides a roll of lead foil across the counter.");
                    open(p);
                });
        m.set(22, Menu.icon(Material.BARRIER, "ui/close", "<gray>Close", List.of()), e -> p.closeInventory());
        m.open(p);
    }

    private void sell(Player p, Kind kind, String what) {
        long pay = total(p, kind);
        if (pay <= 0) { Text.send(p, "<red>You've got no " + what + " to sell."); return; }
        remove(p, kind);
        Services.find(EconomyService.class).ifPresent(e -> e.give(p.getUniqueId(), Currency.CREDITS, pay, "income:undercroft"));
        Text.send(p, "<gray>The fence counts out <gold>" + Numbers.shortFormat(pay) + " Credits</gold> for your " + what + ".");
        p.playSound(Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.PLAYER, 0.8f, 0.8f));
        open(p);
    }

    private void turnIn(Player p) {
        int n = count(p, Kind.TAG);
        long pay = total(p, Kind.TAG);
        if (n <= 0) { Text.send(p, "<red>You've got no bounty tags."); return; }
        remove(p, Kind.TAG);
        Services.find(EconomyService.class).ifPresent(e -> e.give(p.getUniqueId(), Currency.CREDITS, pay, "income:undercroft-bounty"));
        long points = n * uc.cfg().pointsPerTag;
        uc.plugin().getServer().getPluginManager().callEvent(new BountyTurnInEvent(p, n, pay, points));
        Text.send(p, "<gray>Turned in <white>" + n + "</white> tags for <gold>" + Numbers.shortFormat(pay) + " Credits</gold>.");
        p.playSound(Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.PLAYER, 0.8f, 0.6f));
        open(p);
    }
}
