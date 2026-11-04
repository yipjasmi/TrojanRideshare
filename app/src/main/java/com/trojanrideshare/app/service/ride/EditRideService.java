package com.trojanrideshare.app.service.ride;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.notification.NotificationDataAccess;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.data.ride.RideMembershipDataAccess;
import com.trojanrideshare.app.model.Notification;
import com.trojanrideshare.app.model.Ride;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Organizer-only activity changes on a posted ride.
 *
 * <p>Title, date, locations, expected cost and maximum riders are fixed once posted. The
 * organizer may only:
 * <ul>
 *   <li>mark the ride Delayed with a new, later departure time (the new time replaces
 *       {@code departureTime}, so search and sorting use it; the posted time is kept in
 *       {@code originalDepartureTime});</li>
 *   <li>mark it Completed, only after its departure time has passed.</li>
 *   <li>cancel it while it is still active.</li>
 * </ul>
 * Completed and Cancelled are final and cannot be undone.
 */
public class EditRideService {
    public interface Callback {
        /** The change was saved. */
        void onSuccess();

        /** The action was not allowed; {@code reason} explains why. */
        void onDenied(String reason);

        /** A system/network error prevented the change. */
        void onError(String message);
    }

    public static final String REASON_NOT_ORGANIZER = "Only the organizer can change this ride.";
    public static final String REASON_UNAVAILABLE = "This ride is unavailable.";
    public static final String REASON_FINAL =
            "Completed and cancelled rides can't be changed.";
    public static final String REASON_DELAY_TIME_REQUIRED = "Pick the new departure time.";
    public static final String REASON_DELAY_IN_PAST = "The new departure time must be in the future.";
    public static final String REASON_DELAY_NOT_LATER =
            "The new departure time must be later than the current one.";
    public static final String REASON_NOT_DEPARTED =
            "A ride can only be completed after its departure time.";

    private static final String GENERIC_ERROR = "Unable to save your changes. Please try again.";

    private final RideDataAccess rideDataAccess;
    private final NotificationDataAccess notificationDataAccess;
    private final RideMembershipDataAccess membershipDataAccess;

    public EditRideService() {
        this(new RideDataAccess(), new NotificationDataAccess(), new RideMembershipDataAccess());
    }

    EditRideService(RideDataAccess rideDataAccess, NotificationDataAccess notificationDataAccess,
                    RideMembershipDataAccess membershipDataAccess) {
        this.rideDataAccess = rideDataAccess;
        this.notificationDataAccess = notificationDataAccess;
        this.membershipDataAccess = membershipDataAccess;
    }

    /** Marks the ride Delayed and moves its departure to {@code newDeparture}. */
    public void markDelayed(
            Ride existing, Timestamp newDeparture, String actingUserId, Callback callback) {
        String error = delayError(existing, newDeparture, actingUserId, Timestamp.now());
        if (error != null) {
            callback.onDenied(error);
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Ride.STATUS_DELAYED);
        updates.put("departureTime", newDeparture);
        if (existing.originalDepartureTime == null) {
            updates.put("originalDepartureTime", existing.departureTime);
        }

        rideDataAccess.updateRide(existing.id, updates, new RideDataAccess.WriteCallback() {
            @Override
            public void onSuccess() {
                notifyRiders(existing, Notification.TYPE_RIDE_UPDATED,
                        "\"" + existing.title + "\" is delayed to a new departure time.");
                callback.onSuccess();
            }

            @Override
            public void onError(Exception exception) {
                callback.onError(GENERIC_ERROR);
            }
        });
    }

    /** Marks the ride Completed. Allowed only for the organizer, after departure. */
    public void completeRide(Ride existing, String actingUserId, Callback callback) {
        String error = completeError(existing, actingUserId, Timestamp.now());
        if (error != null) {
            callback.onDenied(error);
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Ride.STATUS_COMPLETED);
        updates.put("completedAt", Timestamp.now());
        rideDataAccess.updateRide(
                existing.id, updates, new RideDataAccess.WriteCallback() {
                    @Override
                    public void onSuccess() {
                        notifyRiders(existing, Notification.TYPE_RIDE_UPDATED,
                                "\"" + existing.title + "\" was marked completed.");
                        callback.onSuccess();
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError(GENERIC_ERROR);
                    }
                });
    }

