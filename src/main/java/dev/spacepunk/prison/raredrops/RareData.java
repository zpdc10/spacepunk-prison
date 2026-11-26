package dev.spacepunk.prison.raredrops;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Per-player rare drop state, stored as JSON in moduleData("raredrops"). */
final class RareData {
    /** Relic ids catalogued in the collection log. */
    Set<String> catalogued = new LinkedHashSet<>();
    /** Relic set ids whose completion reward was paid. */
    Set<String> setsClaimed = new LinkedHashSet<>();
    /** Lifetime finds per drop id. */
    Map<String, Long> found = new LinkedHashMap<>();
    /** Drops that did not fit in the inventory, waiting in /salvage: drop id -> amount. */
    Map<String, Long> stash = new LinkedHashMap<>();
    long totalFinds;
    long cachesOpened;
    long modulesAssembled;

    void normalize() {
        if (catalogued == null) catalogued = new LinkedHashSet<>();
        if (setsClaimed == null) setsClaimed = new LinkedHashSet<>();
        if (found == null) found = new LinkedHashMap<>();
        if (stash == null) stash = new LinkedHashMap<>();
    }

    long stashed() { return stash.values().stream().mapToLong(Long::longValue).sum(); }
}
