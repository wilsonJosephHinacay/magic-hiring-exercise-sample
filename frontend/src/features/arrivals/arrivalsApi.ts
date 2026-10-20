import { gql } from "graphql-request";
import { api } from "../../api/api";
import type { ArrivalsQuery, ArrivalsQueryVariables } from "../../gql/generated";

/**
 * RTK Query endpoint for the arrivals query.
 *
 * Types come from codegen (npm run codegen, with the backend running), not
 * from hand-written declarations: if the schema changes, `tsc` tells us.
 */

const ArrivalsDocument = gql`
  query Arrivals($propertyId: ID!, $date: Date) {
    arrivals(propertyId: $propertyId, date: $date) {
      reservationId
      guestName
      arrival
      status
      unit {
        id
        label
        floor
      }
    }
  }
`;

export type Arrival = ArrivalsQuery["arrivals"][number];

export const arrivalsApi = api.injectEndpoints({
  endpoints: (build) => ({
    getArrivals: build.query<Arrival[], ArrivalsQueryVariables>({
      query: (variables) => ({ document: ArrivalsDocument, variables }),
      transformResponse: (res: ArrivalsQuery) => res.arrivals,
      // One cache entry per property, so switching properties does not
      // invalidate the others and switching back is instant.
      providesTags: (_result, _error, { propertyId }) => [{ type: "Arrival", id: propertyId }],
    }),
  }),
});

export const { useGetArrivalsQuery } = arrivalsApi;
