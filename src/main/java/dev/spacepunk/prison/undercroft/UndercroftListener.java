package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.api.GunService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.GunHitEvent;
import dev.spacepunk.prison.api.event.OreSellEvent;
import dev.spacepunk.prison.api.event.PlayerWarpEvent;
import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.event.UndercroftKillEvent;
import dev.spacepunk.prison.data.ProfileLoadedEvent;
import dev.spacepunk.prison.undercroft.Contraband.Id;
import dev.spacepunk.prison.undercroft.Contraband.Kind;
import dev.spacepunk.prison.undercroft.UndercroftConfig.LabSite;
import dev.spacepunk.prison.undercroft.UndercroftConfig.Ring;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Every Undercroft rule: PvP, grace, combat tag, deaths, rings, hot ore, labs, the gate and contraband use. */
final class UndercroftListener implements Listener {
    static final String BUILD = "spacepunk.undercroft.build";
    static final String BYPASS = "spacepunk.undercroft.bypass";

    private final UndercroftManager uc;
    private final Checkpoint checkpoint;
    private final LabManager labs;
    private final SupplyDrops drops;
    private final Loot loot;
    private final Set<UUID> diedHere = new HashSet<>();
    /** killer|victim -> last kill time, for the repeat-kill rule. */
    private final Map<String, Long> recentKills = new HashMap<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private Set<Long> veins = new HashSet<>();

    UndercroftListener(UndercroftManager uc, Checkpoint checkpoint, LabManager labs, SupplyDrops drops, Loot loot) {
        this.uc = uc;
        this.checkpoint = checkpoint;
        this.labs = labs;
        this.drops = drops;
        this.loot = loot;
        reindexVeins();
    }

