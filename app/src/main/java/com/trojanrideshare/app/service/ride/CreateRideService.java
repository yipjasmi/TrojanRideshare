package com.trojanrideshare.app.service.ride;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.Ride;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Validates a {@link RideInput} and, when valid, publishes a new ride with status
 * "On Time" owned by the organizer (SRS main path steps 3-4).
 */
public class CreateRideService {
    public interface Callback {
        /** Form failed validation; no ride was created. */
        void onInvalid(ValidationResult result);

        /** Ride was published; {@code rideId} is its Firestore document id. */
        void onSuccess(String rideId);

        /** A system/network error prevented publishing. */
        void onError(String message);
    }

    private final RideDataAccess rideDataAccess;

    public CreateRideService() {
        this(new RideDataAccess());
    }

    CreateRideService(RideDataAccess rideDataAccess) {
        this.rideDataAccess = rideDataAccess;
    }

    public void createRide(RideInput input, String organizerId, Callback callback) {
        if (organizerId == null || organizerId.trim().isEmpty()) {
            callback.onError("You must be signed in to create a ride.");
            return;
        }

        ValidationResult validation = RideValidator.validate(input, Timestamp.now());
        if (!validation.isValid()) {
            callback.onInvalid(validation);
            return;
        }

        rideDataAccess.createRide(
                buildRide(input, organizerId),
                new RideDataAccess.CreateCallback() {
                    @Override
                    public void onSuccess(String rideId) {
                        callback.onSuccess(rideId);
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError("Unable to publish your ride. Please try again.");
                    }
                });
    }

    private static Ride buildRide(RideInput input, String organizerId) {
        Ride ride = new Ride();
        // id is left null on purpose: it is annotated @DocumentId and Firestore rejects
        // a non-null value on write.
        ride.title = input.title.trim();
        ride.organizerId = organizerId;
        ride.origin = input.origin;
        ride.destination = input.destination;
        ride.departureTime = input.departureTime;
        ride.maxRiders = input.maxRiders;
        ride.expectedCost = input.expectedCost;
        ride.status = Ride.STATUS_ON_TIME;
        ride.participantIds = new ArrayList<>(Collections.singletonList(organizerId));
        ride.createdAt = Timestamp.now();
        return ride;
    }
}
