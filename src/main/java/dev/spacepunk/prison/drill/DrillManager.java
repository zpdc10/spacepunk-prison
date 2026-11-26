package dev.spacepunk.prison.drill;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.spacepunk.prison.api.Currency;
import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.EconomyService;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.drill.DrillConfig.EnchantDef;
import dev.spacepunk.prison.drill.DrillConfig.FrameDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Owns per-player drill state, the drill item, and purchases. Main thread only. */
public final class DrillManager implements DrillService {
    public static final String DATA_KEY = "drill";
    private static final Gson GSON = new Gson();

    private final Logger log;
    private final DrillItems items;
    private DrillConfig cfg;
    private final Map<UUID, DrillData> data = new HashMap<>();
    private final Map<UUID, DrillMath.Effective> effective = new HashMap<>();
    /** Players whose stats changed since the last flush to their profile. */
    private final Set<UUID> statsDirty = ConcurrentHashMap.newKeySet();

    public DrillManager(Logger log, DrillItems items, DrillConfig cfg) {
        this.log = log;
        this.items = items;
        this.cfg = cfg;
    }

    public DrillConfig cfg() { return cfg; }

    public DrillItems items() { return items; }

    public void config(DrillConfig cfg) {
        this.cfg = cfg;
        items.config(cfg);
        effective.clear();
    }

    // ---- state ----

    public DrillData data(UUID id) { return data.get(id); }

    public DrillMath.Effective effective(UUID id) {
        DrillData d = data.get(id);
        if (d == null) return null;
        return effective.computeIfAbsent(id, k -> new DrillMath.Effective(cfg, d));
    }

    /** Loads (or creates) state from the profile. Returns true if this is a brand-new drill. */
    public boolean load(PlayerProfile profile) {
        String json = profile.moduleData(DATA_KEY);
        DrillData d = null;
        if (json != null) {
            try {
                d = GSON.fromJson(json, DrillData.class);
            } catch (JsonParseException e) {
                log.warning("Corrupt drill data for " + profile.lastName() + "; keeping a backup and starting fresh");
                profile.moduleData(DATA_KEY + "_corrupt", json);
            }
        }
        boolean fresh = d == null;
        if (fresh) d = new DrillData();
        d.normalize(cfg.firstFrame().id());
        data.put(profile.uuid(), d);
        effective.remove(profile.uuid());
        if (fresh) save(profile.uuid());
        return fresh;
    }

    public void unload(UUID id) {
        flush(id);
        data.remove(id);
        effective.remove(id);
        statsDirty.remove(id);
    }

    /** Writes state into the profile now. */
    public void save(UUID id) {
        DrillData d = data.get(id);
        if (d == null) return;
        ProfileService ps = Services.get(ProfileService.class);
        ps.get(id).ifPresent(p -> {
            p.moduleData(DATA_KEY, GSON.toJson(d));
            ps.markDirty(id);
        });
        statsDirty.remove(id);
    }

    public void markStatsDirty(UUID id) { statsDirty.add(id); }

    public void flush(UUID id) {
        if (statsDirty.contains(id)) save(id);
    }

    public void flushAll() {
        for (UUID id : Set.copyOf(statsDirty)) save(id);
    }

    /** Call after any change that affects effects or the item. */
    public void changed(Player p) {
        effective.remove(p.getUniqueId());
        save(p.getUniqueId());
        refreshItem(p);
    }

    // ---- the item ----

