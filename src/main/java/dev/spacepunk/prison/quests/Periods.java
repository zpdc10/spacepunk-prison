package dev.spacepunk.prison.quests;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/** Reset keys and countdowns for daily (midnight), weekly (Monday midnight) and monthly contracts. */
final class Periods {
    private Periods() {}

    static String day(ZoneId z) { return LocalDate.now(z).toString(); }

    static String week(ZoneId z) {
        LocalDate d = LocalDate.now(z);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    static String month(ZoneId z) {
        LocalDate d = LocalDate.now(z);
        return d.getYear() + "-" + (d.getMonthValue() < 10 ? "0" : "") + d.getMonthValue();
    }

    static Duration untilDay(ZoneId z) {
        ZonedDateTime now = ZonedDateTime.now(z);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(z));
    }

    static Duration untilWeek(ZoneId z) {
        ZonedDateTime now = ZonedDateTime.now(z);
        LocalDate next = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return Duration.between(now, next.atStartOfDay(z));
    }

    static Duration untilMonth(ZoneId z) {
        ZonedDateTime now = ZonedDateTime.now(z);
        LocalDate next = now.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth());
        return Duration.between(now, next.atStartOfDay(z));
    }

    /** "5h 12m", "2d 3h", "45s". */
    static String format(Duration d) {
        long s = Math.max(0, d.getSeconds());
        long days = s / 86400, hours = (s % 86400) / 3600, mins = (s % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        if (mins > 0) return mins + "m";
        return s + "s";
    }
}
