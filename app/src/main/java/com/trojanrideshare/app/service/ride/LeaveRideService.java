package com.trojanrideshare.app.service.ride;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.notification.NotificationDataAccess;
import com.trojanrideshare.app.data.ride.RideMembershipDataAccess;
import com.trojanrideshare.app.model.Notification;
import com.trojanrideshare.app.model.Ride;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leaving a ride.
 *
 * <ul>
 *   <li>Allowed only on an active (On Time/Delayed) ride, and only at least three hours before
 *       its current departure time. For a Delayed ride that is the updated time. The cutoff
 *       applies to the organizer too.</li>
 *   <li>A rider who isn't the organizer is removed; ownership and status don't change. A Full
 *       ride reopens because a seat frees up.</li>
 *   <li>A solo organizer leaving cancels the ride. Pending requests are then closed.</li>
 *   <li>An organizer leaving with other riders hands the ride to the earliest-joined remaining
 *       rider (riders are stored in the order they were accepted).</li>
 * </ul>
 */
public class LeaveRideService {
    public static final long LEAVE_CUTOFF_MILLIS = 3L * 60 * 60 * 1000;

    public static final String REASON_NOT_MEMBER = "You are not on this ride.";
    public static final String REASON_COMPLETED = "You can't leave a completed ride.";
    public static final String REASON_CANCELLED = "This ride has already been cancelled.";
    public static final String REASON_TOO_LATE =
            "You can only leave at least 3 hours before departure.";
    public static final String REASON_UNAVAILABLE = "This ride is unavailable.";

    public enum Outcome { RIDER_LEFT, OWNERSHIP_TRANSFERRED, RIDE_CANCELLED }

    /** What leaving will do, computed from the ride as it is now. */
    public static final class Plan {
        public final Outcome outcome;
        public final String newOrganizerId;
        public final Map<String, Object> rideUpdates;

        Plan(Outcome outcome, String newOrganizerId, Map<String, Object> rideUpdates) {
            this.outcome = outcome;
            this.newOrganizerId = newOrganizerId;
            this.rideUpdates = rideUpdates;
        }
    }

    public interface Callback {
        void onSuccess(Plan plan);
        void onDenied(String reason);
        void onError(String message);
    }

    private final RideMembershipDataAccess membershipDataAccess;
    private final NotificationDataAccess notificationDataAccess;

    public LeaveRideService() {
        this(new RideMembershipDataAccess(), new NotificationDataAccess());
    }

    LeaveRideService(RideMembershipDataAccess membershipDataAccess,
                     NotificationDataAccess notificationDataAccess) {
        this.membershipDataAccess = membershipDataAccess;
        this.notificationDataAccess = notificationDataAccess;
    }

    public void leave(Ride ride, String userId, Callback callback) {
        String error = leaveError(ride, userId, Timestamp.now());
        if (error != null) {
            callback.onDenied(error);
            return;
        }
        membershipDataAccess.leave(ride.id, userId, new RideMembershipDataAccess.LeaveCallback() {
            @Override
            public void onSuccess(Ride before, Plan plan) {
                afterLeave(before, userId, plan);
                callback.onSuccess(plan);
            }

            @Override
            public void onDenied(String reason) {
                callback.onDenied(reason);
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to leave this ride. Please try again.");
            }
        });
    }

    private void afterLeave(Ride ride, String userId, Plan plan) {
        String title = ride.title == null ? "your ride" : "\"" + ride.title + "\"";
        switch (plan.outcome) {
            case RIDE_CANCELLED:
                membershipDataAccess.closePendingRequests(ride.id, requesterIds -> {
                    for (String requesterId : requesterIds) {
                        sendNotification(requesterId, ride.id, Notification.TYPE_RIDE_CANCELLED,
                                title + " was cancelled, so your pending request was closed.");
                    }
                });
                break;
            case OWNERSHIP_TRANSFERRED:
                sendNotification(plan.newOrganizerId, ride.id, Notification.TYPE_RIDE_UPDATED,
                        "The organizer left " + title + ". You are now the organizer.");
                for (String riderId : remaining(ride, userId)) {
                    if (!riderId.equals(plan.newOrganizerId)) {
                        sendNotification(riderId, ride.id, Notification.TYPE_RIDE_UPDATED,
                                "The organizer left " + title + "; it has a new organizer.");
                    }
                }
                break;
            case RIDER_LEFT:
            default:
                sendNotification(ride.organizerId, ride.id, Notification.TYPE_RIDE_UPDATED,
                        "A rider left " + title + ". A seat is open again.");
                break;
        }
    }

    private void sendNotification(String userId, String rideId, String type, String message) {
        if (notificationDataAccess == null || userId == null) return;
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.rideId = rideId;
        notification.message = message;
        notification.read = false;
        notification.createdAt = Timestamp.now();
        notificationDataAccess.create(notification, new NotificationDataAccess.WriteCallback() {
            @Override
            public void onSuccess() {}

            @Override
            public void onError(Exception exception) {}
        });
    }

    /** Returns why {@code userId} can't leave right now, or {@code null} if they can. */
    public static String leaveError(Ride ride, String userId, Timestamp now) {
        if (ride == null || ride.id == null || userId == null || userId.trim().isEmpty()) {
            return REASON_UNAVAILABLE;
        }
        if (Ride.STATUS_COMPLETED.equals(ride.status)) {
            return REASON_COMPLETED;
        }
        if (Ride.STATUS_CANCELLED.equals(ride.status) || !ride.isActive()) {
            return REASON_CANCELLED;
        }
        boolean member = userId.equals(ride.organizerId)
                || (ride.participantIds != null && ride.participantIds.contains(userId));
        if (!member) {
            return REASON_NOT_MEMBER;
        }
        if (ride.departureTime == null || now == null
                || ride.departureTime.toDate().getTime() - now.toDate().getTime()
                        < LEAVE_CUTOFF_MILLIS) {
            return REASON_TOO_LATE;
        }
        return null;
    }

    /** Works out the ride changes for {@code userId} leaving. Call after {@link #leaveError}. */
    public static Plan plan(Ride ride, String userId) {
        List<String> remaining = remaining(ride, userId);
        Map<String, Object> updates = new HashMap<>();
        if (userId.equals(ride.organizerId)) {
            if (remaining.isEmpty()) {
                // The organizer stays listed so the cancelled ride appears in their history.
                updates.put("status", Ride.STATUS_CANCELLED);
                return new Plan(Outcome.RIDE_CANCELLED, null, updates);
            }
            String newOrganizer = remaining.get(0);
            updates.put("organizerId", newOrganizer);
            updates.put("participantIds", remaining);
            return new Plan(Outcome.OWNERSHIP_TRANSFERRED, newOrganizer, updates);
        }
        updates.put("participantIds", remaining);
        return new Plan(Outcome.RIDER_LEFT, null, updates);
    }

    /** Riders other than {@code userId}, in the order they joined. */
    static List<String> remaining(Ride ride, String userId) {
        List<String> participants = ride.participantIds == null
                ? Collections.emptyList() : ride.participantIds;
        List<String> remaining = new ArrayList<>();
        for (String id : participants) {
            if (id != null && !id.equals(userId) && !remaining.contains(id)) {
                remaining.add(id);
            }
        }
        return remaining;
    }
}
