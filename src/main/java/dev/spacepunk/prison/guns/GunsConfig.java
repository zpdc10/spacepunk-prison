package dev.spacepunk.prison.guns;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** guns.yml, parsed once. Immutable; reload builds a new one. */
public final class GunsConfig {
    public record AmmoDef(String id, String name, String lore, int pack, long price) {}

    public record GunDef(String id, String name, String lore, String ammo, double damage, double headshot,
                         int fireDelay, int burst, int burstDelay, int magazine, int reloadTicks, double range,
                         double spread, double sneakSpread, double recoil, int pellets, int pierce,
                         double falloffStart, double falloffMin, int zoom, String sound, float pitch, Color tracer,
                         long price, int parts, int prestige) {
        /**
         * This gun with Weapon Rig module multipliers applied (stat -> multiplier, see DrillService#gearMultipliers).
         * Returns this when nothing changes.
         */
        public GunDef tuned(java.util.Map<String, Double> m) {
            if (m == null || m.isEmpty()) return this;
            double dmg = damage * m.getOrDefault("damage", 1.0);
            double rate = Math.max(0.1, m.getOrDefault("fire-rate", 1.0));
            int delay = Math.max(1, (int) Math.round(fireDelay / rate));
            int bDelay = burstDelay <= 0 ? burstDelay : Math.max(1, (int) Math.round(burstDelay / rate));
            int mag = Math.max(1, (int) Math.round(magazine * m.getOrDefault("magazine", 1.0)));
            int reload = Math.max(4, (int) Math.round(reloadTicks * m.getOrDefault("reload-time", 1.0)));
            double rng = Math.max(4, range * m.getOrDefault("range", 1.0));
            double sp = m.getOrDefault("spread", 1.0);
            return new GunDef(id, name, lore, ammo, dmg, headshot * m.getOrDefault("headshot", 1.0), delay, burst, bDelay, mag, reload,
                    rng, spread * sp, sneakSpread * sp, recoil * m.getOrDefault("recoil", 1.0), pellets, pierce,
                    Math.min(rng, falloffStart * m.getOrDefault("range", 1.0)), falloffMin, zoom, sound, pitch, tracer, price, parts, prestige);
        }

        /** Damage multiplier at this distance. */
        public double falloff(double distance) {
            if (distance <= falloffStart || range <= falloffStart) return 1.0;
            double t = Math.min(1.0, (distance - falloffStart) / (range - falloffStart));
            return 1.0 - t * (1.0 - falloffMin);
        }
    }

    public final boolean itemModels;
    public final Material gunMaterial, ammoMaterial, partsMaterial;
    public final List<String> extraWorlds;
    public final boolean hitMobsAnywhere, tracers;
    public final int hitMemorySeconds;
    public final double priceGrowth;
    public final String partsName, partsLore;
    public final Map<String, AmmoDef> ammo = new LinkedHashMap<>();
    public final Map<String, GunDef> guns = new LinkedHashMap<>();

    private GunsConfig(YamlConfiguration y, Logger log) {
        itemModels = y.getBoolean("item-models", true);
        gunMaterial = material(y.getString("gun-material"), Material.CARROT_ON_A_STICK, log);
        ammoMaterial = material(y.getString("ammo-material"), Material.PAPER, log);
        partsMaterial = material(y.getString("parts-material"), Material.PAPER, log);
        extraWorlds = y.getStringList("extra-worlds");
        hitMobsAnywhere = y.getBoolean("hit-mobs-anywhere", false);
        tracers = y.getBoolean("tracers", true);
        hitMemorySeconds = y.getInt("hit-memory-seconds", 8);
        priceGrowth = y.getDouble("gunsmith.price-grade-growth", 1.21);
        partsName = y.getString("parts.name", "<#d6a640>Gun Parts");
        partsLore = y.getString("parts.lore", "");

        ConfigurationSection as = y.getConfigurationSection("ammo");
        if (as != null) for (String id : as.getKeys(false)) {
            ConfigurationSection s = as.getConfigurationSection(id);
            if (s == null) continue;
            ammo.put(id, new AmmoDef(id, s.getString("name", id), s.getString("lore", ""),
                    Math.max(1, s.getInt("pack", 16)), Math.max(0, s.getLong("price", 500))));
        }
        ConfigurationSection gs = y.getConfigurationSection("guns");
        if (gs != null) for (String id : gs.getKeys(false)) {
            ConfigurationSection s = gs.getConfigurationSection(id);
            if (s == null) continue;
            String ammoId = s.getString("ammo", "pistol");
            if (!ammo.containsKey(ammoId)) log.warning("[guns] " + id + " uses unknown ammo " + ammoId);
            Color tracer;
            try {
                tracer = Color.fromRGB(Integer.parseInt(s.getString("tracer", "#ffffff").replace("#", ""), 16));
            } catch (IllegalArgumentException e) {
                tracer = Color.WHITE;
            }
            guns.put(id, new GunDef(id, s.getString("name", id), s.getString("lore", ""), ammoId,
                    s.getDouble("damage", 4), s.getDouble("headshot", 1.5),
                    Math.max(1, s.getInt("fire-delay", 6)), Math.max(1, s.getInt("burst", 1)),
                    Math.max(0, s.getInt("burst-delay", 0)), Math.max(1, s.getInt("magazine", 10)),
                    Math.max(1, s.getInt("reload-ticks", 30)), Math.max(1, s.getDouble("range", 40)),
                    s.getDouble("spread", 1.5), s.getDouble("sneak-spread", 0.5), s.getDouble("recoil", 2),
                    Math.max(1, s.getInt("pellets", 1)), Math.max(0, s.getInt("pierce", 0)),
                    s.getDouble("falloff-start", 20), s.getDouble("falloff-min", 0.5), Math.max(0, s.getInt("zoom", 0)),
                    s.getString("sound", "entity.firework_rocket.blast").toLowerCase(Locale.ROOT),
                    (float) s.getDouble("pitch", 1.5), tracer,
                    Math.max(0, s.getLong("gunsmith.price", 10000)), Math.max(0, s.getInt("gunsmith.parts", 0)),
                    Math.max(0, s.getInt("gunsmith.prestige", 0))));
        }
    }

    public static GunsConfig load(File file, Logger log) {
        return new GunsConfig(YamlConfiguration.loadConfiguration(file), log);
    }

    private static Material material(String name, Material def, Logger log) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isItem()) {
            log.warning("[guns] unknown item material " + name + ", using " + def);
            return def;
        }
        return m;
    }
}
