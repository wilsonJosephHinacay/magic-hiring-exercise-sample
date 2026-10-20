import {
  Alert,
  Button,
  Chip,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useGetPropertiesQuery } from "../properties/propertiesApi";
import { useGetArrivalsQuery } from "./arrivalsApi";

/**
 * Today's arrivals for one property. Plain MUI table; four states:
 * nothing selected, loading, error (with retry), empty, and the list.
 */
export function ArrivalsList({ propertyId }: { propertyId: string }) {
  // Cache hit: PropertyPicker already fetched this list. We only need the
  // property's timezone so arrival times render in the property's local day,
  // which is the day the backend used to build the list.
  const { data: properties } = useGetPropertiesQuery();
  const timezone = properties?.find((p) => p.id === propertyId)?.timezone;

  const { data, isLoading, isFetching, error, refetch } = useGetArrivalsQuery(
    { propertyId },
    { skip: !propertyId },
  );

  if (!propertyId) {
    return (
      <Typography variant="body2" color="text.secondary">
        Select a property above to see today&apos;s arrivals.
      </Typography>
    );
  }

  if (isLoading) return <Skeleton variant="rounded" height={160} />;

  if (error) {
    return (
      <Alert
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={() => refetch()}>
            Retry
          </Button>
        }
      >
        Could not load arrivals. {"message" in error ? error.message : ""}
      </Alert>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No arrivals today.
      </Typography>
    );
  }

  const formatTime = (iso: string) =>
    new Intl.DateTimeFormat(undefined, {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
      timeZone: timezone,
    }).format(new Date(iso));

  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        {data.length} arrival{data.length === 1 ? "" : "s"} today
        {timezone ? `, times in ${timezone}` : ""}
        {isFetching ? " (refreshing...)" : ""}
      </Typography>
      <Table size="small" aria-label="Today's arrivals">
        <TableHead>
          <TableRow>
            <TableCell>Guest</TableCell>
            <TableCell>Arrival</TableCell>
            <TableCell>Unit</TableCell>
            <TableCell>Status</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {data.map((a) => (
            <TableRow key={a.reservationId}>
              <TableCell>{a.guestName}</TableCell>
              <TableCell>{formatTime(a.arrival)}</TableCell>
              <TableCell>
                {a.unit ? (
                  a.unit.label
                ) : (
                  <Typography component="span" variant="body2" color="text.secondary" fontStyle="italic">
                    Unassigned
                  </Typography>
                )}
              </TableCell>
              <TableCell>
                <Chip size="small" label={a.status} variant="outlined" />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  );
}
