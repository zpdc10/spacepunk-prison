package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.api.event.StimCookedEvent;
import dev.spacepunk.prison.undercroft.Contraband.Kind;
import dev.spacepunk.prison.undercroft.UndercroftConfig.Ingredient;
import dev.spacepunk.prison.undercroft.UndercroftConfig.LabSite;
import dev.spacepunk.prison.undercroft.UndercroftConfig.Recipe;
import dev.spacepunk.prison.undercroft.UndercroftConfig.Stim;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Drug labs: pick a recipe, stay close while it cooks, walk away with stims (minus the controller's cut). */
final class LabManager {
    private final UndercroftManager uc;
    private final Map<UUID, Cook> cooks = new HashMap<>();

    private static final class Cook {
        final LabSite lab;
        final Recipe recipe;
        final int total;
        int left;
        final BossBar bar;

        Cook(LabSite lab, Recipe recipe, BossBar bar) {
            this.lab = lab;
            this.recipe = recipe;
            this.total = recipe.seconds();
            this.left = recipe.seconds();
            this.bar = bar;
        }
    }

    LabManager(UndercroftManager uc) { this.uc = uc; }

    /** The lab whose block this is, or null. */
    LabSite labAt(Block b) {
        if (b == null || b.getType() != uc.cfg().labBlock || !uc.world.isWorld(b.getWorld())) return null;
        for (LabSite s : uc.cfg().labs.values()) {
            if (b.getX() == s.x() && b.getZ() == s.z() && Math.abs(b.getY() - (uc.cfg().floorY + 1)) <= 1) return s;
        }
        return null;
    }

    void open(Player p, LabSite lab) {
        UndercroftConfig cfg = uc.cfg();
        Menu m = new Menu(3, "<#3a3028>" + Text.MM.stripTags(lab.name()).toUpperCase());
        UndercroftStore.Controller c = uc.store.controllers.get(lab.id());
        List<String> info = new ArrayList<>();
        info.add("<gray>Ring <white>" + uc.world.ringOfX(lab.x()));
        info.add(c == null ? "<gray>Nobody controls this lab." : "<gray>Controlled by <white>" + c.name());
        if (c != null) info.add("<gray>They take <white>" + Math.round(cfg.controllerCut * 100) + "%</white> of every batch.");
        info.add("");
        info.add("<gray>Stay within <white>" + (int) cfg.cookRadius + "</white> blocks until it's done,");
        info.add("<gray>or the batch is ruined.");
        UUID busy = uc.cooking.get(lab.id());
        if (busy != null) info.add("<red>Someone is cooking here right now.");
        m.set(4, Menu.icon(Material.BREWING_STAND, "ui/info", lab.name(), info), null);
        int slot = 10;
        for (String rid : lab.recipes()) {
            Recipe r = cfg.recipes.get(rid);
            Stim s = cfg.stims.get(rid);
            if (r == null || s == null || slot > 16) continue;
            int doses = doses(lab, r);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Needs:");
            boolean ok = true;
            for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
                int have = count(p, e.getKey());
                Ingredient ing = cfg.ingredients.get(e.getKey());
                boolean enough = have >= e.getValue();
                ok &= enough;
                lore.add((enough ? "<green>✔ " : "<red>✘ ") + "<white>" + e.getValue() + "x </white>"
                        + (ing == null ? e.getKey() : ing.name()) + " <dark_gray>(" + have + ")");
            }
            lore.add("");
            lore.add("<gray>Makes <white>" + doses + "</white> doses in <white>" + r.seconds() + "s");
            lore.add(ok ? "<#c8743a>Click to start cooking" : "<red>Missing ingredients");
            ItemStack icon = Menu.withLore(uc.items.stim(rid, doses), lore);
            m.set(slot, icon, e -> {
                p.closeInventory();
                start(p, lab, r);
            });
            slot += 2;
        }
        m.set(22, Menu.icon(Material.BARRIER, "ui/close", "<gray>Close", List.of()), e -> p.closeInventory());
        m.open(p);
        p.playSound(Sound.sound(Key.key("block.brewing_stand.brew"), Sound.Source.BLOCK, 0.6f, 0.7f));
    }

    private int doses(LabSite lab, Recipe r) {
        int ring = uc.world.ringOfX(lab.x());
        return r.doses() + Math.max(0, ring - 1) * uc.cfg().bonusDoses;
    }

    private int count(Player p, String ingredient) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (uc.items.is(it, Kind.INGREDIENT, ingredient)) n += it.getAmount();
        return n;
    }

    private void take(Player p, String ingredient, int want) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length && want > 0; i++) {
            if (!uc.items.is(c[i], Kind.INGREDIENT, ingredient)) continue;
            int use = Math.min(want, c[i].getAmount());
            c[i].setAmount(c[i].getAmount() - use);
            p.getInventory().setItem(i, c[i].getAmount() <= 0 ? null : c[i]);
            want -= use;
        }
    }

    void start(Player p, LabSite lab, Recipe r) {
        if (cooks.containsKey(p.getUniqueId())) { Text.send(p, "<red>You're already cooking a batch."); return; }
        if (uc.cooking.containsKey(lab.id())) { Text.send(p, "<red>This lab is in use."); return; }
        for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
            if (count(p, e.getKey()) < e.getValue()) { Text.send(p, "<red>You're missing ingredients."); return; }
        }
        for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) take(p, e.getKey(), e.getValue());
        Stim s = uc.cfg().stims.get(r.id());
        BossBar bar = BossBar.bossBar(Text.mm("<#c070ff>Cooking " + (s == null ? r.id() : s.name())), 0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        p.showBossBar(bar);
        cooks.put(p.getUniqueId(), new Cook(lab, r, bar));
        uc.cooking.put(lab.id(), p.getUniqueId());
        Text.send(p, "<gray>The still starts to hiss. Stay close.");
        if (uc.cfg().announceCooking) {
            uc.broadcastZone("<#c070ff>Someone is cooking at " + lab.name() + "<#c070ff>. Smells like profit.");
        }
    }

    /** Every second. */
    void tick() {
        World w = uc.world.world();
        for (Map.Entry<UUID, Cook> e : new ArrayList<>(cooks.entrySet())) {
            Player p = uc.plugin().getServer().getPlayer(e.getKey());
            Cook c = e.getValue();
            if (p == null || w == null || !p.getWorld().equals(w) || p.isDead()) {
                ruin(e.getKey(), p, c, "You left the lab. The batch boiled over.");
                continue;
            }
            Location lab = uc.world.labLocation(w, c.lab);
            if (p.getLocation().distanceSquared(lab) > uc.cfg().cookRadius * uc.cfg().cookRadius) {
                ruin(e.getKey(), p, c, "You wandered off. The batch is ruined.");
                continue;
            }
            c.left--;
            c.bar.progress(Math.max(0f, Math.min(1f, 1f - c.left / (float) c.total)));
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, lab.clone().add(0, 0.8, 0), 1, 0.1, 0.1, 0.1, 0.01);
            w.spawnParticle(Particle.WITCH, lab.clone().add(0, 0.6, 0), 3, 0.2, 0.2, 0.2, 0.01);
            if (c.left <= 0) finish(p, c);
        }
    }

    private void ruin(UUID id, Player p, Cook c, String why) {
        cooks.remove(id);
        uc.cooking.remove(c.lab.id());
        if (p != null) {
            p.hideBossBar(c.bar);
            Text.send(p, "<red>" + why);
        }
    }

    private void finish(Player p, Cook c) {
        cooks.remove(p.getUniqueId());
        uc.cooking.remove(c.lab.id());
        p.hideBossBar(c.bar);
        int doses = doses(c.lab, c.recipe);
        UndercroftStore.Controller ctl = uc.store.controllers.get(c.lab.id());
        int cut = ctl == null ? 0 : (int) Math.floor(doses * uc.cfg().controllerCut);
        StimCookedEvent ev = new StimCookedEvent(p, c.lab.id(), c.recipe.id(), doses - cut, ctl == null ? null : ctl.id(), cut);
        uc.plugin().getServer().getPluginManager().callEvent(ev);
        if (ev.amount() > 0) uc.give(p, uc.items.stim(c.recipe.id(), ev.amount()));
        Stim s = uc.cfg().stims.get(c.recipe.id());
        Text.send(p, "<gray>Batch done: <white>" + ev.amount() + "x</white> " + (s == null ? c.recipe.id() : s.name())
                + (cut > 0 ? "<gray> (" + cut + " went to " + ctl.name() + "<gray>)" : "") + "<gray>.");
        p.playSound(Sound.sound(Key.key("block.brewing_stand.brew"), Sound.Source.BLOCK, 1f, 1.4f));
    }

    void cancelAll() {
        for (Map.Entry<UUID, Cook> e : new ArrayList<>(cooks.entrySet())) {
            ruin(e.getKey(), uc.plugin().getServer().getPlayer(e.getKey()), e.getValue(), "The lab shut down.");
        }
    }
}
