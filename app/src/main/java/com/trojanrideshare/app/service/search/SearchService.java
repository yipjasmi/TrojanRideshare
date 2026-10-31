package com.trojanrideshare.app.service.search;

import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.SearchCriteria;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SearchService {
    public interface Callback {
        void onSuccess(List<Ride> rides);
        void onError(String message);
    }

    private final RideDataAccess rideDataAccess;

    public SearchService() {
        this(new RideDataAccess());
    }

    SearchService(RideDataAccess rideDataAccess) {
        this.rideDataAccess = rideDataAccess;
    }

    public void search(
            SearchCriteria criteria,
            String currentUserId,
            Callback callback
    ) {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            callback.onError("You must be signed in to search for rides.");
            return;
        }

        rideDataAccess.getActiveRides(new RideDataAccess.Callback() {
            @Override
            public void onSuccess(List<Ride> rides) {
                callback.onSuccess(filterRides(rides, criteria, currentUserId));
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load rides. Please try again.");
            }
        });
    }

    static List<Ride> filterRides(
            List<Ride> rides,
            SearchCriteria criteria,
            String currentUserId
    ) {
        if (rides == null || currentUserId == null) {
            return Collections.emptyList();
        }

        SearchCriteria safeCriteria = criteria == null
                ? new SearchCriteria("", "")
                : criteria;
        List<Ride> results = new ArrayList<>();

        for (Ride ride : rides) {
            if (!isValidResult(ride, currentUserId)) {
                continue;
            }
            if (!containsIgnoreCase(addressOf(ride.origin), safeCriteria.originKeyword)) {
                continue;
            }
            if (!containsIgnoreCase(
                    addressOf(ride.destination),
                    safeCriteria.destinationKeyword
            )) {
                continue;
            }
            results.add(ride);
        }
        return results;
    }

    private static boolean isValidResult(Ride ride, String currentUserId) {
        if (ride == null) {
            return false;
        }
        if (!Ride.STATUS_ON_TIME.equals(ride.status)
                && !Ride.STATUS_DELAYED.equals(ride.status)) {
            return false;
        }
        if (currentUserId.equals(ride.organizerId)) {
            return false;
        }

        List<String> participants = ride.participantIds == null
                ? Collections.emptyList()
                : ride.participantIds;
        if (participants.contains(currentUserId)) {
            return false;
        }
        return ride.maxRiders > participants.size();
    }

    private static String addressOf(com.trojanrideshare.app.model.PlaceInfo place) {
        return place == null || place.address == null ? "" : place.address;
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        return value.toLowerCase(Locale.ROOT)
                .contains(keyword.toLowerCase(Locale.ROOT));
    }
}
