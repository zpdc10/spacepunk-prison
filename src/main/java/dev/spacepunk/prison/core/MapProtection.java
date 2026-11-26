package dev.spacepunk.prison.core;

import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.ProtectionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Default-deny map protection for every world (see {@link ProtectionService}). Runs at LOWEST so module
 * listeners see the decision; mines, Undercroft veins and other gameplay handle their own blocks after it.
 */
public final class MapProtection implements ProtectionService, Listener {
    public static final String ADMIN = "spacepunk.admin";

    private record Rule<T>(String owner, T test) {}

    private final List<Rule<BiPredicate<Player, Block>>> breakRules = new ArrayList<>();
    private final List<Rule<BiPredicate<Player, Block>>> placeRules = new ArrayList<>();
    private final List<Rule<BiPredicate<Player, Block>>> useRules = new ArrayList<>();
    private final List<Rule<Predicate<Block>>> envRules = new ArrayList<>();
    private final Set<UUID> building = new HashSet<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public MapProtection() {
        // Ore inside a mine pit is minable; the mines module removes it itself. Blocks in a pit may also move
        // on their own (falling sand), since the pit refills anyway.
        allowBreak("mines", (p, b) -> inMine(b));
        allowEnvironment("mines", MapProtection::inMine);
    }

    private static boolean inMine(Block b) {
        return Services.find(MineService.class).map(s -> s.mineAt(b).isPresent()).orElse(false);
    }

    public void start(Plugin plugin) { Bukkit.getPluginManager().registerEvents(this, plugin); }

    // ------------------------------------------------------------------ service

    @Override public synchronized void allowBreak(String owner, BiPredicate<Player, Block> rule) { breakRules.add(new Rule<>(owner, rule)); }
    @Override public synchronized void allowPlace(String owner, BiPredicate<Player, Block> rule) { placeRules.add(new Rule<>(owner, rule)); }
    @Override public synchronized void allowUse(String owner, BiPredicate<Player, Block> rule) { useRules.add(new Rule<>(owner, rule)); }
    @Override public synchronized void allowEnvironment(String owner, Predicate<Block> rule) { envRules.add(new Rule<>(owner, rule)); }

    @Override public synchronized void clearRules(String owner) {
        breakRules.removeIf(r -> r.owner().equals(owner));
        placeRules.removeIf(r -> r.owner().equals(owner));
        useRules.removeIf(r -> r.owner().equals(owner));
        envRules.removeIf(r -> r.owner().equals(owner));
    }

    @Override public boolean buildMode(UUID player) { return building.contains(player); }

    @Override public boolean canBreak(Player p, Block b) { return buildMode(p.getUniqueId()) || test(breakRules, p, b); }

    @Override public boolean canPlace(Player p, Block b) { return buildMode(p.getUniqueId()) || test(placeRules, p, b); }

    /** Placing is the broader right; anywhere a player may place, they may also use blocks. */
    @Override public boolean canUse(Player p, Block b) { return canPlace(p, b) || test(useRules, p, b); }

    private boolean envAllowed(Block b) {
        for (Rule<Predicate<Block>> r : envRules) {
            try { if (r.test().test(b)) return true; } catch (RuntimeException ignored) { /* a broken rule denies */ }
        }
        return false;
    }

    private static boolean test(List<Rule<BiPredicate<Player, Block>>> rules, Player p, Block b) {
        for (Rule<BiPredicate<Player, Block>> r : rules) {
            try { if (r.test().test(p, b)) return true; } catch (RuntimeException ignored) { /* a broken rule denies */ }
        }
        return false;
    }