    /** Finds the player's own drill in their inventory, or -1. */
    public int drillSlot(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (p.getUniqueId().equals(items.owner(inv.getItem(i)))) return i;
        }
        return -1;
    }

    public boolean holdingOwnDrill(Player p) {
        return p.getUniqueId().equals(items.owner(p.getInventory().getItemInMainHand()));
    }

    /**
     * Makes sure the player has exactly one drill: removes duplicates and drills owned by someone else,
     * gives one if missing. Returns true if a drill had to be given.
     */
    public boolean ensureDrill(Player p) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return false;
        PlayerInventory inv = p.getInventory();
        boolean found = false;
        for (int i = 0; i < inv.getSize(); i++) {
            UUID owner = items.owner(inv.getItem(i));
            if (owner == null) continue;
            if (!owner.equals(p.getUniqueId()) || found) {
                inv.setItem(i, null);
                continue;
            }
            found = true;
            items.apply(inv.getItem(i), p.getUniqueId(), d);
        }
        if (found) return false;
        ItemStack drill = items.drill(p.getUniqueId(), d);
        if (inv.getItem(0) == null || inv.getItem(0).getType().isAir()) {
            inv.setItem(0, drill);
        } else {
            Map<Integer, ItemStack> left = inv.addItem(drill);
            if (!left.isEmpty()) {
                // Inventory full: the drill takes slot 0 and the old item drops at the player's feet.
                ItemStack displaced = inv.getItem(0);
                inv.setItem(0, drill);
                if (displaced != null) p.getWorld().dropItemNaturally(p.getLocation(), displaced);
            }
        }
        return true;
    }

    public void refreshItem(Player p) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return;
        int slot = drillSlot(p);
        if (slot < 0) return;
        ItemStack it = p.getInventory().getItem(slot);
        items.apply(it, p.getUniqueId(), d);
        p.getInventory().setItem(slot, it);
    }

    // ---- grade / prestige ----

    public int grade(Player p) {
        return Services.find(ProgressionService.class).map(s -> s.grade(p.getUniqueId()))
                .orElseGet(() -> Services.get(ProfileService.class).get(p.getUniqueId()).map(PlayerProfile::grade).orElse(1));
    }

    public int prestige(Player p) {
        return Services.find(ProgressionService.class).map(s -> s.prestige(p.getUniqueId()))
                .orElseGet(() -> Services.get(ProfileService.class).get(p.getUniqueId()).map(PlayerProfile::prestige).orElse(0));
    }

    // ---- purchases ----

    public record Quote(int levels, long cost) {}

    /** How many levels (up to want) the player can buy right now, and the total cost. */
    public Quote quote(Player p, Enchant e, int want) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return new Quote(0, 0);
        EnchantDef def = cfg.enchant(e);
        int cap = def.cap(cfg.frame(d.frame));
        long bal = Services.get(EconomyService.class).balance(p.getUniqueId(), Currency.TOKENS);
        int lvl = d.level(e);
        int n = 0;
        long total = 0;
        while (n < want && lvl + n < cap) {
            long c = def.cost(lvl + n + 1);
            if (total + c > bal) break;
            total += c;
            n++;
        }
        return new Quote(n, total);
    }

    /** Buys up to want levels. Returns the number bought (0 if none). */
    public int buy(Player p, Enchant e, int want) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return 0;
        Quote q = quote(p, e, want);
        if (q.levels() <= 0) return 0;
        if (!Services.get(EconomyService.class).take(p.getUniqueId(), Currency.TOKENS, q.cost(), "drill:enchant:" + e.id())) return 0;
        d.level(e, d.level(e) + q.levels());
        changed(p);
        return q.levels();
    }

    public enum FrameResult { OK, MAX, LOCKED, POOR }

    public FrameResult upgradeFrame(Player p) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return FrameResult.LOCKED;
        FrameDef next = cfg.nextFrame(cfg.frame(d.frame));
        if (next == null) return FrameResult.MAX;
        if (!next.unlockedFor(grade(p), prestige(p))) return FrameResult.LOCKED;
        if (next.cost() > 0 && !Services.get(EconomyService.class).take(p.getUniqueId(), next.costCurrency(), next.cost(), "drill:frame:" + next.id())) {
            return FrameResult.POOR;
        }
        d.frame = next.id();
        changed(p);
        return FrameResult.OK;
    }

    /** Admin: set a frame directly. Modules in sockets the new frame lacks go back to the player. */
    public void setFrame(Player p, FrameDef f) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return;
        d.frame = f.id();
        for (int i = f.sockets(); i < d.sockets.size(); i++) {
            ModuleDef m = cfg.module(d.socket(i));
            if (m != null) give(p, items.module(m));
            d.sockets.set(i, null);
        }
        changed(p);
    }

    /** Puts a module into the first free socket. Returns the socket index or -1 (no free socket / duplicate). */
    public int socket(Player p, ModuleDef m) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return -1;
        FrameDef f = cfg.frame(d.frame);
        for (int i = 0; i < f.sockets(); i++) {
            if (m.id().equals(d.socket(i))) return -2;
        }
        for (int i = 0; i < f.sockets(); i++) {
            if (cfg.module(d.socket(i)) == null) {
                d.socket(i, m.id());
                changed(p);
                return i;
            }
        }
        return -1;
    }

    /** Takes the module out of a socket and returns its item, or null if empty. */
    public ItemStack unsocket(Player p, int i) {
        DrillData d = data.get(p.getUniqueId());
        if (d == null) return null;
        ModuleDef m = cfg.module(d.socket(i));
        if (m == null) return null;
        d.socket(i, null);
        changed(p);
        return items.module(m);
    }

    public void give(Player p, ItemStack item) {
        for (ItemStack left : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    // ---- DrillService ----

    @Override public boolean autoSell(UUID player) {
        DrillMath.Effective e = effective(player);
        return e != null && e.autoSell;
    }

    @Override public double sellMultiplier(UUID player) {
        DrillMath.Effective e = effective(player);
        return e == null ? 1.0 : e.sellMultiplier;
    }

    @Override public double rareDropMultiplier(UUID player) {
        DrillMath.Effective e = effective(player);
        return e == null ? 1.0 : e.rareMultiplier;
    }

    @Override public int enchantLevel(UUID player, String enchantId) {
        DrillData d = data.get(player);
        Enchant e = Enchant.byId(enchantId);
        return d == null || e == null ? 0 : d.level(e);
    }

    @Override public String frameId(UUID player) {
        DrillData d = data.get(player);
        return d == null ? cfg.firstFrame().id() : cfg.frame(d.frame).id();
    }

    @Override public boolean isDrill(ItemStack item) { return items.isDrill(item); }
}
