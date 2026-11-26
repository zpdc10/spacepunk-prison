package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.MineService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.event.GradeChangeEvent;
import dev.spacepunk.prison.api.event.MineBlocksBrokenEvent;
import dev.spacepunk.prison.drill.DrillConfig.FrameDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drill effects when a player mines: Yield, Tokens, heat, and enchant procs (HAND breaks only).
 * Also blocks mining while the drill is stalled.
 */
public final class MiningListener implements Listener {
    private final DrillManager drills;
    private final HeatManager heat;
    /** Last block each player started breaking; fallback origin when the event carries none. */
    private final Map<UUID, Block> lastTarget = new HashMap<>();

    public MiningListener(DrillManager drills, HeatManager heat) {
        this.drills = drills;
        this.heat = heat;
    }

    public void forget(UUID id) { lastTarget.remove(id); }

    // ---- stall + origin tracking ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        Player p = e.getPlayer();
        if (heat.stalled(p) && drills.holdingOwnDrill(p)) {
            e.setCancelled(true);
            return;
        }
        lastTarget.put(p.getUniqueId(), e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        lastTarget.put(p.getUniqueId(), e.getBlock());
        if (heat.stalled(p) && drills.holdingOwnDrill(p)) e.setCancelled(true);
    }

    // ---- mining ----

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMine(MineBlocksBrokenEvent e) {
        if (e.cause() == MineService.BreakCause.ADMIN) return;
        Player p = e.player();
        UUID id = p.getUniqueId();
        DrillData d = drills.data(id);
        DrillMath.Effective eff = drills.effective(id);
        if (d == null || eff == null) return;
        boolean hand = e.cause() == MineService.BreakCause.HAND;
        // Hand mining only counts with the player's own drill in hand; enchant breaks came from the drill.
        if (hand && !drills.holdingOwnDrill(p)) return;
        DrillConfig cfg = drills.cfg();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = Math.max(0, e.blockCount());

        // Yield on every drill break.
        if (eff.yield > 1.0) {
            for (Map.Entry<Material, Long> en : e.ores().entrySet()) {
                double v = en.getValue() * eff.yield;
                long whole = (long) Math.floor(v);
                if (rng.nextDouble() < v - whole) whole++;
                en.setValue(whole);
            }
        }

        d.blocksMined += count;
        drills.markStatsDirty(id);

        // Tokens: uncommon finds (base + Token Siphon), bigger in deeper mines and at higher prestige,
        // reduced for enchant-broken blocks.
        double rate = hand ? 1.0 : cfg.tokenEnchantRate;
        double scale = cfg.tokenScale(e.mine().requiredGrade(), prestigeOf(id));
        long tokens = 0;
        if (eff.tokenMultiplier > 0) {
            int baseHits = successes(count, cfg.tokenHandChance * rate, rng);
            for (int i = 0; i < baseHits; i++) tokens += Math.round(rng.nextInt(cfg.tokenMin, cfg.tokenMax + 1) * scale);
            if (eff.siphonChance > 0) {
                DrillConfig.EnchantDef sd = cfg.enchant(Enchant.TOKEN_SIPHON);
                int hits = successes(count, eff.siphonChance * rate, rng);
                int lo = (int) Math.round(sd.p("amount-min", 4) * eff.siphonAmount);
                int hi = Math.max(lo, (int) Math.round(sd.p("amount-max", 7) * eff.siphonAmount));
                for (int i = 0; i < hits; i++) tokens += Math.round(rng.nextInt(lo, hi + 1) * scale);
            }
            tokens = Math.round(tokens * eff.tokenMultiplier);
        }

        double deckMul = cfg.heat.deckMultiplier(e.mine().requiredGrade());
        if (hand) heat.add(p, cfg.heat.handBlock() * eff.handHeatMultiplier * deckMul * count);

        if (hand && !heat.stalled(p)) {
            tokens += salvage(p, d, eff, rng, scale);
            procs(p, e, eff, deckMul, rng);
        }

        if (tokens > 0) {
            Services.get(EconomyService.class).give(id, Currency.TOKENS, tokens, "drill:mining");
            d.tokensFound += tokens;
            if (hand) {
                // The HUD's mining summary adds the amount ("+6 ✦"); the flash marks the find itself.
                var hud = Services.find(dev.spacepunk.prison.api.HudService.class);
                if (hud.isPresent()) hud.get().flash(p, "<aqua>✦ Token find</aqua> <white>+" + tokens + "</white>");
                else heat.flash(p, "<aqua>✦ Token find +" + tokens + " Token" + (tokens == 1 ? "" : "s"));
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
            }
        }
    }

