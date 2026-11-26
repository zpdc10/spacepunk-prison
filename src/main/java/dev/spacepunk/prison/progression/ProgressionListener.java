package dev.spacepunk.prison.progression;

import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.event.GradeChangeEvent;
import dev.spacepunk.prison.api.event.MineBlocksBrokenEvent;
import dev.spacepunk.prison.data.ProfileLoadedEvent;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;

final class ProgressionListener implements Listener {
    private final ProgressionManager progression;
    private final CargoHold cargo;

    ProgressionListener(ProgressionManager progression, CargoHold cargo) {
        this.progression = progression;
        this.cargo = cargo;
    }

    @EventHandler
    public void onLoaded(ProfileLoadedEvent e) {
        cargo.store().load(e.profile());
        progression.refreshTab(e.player());
    }

    /** Runs before core's MONITOR quit handler, which snapshots and saves the profile. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent e) {
        cargo.store().unload(e.getPlayer().getUniqueId());
        cargo.forget(e.getPlayer().getUniqueId());
    }

    /** Consumes ore after the drill (NORMAL) has applied Yield and any module (HIGH) has taken its share. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMine(MineBlocksBrokenEvent e) {
        cargo.deposit(e.player(), e.ores());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGradeChange(GradeChangeEvent e) {
        Player p = e.player();
        progression.refreshTab(p);
        ProgressionConfig c = progression.cfg();
        if (e.isPrestige()) {
            p.showTitle(Title.title(Text.mm("<light_purple><bold>PRESTIGE " + e.newPrestige()),
                    Text.mm("<gray>Back to the Scrapyard, stronger than before"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            Bukkit.getServer().sendMessage(Text.mm(Text.PREFIX + "<white>" + p.getName()
                    + "</white> <gray>reached</gray> <light_purple><bold>Prestige " + e.newPrestige() + "</bold></light_purple><gray>!"));
            return;
        }
        if (e.newGrade() <= e.oldGrade()) return;
        boolean newDeck = c.deckIndex(e.newGrade()) != c.deckIndex(e.oldGrade());
        String sub = newDeck ? "<gray>Clearance granted: " + c.deckName(e.newGrade()) : "<gray>" + c.deckName(e.newGrade());
        p.showTitle(Title.title(Text.mm("<#c8743a><bold>" + c.gradeName(e.newGrade()).toUpperCase()), Text.mm(sub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, newDeck ? 0.7f : 1.2f);
        if (newDeck) {
            Bukkit.getServer().sendMessage(Text.mm(Text.PREFIX + "<white>" + p.getName() + "</white> <gray>was cleared for</gray> "
                    + c.deckName(e.newGrade()) + "<gray>.</gray>"));
        }
        if (e.newGrade() >= ProgressionService.MAX_GRADE) {
            Text.send(p, "<light_purple>Maximum clearance reached.</light_purple> <gray>Type <white>/prestige</white> to reset for Shards and a permanent sell bonus.");
        }
    }
}
