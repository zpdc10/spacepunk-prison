package dev.spacepunk.prison.drill;

import java.util.Locale;

/** Token enchants (and the Coolant system upgrade). Numbers come from drill.yml; see {@link DrillMath}. */
public enum Enchant {
    BORE_SPEED, YIELD, TOKEN_SIPHON, SHAPED_CHARGE, JACKHAMMER, CUTTING_LASER, OVERCLOCK, SALVAGE_SENSE, COOLANT;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Enchant byId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
