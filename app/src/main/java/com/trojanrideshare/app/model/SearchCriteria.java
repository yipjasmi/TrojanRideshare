package com.trojanrideshare.app.model;

public class SearchCriteria {
    public final String originKeyword;
    public final String destinationKeyword;
    public final PlaceInfo preferredOrigin;
    public final PlaceInfo preferredDestination;
    public final double maximumDistanceMiles;
    public final Long startDateMillis;
    public final Long endDateMillis;
    public final Integer earliestDepartureMinute;
    public final Integer latestDepartureMinute;
    public final int minimumAvailableSeats;
    public final SortOption sortOption;

    public SearchCriteria(String originKeyword, String destinationKeyword) {
        this(
                originKeyword,
                destinationKeyword,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                0,
                SortOption.DEPARTURE_EARLIEST
        );
    }

    public SearchCriteria(
            String originKeyword,
            String destinationKeyword,
            PlaceInfo preferredOrigin,
            PlaceInfo preferredDestination,
            double maximumDistanceMiles,
            Long startDateMillis,
            Long endDateMillis,
            Integer earliestDepartureMinute,
            Integer latestDepartureMinute,
            int minimumAvailableSeats,
            SortOption sortOption
    ) {
        this.originKeyword = clean(originKeyword);
        this.destinationKeyword = clean(destinationKeyword);
        this.preferredOrigin = preferredOrigin;
        this.preferredDestination = preferredDestination;
        this.maximumDistanceMiles = Math.max(0, maximumDistanceMiles);
        this.startDateMillis = startDateMillis;
        this.endDateMillis = endDateMillis;
        this.earliestDepartureMinute = earliestDepartureMinute;
        this.latestDepartureMinute = latestDepartureMinute;
        this.minimumAvailableSeats = Math.max(0, minimumAvailableSeats);
        this.sortOption = sortOption == null
                ? SortOption.DEPARTURE_EARLIEST
                : sortOption;
    }

    public boolean hasRouteReference() {
        return preferredOrigin != null && preferredDestination != null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
