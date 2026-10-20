package tech.likemagic.exercise.reservation;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ReservationRepository extends ReactiveCrudRepository<Reservation, UUID> {

    Flux<Reservation> findAllByPropertyIdOrderByArrivalAsc(UUID propertyId);

    /**
     * Arrivals in the half-open range [from, until) restricted to the given
     * statuses. Half-open so that a day boundary belongs to exactly one day:
     * an arrival at exactly midnight is tomorrow's, never both.
     *
     * The filter runs in SQL rather than in Java so the database only ships
     * rows we will actually show, and so (property_id, arrival) can use the
     * existing index.
     */
    @Query("""
           select * from reservation
           where property_id = :propertyId
             and arrival >= :from
             and arrival <  :until
             and status in (:statuses)
           order by arrival asc
           """)
    Flux<Reservation> findArrivals(@Param("propertyId") UUID propertyId,
                                   @Param("from") OffsetDateTime from,
                                   @Param("until") OffsetDateTime until,
                                   @Param("statuses") Collection<String> statuses);
}
