package dev.spacepunk.prison.api;

import org.bukkit.entity.Player;

/**
 * The player HUD: action bar, sidebar, tab list and cargo boss bar. Implemented by the hud module, which owns
 * the action bar so modules don't overwrite each other. Main thread only. If this service is missing, modules
 * fall back to sending their own action bar.
 */
public interface HudService {
    /** Shows a short MiniMessage notice on the action bar for about two seconds. */
    void flash(Player player, String miniMessage);

    /**
     * Sets a persistent action bar segment owned by a module, for example the drill's heat gauge under key
     * "heat". Segments are shown first, then mining gains and flashes. Pass null to remove it.
     */
    void segment(Player player, String key, String miniMessage);
}
