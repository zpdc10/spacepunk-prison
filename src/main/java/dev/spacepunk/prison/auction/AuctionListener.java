package dev.spacepunk.prison.auction;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.PrestigeAttemptEvent;
import dev.spacepunk.prison.data.ProfileLoadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

/** Pays out what players were owed while offline, and keeps bid escrow from dodging the prestige wipe. */
final class AuctionListener implements Listener {
    private final AuctionManager auctions;

    AuctionListener(AuctionManager auctions) { this.auctions = auctions; }

    @EventHandler
    public void onLoad(ProfileLoadedEvent e) { auctions.onLoad(e.player()); }

    @EventHandler
    public void onPrestige(PrestigeAttemptEvent e) {
        List<Listing> leading = auctions.leadingBids(e.player().getUniqueId());
        if (leading.isEmpty()) return;
        long held = leading.stream().mapToLong(l -> l.topBid).sum();
        e.deny("<red>You can't prestige while you lead " + leading.size() + " Black Market auction"
                + (leading.size() == 1 ? "" : "s") + ".</red> <gray>" + Services.get(EconomyService.class).format(Currency.CREDITS, held)
                + " <gray>is held in escrow for your bids, and prestige wipes Credits. Wait until "
                + (leading.size() == 1 ? "it ends" : "they end") + " or you're outbid (<white>/ah mine</white>).");
    }
}