    /**
     * Organizer cancels an active ride. Pending requests are closed (shown to those requesters as
     * closed because the ride was cancelled) and everyone on the ride sees it as Cancelled.
     */
    public void cancelRide(Ride existing, String actingUserId, Callback callback) {
        String authorization = authorizationError(existing, actingUserId);
        if (authorization != null) {
            callback.onDenied(authorization);
            return;
        }
        if (!existing.isActive()) {
            callback.onDenied(REASON_FINAL);
            return;
        }

        rideDataAccess.updateStatus(
                existing.id, Ride.STATUS_CANCELLED, new RideDataAccess.WriteCallback() {
                    @Override
                    public void onSuccess() {
                        notifyRiders(existing, Notification.TYPE_RIDE_CANCELLED,
                                "\"" + existing.title + "\" was cancelled.");
                        closePendingRequests(existing);
                        callback.onSuccess();
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError(GENERIC_ERROR);
                    }
                });
    }

    private void closePendingRequests(Ride ride) {
        if (membershipDataAccess == null) return;
        membershipDataAccess.closePendingRequests(ride.id, requesterIds -> {
            for (String requesterId : requesterIds) {
                Notification notification = new Notification();
                notification.userId = requesterId;
                notification.type = Notification.TYPE_RIDE_CANCELLED;
                notification.rideId = ride.id;
                notification.message = "\"" + ride.title
                        + "\" was cancelled, so your pending request was closed.";
                notification.read = false;
                notification.createdAt = Timestamp.now();
                notificationDataAccess.create(notification, new NotificationDataAccess.WriteCallback() {
                    @Override
                    public void onSuccess() {}

                    @Override
                    public void onError(Exception exception) {}
                });
            }
        });
    }

    /** Best-effort in-app notification to every rider except the organizer. */
    private void notifyRiders(Ride ride, String type, String message) {
        List<String> participants = ride.participantIds == null
                ? Collections.emptyList()
                : ride.participantIds;
        for (String participantId : participants) {
            if (participantId == null || participantId.equals(ride.organizerId)) {
                continue;
            }
            Notification notification = new Notification();
            notification.userId = participantId;
            notification.type = type;
            notification.rideId = ride.id;
            notification.message = message;
            notification.read = false;
            notification.createdAt = Timestamp.now();
            notificationDataAccess.create(
                    notification, new NotificationDataAccess.WriteCallback() {
                        @Override
                        public void onSuccess() {}

                        @Override
                        public void onError(Exception exception) {}
                    });
        }
    }

    /** Returns a reason the user may not change the ride, or {@code null} if they may. */
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

    /** Returns why the delay can't be applied, or {@code null} if it can. */
    public static String delayError(
            Ride ride, Timestamp newDeparture, String actingUserId, Timestamp now) {
        String authorization = authorizationError(ride, actingUserId);
        if (authorization != null) {
            return authorization;
        }
        if (!ride.isActive()) {
            return REASON_FINAL;
        }
        if (newDeparture == null) {
            return REASON_DELAY_TIME_REQUIRED;
        }
        if (now != null && newDeparture.compareTo(now) <= 0) {
            return REASON_DELAY_IN_PAST;
        }
        if (ride.departureTime != null && newDeparture.compareTo(ride.departureTime) <= 0) {
            return REASON_DELAY_NOT_LATER;
        }
        return null;
    }

    /** Returns why the ride can't be completed now, or {@code null} if it can. */
    public static String completeError(Ride ride, String actingUserId, Timestamp now) {
        String authorization = authorizationError(ride, actingUserId);
        if (authorization != null) {
            return authorization;
        }
        if (!ride.isActive()) {
            return REASON_FINAL;
        }
        if (!ride.hasDeparted(now)) {
            return REASON_NOT_DEPARTED;
        }
        return null;
    }
}
