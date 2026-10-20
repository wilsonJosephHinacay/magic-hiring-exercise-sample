# NOTES

## What I built

A thin vertical slice for "today's arrivals at a property", through every layer:

- **Schema**: `arrivals(propertyId: ID!, date: Date): [Arrival!]!`. `Arrival` nests a
  nullable `Unit` (the type was already declared) rather than flattening to a label
  string, and carries `status` so the UI can show it. `date` is optional and defaults
  to today in the property's zone. It costs one line and lets a host look at
  tomorrow, and it makes the query testable without a clock.
- **Backend** (`arrival` package): `ArrivalService` builds the list,
  `ArrivalGraphqlController` binds arguments and translates errors,
  `ArrivalWindow` is the pure "one local day as a half-open instant range" function.
  `ReservationRepository.findArrivals` gained a status filter.
- **Frontend** (`features/arrivals`): RTK Query endpoint typed from codegen output,
  and a plain MUI table with not-selected / loading / error-with-retry / empty states.
  Arrival times render in the property's timezone, taken from the already cached
  `properties` query so no extra request is made.
- **Tests**: a pure unit test for the day window (including DST days of 23 and 25
  hours) and an HTTP-level GraphQL test asserting the seed's boundary cases.

### The reactive chain, in words

```
properties.findById(id)                      -> Mono<Property>   (1 query)
  .switchIfEmpty(error NOT_FOUND)
  .flatMapMany(property ->
      reservations.findArrivals(...)          -> Flux<Reservation> (1 query)
        .collectList()
        .flatMapMany(list ->
            units.findAllByIdIn(distinct ids) -> Flux<Unit>        (1 query, skipped if no ids)
              .collectMap(Unit::id)
              .flatMapMany(byId -> Flux.fromIterable(list).map(join))))
```

Three SQL statements per request regardless of row count. I checked this against the
R2DBC debug log: `SELECT property ...`, `select * from reservation ...`,
`SELECT unit ... WHERE unit.id IN ($1, $2, $3, $4)`. Nothing blocks: every step
returns a publisher and the event-loop thread is free while Postgres works.

The alternative I rejected was `flatMap` per reservation to look up its unit. It is
non-blocking too, but it is N+1: N concurrent queries each holding a connection from
a pool of 20. Under load, requests queue on the pool, not the CPU. Non-blocking makes
N+1 concurrent, not cheap. The other alternative, a single SQL `LEFT JOIN` with a
projection record, is equally valid and is one query instead of three. I kept the
batched lookup because it reuses the provided repositories and keeps the entities
as they are; I would switch to the join if this list grew other per-row data.

`collectList()` before the unit lookup is intentional: I need the whole set of unit
ids before I can ask for them in one query. The list is one day's arrivals at one
property, so holding it in memory is fine. Order from the SQL `order by arrival` is
preserved because `Flux.fromIterable` does not reorder (a `flatMap` here would).

### Things I would do next

1. **Testcontainers** for the integration test, so `mvn test` does not depend on
   `docker compose up` having been run, and so the test is not tied to the day the
   seed migration ran (the seed is relative to `current_date` at migration time).
2. **Pagination** or a cap on the arrivals list. At 10,000 arrivals the `IN (...)`
   list gets large and the response does too. A `LEFT JOIN` with `limit/offset` or
   a cursor on `(arrival, id)` would be the shape.
3. **Fix the worked example's hand-written `Property` type** in `propertiesApi.ts`
   to use the generated `PropertiesQuery`, as the house rule says. I left the
   example untouched to keep the diff focused on the feature.
4. **Departures** and a multi-day view are the obvious next asks. The service shape
   (window in property zone, batched unit join) extends to both.

## Assumptions and open questions

| Topic | What I assumed | What I would have asked |
| ----- | -------------- | ----------------------- |
| "Today" | The property's local calendar day, not the server's or the browser's. Sydney is a different day from Europe for many hours. | Confirmed by the seed comments, but: should a host in Zurich looking at the Sydney property see Sydney's today? I assumed yes. |
| Day boundary | Half-open `[00:00, 24:00)` in the property zone. 23:30 is today, 00:30 tomorrow is not, exactly midnight belongs to the new day. | Is there a hotel-day concept (e.g. night audit at 04:00) that should shift the window? |
| Status | Exclude `CANCELLED` and `NO_SHOW`. Include `IN_HOUSE` (arrived today, host still wants to see them) and `CHECKED_OUT` (same-day stays are rare but real arrivals). | Should `IN_HOUSE` be shown differently or split out as "already checked in"? Is `NO_SHOW` worth showing greyed out so the host knows who did not turn up? |
| Unit | Nullable in the schema, "Unassigned" in the UI. | Should unassigned arrivals sort first, since they need action? |
| Unknown property | A `NOT_FOUND` GraphQL error, not an empty list. An empty list for a bad id is indistinguishable from a quiet day, and the picker only offers real ids so this is a client bug worth surfacing. | Fine either way; wanted to state the choice. |
| Timestamps | The API returns the arrival as RFC 3339 with the property's local offset (`...+02:00` for Zurich, `...+11:00` for Sydney), and the UI formats in that zone. | Do hosts ever need to see it in their own local time? |
| Scale | Not built: pagination, a cap, or streaming. One day at one property is small. | What is the largest property? Hundreds of arrivals a day changes the answer. |

## AI note

I used Claude Code throughout, working from a written plan (schema shape, batched
unit lookup, timezone rule, codegen for types) and asking it to produce each layer
following the worked example. It wrote the first pass of every file, the tests and
this notes draft. Three places where its first output was wrong and had to be
corrected during the session:

1. **The resolver returned `Flux<Arrival>`**, mirroring the worked example, with a
   `@GraphQlExceptionHandler` for the not-found case. That looked idiomatic but the
   unknown-property response carried two errors: the intended `NOT_FOUND` plus a
   spurious "null value in non-null list at `/arrivals[0]`". Reason: Spring GraphQL
   `collectList()`s a Flux anyway (a GraphQL list field is one value, nothing
   streams), and the handled error got appended as the last *element* of that list.
   Fix: the controller returns `Mono<List<Arrival>>`, collecting the service's Flux.
   The service still exposes a Flux so it composes.
2. **Arrival timestamps came back in the JVM's default offset** (`+08:00` on this
   machine), which is correct as an instant but meaningless to a host. Fixed by
   re-expressing each instant in the property's zone before it leaves the service.
3. **`findAllByIdIn` with an empty set** would have issued `WHERE id IN ()`, which is
   invalid SQL, on any day where no arrival has a unit. Guarded with an empty map.

The plan it worked from already ruled out the classic failure modes (per-row
`flatMap`, `LocalDate.now()` in server time, hand-written TypeScript types,
`.block()`), so none of those appeared.

Also for transparency: my local shell's `JAVA_HOME` pointed at JDK 8 while `java` on
the path was 25, so the first Maven compile failed on records and text blocks. Not an
AI issue, but it cost ten minutes and is the kind of thing `verify.sh` could check.