    private void notice(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && now - last < 3000) return;
        lastNotice.put(p.getUniqueId(), now);
        String msg = p.hasPermission(ADMIN)
                ? "<gray>Map blocks are protected. <white>/buildmode</white> to edit the map."
                : "<gray>Station hull is protected. Only ore in a mine can be mined.";
        var hud = Services.find(dev.spacepunk.prison.api.HudService.class);
        if (hud.isPresent()) hud.get().flash(p, msg);
        else Text.send(p, msg);
    }

    // ------------------------------------------------------------------ /buildmode

    public BasicCommand command() {
        return new BasicCommand() {
            @Override public void execute(@NotNull CommandSourceStack src, String @NotNull [] args) {
                if (!(src.getSender() instanceof Player p)) { Text.send(src.getSender(), "<red>Players only."); return; }
                boolean on = !building.contains(p.getUniqueId());
                if (args.length > 0) on = args[0].equalsIgnoreCase("on");
                if (on) building.add(p.getUniqueId()); else building.remove(p.getUniqueId());
                Text.send(p, on ? "<yellow>Build mode ON: you can edit map blocks. <gray>/buildmode again to turn it off."
                        : "<green>Build mode off. Map blocks are protected again.");
            }

            @Override public String permission() { return ADMIN; }
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        building.remove(e.getPlayer().getUniqueId());
        lastNotice.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ players

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!canBreak(e.getPlayer(), e.getBlock())) { e.setCancelled(true); notice(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!canPlace(e.getPlayer(), e.getBlockPlaced())) { e.setCancelled(true); notice(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!canPlace(e.getPlayer(), e.getBlock())) { e.setCancelled(true); notice(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canBreak(e.getPlayer(), e.getBlockClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        if (!canPlace(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        Player p = e.getPlayer();
        if (p != null ? !canPlace(p, e.getBlock()) : !envAllowed(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        Player p = e.getPlayer();
        if (p != null ? !canPlace(p, e.getBlock()) : !envAllowed(e.getBlock())) e.setCancelled(true);
    }

    /** Trampling farmland, and right-clicks that change a block (tools on blocks, pots, cake, containers...). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();
        if (e.getAction() == Action.PHYSICAL) {
            if (b.getType() == Material.FARMLAND && !canBreak(p, b)) e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || canUse(p, b)) return;
        ItemStack hand = e.getItem();
        if (hand != null && reshapes(hand.getType())) e.setUseItemInHand(Event.Result.DENY);
        if (altersOnUse(b)) e.setUseInteractedBlock(Event.Result.DENY);
    }

    /** Items that change the block they are used on (strip, till, path, wax, dye signs, place on blocks). */
    private static boolean reshapes(Material m) {
        String n = m.name();
        return n.endsWith("_AXE") || n.endsWith("_HOE") || n.endsWith("_SHOVEL") || n.endsWith("_DYE")
                || n.endsWith("_BUCKET") || m == Material.HONEYCOMB || m == Material.BONE_MEAL
                || m == Material.GLOW_INK_SAC || m == Material.INK_SAC || m == Material.FLINT_AND_STEEL
                || m == Material.FIRE_CHARGE || n.endsWith("_SPAWN_EGG") || n.endsWith("_BOAT") || n.endsWith("_MINECART")
                || m == Material.ARMOR_STAND || m == Material.ITEM_FRAME || m == Material.GLOW_ITEM_FRAME
                || m == Material.PAINTING || m == Material.END_CRYSTAL;
    }

    /** Blocks whose state a right-click changes for good, plus containers (shared map storage). */
    private static boolean altersOnUse(Block b) {
        String n = b.getType().name().toLowerCase(Locale.ROOT);
        if (n.startsWith("potted_") || n.equals("flower_pot") || n.contains("cake") || n.contains("candle")
                || n.equals("composter") || n.contains("cauldron") || n.equals("note_block") || n.equals("repeater")
                || n.equals("comparator") || n.equals("daylight_detector") || n.equals("lectern") || n.equals("jukebox")
                || n.equals("chiseled_bookshelf") || n.equals("decorated_pot") || n.equals("respawn_anchor")
                || n.endsWith("_bed") || n.contains("campfire") || n.equals("sweet_berry_bush") || n.startsWith("cave_vines")
                || n.equals("redstone_wire") || n.equals("beehive") || n.equals("bee_nest") || n.equals("vault")
                || n.equals("brewing_stand") || n.equals("dragon_egg") || n.endsWith("_shelf")) return true;
        return b.getState(false) instanceof Container;
    }

    // ------------------------------------------------------------------ entities that are part of the map

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        Player p = e.getPlayer();
        if (p == null || !canPlace(p, e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        Block b = e.getEntity().getLocation().getBlock();
        if (e instanceof HangingBreakByEntityEvent be) {
            Player p = playerOf(be.getRemover());
            if (p != null ? !canBreak(p, b) : !envAllowed(b)) e.setCancelled(true);
        } else if (!envAllowed(b)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if ((t instanceof ItemFrame || t instanceof ArmorStand) && !canPlace(e.getPlayer(), t.getLocation().getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!canPlace(e.getPlayer(), e.getRightClicked().getLocation().getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDecorDamage(EntityDamageByEntityEvent e) {
        Entity t = e.getEntity();
        if (!(t instanceof ItemFrame || t instanceof ArmorStand || t instanceof Hanging)) return;
        Player p = playerOf(e.getDamager());
        Block b = t.getLocation().getBlock();
        if (p != null ? !canBreak(p, b) : !envAllowed(b)) e.setCancelled(true);
    }

    private static Player playerOf(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    // ------------------------------------------------------------------ the world changing itself

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> !envAllowed(b)); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> !envAllowed(b)); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (!envAllowed(e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) { if (!envAllowed(e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (e instanceof BlockSpreadEvent) return;
        if (!envAllowed(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (e.getBlock().getType() == Material.FIRE || e.getBlock().getType() == Material.SOUL_FIRE) return;
        if (!envAllowed(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) { if (!envAllowed(e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) { if (!envAllowed(e.getToBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!envAllowed(e.getBlock()) || e.getBlocks().stream().anyMatch(b -> !envAllowed(b))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!envAllowed(e.getBlock()) || e.getBlocks().stream().anyMatch(b -> !envAllowed(b))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        Player p = playerOf(e.getEntity());
        if (p != null ? !canBreak(p, e.getBlock()) : !envAllowed(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        e.getBlocks().removeIf(s -> !envAllowed(s.getBlock()));
    }
}
