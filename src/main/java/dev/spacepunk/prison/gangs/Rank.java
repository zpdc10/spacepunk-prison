package dev.spacepunk.prison.gangs;

import java.util.Locale;

/** Gang ranks, lowest first. */
enum Rank {
    RECRUIT("Recruit", "<gray>"),
    MEMBER("Member", "<white>"),
    OFFICER("Officer", "<#e0b020>"),
    LEADER("Leader", "<#c8743a>");

    final String display;
    final String color;

    Rank(String display, String color) {
        this.display = display;
        this.color = color;
    }

    boolean atLeast(Rank other) { return ordinal() >= other.ordinal(); }

    String id() { return name().toLowerCase(Locale.ROOT); }

    String colored() { return color + display; }

    Rank up() { return this == LEADER || this == OFFICER ? this : values()[ordinal() + 1]; }

    Rank down() { return this == RECRUIT || this == LEADER ? this : values()[ordinal() - 1]; }

    static Rank parse(String s, Rank def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
