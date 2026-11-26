package dev.spacepunk.prison.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-player guide state, stored as JSON in the profile under module data "guide". */
final class GuideData {
    /** Chapters whose first-visit popup has fired (each fires once). */
    Set<String> seen = new LinkedHashSet<>();
    /** Chapters whose guide book was opened. */
    Set<String> read = new LinkedHashSet<>();
    /** Checklists finished or skipped. */
    Set<String> done = new LinkedHashSet<>();
    /** Running checklists; the last one is shown on the boss bar. */
    List<String> active = new ArrayList<>();
    /** Current step index per running checklist. */
    Map<String, Integer> step = new HashMap<>();
    /** Progress on the current step per running checklist. */
    Map<String, Long> have = new HashMap<>();
    Boolean popups;
    Boolean bar;

    void normalize() {
        if (seen == null) seen = new LinkedHashSet<>();
        if (read == null) read = new LinkedHashSet<>();
        if (done == null) done = new LinkedHashSet<>();
        if (active == null) active = new ArrayList<>();
        if (step == null) step = new HashMap<>();
        if (have == null) have = new HashMap<>();
    }
}
