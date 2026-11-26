package dev.spacepunk.prison.api;

import org.bukkit.entity.Player;

/** Null-safe helper for activities to roll scrap part finds; does nothing when the rare drops module is missing. */
public final class ScrapDrops {
    private ScrapDrops() {}

    /** See {@link ScrapService#roll}. Main thread. */
    public static int roll(Player player, String source, int trials) {
        if (player == null || trials <= 0) return 0;
        return Services.find(ScrapService.class).map(s -> s.roll(player, source, trials, 1.0)).orElse(0);
    }
}
