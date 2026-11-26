package dev.spacepunk.prison.gangs;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Parsed gangs.yml. Immutable; reload swaps the instance. */
final class GangsConfig {
    final int createPrestige, joinPrestige;
    final long createCost;
    final int nameMin, nameMax, tagMin, tagMax;
    final int inviteSeconds;
    final int baseMaxMembers;
    final Map<String, String> colors = new LinkedHashMap<>();

    final Rank rankInvite, rankKick, rankWithdraw, rankUpgrade, rankMotd;

    final String chatFormat;
    final boolean friendlyFire;

    final int depositLockGrade;
    final int exMemberReleaseDays;
    final long minDeposit;
    final long baseCapacity;
    final int logSize;

    final double pointsPerBlock;
    final long creditsPerPoint;
    final long pointsPerRankup, pointsPerPrestige;
    final List<Long> levels = new ArrayList<>();

    final boolean upkeepEnabled;
    final int upkeepIntervalHours;
    final long upkeepPerMember, upkeepPerUpgradeLevel;

    final Map<String, UpgradeDef> upgrades = new LinkedHashMap<>();

    final int contractSlots;
    final Map<String, ContractDef> contractPool = new LinkedHashMap<>();

    final ZoneId weekZone;
    final List<Long> weeklyRewards = new ArrayList<>();

    final List<String> incomeReasons;
    final Map<String, String> territories = new LinkedHashMap<>();

    final int captureRadius, captureSeconds, labPayoutMinutes;
    final List<Long> labIncome = new ArrayList<>();
    final long capturePoints, creditsPerDose, killPoints;

    record UpgradeDef(String id, String name, Material icon, String description, int maxLevel, long baseCost,
                      double growth, double value, List<Integer> minGangLevel) {
        long cost(int level) { return Math.round(baseCost * Math.pow(growth, level - 1)); }

        int minLevelFor(int level) {
            if (minGangLevel.isEmpty()) return 1;
            return minGangLevel.get(Math.min(level, minGangLevel.size()) - 1);
        }
    }

    record ContractDef(String id, String name, String objective, long base, long perMember, long points, long credits) {
        long target(int members) { return base + perMember * Math.max(1, members); }
    }

