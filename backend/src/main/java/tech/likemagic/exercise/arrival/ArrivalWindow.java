package tech.likemagic.exercise.arrival;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * The half-open instant range [from, until) covering one calendar day in one
 * time zone. Pure function of its inputs, so it is trivially unit testable
 * without a database.
 *
 * Computed in Java rather than in SQL so the zone comes from the property row
 * we already loaded and the query stays a plain index range scan on
 * (property_id, arrival). atStartOfDay(zone) also handles DST days correctly:
 * the window is 23 or 25 hours long on transition days, not 24.
 */
public record ArrivalWindow(OffsetDateTime from, OffsetDateTime until) {

    public static ArrivalWindow forDay(LocalDate day, ZoneId zone) {
        return new ArrivalWindow(
                day.atStartOfDay(zone).toOffsetDateTime(),
                day.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
    }
}
