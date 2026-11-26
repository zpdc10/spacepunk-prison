package dev.spacepunk.prison.api;

/** The three core currencies. Amounts are whole numbers (long). */
public enum Currency {
    /** Scrip earned by selling ore. Spent on grade rank-ups, cargo hold, auction house. */
    CREDITS("Credits", "<gold>"),
    /** Dropped while mining. Spent on drill enchants. */
    TOKENS("Tokens", "<aqua>"),
    /** Earned by prestiging. Spent on drill frames and permanent bonuses. */
    SHARDS("Prestige Shards", "<light_purple>");

    private final String displayName;
    private final String color;

    Currency(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() { return displayName; }

    /** MiniMessage color tag for this currency, e.g. "&lt;gold&gt;". */
    public String color() { return color; }
}
