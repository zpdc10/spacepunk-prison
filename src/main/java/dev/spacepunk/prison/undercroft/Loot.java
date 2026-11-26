package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.GunService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Rolls loot tables from undercroft.yml into items (and, for crates, Tokens and console commands). */
final class Loot {
    private final UndercroftManager uc;

    Loot(UndercroftManager uc) { this.uc = uc; }

    static UndercroftConfig.Loot pick(List<UndercroftConfig.Loot> table) {
        int total = table.stream().mapToInt(UndercroftConfig.Loot::weight).sum();
        if (total <= 0) return null;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (UndercroftConfig.Loot l : table) {
            r -= l.weight();
            if (r < 0) return l;
        }
        return table.getLast();
    }

    /** Item for an entry, amount multiplied by mult (deeper rings), or null for non-item entries. */
    ItemStack item(UndercroftConfig.Loot l, double mult) {
        int amount = (int) Math.max(1, Math.round(ThreadLocalRandom.current().nextInt(l.min(), l.max() + 1) * mult));
        GunService guns = Services.find(GunService.class).orElse(null);
        return switch (l.type()) {
            case "contraband" -> uc.items.create(l.id(), amount);
            case "parts" -> guns == null ? null : guns.createParts(amount);
            case "ammo" -> guns == null ? null : guns.createAmmo(l.id(), amount).orElse(null);
            case "gun" -> guns == null ? null : guns.createGun(l.id()).orElse(null);
            default -> null;
        };
    }

    /** Rolls items for a supply drop chest. */
    List<ItemStack> supply(int ring) {
        List<ItemStack> out = new ArrayList<>();
        int rolls = uc.cfg().dropRolls + Math.max(0, ring - 1);
        for (int i = 0; i < rolls; i++) {
            UndercroftConfig.Loot l = pick(uc.cfg().dropLoot);
            if (l == null) break;
            ItemStack it = item(l, 1.0 + 0.5 * (ring - 1));
            if (it != null) out.add(it);
        }
        return out;
    }

    /** Opens a tech crate for a player: items, Tokens or console commands. */
    void openCrate(Player p) {
        for (int i = 0; i < uc.cfg().crateRolls; i++) {
            UndercroftConfig.Loot l = pick(uc.cfg().crateLoot);
            if (l == null) return;
            switch (l.type()) {
                case "tokens" -> {
                    long n = ThreadLocalRandom.current().nextLong(l.min(), l.max() + 1L);
                    Services.find(EconomyService.class).ifPresent(e -> e.give(p.getUniqueId(), Currency.TOKENS, n, "undercroft:crate"));
                    Text.send(p, "<gray>Inside: <aqua>" + n + " Tokens");
                }
                case "command" -> {
                    if (!l.command().isBlank()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), l.command().replace("{player}", p.getName()));
                        Text.send(p, "<gray>Inside: " + (l.label().isBlank() ? "<white>something rare" : l.label()));
                    }
                }
                default -> {
                    ItemStack it = item(l, 1.0);
                    if (it == null) continue;
                    uc.give(p, it);
                    p.sendMessage(Text.mm(Text.PREFIX + "<gray>Inside: <white>" + it.getAmount() + "x </white>").append(it.effectiveName()));
                }
            }
        }
    }
}
