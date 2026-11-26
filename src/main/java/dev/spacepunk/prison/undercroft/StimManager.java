package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.undercroft.UndercroftConfig.Phase;
import dev.spacepunk.prison.undercroft.UndercroftConfig.Stim;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Taking stims, the high and the comedown, and the bonuses other systems read (sell price, gun damage). */
final class StimManager {
    private static final NamespacedKey MINING = new NamespacedKey("spacepunk", "stim_mining");
    private final UndercroftManager uc;

    StimManager(UndercroftManager uc) { this.uc = uc; }

    private UndercroftConfig cfg() { return uc.cfg(); }

    /** Right-click on a stim item. Returns true if it was taken (caller removes one). */
    boolean take(Player p, String stimId) {
        PlayerData d = uc.data(p);
        Stim s = cfg().stims.get(stimId);
        if (d == null || s == null) return false;
        if (d.stim != null && !d.comedown) {
            Text.send(p, "<red>You're already on " + name(d.stim) + "<red>. One stim at a time.");
            return false;
        }
        int carry = 0;
        if (d.stim != null && d.comedown) {
            carry = d.remaining + d.carry;
            clearPhase(p, d);
            Text.send(p, "<#e0a030>Stacking on a comedown. The next crash will be longer.");
        }
        d.stim = stimId;
        d.comedown = false;
        d.remaining = s.duration();
        d.carry = carry;
        startPhase(p, d);
        uc.save(p);
        p.getWorld().playSound(Sound.sound(Key.key("item.honey_bottle.drink"), Sound.Source.PLAYER, 1f, 0.7f), p.getX(), p.getY(), p.getZ());
        p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1.6, 0), 12, 0.3, 0.3, 0.3, 0.02);
        Text.send(p, "<gray>You take a hit of " + s.name() + "<gray>. " + Contraband.describe(s.high()) + "<gray> for <white>"
                + Contraband.mmss(s.duration()) + "</white>.");
        return true;
    }

    /** Every second. */
    void tick(Player p) {
        PlayerData d = uc.data(p);
        if (d == null || d.stim == null) return;
        Stim s = cfg().stims.get(d.stim);
        if (s == null) {
            clear(p, d);
            return;
        }
        d.remaining--;
        if (d.remaining <= 0) {
            if (!d.comedown) {
                clearPhase(p, d);
                d.comedown = true;
                d.remaining = s.comedownDuration() + d.carry;
                d.carry = 0;
                if (d.remaining > 0) {
                    startPhase(p, d);
                    Text.send(p, "<#e0a030>The " + s.name() + "<#e0a030> wears off. Here comes the comedown: "
                            + Contraband.describe(s.comedown()) + "<#e0a030> for <white>" + Contraband.mmss(d.remaining) + "</white>.");
                    p.playSound(Sound.sound(Key.key("entity.player.hurt_sweet_berry_bush"), Sound.Source.PLAYER, 0.8f, 0.6f));
                } else {
                    clear(p, d);
                }
            } else {
                clear(p, d);
                Text.send(p, "<gray>The comedown fades. You feel almost normal.");
            }
            uc.save(p);
        }
        hud(p, d);
    }

    private void hud(Player p, PlayerData d) {
        Optional<HudService> hud = Services.find(HudService.class);
        if (hud.isEmpty()) return;
        if (d.stim == null) {
            hud.get().segment(p, "stim", null);
            return;
        }
        String label = d.comedown ? "<#e0a030>↓ " + Text.MM.stripTags(name(d.stim)) + " comedown" : "<#c070ff>⚗ " + name(d.stim);
        hud.get().segment(p, "stim", label + " <white>" + Contraband.mmss(Math.max(0, d.remaining)));
    }

    /** After login: re-apply the phase with whatever time is left. */
    void resume(Player p) {
        PlayerData d = uc.data(p);
        if (d == null || d.stim == null) return;
        if (!cfg().stims.containsKey(d.stim)) {
            clear(p, d);
            return;
        }
        startPhase(p, d);
    }

    void clear(Player p, PlayerData d) {
        clearPhase(p, d);
        d.stim = null;
        d.comedown = false;
        d.remaining = 0;
        d.carry = 0;
        Services.find(HudService.class).ifPresent(h -> h.segment(p, "stim", null));
    }

    private Phase phase(PlayerData d) {
        Stim s = d.stim == null ? null : cfg().stims.get(d.stim);
        if (s == null) return Phase.NONE;
        return d.comedown ? s.comedown() : s.high();
    }

    private void startPhase(Player p, PlayerData d) {
        Phase ph = phase(d);
        int ticks = Math.max(1, d.remaining) * 20;
        for (Map.Entry<PotionEffectType, Integer> e : ph.effects().entrySet()) {
            p.addPotionEffect(new PotionEffect(e.getKey(), ticks, e.getValue() - 1, true, false, true));
        }
        AttributeInstance inst = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (inst != null) {
            removeMining(inst);
            if (ph.mining() != 0) inst.addModifier(new AttributeModifier(MINING, ph.mining(),
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    private void clearPhase(Player p, PlayerData d) {
        Phase ph = phase(d);
        for (PotionEffectType t : ph.effects().keySet()) {
            PotionEffect cur = p.getPotionEffect(t);
            // Only strip effects that look like ours (ambient, no particles), not a beacon or another plugin's.
            if (cur != null && cur.isAmbient() && !cur.hasParticles()) p.removePotionEffect(t);
        }
        AttributeInstance inst = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (inst != null) removeMining(inst);
    }

    private static void removeMining(AttributeInstance inst) {
        for (AttributeModifier m : List.copyOf(inst.getModifiers())) if (MINING.equals(m.getKey())) inst.removeModifier(m);
    }

    double sellMultiplier(Player p) {
        PlayerData d = uc.data(p);
        return d == null ? 1.0 : phase(d).sell();
    }

    double gunDamageMultiplier(Player p) {
        PlayerData d = uc.data(p);
        return d == null ? 1.0 : phase(d).gunDamage();
    }

    Optional<String> active(Player p) {
        PlayerData d = uc.data(p);
        return d == null || d.stim == null || d.comedown ? Optional.empty() : Optional.of(d.stim);
    }

    private String name(String id) {
        Stim s = cfg().stims.get(id);
        return s == null ? id : s.name();
    }
}