    GangsConfig(YamlConfiguration y, Logger log) {
        createPrestige = y.getInt("create-prestige", 1);
        joinPrestige = y.getInt("join-prestige", 1);
        createCost = y.getLong("create-cost", 25000);
        nameMin = y.getInt("name-min", 3);
        nameMax = y.getInt("name-max", 16);
        tagMin = y.getInt("tag-min", 2);
        tagMax = y.getInt("tag-max", 5);
        inviteSeconds = y.getInt("invite-seconds", 300);
        baseMaxMembers = y.getInt("base-max-members", 6);
        ConfigurationSection cs = y.getConfigurationSection("colors");
        if (cs != null) for (String k : cs.getKeys(false)) colors.put(k.toLowerCase(Locale.ROOT), cs.getString(k, "#c8743a"));
        if (colors.isEmpty()) colors.put("rust", "#c8743a");

        rankInvite = Rank.parse(y.getString("ranks.invite"), Rank.OFFICER);
        rankKick = Rank.parse(y.getString("ranks.kick"), Rank.OFFICER);
        rankWithdraw = Rank.parse(y.getString("ranks.withdraw"), Rank.OFFICER);
        rankUpgrade = Rank.parse(y.getString("ranks.upgrade"), Rank.OFFICER);
        rankMotd = Rank.parse(y.getString("ranks.motd"), Rank.OFFICER);

        chatFormat = y.getString("chat.format", "<dark_gray>[<color>{tag}</color>]</dark_gray> <white>{player}</white>: {message}");
        friendlyFire = y.getBoolean("chat.friendly-fire", false);

        depositLockGrade = y.getInt("bank.deposit-lock-grade", 21);
        exMemberReleaseDays = y.getInt("bank.ex-member-release-days", 14);
        minDeposit = Math.max(1, y.getLong("bank.min-deposit", 100));
        baseCapacity = y.getLong("bank.base-capacity", 5_000_000);
        logSize = Math.max(5, y.getInt("bank.log-size", 60));

        pointsPerBlock = y.getDouble("points.per-block-mined", 0.05);
        creditsPerPoint = Math.max(1, y.getLong("points.credits-per-point", 20000));
        pointsPerRankup = y.getLong("points.per-rankup", 25);
        pointsPerPrestige = y.getLong("points.per-prestige", 2500);
        for (Object o : y.getList("levels", List.of(0))) {
            if (o instanceof Number n) levels.add(n.longValue());
        }
        if (levels.isEmpty()) levels.add(0L);

        upkeepEnabled = y.getBoolean("upkeep.enabled", true);
        upkeepIntervalHours = Math.max(1, y.getInt("upkeep.interval-hours", 24));
        upkeepPerMember = y.getLong("upkeep.per-member", 2500);
        upkeepPerUpgradeLevel = y.getLong("upkeep.per-upgrade-level", 5000);

        ConfigurationSection us = y.getConfigurationSection("upgrades");
        if (us != null) {
            for (String id : us.getKeys(false)) {
                ConfigurationSection u = us.getConfigurationSection(id);
                if (u == null) continue;
                Material icon = Material.matchMaterial(u.getString("icon", "PAPER"));
                if (icon == null) {
                    log.warning("gangs.yml: unknown icon for upgrade " + id);
                    icon = Material.PAPER;
                }
                List<Integer> min = new ArrayList<>();
                for (Object o : u.getList("min-gang-level", List.of())) if (o instanceof Number n) min.add(n.intValue());
                upgrades.put(id, new UpgradeDef(id, u.getString("name", id), icon, u.getString("description", ""),
                        Math.max(0, u.getInt("max-level", 1)), u.getLong("base-cost", 100000), u.getDouble("growth", 2.0),
                        u.getDouble("value", 1), min));
            }
        }

        contractSlots = Math.max(0, y.getInt("contracts.slots", 3));
        ConfigurationSection ps = y.getConfigurationSection("contracts.pool");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection c = ps.getConfigurationSection(id);
                if (c == null) continue;
                contractPool.put(id, new ContractDef(id, c.getString("name", id), c.getString("objective", "blocks_mined"),
                        c.getLong("base", 1000), c.getLong("per-member", 0), c.getLong("points", 0), c.getLong("credits", 0)));
            }
        }

        String zone = y.getString("weekly.zone", "");
        ZoneId z;
        try {
            z = zone == null || zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zone);
        } catch (Exception e) {
            log.warning("gangs.yml: bad weekly.zone '" + zone + "', using the server zone");
            z = ZoneId.systemDefault();
        }
        weekZone = z;
        for (Object o : y.getList("weekly.rewards", List.of())) if (o instanceof Number n) weeklyRewards.add(n.longValue());

        List<String> inc = y.getStringList("points.income-reasons");
        incomeReasons = inc.isEmpty() ? List.of("sell:", "income:") : List.copyOf(inc);

        captureRadius = Math.max(1, y.getInt("turf.capture-radius", 6));
        captureSeconds = y.getInt("turf.capture-seconds", 60);
        labPayoutMinutes = y.getInt("turf.payout-minutes", 60);
        for (Object o : y.getList("turf.lab-income", List.of())) if (o instanceof Number n) labIncome.add(n.longValue());
        capturePoints = y.getLong("turf.capture-points", 500);
        creditsPerDose = y.getLong("turf.credits-per-dose", 12000);
        killPoints = y.getLong("turf.kill-points", 15);

        ConfigurationSection ts = y.getConfigurationSection("territories");
        if (ts != null) for (String k : ts.getKeys(false)) territories.put(k, ts.getString(k, k));
    }

    int level(long points) {
        int lvl = 0;
        for (long t : levels) if (points >= t) lvl++;
        return Math.max(1, lvl);
    }

    /** Lifetime points needed for the next level, or -1 at max. */
    long nextLevelPoints(long points) {
        for (long t : levels) if (points < t) return t;
        return -1;
    }

    int maxMembers(Gang g) {
        UpgradeDef d = upgrades.get("bunks");
        return baseMaxMembers + (d == null ? 0 : (int) Math.round(d.value() * g.upgrade("bunks")));
    }

    long bankCapacity(Gang g) {
        UpgradeDef d = upgrades.get("vault");
        double mult = 1 + (d == null ? 0 : d.value() * g.upgrade("vault"));
        double cap = baseCapacity * mult;
        return cap >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) cap;
    }

    double sellBonus(Gang g) {
        UpgradeDef d = upgrades.get("fence");
        return d == null || g.perksSuspended ? 0 : d.value() * g.upgrade("fence");
    }

    int contractSlots(Gang g) {
        UpgradeDef d = upgrades.get("dispatch");
        return contractSlots + (d == null ? 0 : (int) Math.round(d.value() * g.upgrade("dispatch")));
    }

    long upkeep(Gang g) {
        int lv = 0;
        for (int v : g.upgrades.values()) lv += v;
        return upkeepPerMember * g.members.size() + upkeepPerUpgradeLevel * lv;
    }

    boolean isServerIncome(String reason) {
        if (reason == null) return false;
        for (String r : incomeReasons) if (reason.startsWith(r)) return true;
        return false;
    }

    long labIncome(int ring) {
        if (labIncome.isEmpty()) return 0;
        return labIncome.get(Math.max(0, Math.min(labIncome.size(), ring) - 1));
    }

    String territoryName(String id) { return territories.getOrDefault(id, id); }
}
