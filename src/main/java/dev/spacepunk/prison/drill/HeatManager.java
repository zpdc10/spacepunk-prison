package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Heat, overheat stalls, venting and speed bursts (Overclock, vent burst). State is transient.
 * Speed effects are player attribute modifiers on block_break_speed, keyed so they can be cleaned up.
 */
public final class HeatManager {
    static final NamespacedKey STALL = new NamespacedKey(DrillItems.NS, "drill_stall");
    static final NamespacedKey OVERCLOCK = new NamespacedKey(DrillItems.NS, "drill_overclock");
    static final NamespacedKey VENT = new NamespacedKey(DrillItems.NS, "drill_vent");
    private static final List<NamespacedKey> ALL_KEYS = List.of(STALL, OVERCLOCK, VENT);
    private static final int BAR = 20;

    private final DrillManager drills;
    private final Map<UUID, State> states = new HashMap<>();

    public HeatManager(DrillManager drills) { this.drills = drills; }

    static final class State {
        double heat;
        long lastGain;
        long stallUntil;
        long ventReadyAt;
        long overclockUntil;
        long burstUntil;
        String flash;
        long flashUntil;
        boolean shownLastTick;
    }

    private State state(Player p) { return states.computeIfAbsent(p.getUniqueId(), k -> new State()); }

    public void remove(Player p) {
        states.remove(p.getUniqueId());
        clearModifiers(p);
    }

    public boolean stalled(Player p) {
        State s = states.get(p.getUniqueId());
        return s != null && s.stallUntil > System.currentTimeMillis();
    }

    public boolean overclocked(Player p) {
        State s = states.get(p.getUniqueId());
        return s != null && s.overclockUntil > System.currentTimeMillis();
    }

    public double heat(Player p) {
        State s = states.get(p.getUniqueId());
        return s == null ? 0 : s.heat;
    }

    /** Short message shown on the action bar next to the heat gauge for a moment. */
    public void flash(Player p, String miniMessage) {
        State s = state(p);
        s.flash = miniMessage;
        s.flashUntil = System.currentTimeMillis() + 1800;
    }

    /** Adds heat. Amount already includes deck and module multipliers except overclock, applied here. */
    public void add(Player p, double amount) {
        if (amount <= 0) return;
        DrillMath.Effective eff = drills.effective(p.getUniqueId());
        if (eff == null) return;
        State s = state(p);
        long now = System.currentTimeMillis();
        if (s.stallUntil > now) return;
        if (s.overclockUntil > now) amount *= drills.cfg().enchant(Enchant.OVERCLOCK).p("heat-multiplier", 1.5);
        s.heat += amount;
        s.lastGain = now;
        if (s.heat >= eff.heatCapacity) overheat(p, s, eff);
    }

    public void set(Player p, double heat) {
        DrillMath.Effective eff = drills.effective(p.getUniqueId());
        State s = state(p);
        s.heat = Math.max(0, heat);
        s.lastGain = System.currentTimeMillis();
        if (eff != null && s.heat >= eff.heatCapacity) overheat(p, s, eff);
    }

