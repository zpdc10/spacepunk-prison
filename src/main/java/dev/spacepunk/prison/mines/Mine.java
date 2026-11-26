package dev.spacepunk.prison.mines;

import dev.spacepunk.prison.api.MineService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;

/** Runtime state of one mine. Main thread only. */
public final class Mine implements MineService.MineInfo {
    private final MinesConfig.MineDef def;
    private final MinesConfig.Deck deck;
    private Region region;
    private Location spawn;
    private long remaining;
    private boolean refilling;
    private boolean refillPending;
    private long lastRefillMillis = System.currentTimeMillis();
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);

    Mine(MinesConfig.MineDef def, MinesConfig.Deck deck) {
        this.def = def;
        this.deck = deck;
    }

    @Override public String id() { return def.id(); }
    @Override public String displayName() { return def.name(); }
    @Override public int requiredGrade() { return def.grade(); }

    @Override public double fillRatio() {
        if (region == null) return 0;
        long total = region.volume();
        return total == 0 ? 0 : Math.max(0, Math.min(1, (double) remaining / total));
    }

    @Override public boolean contains(Block block) { return region != null && region.contains(block); }

    public MinesConfig.MineDef def() { return def; }
    /** May be null if the mine's deck id is not in config. */
    public MinesConfig.Deck deck() { return deck; }
    public String deckName() { return deck == null ? "Unassigned" : deck.name(); }
    public String color() { return deck == null ? "<gray>" : deck.color(); }
    public BlockMix mix() { return def.mix(); }

    public Region region() { return region; }
    void region(Region r) {
        this.region = r;
        this.remaining = 0;
    }

    /** Where /mine sends players. Defaults to the middle of the top face. */
    public Location spawn() {
        if (spawn != null && spawn.getWorld() != null) return spawn.clone();
        return region == null ? null : region.topCenter();
    }
    Location customSpawn() { return spawn; }
    void spawn(Location l) { this.spawn = l == null ? null : l.clone(); }

    public boolean placed() { return region != null && region.bukkitWorld() != null; }

    public long remaining() { return remaining; }
    void removed(long n) { remaining = Math.max(0, remaining - n); }
    void filled() {
        remaining = region == null ? 0 : region.volume();
        lastRefillMillis = System.currentTimeMillis();
    }

    public boolean refilling() { return refilling; }
    void refilling(boolean b) { refilling = b; }
    boolean refillPending() { return refillPending; }
    void refillPending(boolean b) { refillPending = b; }
    long lastRefillMillis() { return lastRefillMillis; }

    BossBar bar() { return bar; }
}
