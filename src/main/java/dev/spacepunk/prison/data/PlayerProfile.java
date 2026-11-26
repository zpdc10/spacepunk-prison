package dev.spacepunk.prison.data;

import dev.spacepunk.prison.api.Currency;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything persisted about one player. Main-thread access only.
 *
 * Core fields (balances, grade, prestige) are typed. Each feature module stores its own state as a JSON
 * string under its own key in {@link #moduleData} (e.g. "drill", "cargo"), and parses it with Gson itself.
 * Call ProfileService.markDirty(uuid) after changing anything.
 */
public final class PlayerProfile {
    private final UUID uuid;
    private String lastName;
    private final EnumMap<Currency, Long> balances = new EnumMap<>(Currency.class);
    private int grade = 1;
    private int prestige = 0;
    private final Map<String, String> moduleData = new HashMap<>();

    public PlayerProfile(UUID uuid, String lastName) {
        this.uuid = uuid;
        this.lastName = lastName;
        for (Currency c : Currency.values()) balances.put(c, 0L);
    }

    public UUID uuid() { return uuid; }
    public String lastName() { return lastName; }
    public void lastName(String name) { this.lastName = name; }

    public long balance(Currency c) { return balances.getOrDefault(c, 0L); }
    /** Raw setter; prefer EconomyService so events fire. */
    public void balance(Currency c, long v) { balances.put(c, Math.max(0, v)); }

    public int grade() { return grade; }
    /** Raw setter; prefer ProgressionService so events fire. */
    public void grade(int g) { this.grade = g; }

    public int prestige() { return prestige; }
    /** Raw setter; prefer ProgressionService so events fire. */
    public void prestige(int p) { this.prestige = p; }

    /** JSON blob owned by a module, or null if none yet. */
    public String moduleData(String key) { return moduleData.get(key); }
    public void moduleData(String key, String json) {
        if (json == null) moduleData.remove(key); else moduleData.put(key, json);
    }
    public Map<String, String> allModuleData() { return moduleData; }
}
