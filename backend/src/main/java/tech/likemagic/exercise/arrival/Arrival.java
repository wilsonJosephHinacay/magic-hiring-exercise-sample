package tech.likemagic.exercise.arrival;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.lang.Nullable;
import tech.likemagic.exercise.reservation.ReservationStatus;
import tech.likemagic.exercise.unit.Unit;

/**
 * GraphQL view of one arriving reservation. Not a table: it is assembled in
 * {@link ArrivalService} from a Reservation row and (optionally) its Unit row.
 */
public record Arrival(
        UUID reservationId,
        String guestName,
        OffsetDateTime arrival,
        ReservationStatus status,
        @Nullable Unit unit          // null when no unit has been assigned yet
) {}
