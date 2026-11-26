package dev.spacepunk.prison.guns;

import dev.spacepunk.prison.api.GunService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.UndercroftService;
import dev.spacepunk.prison.api.event.GunHitEvent;
import dev.spacepunk.prison.guns.GunsConfig.AmmoDef;
import dev.spacepunk.prison.guns.GunsConfig.GunDef;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Firing, reloading, scopes and the ammo readout. Also the {@link GunService} other modules use. */
public final class GunManager implements GunService {
    private final Plugin plugin;
    private GunsConfig cfg;
    private final GunItems items;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Hit> lastHits = new HashMap<>();
    private final Set<UUID> scoped = new HashSet<>();

    private static final class State {
        long nextShotTick;
        BukkitTask reload;
        int reloadSlot = -1;
        long reloadStart, reloadEnd;
    }

    private record Hit(String gunId, long at) {}

    GunManager(Plugin plugin, GunsConfig cfg, GunItems items) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.items = items;
    }

    void config(GunsConfig cfg) {
        this.cfg = cfg;
        items.config(cfg);
    }

    GunsConfig cfg() { return cfg; }
    GunItems items() { return items; }

    private State state(Player p) { return states.computeIfAbsent(p.getUniqueId(), k -> new State()); }

    void forget(Player p) {
        cancelReload(p, false);
        unscope(p);
        states.remove(p.getUniqueId());
    }

    // ---- zones ----

    /** True if guns may fire here at all. */
    boolean canFire(Location loc) {
        if (cfg.extraWorlds.contains(loc.getWorld().getName())) return true;
        Optional<UndercroftService> uc = Services.find(UndercroftService.class);
        if (uc.isPresent()) return uc.get().pvpAllowed(loc) || (cfg.hitMobsAnywhere && !uc.get().inUndercroft(loc));
        return cfg.hitMobsAnywhere;
    }

    private boolean canHitPlayers(Location loc) {
        if (cfg.extraWorlds.contains(loc.getWorld().getName())) return true;
        return Services.find(UndercroftService.class).map(u -> u.pvpAllowed(loc)).orElse(false);
    }

    // ---- firing ----

    /** Trigger pull from the listener. */
    void trigger(Player p, ItemStack gunItem) {
        GunDef g = items.gun(gunItem);
        if (g == null) return;
        State s = state(p);
        if (s.reload != null) return;
        long now = p.getWorld().getGameTime();
        if (now < s.nextShotTick) return;
        if (!canFire(p.getLocation())) {
            s.nextShotTick = now + 20;
            flash(p, "<red>Safety's on. Guns only fire in PvP zones.");
            p.playSound(Sound.sound(Key.key("block.lever.click"), Sound.Source.PLAYER, 0.6f, 0.6f));
            return;
        }
        int loaded = items.loaded(gunItem);
        if (loaded <= 0) {
            s.nextShotTick = now + 6;
            if (!startReload(p)) {
                p.playSound(Sound.sound(Key.key("block.dispenser.fail"), Sound.Source.PLAYER, 0.7f, 1.6f));
                flash(p, "<red>Click. No " + ammoName(g) + "<red> left.");
            }
            return;
        }
        s.nextShotTick = now + g.fireDelay() + (long) (g.burst() - 1) * g.burstDelay();
        int slot = p.getInventory().getHeldItemSlot();
        fireOnce(p, g, slot);
        for (int i = 1; i < g.burst(); i++) {
            int delay = Math.max(1, g.burstDelay() * i);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getInventory().getHeldItemSlot() == slot && state(p).reload == null) fireOnce(p, g, slot);
            }, delay);
        }
    }

    private void fireOnce(Player p, GunDef g, int slot) {
        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || !g.id().equals(items.gunId(item))) return;
        int loaded = items.loaded(item);
        if (loaded <= 0) return;
        if (!canFire(p.getLocation())) return;
        items.setLoaded(item, g, loaded - 1);

        Location eye = p.getEyeLocation();
        World w = p.getWorld();
        w.playSound(Sound.sound(Key.key(g.sound()), Sound.Source.PLAYER, 1.2f, g.pitch()), eye.getX(), eye.getY(), eye.getZ());
        Vector base = eye.getDirection();
        Location muzzle = eye.clone().add(base.clone().multiply(0.8));
        w.spawnParticle(Particle.SMOKE, muzzle, 3, 0.03, 0.03, 0.03, 0.01);
        w.spawnParticle(Particle.CRIT, muzzle, 2, 0.02, 0.02, 0.02, 0.05);

        double spread = p.isSneaking() ? g.sneakSpread() : g.spread();
        if (!p.isOnGround()) spread *= 1.6;
        boolean hitAny = false, headAny = false;
        for (int i = 0; i < g.pellets(); i++) {
            Vector dir = spread(base, spread);
            ShotResult r = trace(p, g, eye, dir);
            hitAny |= r.hit;
            headAny |= r.head;
        }
        if (hitAny) {
            p.playSound(Sound.sound(Key.key(headAny ? "block.note_block.bell" : "entity.arrow.hit_player"), Sound.Source.PLAYER, 0.6f, headAny ? 1.8f : 1.2f));
            if (headAny) flash(p, "<#ff5040><b>HEADSHOT");
        }
        recoil(p, g.recoil() * (p.isSneaking() ? 0.6 : 1.0));
        if (loaded - 1 <= 0) {
            p.playSound(Sound.sound(Key.key("block.iron_trapdoor.close"), Sound.Source.PLAYER, 0.5f, 1.8f));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getInventory().getHeldItemSlot() == slot) startReload(p);
            }, 4L);
        }
    }

    private record ShotResult(boolean hit, boolean head) {}

    private ShotResult trace(Player p, GunDef g, Location eye, Vector dir) {
        World w = eye.getWorld();
        Location from = eye.clone();
        double left = g.range();
        Set<UUID> done = new HashSet<>();
        done.add(p.getUniqueId());
        boolean hit = false, head = false;
        int targets = 0;
        double travelled = 0;
        Location end = eye.clone().add(dir.clone().multiply(g.range()));
        while (left > 0.1 && targets <= g.pierce()) {
            RayTraceResult r = w.rayTrace(from, dir, left, FluidCollisionMode.NEVER, true, 0.15,
                    e -> e instanceof LivingEntity && !(e instanceof ArmorStand) && !done.contains(e.getUniqueId())
                            && !(e instanceof Player pl && (pl.getGameMode() == org.bukkit.GameMode.SPECTATOR)));
            if (r == null) break;
            Vector pos = r.getHitPosition();
            double seg = pos.distance(from.toVector());
            travelled += seg;
            Location at = pos.toLocation(w);
            if (r.getHitEntity() instanceof LivingEntity target) {
                done.add(target.getUniqueId());
                targets++;
                boolean isHead = target instanceof Player && pos.getY() >= target.getEyeLocation().getY() - 0.3;
                if (damage(p, g, target, isHead, travelled)) {
                    hit = true;
                    head |= isHead;
                    w.spawnParticle(Particle.DAMAGE_INDICATOR, at, 2, 0.1, 0.1, 0.1, 0.05);
                }
                left = g.range() - travelled;
                from = at.clone().add(dir.clone().multiply(0.05));
                end = at;
                continue;
            }
            Block b = r.getHitBlock();
            if (b != null) w.spawnParticle(Particle.BLOCK, at, 6, 0.05, 0.05, 0.05, 0.1, b.getBlockData());
            end = at;
            break;
        }
        if (cfg.tracers) tracer(eye, end, g);
        return new ShotResult(hit, head);
    }

    private boolean damage(Player shooter, GunDef g, LivingEntity target, boolean head, double distance) {
        if (target instanceof Player && !canHitPlayers(target.getLocation())) return false;
        if (!(target instanceof Player) && !canFire(target.getLocation())) return false;
        double dmg = g.damage() * g.falloff(distance) * (head ? g.headshot() : 1.0);
        GunHitEvent ev = new GunHitEvent(shooter, target, g.id(), head, dmg);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || ev.damage() <= 0) return false;
        if (target instanceof Player) lastHits.put(target.getUniqueId(), new Hit(g.id(), System.currentTimeMillis()));
        target.setNoDamageTicks(0);
        double before = target.getHealth();
        target.damage(ev.damage(), shooter);
        return target.isDead() || target.getHealth() < before;
    }

    private void tracer(Location from, Location to, GunDef g) {
        Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 1.2) return;
        d.normalize();
        Particle.DustOptions dust = new Particle.DustOptions(g.tracer(), 0.5f);
        for (double t = 1.0; t < len; t += 0.6) {
            from.getWorld().spawnParticle(Particle.DUST, from.clone().add(d.clone().multiply(t)), 1, 0, 0, 0, 0, dust);
        }
    }

    private static Vector spread(Vector base, double degrees) {
        if (degrees <= 0) return base.clone();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double maxRad = Math.toRadians(degrees);
        double angle = Math.sqrt(r.nextDouble()) * maxRad;
        double around = r.nextDouble() * Math.PI * 2;
        Vector b = base.clone().normalize();
        Vector ortho1 = Math.abs(b.getY()) < 0.99 ? b.getCrossProduct(new Vector(0, 1, 0)).normalize()
                : b.getCrossProduct(new Vector(1, 0, 0)).normalize();
        Vector ortho2 = b.getCrossProduct(ortho1).normalize();
        Vector offset = ortho1.multiply(Math.cos(around)).add(ortho2.multiply(Math.sin(around))).multiply(Math.tan(angle));
        return b.add(offset).normalize();
    }

    /** Kicks the view up with a little sideways jitter. Uses the look-at packet so movement is not interrupted. */
    private void recoil(Player p, double degrees) {
        if (degrees <= 0) return;
        Location eye = p.getEyeLocation();
        float pitch = (float) Math.max(-90, eye.getPitch() - degrees);
        float yaw = (float) (eye.getYaw() + ThreadLocalRandom.current().nextDouble(-degrees / 3, degrees / 3));
        Location look = eye.clone();
        look.setPitch(pitch);
        look.setYaw(yaw);
        Vector at = eye.toVector().add(look.getDirection().multiply(8));
        p.lookAt(at.getX(), at.getY(), at.getZ(), LookAnchor.EYES);
    }

    // ---- reloading ----

    /** Starts reloading the held gun. Returns false if it is full or there is no ammo. */
    boolean startReload(Player p) {
        PlayerInventory inv = p.getInventory();
        int slot = inv.getHeldItemSlot();
        ItemStack item = inv.getItem(slot);
        GunDef g = items.gun(item);
        if (g == null) return false;
        State s = state(p);
        if (s.reload != null) return true;
        if (items.loaded(item) >= g.magazine()) {
            flash(p, "<gray>Magazine full.");
            return true;
        }
        if (count(p, g.ammo()) <= 0) {
            flash(p, "<red>No " + ammoName(g) + "<red>. Buy more at the <white>/gunsmith</white>.");
            return false;
        }
        unscope(p);
        s.reloadSlot = slot;
        long now = p.getWorld().getGameTime();
        s.reloadStart = now;
        s.reloadEnd = now + g.reloadTicks();
        p.getWorld().playSound(Sound.sound(Key.key("item.crossbow.loading_start"), Sound.Source.PLAYER, 0.8f, 0.8f),
                p.getX(), p.getY(), p.getZ());
        s.reload = plugin.getServer().getScheduler().runTaskLater(plugin, () -> finishReload(p, g, slot), g.reloadTicks());
        return true;
    }

    private void finishReload(Player p, GunDef g, int slot) {
        State s = state(p);
        s.reload = null;
        s.reloadSlot = -1;
        if (!p.isOnline()) return;
        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || !g.id().equals(items.gunId(item))) return;
        int need = g.magazine() - items.loaded(item);
        int got = take(p, g.ammo(), need);
        if (got <= 0) return;
        items.setLoaded(item, g, items.loaded(item) + got);
        p.getWorld().playSound(Sound.sound(Key.key("item.crossbow.loading_end"), Sound.Source.PLAYER, 0.9f, 1.3f),
                p.getX(), p.getY(), p.getZ());
        s.nextShotTick = p.getWorld().getGameTime() + 2;
    }

    void cancelReload(Player p, boolean message) {
        State s = states.get(p.getUniqueId());
        if (s == null || s.reload == null) return;
        s.reload.cancel();
        s.reload = null;
        s.reloadSlot = -1;
        if (message && p.isOnline()) flash(p, "<gray>Reload cancelled.");
    }

    int count(Player p, String ammoId) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && ammoId.equals(items.ammoType(it))) n += it.getAmount();
        }
        return n;
    }

    private int take(Player p, String ammoId, int want) {
        int got = 0;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && got < want; i++) {
            ItemStack it = contents[i];
            if (it == null || !ammoId.equals(items.ammoType(it))) continue;
            int use = Math.min(it.getAmount(), want - got);
            it.setAmount(it.getAmount() - use);
            got += use;
            p.getInventory().setItem(i, it.getAmount() <= 0 ? null : it);
        }
        return got;
    }

    // ---- scope ----

    void scope(Player p, GunDef g) {
        if (g.zoom() <= 0 || state(p).reload != null) return;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, g.zoom() - 1, false, false, false));
        scoped.add(p.getUniqueId());
    }

    void unscope(Player p) {
        if (scoped.remove(p.getUniqueId())) p.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    boolean isScoped(Player p) { return scoped.contains(p.getUniqueId()); }

    // ---- HUD ----

    /** Every few ticks: ammo readout and reload bar for players holding a gun. */
    void tickHud() {
        Optional<HudService> hud = Services.find(HudService.class);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            ItemStack held = p.getInventory().getItemInMainHand();
            GunDef g = items.gun(held);
            State s = states.get(p.getUniqueId());
            if (g == null) {
                if (s != null && s.reload != null) cancelReload(p, true);
                if (isScoped(p)) unscope(p);
                if (hud.isPresent()) hud.get().segment(p, "gun", null);
                continue;
            }
            if (isScoped(p) && !p.isSneaking()) unscope(p);
            String text;
            if (s != null && s.reload != null) {
                long now = p.getWorld().getGameTime();
                double t = Math.min(1.0, (now - s.reloadStart) / (double) Math.max(1, s.reloadEnd - s.reloadStart));
                int filled = (int) Math.round(t * 10);
                text = "<#c8743a>RELOADING</#c8743a> <white>" + "▮".repeat(filled) + "<dark_gray>" + "▯".repeat(10 - filled);
            } else {
                int loaded = items.loaded(held);
                String col = loaded == 0 ? "<red>" : loaded <= Math.max(1, g.magazine() / 4) ? "<#e0a030>" : "<white>";
                text = g.name() + " " + col + loaded + "</" + col.substring(1) + "<dark_gray>/" + g.magazine()
                        + "  <gray>" + count(p, g.ammo()) + " spare";
                if (!canFire(p.getLocation())) text += "  <dark_gray>[safe]";
            }
            if (hud.isPresent()) hud.get().segment(p, "gun", text);
            else p.sendActionBar(Text.mm(text));
        }
        long cutoff = System.currentTimeMillis() - cfg.hitMemorySeconds * 1000L;
        lastHits.values().removeIf(h -> h.at < cutoff);
    }

    private void flash(Player p, String mm) {
        Optional<HudService> hud = Services.find(HudService.class);
        if (hud.isPresent()) hud.get().flash(p, mm);
        else p.sendActionBar(Text.mm(mm));
    }

    private String ammoName(GunDef g) {
        AmmoDef a = cfg.ammo.get(g.ammo());
        return a == null ? g.ammo() : a.name();
    }

    // ---- GunService ----

    @Override public Set<String> gunIds() { return Set.copyOf(cfg.guns.keySet()); }
    @Override public Set<String> ammoIds() { return Set.copyOf(cfg.ammo.keySet()); }

    @Override
    public Optional<ItemStack> createGun(String gunId) {
        GunDef g = cfg.guns.get(gunId);
        return g == null ? Optional.empty() : Optional.of(items.gun(g, g.magazine()));
    }

    @Override
    public Optional<ItemStack> createAmmo(String ammoId, int amount) {
        AmmoDef a = cfg.ammo.get(ammoId);
        return a == null ? Optional.empty() : Optional.of(items.ammo(a, amount));
    }

    @Override public ItemStack createParts(int amount) { return items.parts(amount); }

    @Override public Optional<String> gunId(ItemStack item) { return Optional.ofNullable(items.gunId(item)); }

    @Override
    public Optional<String> lastHitBy(UUID victim) {
        Hit h = lastHits.get(victim);
        if (h == null || h.at < System.currentTimeMillis() - cfg.hitMemorySeconds * 1000L) return Optional.empty();
        return Optional.of(h.gunId);
    }
}
