package com.trojanrideshare.app.service.search;

import static org.junit.Assert.assertEquals;

import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.SearchCriteria;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
}
