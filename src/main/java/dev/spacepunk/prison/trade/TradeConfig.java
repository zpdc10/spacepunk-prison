package dev.spacepunk.prison.trade;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** trade.yml */
final class TradeConfig {
    int requestSeconds;
    int countdownSeconds;
    int inputSeconds;
    /** Blocks between the two players, or -1 for anywhere on the server (any world). */
    double maxDistance;
    long maxCredits;
    /** From this grade on a player can't put Credits into a trade (0 = off). Mirrors the gang bank deposit lock. */
    int creditLockGrade;
    /** Days a return lock lasts after the sender prestiges (0 = off). */
    int returnLockDays;
    int logKeep;
    final Set<Material> blockedMaterials = EnumSet.noneOf(Material.class);
    /** Persistent data keys (plugin namespace) that mark an item as untradeable, e.g. the soulbound rod. */
    final Set<String> blockedKeys = new HashSet<>();

    static TradeConfig load(YamlConfiguration y) {
        TradeConfig c = new TradeConfig();
        c.requestSeconds = Math.max(5, y.getInt("request-seconds", 60));
        c.countdownSeconds = Math.max(1, y.getInt("countdown-seconds", 5));
        c.inputSeconds = Math.max(5, y.getInt("chat-input-seconds", 30));
        c.maxDistance = y.getDouble("max-distance", -1);
        c.maxCredits = Math.max(1, y.getLong("max-credits", 1_000_000_000_000_000L));
        c.creditLockGrade = y.getInt("prestige-lock.credit-lock-grade", 21);
        c.returnLockDays = Math.max(0, y.getInt("prestige-lock.return-lock-days", 30));
        c.logKeep = Math.max(50, y.getInt("log.keep-in-game", 1000));
        for (String m : y.getStringList("blocked-materials")) {
            Material mat = Material.matchMaterial(m);
            if (mat != null) c.blockedMaterials.add(mat);
        }
        c.blockedKeys.addAll(y.getStringList("blocked-item-keys"));
        if (!y.contains("blocked-item-keys")) c.blockedKeys.add("rod_owner");
        return c;
    }
}
