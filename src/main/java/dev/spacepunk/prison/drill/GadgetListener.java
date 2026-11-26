package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.api.GunService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Exo-Rig gadgets: sneak + swap hands (F) fires the gadget module fitted on the Exo-Rig, on a cooldown. */
final class GadgetListener implements Listener {
    private final DrillManager drills;
    private final Map<UUID, Long> readyAt = new HashMap<>();

    GadgetListener(DrillManager drills) { this.drills = drills; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        if (isGun(p.getInventory().getItemInMainHand()) || isGun(e.getOffHandItem())) return;
        String gadget = drills.gadget(p.getUniqueId());
        if (gadget == null) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        long ready = readyAt.getOrDefault(p.getUniqueId(), 0L);
        if (now < ready) {
            flash(p, "<gray>Gadget recharging: <white>" + (int) Math.ceil((ready - now) / 1000.0) + "s");
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.5f, 1.6f);
            return;
        }
        if (!fire(p, gadget)) return;
        double base = drills.cfg().gadgetCooldowns.getOrDefault(gadget, 30.0);
        double mult = drills.gearTotals(p.getUniqueId(), DrillManager.RIG_EXO).m("gadget-cooldown");
        readyAt.put(p.getUniqueId(), now + Math.round(Math.max(1, base * mult) * 1000));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { readyAt.remove(e.getPlayer().getUniqueId()); }

    private boolean fire(Player p, String gadget) {
        Location at = p.getLocation();
        switch (gadget) {
            case "gadget-dash" -> {
                Vector dir = at.getDirection().setY(0);
                if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
                p.setVelocity(dir.normalize().multiply(1.5).setY(0.45));
                p.setFallDistance(0);
                p.getWorld().spawnParticle(Particle.CLOUD, at, 12, 0.3, 0.1, 0.3, 0.05);
                p.getWorld().playSound(at, Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.2f);
                flash(p, "<#c8743a>Grapple dash");
            }
            case "gadget-scan" -> {
                int n = 0;
                for (Entity en : p.getNearbyEntities(24, 12, 24)) {
                    if (!(en instanceof LivingEntity le) || en.equals(p)) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 6, 0, false, false, false));
                    if (en instanceof Player) n++;
                }
                p.getWorld().spawnParticle(Particle.SONIC_BOOM, at.clone().add(0, 1, 0), 1);
                p.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
                flash(p, "<#5fb4c8>Scanner pulse: <white>" + n + "</white> player" + (n == 1 ? "" : "s") + " in range");
            }
            case "gadget-stim" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 6, 1, false, true, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 20, 0, false, true, true));
                p.getWorld().playSound(at, Sound.ITEM_HONEY_BOTTLE_DRINK, 0.9f, 0.8f);
                flash(p, "<#7fd65a>Stim injected");
            }
            case "gadget-cloak" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 5, 0, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 5, 1, false, false, true));
                p.getWorld().spawnParticle(Particle.PORTAL, at.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
                p.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.0f);
                flash(p, "<#b07cff>Phase cloak engaged");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static boolean isGun(ItemStack item) {
        return Services.find(GunService.class).map(g -> g.gunId(item).isPresent()).orElse(false);
    }

    private static void flash(Player p, String msg) {
        HudService hud = Services.find(HudService.class).orElse(null);
        if (hud != null) hud.flash(p, msg);
        else p.sendActionBar(Text.mm(msg));
    }
}