    void reindexVeins() {
        Set<Long> s = new HashSet<>();
        for (UndercroftStore.Vein v : uc.store.veins) s.add(pack(v.x(), v.y(), v.z()));
        veins = s;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private boolean in(Location l) { return uc.world.isWorld(l.getWorld()); }

    private void notice(Player p, String mm) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && now - last < 1500) return;
        lastNotice.put(p.getUniqueId(), now);
        Text.send(p, mm);
    }

    // ---- profiles ----

    @EventHandler
    public void onLoaded(ProfileLoadedEvent e) {
        uc.load(e.player(), e.profile());
        uc.stims.resume(e.player());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (uc.cfg().killOnLogout && uc.tagLeft(p.getUniqueId()) > 0 && uc.pvpAllowed(p.getLocation()) && !p.isDead()) {
            uc.broadcastZone("<red>" + p.getName() + " <gray>logged out mid-fight and was left for the rats.");
            p.setHealth(0);
        }
        uc.unload(p);
        lastNotice.remove(p.getUniqueId());
    }

    // ---- PvP ----

    private static Player attacker(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    /** Null if allowed, else why not (shown to the attacker). */
    private String pvpBlock(Player attacker, Player victim) {
        boolean a = uc.pvpAllowed(attacker.getLocation()), v = uc.pvpAllowed(victim.getLocation());
        if (!a || !v) return in(attacker.getLocation()) || in(victim.getLocation()) ? "<red>No fighting at the checkpoint." : null;
        if (uc.graceLeft(attacker.getUniqueId()) > 0) return "<gray>You're still in your grace period.";
        if (uc.graceLeft(victim.getUniqueId()) > 0) return "<gray>" + victim.getName() + " just came in. They're in grace.";
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(e);
        if (attacker == null || attacker == victim) return;
        if (!in(attacker.getLocation()) && !in(victim.getLocation())) return;
        String block = pvpBlock(attacker, victim);
        if (block != null || !uc.pvpAllowed(victim.getLocation())) {
            e.setCancelled(true);
            if (block != null) notice(attacker, block);
            return;
        }
        uc.tag(attacker);
        uc.tag(victim);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onGunHit(GunHitEvent e) {
        if (e.target() instanceof Player victim && (in(victim.getLocation()) || in(e.shooter().getLocation()))) {
            String block = pvpBlock(e.shooter(), victim);
            if (block != null) {
                e.setCancelled(true);
                notice(e.shooter(), block);
                return;
            }
        }
        e.damage(e.damage() * uc.stims.gunDamageMultiplier(e.shooter()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!in(p.getLocation()) || uc.tagLeft(p.getUniqueId()) <= 0 || p.hasPermission(BYPASS)) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (uc.cfg().allowedCommands.contains(label)) return;
        e.setCancelled(true);
        Text.send(p, "<red>You're in combat. Commands are locked for <white>" + (uc.tagLeft(p.getUniqueId()) / 1000 + 1) + "s</white>.");
    }

    // ---- death + respawn ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (!in(victim.getLocation())) return;
        diedHere.add(victim.getUniqueId());
        int ring = Math.max(1, uc.world.ringAt(victim.getLocation()));
        PlayerData vd = uc.data(victim);
        Player killer = victim.getKiller();
        uc.untag(victim.getUniqueId());
        if (vd != null) {
            vd.deaths++;
            vd.streak = 0;
        }
        if (killer == null || killer == victim) {
            if (vd != null) { vd.bounty = 0; uc.save(victim); }
            return;
        }
        String key = killer.getUniqueId() + "|" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = recentKills.get(key);
        boolean repeat = last != null && now - last < uc.cfg().repeatCooldownMinutes * 60_000L;
        recentKills.put(key, now);
        recentKills.values().removeIf(t -> now - t > uc.cfg().repeatCooldownMinutes * 60_000L);
        long bounty = vd == null ? 0 : vd.bounty;
        if (!repeat) {
            long value = uc.scale(uc.grade(victim), uc.cfg().tagValue) + bounty;
            e.getDrops().add(uc.items.bountyTag(victim.getName(), value));
        }
        if (vd != null) { vd.bounty = 0; uc.save(victim); }
        PlayerData kd = uc.data(killer);
        if (kd != null) {
            kd.kills++;
            kd.streak++;
            if (!repeat) kd.bounty += uc.scale(uc.grade(killer), uc.cfg().bountyPerKill);
            uc.save(killer);
        }
        String weapon = Services.find(GunService.class).flatMap(g -> g.lastHitBy(victim.getUniqueId())).orElse("melee");
        String streak = kd != null && kd.streak >= 3 ? " <#ff5040>(" + kd.streak + " kill streak, bounty " + dev.spacepunk.prison.util.Numbers.shortFormat(kd.bounty) + ")" : "";
        uc.broadcastZone("<red>☠ <white>" + killer.getName() + "</white> <gray>dropped <white>" + victim.getName() + "</white>"
                + (weapon.equals("melee") ? "" : " <dark_gray>[" + weapon.replace('_', ' ') + "]") + streak);
        uc.plugin().getServer().getPluginManager().callEvent(new UndercroftKillEvent(killer, victim, ring, !repeat, weapon));
        if (!repeat) Services.find(GangService.class).ifPresent(g -> g.addContractProgress(killer.getUniqueId(), "pvp_kills", 1));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (diedHere.remove(p.getUniqueId())) {
            e.setRespawnLocation(uc.exitLocation(p));
            uc.plugin().getServer().getScheduler().runTask(uc.plugin(), () -> {
                if (p.isOnline()) Text.send(p, "<gray>The guards dragged what was left of you back upstairs.");
            });
        }
        uc.plugin().getServer().getScheduler().runTaskLater(uc.plugin(), () -> { if (p.isOnline()) uc.stims.resume(p); }, 2L);
    }

    // ---- movement: rings, grace, gate ----

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        if (!in(to)) return;
        Player p = e.getPlayer();
        int a = uc.world.ringAt(from), b = uc.world.ringAt(to);
        if (a == b || p.hasPermission(BYPASS)) return;
        if (b > a) {
            Ring r = uc.cfg().ring(b);
            if (r != null && uc.prestige(p) < r.prestige()) {
                e.setTo(from);
                notice(p, "<red>" + Text.MM.stripTags(r.name()) + " needs Prestige " + r.prestige() + ". The regulars down there would eat you.");
                return;
            }
            if (a == 0) {
                uc.giveGrace(p);
                long g = uc.graceLeft(p.getUniqueId()) / 1000;
                Text.send(p, "<#ff5040>You're in the Undercroft. PvP is on.</#ff5040> <gray>Grace: <white>" + g + "s</white>. "
                        + "Anything you carry drops if you die.");
            }
            if (r != null) p.showTitle(net.kyori.adventure.title.Title.title(Text.mm(r.name()),
                    Text.mm("<gray>Ring " + r.number() + (r.prestige() > 0 ? " · Prestige " + r.prestige() : ""))));
        } else if (b == 0 && uc.tagLeft(p.getUniqueId()) > 0) {
            e.setTo(from);
            notice(p, "<red>The guards won't let you in mid-fight.");
        } else if (b == 0) {
            Text.send(p, "<gray>You're back at the " + uc.cfg().checkpointName + ". Use the <white>gate terminal</white> or <white>/undercroft leave</white> to get out.");
            uc.endGrace(p.getUniqueId());
        }
    }

    /** Nothing leaves the Undercroft except through the checkpoint scanner. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!in(e.getFrom()) || (e.getTo() != null && in(e.getTo()))) return;
        if (checkpoint.leaving.contains(p.getUniqueId()) || p.hasPermission(BYPASS)) return;
        e.setCancelled(true);
        Location dest = e.getTo();
        if (uc.world.ringAt(p.getLocation()) == 0) {
            uc.plugin().getServer().getScheduler().runTask(uc.plugin(), () -> checkpoint.request(p, dest));
        } else {
            notice(p, "<red>No shortcuts out of the Undercroft. Get back to the " + uc.cfg().checkpointName + " (west).");
        }
    }

    /** Station warps out of the Undercroft go through the checkpoint scanner first. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWarp(PlayerWarpEvent e) {
        Player p = e.player();
        if (!in(e.from()) || in(e.to()) || checkpoint.leaving.contains(p.getUniqueId()) || p.hasPermission(BYPASS)) return;
        Location dest = e.to();
        if (uc.world.ringAt(p.getLocation()) == 0) {
            e.cancel("<gray>Checkpoint scan first.");
            uc.plugin().getServer().getScheduler().runTask(uc.plugin(), () -> checkpoint.request(p, dest));
        } else {
            e.cancel("<red>No shortcuts out of the Undercroft. Get back to the " + uc.cfg().checkpointName + " (west).");
        }
    }

    // ---- blocks: hot ore, labs, protection ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!in(b.getLocation())) return;
        Player p = e.getPlayer();
        if (veins.contains(pack(b.getX(), b.getY(), b.getZ()))) {
            e.setCancelled(true);
            int ringNo = uc.world.ringAt(b.getLocation());
            Ring r = uc.cfg().ring(ringNo);
            if (r == null || b.getType() != r.oreBlock()) return;
            mineVein(p, b, r);
            return;
        }
        if (!p.hasPermission(BUILD)) e.setCancelled(true);
    }

    private void mineVein(Player p, Block b, Ring r) {
        Material ore = r.oreBlock();
        b.setType(uc.cfg().depletedBlock, false);
        ItemStack hot = uc.items.hotOre(r.hotOre(), 1);
        if (hot != null) uc.give(p, hot);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Map.Entry<String, Double> ing : r.ingredients().entrySet()) {
            if (rng.nextDouble() < ing.getValue()) {
                ItemStack it = uc.items.ingredient(ing.getKey(), 1);
                if (it != null) {
                    uc.give(p, it);
                    notice(p, "<gray>Found some " + uc.cfg().ingredients.get(ing.getKey()).name() + "<gray> in the vein.");
                }
            }
        }
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().spawnParticle(Particle.LAVA, c, 4, 0.2, 0.2, 0.2, 0);
        p.playSound(Sound.sound(Key.key("block.nether_gold_ore.break"), Sound.Source.BLOCK, 1f, 0.8f));
        uc.plugin().getServer().getScheduler().runTaskLater(uc.plugin(), () -> {
            if (b.getType() == uc.cfg().depletedBlock) b.setType(ore, false);
        }, uc.cfg().regrowSeconds * 20L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (in(e.getBlock().getLocation()) && !e.getPlayer().hasPermission(BUILD)) e.setCancelled(true);
        if (uc.items.read(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // Using Undercroft items (any world).
        if (e.getHand() == EquipmentSlot.HAND && e.getAction().isRightClick()) {
            ItemStack it = p.getInventory().getItemInMainHand();
            Id id = uc.items.read(it);
            if (id != null) {
                e.setCancelled(true);
                if (id.kind() == Kind.STIM) {
                    if (uc.stims.take(p, id.id())) consumeOne(p);
                    return;
                }
                if (id.kind() == Kind.CRATE) {
                    if (uc.inUndercroft(p.getLocation())) {
                        notice(p, "<red>Crack it open somewhere safe. Smuggle it out first.");
                        return;
                    }
                    consumeOne(p);
                    p.playSound(Sound.sound(Key.key("block.barrel.open"), Sound.Source.PLAYER, 1f, 0.8f));
                    loot.openCrate(p);
                    return;
                }
                if (e.getClickedBlock() == null) return;
            }
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (!in(b.getLocation())) return;
        if (e.getHand() != EquipmentSlot.HAND) {
            if (!drops.isDrop(b) && !p.hasPermission(BUILD)) e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            return;
        }
        LabSite lab = labs.labAt(b);
        if (lab != null) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            labs.open(p, lab);
            return;
        }
        if (b.equals(uc.world.terminal(b.getWorld()))) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            checkpoint.request(p, null);
            return;
        }
        if (drops.isDrop(b)) return;
        if (!p.hasPermission(BUILD)) e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    private static void consumeOne(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!in(e.getLocation())) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r != CreatureSpawnEvent.SpawnReason.CUSTOM && r != CreatureSpawnEvent.SpawnReason.COMMAND
                && r != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { if (in(e.getLocation())) e.blockList().clear(); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { if (in(e.getBlock().getLocation())) e.blockList().clear(); }

    // ---- economy hooks ----

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSell(OreSellEvent e) {
        double m = uc.stims.sellMultiplier(e.player());
        if (m != 1.0) e.multiplier(e.multiplier() * m);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (uc.items.read(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
