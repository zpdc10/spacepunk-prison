package dev.spacepunk.prison.fishing;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.event.SeaCreatureKilledEvent;
import dev.spacepunk.prison.fishing.FishingConfig.Area;
import dev.spacepunk.prison.fishing.FishingConfig.BaitDef;
import dev.spacepunk.prison.fishing.FishingConfig.CreatureDef;
import dev.spacepunk.prison.fishing.FishingConfig.LureDef;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sea creatures: some bites pull up a hostile mob. It hunts only the angler who hooked it, only that angler can
 * hurt it, and its loot goes straight to them. Creatures are non-persistent and sink back after a while.
 */
final class SeaCreatures implements Listener {
    private final FishingModule module;
    private final NamespacedKey ownerKey;
    private final Map<UUID, Live> live = new HashMap<>();

    private static final class Live {
        final UUID owner;
        final CreatureDef def;
        final Area area;
        final long spawnedAt;
        final LivingEntity entity;

        Live(UUID owner, CreatureDef def, Area area, LivingEntity entity) {
            this.owner = owner;
            this.def = def;
            this.area = area;
            this.entity = entity;
            this.spawnedAt = System.currentTimeMillis();
        }
    }

    SeaCreatures(FishingModule module, Plugin plugin) {
        this.module = module;
        this.ownerKey = new NamespacedKey(plugin, "sea_creature_owner");
    }

    int count(UUID owner) {
        int n = 0;
        for (Live l : live.values()) if (l.owner.equals(owner)) n++;
        return n;
    }

