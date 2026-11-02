package com.trojanrideshare.app.service.ride;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.Ride;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Lets the organizer edit a ride's details or change its status (SRS step 10-11).
 * Only the organizer may act. Cancelling sets the "cancelled" status, which drops the ride
 * from discovery and everyone's upcoming list.
 */
public class EditRideService {
    public interface Callback {
        /** The change was saved. */
        void onSuccess();

        /** The edited details failed validation; nothing was saved. */
        void onInvalid(ValidationResult result);

        /** The action was not allowed; {@code reason} explains why. */
        void onDenied(String reason);

        /** A system/network error prevented the change. */
        void onError(String message);
    }

    public static final String REASON_NOT_ORGANIZER = "Only the organizer can edit this ride.";
    public static final String REASON_UNAVAILABLE = "This ride is unavailable.";

    private static final String GENERIC_ERROR = "Unable to save your changes. Please try again.";

    private final RideDataAccess rideDataAccess;

    public EditRideService() {
        this(new RideDataAccess());
    }

    EditRideService(RideDataAccess rideDataAccess) {
        this.rideDataAccess = rideDataAccess;
    }

    public void updateRide(
            Ride existing,
            RideInput input,
            String newStatus,
            String actingUserId,
            Callback callback) {
        String authorization = authorizationError(existing, actingUserId);
        if (authorization != null) {
            callback.onDenied(authorization);
            return;
        }

        ValidationResult validation = validateForEdit(input, existing, Timestamp.now());
        if (!validation.isValid()) {
            callback.onInvalid(validation);
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", input.title.trim());
        updates.put("origin", input.origin);
        updates.put("destination", input.destination);
        updates.put("departureTime", input.departureTime);
        updates.put("maxRiders", input.maxRiders);
        updates.put("expectedCost", input.expectedCost);
        updates.put("status", newStatus);

        rideDataAccess.updateRide(existing.id, updates, new RideDataAccess.WriteCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception exception) {
                callback.onError(GENERIC_ERROR);
            }
        });
    }

    public void cancelRide(Ride existing, String actingUserId, Callback callback) {
        String authorization = authorizationError(existing, actingUserId);
        if (authorization != null) {
            callback.onDenied(authorization);
            return;
        }

        rideDataAccess.updateStatus(
                existing.id, Ride.STATUS_CANCELLED, new RideDataAccess.WriteCallback() {
                    @Override
                    public void onSuccess() {
                        callback.onSuccess();
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError(GENERIC_ERROR);
                    }
                });
    }

    /** Returns a reason the user may not edit the ride, or {@code null} if they may. */
    public static String authorizationError(Ride ride, String actingUserId) {
        if (ride == null || ride.id == null
                || actingUserId == null || actingUserId.trim().isEmpty()) {
            return REASON_UNAVAILABLE;
        }
        if (!actingUserId.equals(ride.organizerId)) {
            return REASON_NOT_ORGANIZER;
        }
        return null;
    }

    /**
     * Validates edited details: all the create rules, plus the maximum number of riders may
     * not drop below the number of riders already on the ride.
     */
    public static ValidationResult validateForEdit(RideInput input, Ride existing, Timestamp now) {
        ValidationResult base = RideValidator.validate(input, now);
        if (!base.isValid() || input == null || input.maxRiders == null || existing == null) {
            return base;
        }
        int currentRiders = existing.participantIds == null ? 0 : existing.participantIds.size();
        if (input.maxRiders < currentRiders) {
            return new ValidationResult(Collections.singletonMap(
                    RideValidator.FIELD_MAX_RIDERS,
                    "Max riders cannot be fewer than the current riders (" + currentRiders + ")"));
        }
        return base;
    }
}
