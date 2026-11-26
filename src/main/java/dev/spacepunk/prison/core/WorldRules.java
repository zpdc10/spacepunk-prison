package dev.spacepunk.prison.core;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-wide world rules from config.yml (worlds.*), applied to every loaded world and every world loaded later
 * (station, mines, fishing pools, workshop bays, Undercroft, and anything added in future).
 * <ul>
 *   <li>Weather: the space station has no sky, so rain and thunder are off permanently.</li>
 *   <li>Vanilla cleanup: no natural mob spawning (mines were filling with bats), existing natural mobs are removed,
 *       the daylight cycle is frozen at whatever each world set, and phantoms, patrols, wandering traders, raids,
 *       mob griefing, fire spread and advancement chat are switched off.</li>
 * </ul>
 * Mobs the plugin spawns itself (quest NPCs, fishing sea creatures) use the CUSTOM spawn reason and are left alone.
 */
public final class WorldRules implements Listener {
    /** Spawn reasons that come from vanilla mechanics rather than from the plugin or an admin command. */
    private static final Set<String> VANILLA_SPAWNS = Set.of(
            "NATURAL", "CHUNK_GEN", "DEFAULT", "JOCKEY", "MOUNT", "SPAWNER", "TRIAL_SPAWNER", "REINFORCEMENTS", "PATROL",
            "RAID", "VILLAGE_DEFENSE", "VILLAGE_INVASION", "TRAP", "LIGHTNING", "BREEDING", "EGG", "DROWNED", "INFECTION",
            "SILVERFISH_BLOCK", "ENDER_PEARL", "BUILD_SNOWMAN", "BUILD_IRONGOLEM", "BUILD_WITHER", "NETHER_PORTAL",
            "DISPENSE_EGG", "FROZEN", "METAMORPHOSIS", "DUPLICATION", "OCELOT_BABY", "SPELL", "BEEHIVE", "PIGLIN_ZOMBIFIED",
            "SLIME_SPLIT", "POTION_EFFECT", "ENCHANTMENT", "REANIMATE");

    private final Plugin plugin;
    private final boolean disableWeather;
    private final boolean vanillaCleanup;

    public WorldRules(Plugin plugin, boolean disableWeather, boolean vanillaCleanup) {
        this.plugin = plugin;
        this.disableWeather = disableWeather;
        this.vanillaCleanup = vanillaCleanup;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getWorlds().forEach(this::apply);
        // Worlds built by modules on first start may still be loading; sweep again once everything is up.
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getWorlds().forEach(this::apply), 100L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) { apply(e.getWorld()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (disableWeather && e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent e) {
        if (disableWeather && e.toThunderState()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (vanillaCleanup && VANILLA_SPAWNS.contains(e.getSpawnReason().name())) e.setCancelled(true);
    }

    /** Chunks that load later (e.g. a mine nobody has visited since startup) get their stray mobs cleared too. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        if (!vanillaCleanup) return;
        List<Entity> list = e.getEntities();
        Bukkit.getScheduler().runTask(plugin, () -> list.forEach(this::removeIfStray));
    }

    private void apply(World w) {
        if (disableWeather) {
            rule(w, false, "doWeatherCycle", "advance_weather", "minecraft:advance_weather");
            w.setStorm(false);
            w.setThundering(false);
            w.setClearWeatherDuration(Integer.MAX_VALUE / 2);
        }
        if (!vanillaCleanup) return;
        rule(w, false, "doMobSpawning", "spawn_mobs", "minecraft:spawn_mobs");
        rule(w, false, "doDaylightCycle", "advance_time", "minecraft:advance_time");
        rule(w, false, "doInsomnia", "spawn_phantoms", "minecraft:spawn_phantoms");
        rule(w, false, "doPatrolSpawning", "spawn_patrols", "minecraft:spawn_patrols");
        rule(w, false, "doTraderSpawning", "spawn_wandering_traders", "minecraft:spawn_wandering_traders");
        rule(w, false, "doWardenSpawning", "spawn_wardens", "minecraft:spawn_wardens");
        rule(w, true, "disableRaids");
        rule(w, false, "raids", "minecraft:raids");
        rule(w, false, "mobGriefing", "mob_griefing", "minecraft:mob_griefing");
        rule(w, false, "doFireTick", "fire_spread", "minecraft:fire_spread");
        rule(w, false, "announceAdvancements", "show_advancement_messages", "minecraft:show_advancement_messages");
        rule(w, false, "spawnMonsters", "spawn_monsters", "minecraft:spawn_monsters");
        w.getEntities().forEach(this::removeIfStray);
    }

    /** Removes a vanilla-spawned mob. Anything named or carrying this plugin's data is kept. */
    private void removeIfStray(Entity en) {
        if (!(en instanceof Mob) || !en.isValid() || en.customName() != null) return;
        String ns = plugin.getName().toLowerCase(Locale.ROOT);
        for (NamespacedKey k : en.getPersistentDataContainer().getKeys()) if (k.getNamespace().equals(ns)) return;
        if (VANILLA_SPAWNS.contains(en.getEntitySpawnReason().name())) en.remove();
    }

    /** Sets a boolean game rule by whichever name this server version uses; skips names this version lacks. */
    @SuppressWarnings({"unchecked", "deprecation"})
    private static void rule(World w, boolean value, String... names) {
        for (String n : names) {
            GameRule<?> r = null;
            try {
                r = (GameRule<?>) GameRule.class.getMethod("getByName", String.class).invoke(null, n);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // lookup not available on this version
            }
            if (r == null) {
                for (GameRule<?> g : GameRule.values()) if (g.getName().equalsIgnoreCase(n)) { r = g; break; }
            }
            if (r != null && r.getType() == Boolean.class) {
                try {
                    w.setGameRule((GameRule<Boolean>) r, value);
                } catch (RuntimeException ignored) {
                    // rule not settable here
                }
                return;
            }
        }
    }
}
