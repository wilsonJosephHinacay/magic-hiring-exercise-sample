package tech.likemagic.exercise.reservation;

/**
 * Mirrors the GraphQL enum of the same name. The reservation.status column is
 * plain text, so {@link Reservation} keeps a String and callers convert with
 * {@link #valueOf(String)} at the edge where a typed value is needed.
 */
public enum ReservationStatus {
    CONFIRMED,
    IN_HOUSE,
    CHECKED_OUT,
    CANCELLED,
    NO_SHOW
}