    private static int prestigeOf(UUID id) {
        return Services.find(dev.spacepunk.prison.api.ProgressionService.class).map(s -> s.prestige(id)).orElse(0);
    }

    /** Token caches and module finds. Returns Tokens from a cache. */
    private long salvage(Player p, DrillData d, DrillMath.Effective eff, ThreadLocalRandom rng, double scale) {
        DrillConfig cfg = drills.cfg();
        long tokens = 0;
        if (eff.cacheChance > 0 && rng.nextDouble() < eff.cacheChance * eff.rareMultiplier && eff.tokenMultiplier > 0) {
            DrillConfig.EnchantDef sd = cfg.enchant(Enchant.SALVAGE_SENSE);
            double mul = (1 + d.level(Enchant.SALVAGE_SENSE) * sd.p("cache-per-level", 0.02)) * scale;
            int lo = (int) Math.round(sd.p("cache-min", 15) * mul);
            int hi = Math.max(lo, (int) Math.round(sd.p("cache-max", 50) * mul));
            tokens = Math.round(rng.nextInt(lo, hi + 1) * eff.tokenMultiplier);
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.3f);
            Text.send(p, "<#c0a0ff>Salvage Sense</#c0a0ff> <gray>cracked a Token cache: <aqua>+" + tokens + " Tokens");
        }
        if (cfg.moduleChance > 0 && !cfg.modules.isEmpty() && rng.nextDouble() < cfg.moduleChance * eff.rareMultiplier) {
            ModuleDef m = randomModule(cfg, rng);
            if (m != null) {
                drills.give(p, drills.items().module(m, 1));
                d.modulesFound++;
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                p.showTitle(net.kyori.adventure.title.Title.title(Text.mm(m.coloredName()),
                        Text.mm("<gray>Drill module salvaged! Fit it on your module rig.")));
                if (cfg.moduleBroadcast) {
                    Bukkit.broadcast(Text.mm(Text.PREFIX + "<white>" + p.getName() + "</white> <gray>salvaged a "
                            + m.rarity().color + m.rarity().label + "</" + m.rarity().color.substring(1)
                            + " <gray>module: " + m.coloredName()));
                } else {
                    Text.send(p, "<gray>You salvaged a module: " + m.coloredName());
                }
            }
        }
        return tokens;
    }

    private static ModuleDef randomModule(DrillConfig cfg, ThreadLocalRandom rng) {
        double total = 0;
        for (ModuleDef m : cfg.modules.values()) total += m.weight();
        if (total <= 0) return null;
        double r = rng.nextDouble() * total;
        for (ModuleDef m : cfg.modules.values()) {
            r -= m.weight();
            if (r < 0) return m;
        }
        return null;
    }

    private void procs(Player p, MineBlocksBrokenEvent e, DrillMath.Effective eff, double deckMul, ThreadLocalRandom rng) {
        DrillConfig cfg = drills.cfg();
        Block origin = e.origin() != null ? e.origin() : lastTarget.get(p.getUniqueId());
        MineService.MineInfo mine = e.mine();
        MineService mines = Services.find(MineService.class).orElse(null);

        if (eff.overclockChance > 0 && rng.nextDouble() < eff.overclockChance) {
            heat.overclock(p, eff.overclockSeconds);
            heat.add(p, cfg.enchant(Enchant.OVERCLOCK).p("heat", 4) * eff.procHeatMultiplier * deckMul);
        }
        if (mines == null || origin == null || !mine.contains(origin)) return;

        if (eff.jackChance > 0 && rng.nextDouble() < eff.jackChance) {
            int max = (int) cfg.enchant(Enchant.JACKHAMMER).p("max-blocks", 4000);
            List<Block> layer = layer(origin, mine, max);
            if (!layer.isEmpty()) {
                int n = mines.breakBlocks(p, layer, MineService.BreakCause.ENCHANT);
                Location at = origin.getLocation().add(0.5, 0.5, 0.5);
                p.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                p.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
                p.getWorld().spawnParticle(Particle.EXPLOSION, at, 3, 2, 0.2, 2, 0);
                heat.add(p, cfg.enchant(Enchant.JACKHAMMER).p("heat", 22) * eff.procHeatMultiplier * deckMul);
                heat.flash(p, "<#ff4060><bold>JACKHAMMER</bold></#ff4060> <gray>" + n + " blocks");
                return; // the layer is gone; skip smaller procs this break
            }
        }
        if (heat.stalled(p)) return;

        if (eff.shapedChance > 0 && rng.nextDouble() < eff.shapedChance) {
            List<Block> sphere = sphere(origin, eff.shapedRadius, mine);
            if (!sphere.isEmpty()) {
                int n = mines.breakBlocks(p, sphere, MineService.BreakCause.ENCHANT);
                Location at = origin.getLocation().add(0.5, 0.5, 0.5);
                p.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
                p.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
                heat.add(p, eff.shapedHeat * deckMul);
                heat.flash(p, "<#ff7040><bold>SHAPED CHARGE</bold></#ff7040> <gray>" + n + " blocks");
            }
        }
        if (heat.stalled(p)) return;

        if (eff.laserChance > 0 && rng.nextDouble() < eff.laserChance) {
            BlockFace dir = p.getFacing();
            List<Block> line = new ArrayList<>();
            Block b = origin;
            for (int i = 0; i < eff.laserLength; i++) {
                b = b.getRelative(dir);
                if (!mine.contains(b)) break;
                if (!b.getType().isAir()) line.add(b);
            }
            if (!line.isEmpty()) {
                int n = mines.breakBlocks(p, line, MineService.BreakCause.ENCHANT);
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 48, 48), 1.2f);
                Location start = origin.getLocation().add(0.5, 0.5, 0.5);
                for (int i = 0; i <= eff.laserLength; i++) {
                    p.getWorld().spawnParticle(Particle.DUST, start.clone().add(dir.getDirection().multiply(i)), 2, 0.05, 0.05, 0.05, 0, dust);
                }
                p.getWorld().playSound(start, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 2f);
                heat.add(p, cfg.enchant(Enchant.CUTTING_LASER).p("heat", 5) * eff.procHeatMultiplier * deckMul);
                heat.flash(p, "<#ff3030><bold>CUTTING LASER</bold></#ff3030> <gray>" + n + " blocks");
            }
        }
    }

    private static List<Block> sphere(Block center, int r, MineService.MineInfo mine) {
        List<Block> out = new ArrayList<>();
        int r2 = r * r;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > r2) continue;
                    Block b = center.getRelative(x, y, z);
                    if (!b.getType().isAir() && mine.contains(b)) out.add(b);
                }
            }
        }
        return out;
    }

    /** Flood fill along the origin's Y level, bounded by the mine region. */
    private static List<Block> layer(Block origin, MineService.MineInfo mine, int max) {
        List<Block> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(key(origin));
        int visited = 0;
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        while (!queue.isEmpty() && out.size() < max && visited < max * 3) {
            Block b = queue.poll();
            visited++;
            if (!b.getType().isAir()) out.add(b);
            for (BlockFace f : faces) {
                Block n = b.getRelative(f);
                if (seen.add(key(n)) && mine.contains(n)) queue.add(n);
            }
        }
        return out;
    }

    private static long key(Block b) {
        return ((long) b.getX() & 0x3FFFFFF) << 38 | ((long) b.getZ() & 0x3FFFFFF) << 12 | (b.getY() & 0xFFF);
    }

    /** Number of successes in n trials of chance p. */
    private static int successes(int n, double p, ThreadLocalRandom rng) {
        if (n <= 0 || p <= 0) return 0;
        if (p >= 1) return n;
        if (n <= 256) {
            int k = 0;
            for (int i = 0; i < n; i++) if (rng.nextDouble() < p) k++;
            return k;
        }
        double mean = n * p;
        double sd = Math.sqrt(n * p * (1 - p));
        return (int) Math.max(0, Math.min(n, Math.round(mean + rng.nextGaussian() * sd)));
    }

    // ---- frame unlock announcements ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGrade(GradeChangeEvent e) {
        Player p = e.player();
        DrillData d = drills.data(p.getUniqueId());
        if (d == null) return;
        FrameDef next = drills.cfg().nextFrame(drills.cfg().frame(d.frame));
        if (next == null) return;
        boolean before = next.unlockedFor(e.oldGrade(), e.oldPrestige());
        boolean now = next.unlockedFor(e.newGrade(), e.newPrestige());
        if (!before && now) {
            Text.send(p, "<gray>New drill frame available: " + next.coloredName()
                    + "<gray>. Shift + right-click your drill to install it.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
        }
    }
}
