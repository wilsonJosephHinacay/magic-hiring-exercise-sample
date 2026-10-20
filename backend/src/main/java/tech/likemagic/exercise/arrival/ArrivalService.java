package tech.likemagic.exercise.arrival;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.likemagic.exercise.property.PropertyRepository;
import tech.likemagic.exercise.reservation.Reservation;
import tech.likemagic.exercise.reservation.ReservationRepository;
import tech.likemagic.exercise.reservation.ReservationStatus;
import tech.likemagic.exercise.unit.Unit;
import tech.likemagic.exercise.unit.UnitRepository;

/**
 * Builds the arrivals list for one property and one local day.
 *
 * The chain issues at most three SQL statements regardless of how many
 * arrivals there are: load the property (for its zone), load the day's
 * reservations, load the referenced units in one IN (...) query. The
 * tempting alternative, flatMap over reservations and look each unit up
 * individually, is N+1 and would hold N connections from a pool of 20 per
 * request. Non-blocking does not make that cheap, it only makes it concurrent.
 *
 * Nothing here blocks the calling thread. Each repository call returns a
 * publisher and the event-loop thread is released while Postgres works.
 */
@Service
public class ArrivalService {

    /**
     * What a host considers an arrival for the day. Cancellations and no-shows
     * are noise on an arrivals board. IN_HOUSE stays: the guest arrived today
     * and the host still wants to see them. CHECKED_OUT (same-day) is rare but
     * is still a guest who arrived today.
     */
    static final Set<String> LISTED_STATUSES =
            EnumSet.complementOf(EnumSet.of(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW))
                    .stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final PropertyRepository properties;
    private final ReservationRepository reservations;
    private final UnitRepository units;

    public ArrivalService(PropertyRepository properties,
                          ReservationRepository reservations,
                          UnitRepository units) {
        this.properties = properties;
        this.reservations = reservations;
        this.units = units;
    }

    /**
     * @param date the local day at the property, or null for "today" there.
     */
    public Flux<Arrival> arrivals(UUID propertyId, @Nullable LocalDate date) {
        return properties.findById(propertyId)
                .switchIfEmpty(Mono.error(() -> new PropertyNotFoundException(propertyId)))
                .flatMapMany(property -> {
                    ZoneId zone = ZoneId.of(property.timezone());
                    LocalDate day = date != null ? date : LocalDate.now(zone);
                    ArrivalWindow window = ArrivalWindow.forDay(day, zone);
                    return reservations.findArrivals(propertyId, window.from(), window.until(), LISTED_STATUSES)
                            .collectList()
                            .flatMapMany(dayReservations -> withUnits(dayReservations, zone));
                });
    }

    /**
     * Joins the already-loaded reservations with their units using one batched
     * query. collectList() first because we need the whole set of unit ids
     * before we can ask for them; the list is one day's arrivals at one
     * property, so holding it in memory is fine. Order from the SQL is kept.
     */
    private Flux<Arrival> withUnits(List<Reservation> dayReservations, ZoneId zone) {
        Set<UUID> unitIds = dayReservations.stream()
                .map(Reservation::unitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Guard the empty case: "where id in ()" is not valid SQL, and there is
        // nothing to look up anyway.
        Mono<Map<UUID, Unit>> unitsById = unitIds.isEmpty()
                ? Mono.just(Map.of())
                : units.findAllByIdIn(unitIds).collectMap(Unit::id);

        return unitsById.flatMapMany(byId -> Flux.fromIterable(dayReservations)
                .map(r -> toArrival(r, byId.get(r.unitId()), zone)));
    }

    /**
     * The driver hands back the timestamptz in the JVM's default offset, which
     * means nothing to a host. Same instant, re-expressed in the property's
     * zone, so the offset in the API response is the property's local one.
     */
    private static Arrival toArrival(Reservation r, @Nullable Unit unit, ZoneId zone) {
        return new Arrival(
                r.id(),
                r.guestName(),
                r.arrival().atZoneSameInstant(zone).toOffsetDateTime(),
                ReservationStatus.valueOf(r.status()),
                unit);
    }
}
