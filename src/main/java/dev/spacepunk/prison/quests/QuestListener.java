package dev.spacepunk.prison.quests;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.ObjectiveTypes;
import dev.spacepunk.prison.api.event.ContrabandSmuggledEvent;
import dev.spacepunk.prison.api.event.CurrencyChangeEvent;
import dev.spacepunk.prison.api.event.FishCaughtEvent;
import dev.spacepunk.prison.api.event.GangMembershipEvent;
import dev.spacepunk.prison.api.event.GradeChangeEvent;
import dev.spacepunk.prison.api.event.MineBlocksBrokenEvent;
import dev.spacepunk.prison.api.event.UndercroftKillEvent;
import dev.spacepunk.prison.data.ProfileLoadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

/** Turns existing game events into quest progress for the core objective types. */
final class QuestListener implements Listener {
    private final QuestManager quests;

    QuestListener(QuestManager quests) { this.quests = quests; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoaded(ProfileLoadedEvent e) { quests.load(e.player(), e.profile()); }

    /** Before the profile manager saves on quit (MONITOR), so the final JSON is in the snapshot. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) { quests.unload(e.getPlayer().getUniqueId()); }

    /** LOW: before the drill multiplies ores (Yield) and before progression empties the map into the hold. */
    @EventHandler(priority = EventPriority.LOW)
    public void onMine(MineBlocksBrokenEvent e) {
        Player p = e.player();
        if (p == null) return;
        int grade = e.mine().requiredGrade();
        int deck = Math.max(1, Math.min(5, (grade - 1) / 5 + 1));
        if (e.blockCount() > 0) {
            quests.progress(p, ObjectiveTypes.MINE_BLOCKS, e.blockCount(), "mine:" + e.mine().id(), "deck:" + deck, "grade:" + grade);
        }
        for (Map.Entry<Material, Long> o : e.ores().entrySet()) {
            if (o.getValue() != null && o.getValue() > 0) {
                quests.progress(p, ObjectiveTypes.MINE_ITEM, o.getValue(), o.getKey().getKey().toString());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCurrency(CurrencyChangeEvent e) {
        Player p = Bukkit.getPlayer(e.player());
        if (p == null) return;
        long delta = e.newBalance() - e.oldBalance();
        String reason = e.reason() == null ? "" : e.reason();
        if (delta > 0) {
            if (e.currency() == Currency.CREDITS && reason.startsWith("sell:")) {
                quests.progress(p, ObjectiveTypes.SELL_CREDITS, delta);
            } else if (e.currency() == Currency.TOKENS) {
                for (String prefix : quests.cfg().earnTokenReasons) {
                    if (reason.startsWith(prefix)) {
                        quests.progress(p, ObjectiveTypes.EARN_TOKENS, delta);
                        break;
                    }
                }
            }
        } else if (delta < 0) {
            if (reason.startsWith("drill:enchant:")) {
                quests.progress(p, ObjectiveTypes.ENCHANT_UPGRADE, 1, "enchant:" + reason.substring("drill:enchant:".length()));
            } else if (reason.startsWith("drill:frame:")) {
                quests.progress(p, ObjectiveTypes.FRAME_UPGRADE, 1, "frame:" + reason.substring("drill:frame:".length()));
            } else if (reason.equals("cargo-upgrade")) {
                quests.progress(p, ObjectiveTypes.CARGO_UPGRADE, 1);
            } else if (reason.equals("auto-sell-unlock")) {
                quests.progress(p, ObjectiveTypes.AUTOSELL_UNLOCK, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGang(GangMembershipEvent e) {
        if (e.change() != GangMembershipEvent.Change.JOINED || e.player() == null) return;
        Player p = Bukkit.getPlayer(e.player());
        if (p != null) quests.progress(p, ObjectiveTypes.GANG_JOIN, 1, "gang:" + e.gangId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(FishCaughtEvent e) {
        if (e.player() == null || !"fish".equals(e.kind())) return;
        quests.progress(e.player(), ObjectiveTypes.CATCH_FISH, Math.max(1, e.amount()), e.id(), "rarity:" + e.rarity(), "zone:" + e.areaId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUndercroftKill(UndercroftKillEvent e) {
        if (e.killer() != null) quests.progress(e.killer(), ObjectiveTypes.UNDERCROFT_WIN, 1, "ring:" + e.ring());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmuggle(ContrabandSmuggledEvent e) {
        if (e.player() != null && e.items() > 0) quests.progress(e.player(), ObjectiveTypes.SMUGGLE, e.items());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGrade(GradeChangeEvent e) {
        Player p = e.player();
        if (e.isPrestige()) {
            quests.progress(p, ObjectiveTypes.PRESTIGE, e.newPrestige() - e.oldPrestige(), "prestige:" + e.newPrestige());
        } else if (e.newGrade() > e.oldGrade()) {
            quests.progress(p, ObjectiveTypes.RANK_UP, e.newGrade() - e.oldGrade(), "grade:" + e.newGrade());
        }
        quests.gradeChanged(p);
    }
}
