package tech.likemagic.exercise.arrival;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

/**
 * Resolver for the arrivals query. Thin on purpose: argument binding and
 * error translation live here, the reactive chain lives in ArrivalService.
 *
 * The publisher is returned, not consumed: Spring GraphQL subscribes to it.
 *
 * Why Mono<List> and not Flux: a GraphQL list field is one value, so Spring
 * GraphQL collectList()s a Flux anyway, nothing streams. And with a Flux the
 * error produced by the @GraphQlExceptionHandler below is appended as the
 * last *element* of that list, which graphql-java then reports as a second,
 * misleading "null in non-null list" error. Collecting here keeps the
 * not-found response to a single clean error.
 */
@Controller
public class ArrivalGraphqlController {

    private final ArrivalService arrivals;

    public ArrivalGraphqlController(ArrivalService arrivals) {
        this.arrivals = arrivals;
    }

    @QueryMapping
    public Mono<List<Arrival>> arrivals(@Argument UUID propertyId, @Argument @Nullable LocalDate date) {
        return arrivals.arrivals(propertyId, date).collectList();
    }

    /**
     * Surface an unknown property as a typed GraphQL error instead of the
     * generic INTERNAL_ERROR, so the client can tell "bad id" from "we broke".
     */
    @GraphQlExceptionHandler
    public GraphQLError handle(PropertyNotFoundException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.NOT_FOUND)
                .message(ex.getMessage())
                .build();
    }
}
