package com.trojanrideshare.app.service.ride;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.PlaceInfo;

/**
 * Raw, already-parsed values collected from the create/edit ride form.
 *
 * <p>Parsing lives in the UI layer so that {@link RideValidator} can stay pure and
 * unit-testable. Fields that the user left blank (or typed something unparseable into)
 * are represented as {@code null} so the validator can report "required" errors.
 */
public class RideInput {
    public final String title;
    public final PlaceInfo origin;
    public final PlaceInfo destination;
    public final Timestamp departureTime;
    public final Integer maxRiders;
    public final Double expectedCost;

    public RideInput(
            String title,
            PlaceInfo origin,
            PlaceInfo destination,
            Timestamp departureTime,
            Integer maxRiders,
            Double expectedCost
    ) {
        this.title = title;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.maxRiders = maxRiders;
        this.expectedCost = expectedCost;
    }
}
