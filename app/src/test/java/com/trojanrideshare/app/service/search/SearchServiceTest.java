package com.trojanrideshare.app.service.search;

import static org.junit.Assert.assertEquals;

import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.SearchCriteria;
import com.trojanrideshare.app.model.SortOption;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class SearchServiceTest {
    private static final String CURRENT_USER = "current-user";

    @Test
    public void filterRides_matchesOriginAndDestinationIgnoringCase() {
        Ride match = ride(
                "match",
                "USC Village Target",
                "LAX International Airport",
                "other-user",
                Ride.STATUS_ON_TIME,
                4,
                Collections.singletonList("other-user")
        );
        Ride wrongDestination = ride(
                "wrong-destination",
                "USC Village Target",
                "Union Station",
                "other-user",
                Ride.STATUS_ON_TIME,
                4,
                Collections.singletonList("other-user")
        );

        List<Ride> results = SearchService.filterRides(
                Arrays.asList(match, wrongDestination),
                new SearchCriteria("village", "lax"),
                CURRENT_USER
        );

        assertEquals(Collections.singletonList(match), results);
    }

    @Test
    public void filterRides_excludesInvalidDiscoveryResults() {
        Ride valid = ride(
                "valid",
                "USC",
                "LAX",
                "organizer",
                Ride.STATUS_DELAYED,
                4,
                Collections.singletonList("organizer")
        );
        Ride ownRide = ride(
                "own",
                "USC",
                "LAX",
                CURRENT_USER,
                Ride.STATUS_ON_TIME,
                4,
                Collections.singletonList(CURRENT_USER)
        );
        Ride alreadyJoined = ride(
                "joined",
                "USC",
                "LAX",
                "organizer",
                Ride.STATUS_ON_TIME,
                4,
                Arrays.asList("organizer", CURRENT_USER)
        );
        Ride full = ride(
                "full",
                "USC",
                "LAX",
                "organizer",
                Ride.STATUS_ON_TIME,
                2,
                Arrays.asList("organizer", "rider")
        );
        Ride cancelled = ride(
                "cancelled",
                "USC",
                "LAX",
                "organizer",
                Ride.STATUS_CANCELLED,
                4,
                Collections.singletonList("organizer")
        );
        Ride completed = ride(
                "completed",
                "USC",
                "LAX",
                "organizer",
                Ride.STATUS_COMPLETED,
                4,
                Collections.singletonList("organizer")
        );

        List<Ride> results = SearchService.filterRides(
                Arrays.asList(valid, ownRide, alreadyJoined, full, cancelled, completed),
                new SearchCriteria("", ""),
                CURRENT_USER
        );

        assertEquals(Collections.singletonList(valid), results);
    }

    @Test
    public void filterRides_doesNotMutateInputList() {
        Ride valid = ride(
                "valid",
                "USC",
                "LAX",
                "organizer",
                Ride.STATUS_ON_TIME,
                3,
                Collections.singletonList("organizer")
        );
        List<Ride> input = new ArrayList<>(Collections.singletonList(valid));

        SearchService.filterRides(input, new SearchCriteria("usc", ""), CURRENT_USER);

        assertEquals(Collections.singletonList(valid), input);
    }

    @Test
    public void filterRides_filtersDateTimeAndAvailableSeats() {
        Ride match = timedRide("match", 2026, Calendar.NOVEMBER, 2, 10, 30, 4, 1);
        Ride tooEarly = timedRide("too-early", 2026, Calendar.NOVEMBER, 2, 7, 30, 4, 1);
        Ride tooFewSeats = timedRide("too-few", 2026, Calendar.NOVEMBER, 2, 11, 0, 2, 1);
        Ride wrongDate = timedRide("wrong-date", 2026, Calendar.NOVEMBER, 4, 10, 30, 4, 1);

        long startDate = millis(2026, Calendar.NOVEMBER, 2, 0, 0);
        long endDate = millis(2026, Calendar.NOVEMBER, 3, 23, 59);
        SearchCriteria criteria = criteria(
                null,
                null,
                0,
                startDate,
                endDate,
                9 * 60,
                12 * 60,
                2,
                SortOption.DEPARTURE_EARLIEST
        );

        List<Ride> results = SearchService.filterRides(
                Arrays.asList(tooEarly, tooFewSeats, wrongDate, match),
                criteria,
                CURRENT_USER
        );

        assertEquals(Collections.singletonList(match), results);
    }

    @Test
    public void filterRides_sortsByEarliestAndLatestDeparture() {
        Ride early = timedRide("early", 2026, Calendar.NOVEMBER, 2, 8, 0, 4, 1);
        Ride late = timedRide("late", 2026, Calendar.NOVEMBER, 2, 18, 0, 4, 1);

        List<Ride> earliest = SearchService.filterRides(
                Arrays.asList(late, early),
                criteria(null, null, 0, null, null, null, null, 0,
                        SortOption.DEPARTURE_EARLIEST),
                CURRENT_USER
        );
        List<Ride> latest = SearchService.filterRides(
                Arrays.asList(early, late),
                criteria(null, null, 0, null, null, null, null, 0,
                        SortOption.DEPARTURE_LATEST),
                CURRENT_USER
        );

        assertEquals(Arrays.asList(early, late), earliest);
        assertEquals(Arrays.asList(late, early), latest);
    }

    @Test
    public void filterRides_filtersAndSortsByRouteDistance() {
        PlaceInfo preferredOrigin = place("USC", 34.0224, -118.2851);
        PlaceInfo preferredDestination = place("LAX", 33.9416, -118.4085);
        Ride near = timedRide("near", 2026, Calendar.NOVEMBER, 2, 10, 0, 4, 1);
        near.origin = place("Near USC", 34.0230, -118.2860);
        near.destination = place("Near LAX", 33.9420, -118.4090);
        Ride farther = timedRide("farther", 2026, Calendar.NOVEMBER, 2, 9, 0, 4, 1);
        farther.origin = place("Downtown", 34.0522, -118.2437);
        farther.destination = place("El Segundo", 33.9192, -118.4165);
        Ride outsideLimit = timedRide("outside", 2026, Calendar.NOVEMBER, 2, 8, 0, 4, 1);
        outsideLimit.origin = place("San Francisco", 37.7749, -122.4194);
        outsideLimit.destination = place("Oakland", 37.8044, -122.2712);

        List<Ride> results = SearchService.filterRides(
                Arrays.asList(farther, outsideLimit, near),
                criteria(
                        preferredOrigin,
                        preferredDestination,
                        10,
                        null,
                        null,
                        null,
                        null,
                        0,
                        SortOption.ROUTE_PROXIMITY
                ),
                CURRENT_USER
        );

        assertEquals(Arrays.asList(near, farther), results);
    }

    @Test
    public void filterRides_excludesDepartedRidesWhenNowGiven() {
        Ride departed = timedRide("departed", 2026, Calendar.NOVEMBER, 2, 8, 0, 4, 1);
        Ride upcoming = timedRide("upcoming", 2026, Calendar.NOVEMBER, 2, 18, 0, 4, 1);
        Timestamp now = new Timestamp(new Date(millis(2026, Calendar.NOVEMBER, 2, 12, 0)));

        List<Ride> results = SearchService.filterRides(
                Arrays.asList(departed, upcoming),
                criteria(null, null, 0, null, null, null, null, 0,
                        SortOption.DEPARTURE_EARLIEST),
                CURRENT_USER,
                now
        );

        assertEquals(Collections.singletonList(upcoming), results);
    }

    @Test
    public void filterRides_combinedSortUsesTimeToBreakProximityTies() {
        PlaceInfo preferredOrigin = place("USC", 34.0224, -118.2851);
        PlaceInfo preferredDestination = place("LAX", 33.9416, -118.4085);
        // Both within the same half-mile band of the picked points; the later one is closer.
        Ride laterCloser = timedRide("later", 2026, Calendar.NOVEMBER, 2, 12, 0, 4, 1);
        laterCloser.origin = place("USC", 34.0224, -118.2851);
        laterCloser.destination = place("LAX", 33.9416, -118.4085);
        Ride earlierSlightlyFarther = timedRide("earlier", 2026, Calendar.NOVEMBER, 2, 9, 0, 4, 1);
        earlierSlightlyFarther.origin = place("Near USC", 34.0230, -118.2860);
        earlierSlightlyFarther.destination = place("Near LAX", 33.9420, -118.4090);
        Ride far = timedRide("far", 2026, Calendar.NOVEMBER, 2, 7, 0, 4, 1);
        far.origin = place("Downtown", 34.0522, -118.2437);
        far.destination = place("El Segundo", 33.9192, -118.4165);

        List<Ride> results = SearchService.filterRides(
                Arrays.asList(far, laterCloser, earlierSlightlyFarther),
                criteria(preferredOrigin, preferredDestination, 0, null, null, null, null, 0,
                        SortOption.PROXIMITY_AND_TIME),
                CURRENT_USER
        );

        assertEquals(Arrays.asList(earlierSlightlyFarther, laterCloser, far), results);
    }

    @Test
    public void keywordError_requiresBothKeywords() {
        assertEquals(SearchService.ERROR_KEYWORDS_REQUIRED, SearchService.keywordError("USC", " "));
        assertEquals(SearchService.ERROR_KEYWORDS_REQUIRED, SearchService.keywordError("", "LAX"));
        org.junit.Assert.assertNull(SearchService.keywordError("USC", "LAX"));
    }

    private static Ride ride(
            String id,
            String origin,
            String destination,
            String organizerId,
            String status,
            int maxRiders,
            List<String> participantIds
    ) {
        Ride ride = new Ride();
        ride.id = id;
        ride.origin = place(origin);
        ride.destination = place(destination);
        ride.organizerId = organizerId;
        ride.status = status;
        ride.maxRiders = maxRiders;
        ride.participantIds = new ArrayList<>(participantIds);
        return ride;
    }

    private static PlaceInfo place(String address) {
        PlaceInfo place = new PlaceInfo();
        place.address = address;
        return place;
    }

    private static PlaceInfo place(String address, double lat, double lng) {
        PlaceInfo place = place(address);
        place.lat = lat;
        place.lng = lng;
        return place;
    }

    private static Ride timedRide(
            String id,
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int maxRiders,
            int participantCount
    ) {
        List<String> participants = new ArrayList<>();
        for (int index = 0; index < participantCount; index++) {
            participants.add("participant-" + index);
        }
        Ride ride = ride(
                id,
                "USC",
                "LAX",
                "organizer",
                Ride.STATUS_ON_TIME,
                maxRiders,
                participants
        );
        ride.departureTime = new Timestamp(new Date(millis(year, month, day, hour, minute)));
        return ride;
    }

    private static long millis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTimeInMillis();
    }

    private static SearchCriteria criteria(
            PlaceInfo preferredOrigin,
            PlaceInfo preferredDestination,
            double maximumDistanceMiles,
            Long startDateMillis,
            Long endDateMillis,
            Integer earliestDepartureMinute,
            Integer latestDepartureMinute,
            int minimumSeats,
            SortOption sortOption
    ) {
        return new SearchCriteria(
                "",
                "",
                preferredOrigin,
                preferredDestination,
                maximumDistanceMiles,
                startDateMillis,
                endDateMillis,
                earliestDepartureMinute,
                latestDepartureMinute,
                minimumSeats,
                sortOption
        );
    }
}
