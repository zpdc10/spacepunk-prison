package dev.spacepunk.prison.util;

/** Short number formatting: 1234 -> "1.23K", 5_600_000 -> "5.6M". */
public final class Numbers {
    private static final String[] SUFFIX = {"", "K", "M", "B", "T", "Q", "Qi", "Sx"};

    private Numbers() {}

    public static String shortFormat(long value) {
        if (value < 0) return "-" + shortFormat(-value);
        if (value < 1000) return Long.toString(value);
        int idx = 0;
        double v = value;
        while (v >= 1000 && idx < SUFFIX.length - 1) {
            v /= 1000;
            idx++;
        }
        String s = v >= 100 ? String.format("%.0f", v) : v >= 10 ? String.format("%.1f", v) : String.format("%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + SUFFIX[idx];
    }

    /** Parses "1.5k", "2M", "300" etc. Throws NumberFormatException on bad input. */
    public static long parse(String in) {
        String s = in.trim().toUpperCase();
        for (int i = SUFFIX.length - 1; i > 0; i--) {
            if (s.endsWith(SUFFIX[i].toUpperCase())) {
                double base = Double.parseDouble(s.substring(0, s.length() - SUFFIX[i].length()));
                return Math.round(base * Math.pow(1000, i));
            }
        }
        return Long.parseLong(s);
    }
}