    void spawn(Player p, CreatureDef def, Area area, Location hook) {
        Location at = hook.clone();
        at.setY(Math.max(at.getY(), module.cfg().baseY - 2.0));
        LivingEntity e;
        try {
            e = (LivingEntity) at.getWorld().spawnEntity(at, def.entity());
        } catch (RuntimeException ex) {
            module.plugin().getLogger().warning("[fishing] could not spawn " + def.id() + ": " + ex.getMessage());
            return;
        }
        if (e instanceof Slime s) s.setSize(Math.max(1, def.size()));
        if (e instanceof Phantom ph) ph.setSize(Math.max(0, def.size()));
        if (e instanceof Ageable a) a.setAdult();
        attr(e, Attribute.MAX_HEALTH, def.health());
        attr(e, Attribute.ATTACK_DAMAGE, def.damage());
        attr(e, Attribute.MOVEMENT_SPEED, def.speed());
        attr(e, Attribute.SCALE, def.scale());
        attr(e, Attribute.FOLLOW_RANGE, 40);
        e.setHealth(Math.min(def.health(), maxHealth(e)));
        e.setRemoveWhenFarAway(false);
        e.setPersistent(false);
        e.setCanPickupItems(false);
        e.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, p.getUniqueId() + "|" + def.id());
        e.setCustomNameVisible(true);
        Live l = new Live(p.getUniqueId(), def, area, e);
        live.put(e.getUniqueId(), l);
        name(l);
        if (e instanceof Mob m) m.setTarget(p);
        Vector toward = p.getLocation().toVector().subtract(at.toVector());
        if (toward.lengthSquared() > 0.01) e.setVelocity(toward.normalize().multiply(0.6).setY(0.55));
        at.getWorld().spawnParticle(Particle.SPLASH, at, 60, 0.6, 0.3, 0.6, 0.2);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.35f, 1.6f);
        Text.send(p, "<#ff5a3c>☠ A " + def.name() + " <gray>[Lv " + def.level() + "]<#ff5a3c> surfaces! <gray>Fight it for loot.");
        if (def.broadcast()) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<#ff5a3c>☠ " + p.getName() + " hooked a " + def.name()
                    + " <gray>in the " + area.colored() + "<gray>!"));
        }
    }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private static double maxHealth(LivingEntity e) {
        AttributeInstance i = e.getAttribute(Attribute.MAX_HEALTH);
        return i == null ? 20 : i.getValue();
    }

    private void name(Live l) {
        LivingEntity e = l.entity;
        double hp = Math.max(0, e.getHealth()), max = maxHealth(e);
        e.customName(Text.mm("<#ff5a3c>☠ <white>" + l.def.name() + " <gray>[Lv " + l.def.level() + "] <red>❤ "
                + Numbers.shortFormat(Math.round(Math.ceil(hp))) + "/" + Numbers.shortFormat(Math.round(max))));
    }

    /** Once a second: despawn expired or orphaned creatures, refresh names, re-aim at the owner. */
    void tick() {
        long now = System.currentTimeMillis();
        long life = module.cfg().creatureLifetime * 1000L;
        Iterator<Live> it = live.values().iterator();
        while (it.hasNext()) {
            Live l = it.next();
            Player owner = Bukkit.getPlayer(l.owner);
            boolean gone = !l.entity.isValid() || l.entity.isDead();
            boolean orphan = owner == null || !owner.getWorld().equals(l.entity.getWorld())
                    || owner.getLocation().distanceSquared(l.entity.getLocation()) > 64 * 64;
            if (gone || orphan || now - l.spawnedAt > life) {
                it.remove();
                if (!gone) {
                    l.entity.getWorld().spawnParticle(Particle.SPLASH, l.entity.getLocation(), 30, 0.4, 0.3, 0.4, 0.1);
                    l.entity.remove();
                    if (owner != null && !orphan) Text.send(owner, "<gray>The " + l.def.name() + " sank back into the deep.");
                }
                continue;
            }
            name(l);
            if (l.entity instanceof Mob m && (m.getTarget() == null || !m.getTarget().equals(owner))) m.setTarget(owner);
        }
    }

    void removeAll() {
        for (Live l : live.values()) if (l.entity.isValid()) l.entity.remove();
        live.clear();
    }

    private Live of(Entity e) { return e == null ? null : live.get(e.getUniqueId()); }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    // ------------------------------------------------------------------ rules

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Live victim = of(e.getEntity());
        if (victim != null) {
            Player p = attacker(e.getDamager());
            if (p == null || !p.getUniqueId().equals(victim.owner)) {
                e.setCancelled(true);
                if (p != null) module.hudFlash(p, "<gray>That creature is someone else's catch.");
                return;
            }
            if (module.manager().holdingOwnRod(p) && e.getDamager() == p) {
                double bonus = module.manager().effects(p.getUniqueId()).harpoon;
                if (bonus > 0) e.setDamage(e.getDamage() + bonus);
            }
            Bukkit.getScheduler().runTask(module.plugin(), () -> {
                if (victim.entity.isValid()) name(victim);
            });
            return;
        }
        Entity src = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
        Live attacker = of(src);
        if (attacker != null && !e.getEntity().getUniqueId().equals(attacker.owner)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        Live l = of(e.getEntity());
        if (l == null) return;
        if (e.getTarget() == null || !e.getTarget().getUniqueId().equals(l.owner)) {
            Player owner = Bukkit.getPlayer(l.owner);
            if (owner != null && owner.getWorld().equals(e.getEntity().getWorld())) e.setTarget(owner);
            else e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (of(e.getEntity()) == null) return;
        if (e instanceof EntityCombustByEntityEvent || e instanceof EntityCombustByBlockEvent) return;
        e.setCancelled(true); // no burning in the "sun" of a void world
    }

    @EventHandler(ignoreCancelled = true)
    public void onSplit(SlimeSplitEvent e) {
        if (of(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (of(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Live l = live.remove(e.getEntity().getUniqueId());
        if (l == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player p = Bukkit.getPlayer(l.owner);
        if (p == null) return;
        reward(p, l);
    }

    private void reward(Player p, Live l) {
        FishingManager m = module.manager();
        FishingData d = m.data(p.getUniqueId());
        if (d == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        CreatureDef def = l.def;
        long scales = def.maxScales() > def.minScales() ? r.nextLong(def.minScales(), def.maxScales() + 1) : def.minScales();
        long units = def.maxCredits() > def.minCredits() ? r.nextLong(def.minCredits(), def.maxCredits() + 1) : def.minCredits();
        long credits = Math.max(0, Math.round(units * m.gradeScale(p.getUniqueId(), l.area) * l.area.valueMult()));
        d.scales += scales;
        d.creaturesKilled++;
        m.payCredits(p, credits);
        m.addXp(p, d, def.xp());
        StringBuilder loot = new StringBuilder();
        for (FishingConfig.Drop drop : def.drops()) {
            if (r.nextDouble() >= drop.chance()) continue;
            if (drop.lure() != null) {
                LureDef lure = module.cfg().lures.get(drop.lure());
                if (lure == null) continue;
                m.give(p, d, m.items().lure(lure), "lure:" + lure.id(), 1);
                loot.append(" <dark_gray>·</dark_gray> ").append(m.items().lureName(lure)).append(" <gray>lure");
                if ("legendary".equals(lure.rarity()) || "epic".equals(lure.rarity())) {
                    Bukkit.broadcast(Text.mm(Text.PREFIX + "<#ffd23f>" + p.getName() + "<gray> pulled a " + m.items().lureName(lure)
                            + "<gray> from a " + def.name() + "!"));
                }
            } else if (drop.bait() != null) {
                BaitDef b = module.cfg().baits.get(drop.bait());
                if (b == null) continue;
                d.bait.merge(b.id(), Math.max(1, drop.amount()), Integer::sum);
                if (d.activeBait == null || d.baitCount(d.activeBait) <= 0) d.activeBait = b.id();
                loot.append(" <dark_gray>·</dark_gray> <white>").append(Math.max(1, drop.amount())).append("x ").append(b.name());
            }
        }
        String money = m.economy().map(e -> e.format(Currency.CREDITS, credits)).orElse(credits + " Credits");
        Text.send(p, "<#ff5a3c>☠ " + def.name() + " slain! <gray>" + m.formatScales(scales) + " <dark_gray>·</dark_gray> " + money
                + " <dark_gray>·</dark_gray> <dark_aqua>+" + def.xp() + " XP" + loot);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
        if (def.broadcast()) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<#ff5a3c>☠ " + p.getName() + " slew a " + def.name() + "<gray>!"));
        }
        m.changed(p);
        Bukkit.getPluginManager().callEvent(new SeaCreatureKilledEvent(p, l.area.id(), def.id()));
    }
}
