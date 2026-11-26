package dev.spacepunk.prison.fishing;

import dev.spacepunk.prison.fishing.FishingConfig.Minigame;

import java.util.Random;

/**
 * The reel-in minigame, pure state so it is easy to reason about. A fish darts along a track; the player's catch
 * zone rises while they hold sneak and sinks when they let go. While the fish is inside the zone, catch progress
 * fills; outside, it drains. Full = landed, empty = it got away. One {@link #tick(boolean)} per server tick.
 */
final class ReelGame {
    static final int TIMEOUT_TICKS = 20 * 30;

    private final Random rng = new Random();
    private final double zoneWidth, speed, erratic, gain, drain;
    double fish = 0.5, fishVel, target = 0.5;
    double zone, zoneVel;
    double progress = 0.3;
    int ticks;
    boolean wasInside;

    ReelGame(Minigame m, double zoneBonus, double strength) {
        this.zoneWidth = Math.max(0.08, Math.min(0.7, m.zone() + zoneBonus));
        this.speed = m.speed();
        this.erratic = m.erratic();
        this.gain = m.gain();
        this.drain = m.drain() * (1 - Math.max(0, Math.min(0.8, strength)));
        this.zone = zoneWidth / 2;
        this.fish = 0.35 + rng.nextDouble() * 0.3;
        this.target = fish;
    }

    enum Result { CONTINUE, LANDED, ESCAPED }

    Result tick(boolean reeling) {
        ticks++;
        // Zone: sneak lifts it, gravity pulls it back.
        zoneVel += reeling ? 0.0042 : -0.0034;
        zoneVel = Math.max(-0.035, Math.min(0.035, zoneVel));
        zone += zoneVel;
        double half = zoneWidth / 2;
        if (zone < half) {
            zone = half;
            zoneVel = Math.max(0, -zoneVel * 0.25);
        } else if (zone > 1 - half) {
            zone = 1 - half;
            zoneVel = Math.min(0, -zoneVel * 0.25);
        }
        // Fish: picks a new spot now and then, darting harder the rarer it is.
        if (rng.nextDouble() < erratic) {
            double reach = 0.25 + 0.5 * Math.min(1, speed);
            target = Math.max(0, Math.min(1, fish + (rng.nextDouble() * 2 - 1) * reach));
            if (rng.nextDouble() < 0.15 * speed) target = rng.nextBoolean() ? 0.02 : 0.98;
        }
        fishVel += (target - fish) * 0.012 * speed;
        fishVel *= 0.86;
        fish = Math.max(0, Math.min(1, fish + fishVel));

        wasInside = Math.abs(fish - zone) <= half;
        progress += wasInside ? gain : -drain;
        if (progress >= 1) return Result.LANDED;
        if (progress <= 0 || ticks > TIMEOUT_TICKS) return Result.ESCAPED;
        return Result.CONTINUE;
    }

    /** Half the width of the visible target band on the XP bar: the fish counts as hooked while the bar sits in 0.5 ± this. */
    static final double BAND = 0.2;

    /** -1..1 while inside the catch zone; positive when the line is too tight (zone above the fish). */
    double offset() {
        return (zone - fish) / (zoneWidth / 2);
    }

    /**
     * Line tension for the XP bar: the middle of the bar is "on the fish", sneaking pushes it up, the fish yanks it
     * around. The band 0.5 ± {@link #BAND} is exactly the catch zone, whatever the rarity.
     */
    float bar() {
        return (float) Math.max(0, Math.min(1, 0.5 + BAND * offset()));
    }

    /** Catch progress as a whole percent, shown as the XP level number. */
    int percent() {
        return (int) Math.max(0, Math.min(100, Math.round(progress * 100)));
    }
}
