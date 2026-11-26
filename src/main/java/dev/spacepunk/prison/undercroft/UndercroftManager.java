package dev.spacepunk.prison.undercroft;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.spacepunk.prison.api.ProfileService;
import dev.spacepunk.prison.api.ProgressionService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.api.UndercroftService;
import dev.spacepunk.prison.data.PlayerProfile;
import dev.spacepunk.prison.undercroft.UndercroftConfig.LabSite;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Shared state for the Undercroft and the {@link UndercroftService} other modules use. */
final class UndercroftManager implements UndercroftService {
    static final String DATA_KEY = "undercroft";
    private static final Gson GSON = new Gson();

    private final Plugin plugin;
    private UndercroftConfig cfg;
    final UndercroftWorld world;
    final UndercroftStore store;
    final Contraband items;
    final StimManager stims;
    private final Map<UUID, PlayerData> data = new HashMap<>();
    /** Combat tag expiry (epoch ms). */
    private final Map<UUID, Long> tags = new HashMap<>();
    /** Grace expiry (epoch ms). */
    private final Map<UUID, Long> grace = new HashMap<>();
    /** Labs with a cook in progress, lab id -> cook. */
    final Map<String, UUID> cooking = new HashMap<>();

    UndercroftManager(Plugin plugin, UndercroftConfig cfg, UndercroftStore store) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.store = store;
        this.world = new UndercroftWorld(plugin, cfg);
        this.items = new Contraband(cfg);
        this.stims = new StimManager(this);
    }

    Plugin plugin() { return plugin; }
    UndercroftConfig cfg() { return cfg; }

    void config(UndercroftConfig cfg) {
        this.cfg = cfg;
        world.config(cfg);
        items.config(cfg);
    }

    // ---- player data ----

    PlayerData data(Player p) { return data.get(p.getUniqueId()); }

    void load(Player p, PlayerProfile profile) {
        PlayerData d = null;
        String json = profile.moduleData(DATA_KEY);
        if (json != null) {
            try {
                d = GSON.fromJson(json, PlayerData.class);
            } catch (JsonSyntaxException e) {
                plugin.getLogger().warning("[undercroft] bad data for " + p.getName() + ", resetting");
            }
        }
        data.put(p.getUniqueId(), d == null ? new PlayerData() : d);
    }

    void save(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) return;
        Services.find(ProfileService.class).ifPresent(ps -> ps.get(p.getUniqueId()).ifPresent(profile -> {
            profile.moduleData(DATA_KEY, GSON.toJson(d));
            ps.markDirty(p.getUniqueId());
        }));
    }

    void unload(Player p) {
        save(p);
        data.remove(p.getUniqueId());
        tags.remove(p.getUniqueId());
        grace.remove(p.getUniqueId());
    }

    // ---- combat tag + grace ----

    void tag(Player p) { tags.put(p.getUniqueId(), System.currentTimeMillis() + cfg.tagSeconds * 1000L); }

    long tagLeft(UUID id) {
        Long t = tags.get(id);
        long left = t == null ? 0 : t - System.currentTimeMillis();
        if (t != null && left <= 0) tags.remove(id);
        return Math.max(0, left);
    }

    void untag(UUID id) { tags.remove(id); }

    void giveGrace(Player p) {
        int g = cfg.graceSeconds;
        if (prestige(p) == 0 && grade(p) <= cfg.newMaxGrade) g = Math.max(g, cfg.newGraceSeconds);
        if (g > 0) grace.put(p.getUniqueId(), System.currentTimeMillis() + g * 1000L);
    }

    long graceLeft(UUID id) {
        Long t = grace.get(id);
        long left = t == null ? 0 : t - System.currentTimeMillis();
        if (t != null && left <= 0) grace.remove(id);
        return Math.max(0, left);
    }

    void endGrace(UUID id) { grace.remove(id); }

    // ---- progression helpers ----

    int grade(Player p) { return Services.find(ProgressionService.class).map(s -> s.grade(p.getUniqueId())).orElse(1); }

    int prestige(Player p) { return Services.find(ProgressionService.class).map(s -> s.prestige(p.getUniqueId())).orElse(0); }

    /** Grade-scaled price: base x growth^(grade - 1). */
    long scale(int grade, long base) { return Math.round(base * Math.pow(cfg.growth, Math.max(0, grade - 1))); }

    long scale(Player p, long base) { return scale(grade(p), base); }

    // ---- places ----

    /** Where players go when they leave or die: the station hub if there is one, else the fallback world spawn. */
    Location exitLocation(Player p) {
        Location hub = Services.find(dev.spacepunk.prison.api.WarpService.class).map(dev.spacepunk.prison.api.WarpService::hub).orElse(null);
        if (hub != null && !world.isWorld(hub.getWorld())) return hub.clone();
        World w = Bukkit.getWorld(cfg.fallbackWorld);
        if (w == null || world.isWorld(w)) w = Bukkit.getWorlds().getFirst();
        return w.getSpawnLocation().add(0.5, 0, 0.5);
    }

    void give(Player p, ItemStack item) {
        if (item == null) return;
        for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    /** Message to everyone currently in the Undercroft. */
    void broadcastZone(String mm) {
        World w = world.world();
        if (w == null) return;
        for (Player p : w.getPlayers()) Text.send(p, mm);
    }

    int playersInZone() {
        World w = world.world();
        return w == null ? 0 : w.getPlayers().size();
    }

    // ---- UndercroftService ----

    @Override public boolean inUndercroft(Location location) { return world.ringAt(location) >= 0; }

    @Override public boolean pvpAllowed(Location location) { return world.ringAt(location) >= 1; }

    @Override public int ringAt(Location location) { return world.ringAt(location); }

    @Override public boolean combatTagged(UUID player) { return tagLeft(player) > 0; }

    @Override public boolean isContraband(ItemStack item) { return items.isContraband(item); }

    @Override
    public Collection<LabInfo> labs() {
        List<LabInfo> out = new ArrayList<>();
        for (LabSite s : cfg.labs.values()) out.add(info(s));
        return out;
    }

    @Override
    public Optional<LabInfo> lab(String labId) {
        LabSite s = cfg.labs.get(labId);
        return s == null ? Optional.empty() : Optional.of(info(s));
    }

    @Override
    public void setLabController(String labId, String controllerId, String controllerName, boolean announce) {
        LabSite s = cfg.labs.get(labId);
        if (s == null) return;
        if (controllerId == null) store.controllers.remove(labId);
        else store.controllers.put(labId, new UndercroftStore.Controller(controllerId, controllerName == null ? controllerId : controllerName));
        store.save();
        if (announce) {
            String msg = controllerId == null
                    ? "<gray>" + s.name() + " <gray>is up for grabs. Nobody holds it."
                    : "<white>" + (controllerName == null ? controllerId : controllerName) + " <gray>now controls " + s.name() + "<gray> in the Undercroft.";
            for (Player p : Bukkit.getOnlinePlayers()) Text.send(p, msg);
        }
    }

    @Override public Optional<String> activeStim(Player player) { return stims.active(player); }

    private LabInfo info(LabSite s) {
        World w = world.world();
        Location loc = w == null ? null : world.labLocation(w, s);
        int ring = loc == null ? world.ringOfX(s.x()) : world.ringAt(loc);
        UndercroftStore.Controller c = store.controllers.get(s.id());
        boolean busy = cooking.containsKey(s.id());
        return new LabInfo() {
            @Override public String id() { return s.id(); }
            @Override public String displayName() { return s.name(); }
            @Override public Location location() { return loc == null ? null : loc.clone(); }
            @Override public int ring() { return ring; }
            @Override public Optional<String> controllerId() { return c == null ? Optional.empty() : Optional.of(c.id()); }
            @Override public Optional<String> controllerName() { return c == null ? Optional.empty() : Optional.of(c.name()); }
            @Override public boolean cooking() { return busy; }
        };
    }
}
