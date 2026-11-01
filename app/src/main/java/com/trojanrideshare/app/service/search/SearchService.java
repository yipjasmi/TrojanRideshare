package com.trojanrideshare.app.service.search;

import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.SearchCriteria;
import com.trojanrideshare.app.model.SortOption;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
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
            if (!matchesDateAndTime(ride, safeCriteria)) {
                continue;
            }
            if (availableSeats(ride) < safeCriteria.minimumAvailableSeats) {
                continue;
            }
            if (!matchesRouteDistance(ride, safeCriteria)) {
                continue;
            }
            results.add(ride);
        }

        sortResults(results, safeCriteria);
        return results;
    }

    private static boolean matchesDateAndTime(Ride ride, SearchCriteria criteria) {
        if (criteria.startDateMillis == null
                && criteria.endDateMillis == null
                && criteria.earliestDepartureMinute == null
                && criteria.latestDepartureMinute == null) {
            return true;
        }
        if (ride.departureTime == null) {
            return false;
        }

        long departureMillis = ride.departureTime.toDate().getTime();
        if (criteria.startDateMillis != null && departureMillis < criteria.startDateMillis) {
            return false;
        }
        if (criteria.endDateMillis != null && departureMillis > criteria.endDateMillis) {
            return false;
        }

        if (criteria.earliestDepartureMinute == null
                && criteria.latestDepartureMinute == null) {
            return true;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(departureMillis);
        int departureMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60
                + calendar.get(Calendar.MINUTE);
        int earliest = criteria.earliestDepartureMinute == null
                ? 0
                : criteria.earliestDepartureMinute;
        int latest = criteria.latestDepartureMinute == null
                ? 24 * 60 - 1
                : criteria.latestDepartureMinute;

        if (earliest <= latest) {
            return departureMinute >= earliest && departureMinute <= latest;
        }
        return departureMinute >= earliest || departureMinute <= latest;
    }

    private static boolean matchesRouteDistance(Ride ride, SearchCriteria criteria) {
        if (criteria.maximumDistanceMiles <= 0) {
            return true;
        }
        if (!criteria.hasRouteReference() || ride.origin == null || ride.destination == null) {
            return false;
        }
        return distanceMiles(criteria.preferredOrigin, ride.origin)
                <= criteria.maximumDistanceMiles
                && distanceMiles(criteria.preferredDestination, ride.destination)
                <= criteria.maximumDistanceMiles;
    }

    private static void sortResults(List<Ride> rides, SearchCriteria criteria) {
        Comparator<Ride> departureComparator = Comparator.comparingLong(
                SearchService::departureMillis
        );
        Comparator<Ride> proximityComparator = Comparator.comparingDouble(
                ride -> routeDistanceMiles(ride, criteria)
        );

        SortOption option = criteria.sortOption;
        if (option == SortOption.DEPARTURE_LATEST) {
            rides.sort(departureComparator.reversed());
        } else if (option == SortOption.ROUTE_PROXIMITY) {
            rides.sort(proximityComparator.thenComparing(departureComparator));
        } else if (option == SortOption.PROXIMITY_AND_TIME) {
            long earliestDeparture = rides.stream()
                    .mapToLong(SearchService::departureMillis)
                    .min()
                    .orElse(0);
            rides.sort(Comparator.comparingDouble(
                    ride -> combinedSortScore(ride, criteria, earliestDeparture)
            ));
        } else {
            rides.sort(departureComparator);
        }
    }

    private static double combinedSortScore(
            Ride ride,
            SearchCriteria criteria,
            long earliestDeparture
    ) {
        double hoursAfterEarliest = Math.max(
                0,
                (departureMillis(ride) - earliestDeparture) / 3_600_000.0
        );
        return routeDistanceMiles(ride, criteria) + hoursAfterEarliest;
    }

    private static double routeDistanceMiles(Ride ride, SearchCriteria criteria) {
        if (!criteria.hasRouteReference() || ride.origin == null || ride.destination == null) {
            return Double.MAX_VALUE;
        }
        return distanceMiles(criteria.preferredOrigin, ride.origin)
                + distanceMiles(criteria.preferredDestination, ride.destination);
    }

    private static long departureMillis(Ride ride) {
        return ride.departureTime == null
                ? Long.MAX_VALUE
                : ride.departureTime.toDate().getTime();
    }

    private static int availableSeats(Ride ride) {
        return ride.getAvailableSeats();
    }

    static double distanceMiles(PlaceInfo first, PlaceInfo second) {
        if (first == null || second == null) {
            return Double.MAX_VALUE;
        }
        double earthRadiusMiles = 3958.8;
        double latitudeDifference = Math.toRadians(second.lat - first.lat);
        double longitudeDifference = Math.toRadians(second.lng - first.lng);
        double firstLatitude = Math.toRadians(first.lat);
        double secondLatitude = Math.toRadians(second.lat);
        double haversine = Math.sin(latitudeDifference / 2)
                * Math.sin(latitudeDifference / 2)
                + Math.cos(firstLatitude)
                * Math.cos(secondLatitude)
                * Math.sin(longitudeDifference / 2)
                * Math.sin(longitudeDifference / 2);
        return earthRadiusMiles * 2 * Math.atan2(
                Math.sqrt(haversine),
                Math.sqrt(1 - haversine)
        );
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
        return ride.getAvailableSeats() > 0;
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