    private void overheat(Player p, State s, DrillMath.Effective eff) {
        DrillConfig cfg = drills.cfg();
        long now = System.currentTimeMillis();
        long ms = Math.round(cfg.heat.stallSeconds() * eff.stallMultiplier * 1000);
        s.heat = eff.heatCapacity;
        s.stallUntil = now + ms;
        s.overclockUntil = 0;
        s.burstUntil = 0;
        removeModifier(p, OVERCLOCK);
        removeModifier(p, VENT);
        addModifier(p, STALL, -1.0);
        DrillData d = drills.data(p.getUniqueId());
        if (d != null) {
            d.overheats++;
            drills.markStatsDirty(p.getUniqueId());
        }
        p.showTitle(Title.title(Text.mm("<red><bold>OVERHEATED"), Text.mm("<gray>Drill stalled. Vent before the limit next time."),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(Math.min(ms, 2500)), Duration.ofMillis(400))));
        p.playSound(p.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 0.5f);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, eyeFront(p), 20, 0.3, 0.3, 0.3, 0.02);
    }

    /** Q while holding the drill. */
    public void vent(Player p) {
        DrillMath.Effective eff = drills.effective(p.getUniqueId());
        if (eff == null) return;
        DrillConfig.Heat h = drills.cfg().heat;
        State s = state(p);
        long now = System.currentTimeMillis();
        if (s.stallUntil > now) {
            flash(p, "<red>Stalled: wait for the drill to restart");
            return;
        }
        if (s.ventReadyAt > now) {
            flash(p, "<gray>Vents recharging <white>" + DrillMath.fmt((s.ventReadyAt - now) / 1000.0) + "s");
            return;
        }
        double frac = s.heat / eff.heatCapacity;
        if (frac < h.ventMinFraction()) {
            flash(p, "<gray>Not hot enough to vent (needs <white>" + Math.round(h.ventMinFraction() * 100) + "%</white>)");
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.2f);
            return;
        }
        boolean perfect = frac >= h.perfectMin() && frac <= h.perfectMax();
        double bonus = h.burstSpeed() * frac * (perfect ? h.perfectMultiplier() : 1.0);
        double seconds = (h.burstBase() + h.burstPerFraction() * frac) * eff.burstMultiplier;
        s.heat = 0;
        s.ventReadyAt = now + Math.round(h.ventCooldown() * 1000);
        s.burstUntil = now + Math.round(seconds * 1000);
        removeModifier(p, VENT);
        addModifier(p, VENT, bonus);

        Location at = eyeFront(p);
        p.getWorld().spawnParticle(Particle.CLOUD, at, 25, 0.4, 0.3, 0.4, 0.05);
        p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, perfect ? 1.4f : 0.9f);
        if (perfect) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 30, 0.5, 0.4, 0.5, 0.2);
            flash(p, "<gold><bold>PERFECT VENT</bold></gold> <yellow>+" + Math.round(bonus * 100) + "% speed for " + DrillMath.fmt(seconds) + "s");
            DrillData d = drills.data(p.getUniqueId());
            if (d != null) {
                d.perfectVents++;
                drills.markStatsDirty(p.getUniqueId());
            }
        } else {
            flash(p, "<aqua>Vented</aqua> <gray>+" + Math.round(bonus * 100) + "% speed for " + DrillMath.fmt(seconds) + "s");
        }
    }

    /** Overclock proc. */
    public void overclock(Player p, double seconds) {
        State s = state(p);
        long now = System.currentTimeMillis();
        if (s.stallUntil > now) return;
        boolean fresh = s.overclockUntil <= now;
        s.overclockUntil = Math.max(s.overclockUntil, now + Math.round(seconds * 1000));
        if (fresh) {
            addModifier(p, OVERCLOCK, drills.cfg().enchant(Enchant.OVERCLOCK).p("speed-bonus", 0.75));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.8f);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, eyeFront(p), 20, 0.4, 0.4, 0.4, 0.1);
        }
        flash(p, "<#a0f0ff><bold>OVERCLOCK</bold></#a0f0ff> <gray>" + DrillMath.fmt(seconds) + "s");
    }

    /** Runs every few ticks: cooling, effect expiry, action bar. */
    public void tick(double seconds) {
        long now = System.currentTimeMillis();
        DrillConfig.Heat h = drills.cfg().heat;
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.get(p.getUniqueId());
            if (s == null) continue;
            DrillMath.Effective eff = drills.effective(p.getUniqueId());
            if (eff == null) continue;

            if (s.stallUntil != 0 && s.stallUntil <= now) {
                s.stallUntil = 0;
                s.heat = eff.heatCapacity * h.heatAfter();
                s.lastGain = now;
                removeModifier(p, STALL);
                p.playSound(p.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.8f, 1.4f);
                flash(p, "<green>Drill restarted");
            }
            if (s.overclockUntil != 0 && s.overclockUntil <= now) {
                s.overclockUntil = 0;
                removeModifier(p, OVERCLOCK);
            }
            if (s.burstUntil != 0 && s.burstUntil <= now) {
                s.burstUntil = 0;
                removeModifier(p, VENT);
            }
            if (s.stallUntil == 0 && s.heat > 0) {
                boolean idle = now - s.lastGain > h.decayDelay() * 1000;
                double rate = idle ? eff.decayPerSecond : h.activeDecayPerSecond();
                s.heat = Math.max(0, s.heat - rate * seconds);
            }
            if (s.heat > eff.heatCapacity && s.stallUntil == 0) s.heat = eff.heatCapacity;

            boolean active = s.heat > 0.01 || s.stallUntil > now || s.overclockUntil > now || s.burstUntil > now
                    || s.flashUntil > now;
            if (active && drills.holdingOwnDrill(p)) {
                p.sendActionBar(Text.mm(actionBar(s, eff, now)));
                s.shownLastTick = true;
            } else if (s.shownLastTick) {
                p.sendActionBar(Text.mm(""));
                s.shownLastTick = false;
            }
        }
    }

    private String actionBar(State s, DrillMath.Effective eff, long now) {
        DrillConfig.Heat h = drills.cfg().heat;
        StringBuilder sb = new StringBuilder();
        if (s.stallUntil > now) {
            sb.append("<red><bold>OVERHEATED</bold> <gray>restart in <white>")
                    .append(DrillMath.fmt((s.stallUntil - now) / 1000.0)).append("s");
            return sb.toString();
        }
        double frac = Math.min(1, s.heat / eff.heatCapacity);
        int filled = (int) Math.round(frac * BAR);
        int perfectLo = (int) Math.floor(h.perfectMin() * BAR);
        int perfectHi = (int) Math.ceil(h.perfectMax() * BAR);
        String color = frac < 0.5 ? "#6fcf6f" : frac < h.perfectMin() ? "#e0c040" : frac <= h.perfectMax() ? "#ff8c1a" : "#ff3030";
        sb.append("<gray>HEAT </gray>");
        for (int i = 0; i < BAR; i++) {
            if (i < filled) sb.append("<").append(color).append(">|</").append(color).append(">");
            else if (i >= perfectLo && i < perfectHi) sb.append("<#6a4a20>|</#6a4a20>");
            else sb.append("<dark_gray>|</dark_gray>");
        }
        sb.append(" <white>").append(Math.round(frac * 100)).append("%</white>");
        if (frac >= h.perfectMin() && frac <= h.perfectMax() && s.ventReadyAt <= now) sb.append(" <gold><bold>VENT [Q]</bold></gold>");
        if (s.overclockUntil > now) sb.append(" <#a0f0ff>OVERCLOCK ").append(DrillMath.fmt((s.overclockUntil - now) / 1000.0)).append("s</#a0f0ff>");
        if (s.burstUntil > now) sb.append(" <aqua>BURST ").append(DrillMath.fmt((s.burstUntil - now) / 1000.0)).append("s</aqua>");
        if (s.flashUntil > now && s.flash != null) sb.append("  <dark_gray>·</dark_gray> ").append(s.flash);
        return sb.toString();
    }

    private static Location eyeFront(Player p) {
        return p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8));
    }

    // ---- attribute modifiers ----

    private static void addModifier(Player p, NamespacedKey key, double multiplyAmount) {
        AttributeInstance inst = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (inst == null) return;
        removeModifier(p, key);
        inst.addModifier(new AttributeModifier(key, multiplyAmount, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    private static void removeModifier(Player p, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (inst == null) return;
        for (AttributeModifier m : List.copyOf(inst.getModifiers())) {
            if (key.equals(m.getKey())) inst.removeModifier(m);
        }
    }

    /** Removes every drill speed modifier (join, quit, disable). */
    public static void clearModifiers(Player p) {
        for (NamespacedKey k : ALL_KEYS) removeModifier(p, k);
    }
}
