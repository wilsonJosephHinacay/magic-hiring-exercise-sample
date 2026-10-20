package tech.likemagic.exercise.arrival;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

/**
 * End-to-end test over HTTP against the seeded database, in the shape of the
 * provided PropertyGraphqlControllerTest.
 *
 * Needs the database running (docker compose up -d), like the worked example.
 * The seed is relative to the day the migration ran, so reset the database
 * (docker compose down -v && docker compose up -d) if it was seeded on an
 * earlier day. A Testcontainers-backed setup would remove both caveats; see
 * NOTES.md.
 *
 * The assertions are the boundary cases the seed data was built around.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArrivalGraphqlControllerTest {

    private static final String ZURICH = "11111111-1111-1111-1111-111111111111";

    private static final String ARRIVALS = """
            query($propertyId: ID!) {
              arrivals(propertyId: $propertyId) {
                guestName
                arrival
                status
                unit { label }
              }
            }
            """;

    // Local shape for deserialising the response. Test-only, so it does not
    // leak into production code.
    record UnitView(String label) {}
    record ArrivalView(String guestName, String arrival, String status, UnitView unit) {}

    @Autowired
    HttpGraphQlTester graphQlTester;

    @Test
    void listsTodaysArrivalsInThePropertysLocalDay() {
        List<ArrivalView> arrivals = graphQlTester.document(ARRIVALS)
                .variable("propertyId", ZURICH)
                .execute()
                .path("arrivals")
                .entityList(ArrivalView.class)
                .get();

        List<String> names = arrivals.stream().map(ArrivalView::guestName).toList();

        assertThat(names)
                // 23:30 local is still today: the timezone boundary case
                .contains("Yuki Tanaka")
                // IN_HOUSE arrived today and is still an arrival
                .contains("Tom Fischer")
                // 00:30 local tomorrow
                .doesNotContain("Oscar Lindqvist")
                // yesterday
                .doesNotContain("Sofia Rossi")
                // cancelled
                .doesNotContain("Lena Hofer");

        // Ordered by arrival time, as the host reads the board.
        assertThat(arrivals.stream().map(ArrivalView::arrival).toList()).isSorted();

        // Every timestamp is expressed in the property's offset (CEST or CET),
        // never the server's.
        assertThat(arrivals).allSatisfy(a ->
                assertThat(a.arrival()).matches(".*\\+0[12]:00$"));
    }

    @Test
    void reservationWithoutAUnitHasNullUnitNotAnError() {
        ArrivalView priya = graphQlTester.document(ARRIVALS)
                .variable("propertyId", ZURICH)
                .execute()
                .path("arrivals[?(@.guestName == 'Priya Raman')]")
                .entityList(ArrivalView.class)
                .hasSize(1)
                .get()
                .getFirst();

        assertThat(priya.unit()).isNull();
        assertThat(priya.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void unknownPropertyIsASingleNotFoundError() {
        graphQlTester.document(ARRIVALS)
                .variable("propertyId", "00000000-0000-0000-0000-000000000000")
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    assertThat(errors.getFirst().getMessage()).contains("00000000-0000-0000-0000-000000000000");
                })
                // arrivals is declared non-null, so per the GraphQL spec the null
                // bubbles up and the whole data object is null: no path to assert on.
                .path("arrivals").pathDoesNotExist();
    }
}
