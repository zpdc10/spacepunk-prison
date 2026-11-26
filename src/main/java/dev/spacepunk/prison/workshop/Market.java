package dev.spacepunk.prison.workshop;

import com.google.gson.Gson;
import dev.spacepunk.prison.workshop.WorkshopConfig.Good;
import dev.spacepunk.prison.workshop.WorkshopConfig.Stage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The station's goods market, shared by everyone. Each day every good gets a demand multiplier and a few are
 * priority orders (seeded by the date, so it's the same for all players and survives restarts). Selling a lot
 * of one good in a day lowers its price for everyone until the next day. Main thread only.
 */
final class Market {
    private static final Gson GSON = new Gson();

    private static final class Saved {
        String day = "";
        Map<String, Double> sold = new HashMap<>();
    }

    private final Plugin plugin;
    private final Supplier<WorkshopConfig> cfg;
    private final File file;
    private Saved state = new Saved();
    private List<String> priorities = List.of();
    private String prioritiesDay = "";
    private boolean dirty;

    Market(Plugin plugin, Supplier<WorkshopConfig> cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.file = new File(new File(plugin.getDataFolder(), "workshop"), "market.json");
        if (file.exists()) {
            try {
                Saved s = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), Saved.class);
                if (s != null && s.sold != null) state = s;
            } catch (Exception e) {
                plugin.getLogger().warning("workshop/market.json unreadable, starting a fresh market day: " + e.getMessage());
            }
        }
    }

    String today() { return LocalDate.now(cfg.get().zone).toString(); }

    private void roll() {
        String d = today();
        if (!d.equals(state.day)) {
            state.day = d;
            state.sold.clear();
            dirty = true;
        }
    }

    /** Today's demand multiplier for a good (before priority and saturation). */
    double demand(String base) {
        WorkshopConfig c = cfg.get();
        Random r = new Random(seed(today() + "|" + base));
        return c.demandMin + r.nextDouble() * (c.demandMax - c.demandMin);
    }

    /** Today's priority orders: goods from the refined stage up. */
    List<String> priorities() {
        String d = today();
        if (!d.equals(prioritiesDay)) {
            List<String> pool = new ArrayList<>();
            for (Good g : cfg.get().goods.values()) {
                if (g.stage() == Stage.REFINED || g.stage() == Stage.ALLOY || g.stage() == Stage.COMPONENT) pool.add(g.id());
            }
            Collections.sort(pool);
            Collections.shuffle(pool, new Random(seed(d + "|priority")));
            priorities = List.copyOf(pool.subList(0, Math.min(cfg.get().priorityCount, pool.size())));
            prioritiesDay = d;
        }
        return priorities;
    }

    boolean isPriority(String base) { return priorities().contains(base); }

    /** Share of the price left after today's sales of this good (1.0 = untouched). */
    double saturation(String base) {
        roll();
        WorkshopConfig c = cfg.get();
        double sold = state.sold.getOrDefault(base, 0.0);
        return Math.max(c.minFactor, 1.0 / (1.0 + sold / c.saturationCredits));
    }

    /** Full market multiplier for a good right now. */
    double multiplier(String base) {
        double m = demand(base) * saturation(base);
        if (isPriority(base)) m *= cfg.get().priorityBonus;
        return m;
    }

    /** True if the market buys this item key (any workshop good). */
    boolean buys(String key) { return cfg.get().goods.containsKey(BayData.base(key)); }

    /**
     * Credits (before the seller's sell multiplier) for selling n units now, and records the sale. Large sales
     * are priced in slices so they push the price down as they go.
     */
    double sell(String key, long n) {
        String base = BayData.base(key);
        Good g = cfg.get().goods.get(base);
        if (g == null || n <= 0) return 0;
        roll();
        double unit = g.price() * cfg.get().quality(BayData.quality(key)).price();
        double total = 0;
        long left = n;
        long slice = Math.max(1, n / 10);
        while (left > 0) {
            long k = Math.min(slice, left);
            double value = unit * k;
            total += value * multiplier(base);
            state.sold.merge(base, value, Double::sum);
            left -= k;
        }
        dirty = true;
        return total;
    }

    /** Price of one unit right now (before the seller's sell multiplier). */
    double unitPrice(String key) {
        Good g = cfg.get().goods.get(BayData.base(key));
        if (g == null) return 0;
        return g.price() * cfg.get().quality(BayData.quality(key)).price() * multiplier(BayData.base(key));
    }

    /** Called periodically: writes the file off-thread if anything changed. */
    void flush() {
        roll();
        if (!dirty) return;
        dirty = false;
        String json = GSON.toJson(state);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(json));
    }

    void flushNow() {
        dirty = false;
        write(GSON.toJson(state));
    }

    private synchronized void write(String json) {
        try {
            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            File tmp = new File(dir, "market.json.tmp");
            Files.writeString(tmp.toPath(), json, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save workshop/market.json: " + e.getMessage());
        }
    }

    private static long seed(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return h * 0x9E3779B97F4A7C15L;
    }
}
