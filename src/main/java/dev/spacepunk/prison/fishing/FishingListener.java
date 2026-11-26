package dev.spacepunk.prison.fishing;

import dev.spacepunk.prison.api.GangService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.FishCaughtEvent;
import dev.spacepunk.prison.api.event.SeaCreatureKilledEvent;
import dev.spacepunk.prison.data.ProfileLoadedEvent;
import dev.spacepunk.prison.fishing.FishingConfig.Area;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Casting, bites and hooks, rod protection, and the house rules of the waters world. */
final class FishingListener implements Listener {
    private final FishingModule module;
    private final Set<UUID> diedInWaters = new HashSet<>();

    FishingListener(FishingModule module) { this.module = module; }

    private FishingManager m() { return module.manager(); }

    private FishingWorld w() { return module.world(); }

    // ------------------------------------------------------------------ profile lifecycle

    @EventHandler
    public void onLoaded(ProfileLoadedEvent e) {
        m().load(e.profile());
        Player p = e.player();
        if (w().isWaters(p.getWorld())) m().ensureRod(p);
        else m().refreshRod(p);
        m().claimWeekly(p);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        m().unload(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!w().isWaters(p.getWorld())) return;
        FishingData d = m().data(p.getUniqueId());
        if (d == null) return;
        boolean first = !d.rodGiven;
        if (m().ensureRod(p) && first) {
            Text.send(p, "<#5fb4c8>Welcome to the station waters. <gray>Here's a rod. Cast into a pool, right-click when it bites,"
                    + " and hold <white>SNEAK</white> to reel when a fish fights. <dark_gray>/fish for the market and upgrades.");
        }
    }

    // ------------------------------------------------------------------ fishing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        FishHook hook = e.getHook();
        switch (e.getState()) {
            case FISHING -> {
                if (m().reeling(p.getUniqueId())) {
                    e.setCancelled(true);
                    return;
                }
                if (!w().isWaters(p.getWorld())) return;
                int[] wait = m().waitTicks(p.getUniqueId());
                hook.setMinWaitTime(wait[0]);
                hook.setMaxWaitTime(wait[1]);
            }
            case BITE -> {
                if (w().areaAtWater(hook.getLocation()) == null) return;
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.8f);
                module.hudFlash(p, "<#ffd23f><bold>! BITE !</bold> <gray>right-click to hook it");
            }
            case CAUGHT_FISH -> {
                Entity caught = e.getCaught();
                if (caught != null) caught.remove();
                e.setExpToDrop(0);
                Area area = w().areaAtWater(hook.getLocation());
                if (area == null) {
                    Text.send(p, "<gray>Nothing worth catching lives in this water. <dark_gray>Try the station waters: /waters");
                    return;
                }
                int grade = m().grade(p.getUniqueId());
                if (grade < area.grade()) {
                    Text.send(p, "<red>Your clearance is too low to fish the " + area.name() + " (Grade " + area.grade() + ").");
                    return;
                }
                m().hooked(p, area, hook.getLocation());
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCaught(FishCaughtEvent e) {
        if (!"fish".equals(e.kind())) return;
        Services.find(GangService.class).ifPresent(g -> g.addContractProgress(e.player().getUniqueId(), "fish_caught", e.amount()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlain(SeaCreatureKilledEvent e) {
        Services.find(GangService.class).ifPresent(g -> g.addContractProgress(e.player().getUniqueId(), "sea_creatures_slain", 1));
    }

    // ------------------------------------------------------------------ rod and items

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (!p.isSneaking() || !p.getUniqueId().equals(m().items().rodOwner(e.getItem()))) return;
        e.setCancelled(true);
        module.menus().open(p, "rod");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Interaction i && (w().isMarket(i) || w().isTerminal(i))) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) module.menus().open(e.getPlayer(), w().isMarket(i) ? "market" : "waters");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHitMarket(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Interaction i && (w().isMarket(i) || w().isTerminal(i))) {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player p) module.menus().open(p, w().isMarket(i) ? "market" : "waters");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (m().items().isRod(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            module.hudFlash(e.getPlayer(), "<gray>Your rod is soulbound.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) return;
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE) return;
        ItemStack current = e.getCurrentItem(), cursor = e.getCursor();
        boolean intoTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        if ((e.isShiftClick() && m().items().isRod(current) && !intoTop) || (intoTop && m().items().isRod(cursor))) {
            e.setCancelled(true);
            return;
        }
        if (e.getHotbarButton() >= 0 && intoTop) {
            ItemStack hot = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
            if (m().items().isRod(hot)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!m().items().isRod(e.getOldCursor())) return;
        int topSize = e.getView().getTopInventory().getSize();
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (m().items().tag(e.getItem()) != null) {
            e.setCancelled(true);
            module.hudFlash(e.getPlayer(), "<gray>You'd rather sell that than eat it.");
        }
    }

    // ------------------------------------------------------------------ death and void

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (w().isWaters(p.getWorld())) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
            diedInWaters.add(p.getUniqueId());
        } else {
            e.getDrops().removeIf(it -> p.getUniqueId().equals(m().items().rodOwner(it)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (diedInWaters.remove(p.getUniqueId())) {
            Location hub = w().hub();
            if (hub != null) e.setRespawnLocation(hub);
        }
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            FishingData d = m().data(p.getUniqueId());
            if (p.isOnline() && d != null && d.rodGiven) m().ensureRod(p);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getBlockY() == e.getFrom().getBlockY()) return;
        Player p = e.getPlayer();
        if (!w().isWaters(p.getWorld())) return;
        if (e.getTo().getY() < module.cfg().baseY - 40) {
            Area from = w().areaNear(e.getTo());
            Location hub = from != null ? w().arrival(from) : w().hub();
            if (hub != null) {
                p.setFallDistance(0);
                p.teleportAsync(hub);
                Text.send(p, "<gray>A maintenance drone fished you out of the void.");
            }
        }
    }

    // ------------------------------------------------------------------ waters house rules

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!w().isWaters(e.getLocation().getWorld())) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (w().isWaters(e.getEntity().getWorld()) && e.getFoodLevel() < e.getEntity().getFoodLevel()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player) || !w().isWaters(e.getEntity().getWorld())) return;
        Entity src = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
        if (src instanceof Player) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFatigue(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player) || !w().isWaters(e.getEntity().getWorld())) return;
        if (e.getNewEffect() != null && e.getNewEffect().getType().equals(PotionEffectType.MINING_FATIGUE)) e.setCancelled(true);
    }

    private boolean builder(Player p) { return p.hasPermission("spacepunk.admin") && p.getGameMode() == org.bukkit.GameMode.CREATIVE; }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (w().isWaters(e.getBlock().getWorld()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (w().isWaters(e.getBlock().getWorld()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (w().isWaters(e.getBlock().getWorld()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (w().isWaters(e.getBlock().getWorld()) && !builder(e.getPlayer())) e.setCancelled(true);
    }
}
