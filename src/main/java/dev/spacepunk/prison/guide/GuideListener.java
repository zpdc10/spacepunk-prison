package dev.spacepunk.prison.guide;

import dev.spacepunk.prison.api.event.GradeChangeEvent;
import dev.spacepunk.prison.api.event.ObjectiveProgressEvent;
import dev.spacepunk.prison.api.event.PlayerWarpEvent;
import dev.spacepunk.prison.data.ProfileLoadedEvent;
import dev.spacepunk.prison.guide.GuideConfig.Chapter;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

/** Turns what players do into guide events, and opens a chapter when someone clicks an info board. */
final class GuideListener implements Listener {
    private final Plugin plugin;
    private final GuideManager guide;
    private final GuideBoards boards;

    GuideListener(Plugin plugin, GuideManager guide, GuideBoards boards) {
        this.plugin = plugin;
        this.guide = guide;
        this.boards = boards;
    }

    @EventHandler
    public void onLoaded(ProfileLoadedEvent e) {
        Player p = e.player();
        guide.load(p, e.profile());
        // Give the resource pack prompt and the join messages a moment before the welcome.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            guide.fire(p, "join", "", List.of(), 1);
            guide.fire(p, "world", p.getWorld().getName(), List.of(), 1);
        }, guide.cfg().joinDelayTicks + 1L);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        boards.removeBayBoard(e.getPlayer().getUniqueId());
        guide.unload(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        // A tick later so the arrival messages of the place itself come first.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) guide.fire(p, "world", p.getWorld().getName(), List.of(), 1);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarp(PlayerWarpEvent e) {
        guide.fire(e.player(), "warp", e.warpId(), List.of(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String label = msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        guide.fire(e.getPlayer(), "command", label, List.of(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMenu(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Object holder = e.getInventory().getHolder(false);
        if (holder == null || holder instanceof GuideMenu.Holder) return;
        String name = holder.getClass().getName();
        if (!name.startsWith("dev.spacepunk.prison.")) return;
        guide.fire(p, "menu", name.substring("dev.spacepunk.prison.".length()), List.of(), 1);
    }

    @EventHandler
    public void onObjective(ObjectiveProgressEvent e) {
        guide.fire(e.player(), "objective", e.type(), e.targets(), e.amount());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGrade(GradeChangeEvent e) {
        Player p = e.player();
        guide.fire(p, "grade", String.valueOf(e.newGrade()), List.of(), 1);
        if (e.isPrestige()) guide.fire(p, "prestige", String.valueOf(e.newPrestige()), List.of(), 1);
    }

    // ---- info boards

    @EventHandler(priority = EventPriority.HIGH)
    public void onBoardClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            if (isBoard(e.getRightClicked())) e.setCancelled(true);
            return;
        }
        if (open(e.getPlayer(), e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBoardHit(PrePlayerAttackEntityEvent e) {
        if (open(e.getPlayer(), e.getAttacked())) e.setCancelled(true);
    }

    private boolean isBoard(Entity e) {
        return e instanceof Interaction && e.getScoreboardTags().contains(GuideBoards.TAG);
    }

    private boolean open(Player p, Entity e) {
        if (!isBoard(e)) return false;
        String id = e.getPersistentDataContainer().get(boards.chapterKey(), PersistentDataType.STRING);
        Chapter c = id == null ? null : guide.cfg().chapters.get(id);
        if (c != null) guide.openBook(p, c);
        return true;
    }
}
