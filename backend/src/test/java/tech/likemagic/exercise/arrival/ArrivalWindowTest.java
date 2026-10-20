package tech.likemagic.exercise.arrival;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test, no Spring context and no database. Pins down the "today in
 * the property's zone" rule the arrivals query depends on.
 */
class ArrivalWindowTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Test
    void coversTheLocalDayAsAHalfOpenRange() {
        ArrivalWindow w = ArrivalWindow.forDay(LocalDate.of(2026, 10, 20), ZURICH);

        // 20 Oct 2026 is CEST (UTC+2): local midnight is 22:00 UTC the day before.
        assertThat(w.from()).isEqualTo(OffsetDateTime.parse("2026-10-20T00:00+02:00"));
        assertThat(w.until()).isEqualTo(OffsetDateTime.parse("2026-10-21T00:00+02:00"));
        assertThat(w.from().toInstant()).isEqualTo(OffsetDateTime.parse("2026-10-19T22:00Z").toInstant());
    }

    @Test
    void sameCalendarDateIsADifferentInstantRangeInAnotherZone() {
        LocalDate day = LocalDate.of(2026, 10, 20);
        ArrivalWindow zurich = ArrivalWindow.forDay(day, ZURICH);
        ArrivalWindow sydney = ArrivalWindow.forDay(day, SYDNEY);

        // Sydney (UTC+11 in October) starts its day 9 hours before Zurich does.
        assertThat(Duration.between(sydney.from().toInstant(), zurich.from().toInstant()))
                .isEqualTo(Duration.ofHours(9));
    }

    @Test
    void dstTransitionDaysAreNotTwentyFourHoursLong() {
        // Europe/Zurich springs forward on 29 Mar 2026 (23h day) and falls back on 25 Oct 2026 (25h day).
        ArrivalWindow spring = ArrivalWindow.forDay(LocalDate.of(2026, 3, 29), ZURICH);
        ArrivalWindow autumn = ArrivalWindow.forDay(LocalDate.of(2026, 10, 25), ZURICH);

        assertThat(Duration.between(spring.from(), spring.until())).isEqualTo(Duration.ofHours(23));
        assertThat(Duration.between(autumn.from(), autumn.until())).isEqualTo(Duration.ofHours(25));
    }
}
