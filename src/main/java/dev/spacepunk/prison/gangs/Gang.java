package dev.spacepunk.prison.gangs;

import dev.spacepunk.prison.api.GangService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One gang, saved to gangs-data.json with Gson. Field names are JSON keys; keep them stable.
 * Main thread for writes. Async chat only reads name/tag/members.
 */
final class Gang implements GangService.GangInfo {
    String id;
    String name;
    String tag;
    String color = "#c8743a";
    String motd = "";
    long created;

    /** uuid string -> member. LinkedHashMap keeps join order. */
    Map<String, Member> members = new LinkedHashMap<>();

    long bank;
    /** Prestige lock: Credits each player deposited this run and has not had back (uuid string -> parked). */
    Map<String, Parked> parked = new HashMap<>();
    List<LogEntry> log = new ArrayList<>();

    Map<String, Integer> upgrades = new HashMap<>();
    long points;
    long weeklyPoints;
    long nextUpkeepAt;
    boolean perksSuspended;

    List<Contract> contracts = new ArrayList<>();

    static final class Member {
        String name;
        Rank rank = Rank.RECRUIT;
        long joined;
        /** Last known clearance grade (kept for offline members, drives the prestige lock). */
        int grade = 1;
        long deposited;
        long pointsEarned;
        long weeklyPoints;
    }

    static final class Parked {
        long amount;
        /** Owner's last known grade. */
        int grade = 1;
        /** 0 while a member; time they left otherwise. */
        long leftAt;
    }

    static final class LogEntry {
        long at;
        String who;
        String what;
        long amount;

        LogEntry() {}

        LogEntry(long at, String who, String what, long amount) {
            this.at = at;
            this.who = who;
            this.what = what;
            this.amount = amount;
        }
    }

    static final class Contract {
        String id;
        String name;
        String objective;
        long target;
        long progress;
        long points;
        long credits;
        boolean done;
    }

    // ---- helpers ----

    Member member(UUID id) { return members.get(id.toString()); }

    Rank rank(UUID id) {
        Member m = member(id);
        return m == null ? null : m.rank;
    }

    UUID leader() {
        for (Map.Entry<String, Member> e : members.entrySet()) {
            if (e.getValue().rank == Rank.LEADER) return UUID.fromString(e.getKey());
        }
        return null;
    }

    int upgrade(String id) { return upgrades.getOrDefault(id, 0); }

    long parkedTotal() {
        long t = 0;
        for (Parked p : parked.values()) t += p.amount;
        return t;
    }

    void log(String who, String what, long amount, int max) {
        log.add(0, new LogEntry(System.currentTimeMillis(), who, what, amount));
        while (log.size() > max) log.remove(log.size() - 1);
    }

    // ---- GangInfo ----

    @Override public String id() { return id; }
    @Override public String name() { return name; }
    @Override public String tag() { return tag; }
    @Override public String coloredTag() { return "<" + color + ">" + tag + "</" + color + ">"; }
    @Override public int level() { return GangsModule.config().level(points); }

    @Override public Set<UUID> members() {
        Set<UUID> out = new LinkedHashSet<>();
        for (String k : List.copyOf(members.keySet())) out.add(UUID.fromString(k));
        return out;
    }

    @Override public String rankOf(UUID player) {
        Rank r = rank(player);
        return r == null ? null : r.id();
    }

    @Override public long bank() { return bank; }
    @Override public long weeklyPoints() { return weeklyPoints; }
}
