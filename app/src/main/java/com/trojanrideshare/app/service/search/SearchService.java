package com.trojanrideshare.app.service.search;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.SearchCriteria;
import com.trojanrideshare.app.model.SortOption;
import com.trojanrideshare.app.util.KeywordMatcher;

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
                callback.onSuccess(filterRides(rides, criteria, currentUserId, Timestamp.now()));
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load rides. Please try again.");
            }
        });
    }

    /** Rides within this distance of each other count as tied for the combined sort. */
    public static final double PROXIMITY_TIE_MILES = 0.5;

    public static final String ERROR_KEYWORDS_REQUIRED =
            "Enter both an origin keyword and a destination keyword.";

    /**
     * Keywords are optional (no keywords lists every ride), but a keyword search needs both:
     * filling in only one is an error.
     */
    public static String keywordError(String originKeyword, String destinationKeyword) {
        boolean missingOrigin = originKeyword == null || originKeyword.trim().isEmpty();
        boolean missingDestination = destinationKeyword == null || destinationKeyword.trim().isEmpty();
        return missingOrigin != missingDestination ? ERROR_KEYWORDS_REQUIRED : null;
    }

    static List<Ride> filterRides(
            List<Ride> rides,
            SearchCriteria criteria,
            String currentUserId
    ) {
        return filterRides(rides, criteria, currentUserId, null);
    }

    /**
     * Results are every active (On Time or Delayed) ride that hasn't departed yet ({@code now}
     * null skips the departure check), including full rides, the user's own rides and rides
     * they joined; the screen labels those and blocks requesting them. The filters narrow
     * this list. A Delayed ride uses its updated departure time for filtering and sorting.
     */
    static List<Ride> filterRides(
            List<Ride> rides,
            SearchCriteria criteria,
            String currentUserId,
            Timestamp now
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
            if (now != null && ride.hasDeparted(now)) {
                continue;
            }
            if (!safeCriteria.statusFilter.isEmpty() && !safeCriteria.statusFilter.equals(ride.status)) {
                continue;
            }
            if (!KeywordMatcher.matches(ride.origin, safeCriteria.originKeyword)) {
                continue;
            }
            if (!KeywordMatcher.matches(ride.destination, safeCriteria.destinationKeyword)) {
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

    /**
     * One shared radius. Every end the user picked a point for must be within it: with both
     * points picked, a ride is excluded if either its origin or its destination is too far.
     */
    private static boolean matchesRouteDistance(Ride ride, SearchCriteria criteria) {
        if (criteria.maximumDistanceMiles <= 0) {
            return true;
        }
        if (!criteria.hasAnyReference()) {
            return false;
        }
        if (criteria.preferredOrigin != null
                && distanceMiles(criteria.preferredOrigin, ride.origin) > criteria.maximumDistanceMiles) {
            return false;
        }
        return criteria.preferredDestination == null
                || distanceMiles(criteria.preferredDestination, ride.destination)
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
            // Proximity first; rides within the same half-mile band tie and are ordered by
            // earliest (updated) departure.
            rides.sort(Comparator.<Ride>comparingLong(ride -> proximityBand(ride, criteria))
                    .thenComparing(departureComparator));
        } else {
            rides.sort(departureComparator);
        }
    }

    private static long proximityBand(Ride ride, SearchCriteria criteria) {
        double distance = routeDistanceMiles(ride, criteria);
        if (distance == Double.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(distance / PROXIMITY_TIE_MILES);
    }

    /** Distance from each picked point to the matching end of the ride, added together. */
    private static double routeDistanceMiles(Ride ride, SearchCriteria criteria) {
        if (!criteria.hasAnyReference()) {
            return Double.MAX_VALUE;
        }
        double total = 0;
        if (criteria.preferredOrigin != null) {
            double distance = distanceMiles(criteria.preferredOrigin, ride.origin);
            if (distance == Double.MAX_VALUE) return Double.MAX_VALUE;
            total += distance;
        }
        if (criteria.preferredDestination != null) {
            double distance = distanceMiles(criteria.preferredDestination, ride.destination);
            if (distance == Double.MAX_VALUE) return Double.MAX_VALUE;
            total += distance;
        }
        return total;
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
        return true;
    }
}
