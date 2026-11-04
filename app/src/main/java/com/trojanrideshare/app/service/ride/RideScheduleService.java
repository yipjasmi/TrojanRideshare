package com.trojanrideshare.app.service.ride;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.Ride;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a user's rides into upcoming and completed for their profile (SRS step 12).
 *
 * <p>Only the organizer completes a ride (after departure), so a ride counts as completed only
 * once its status is "completed". A departed ride that hasn't been completed yet stays in
 * upcoming. Cancelled rides appear in neither list.
 */
public class RideScheduleService {
    public interface Callback {
        void onSuccess(Schedule schedule);
        void onError(String message);
    }

    public static final class Schedule {
        public final List<Ride> upcoming;
        public final List<Ride> completed;

        Schedule(List<Ride> upcoming, List<Ride> completed) {
            this.upcoming = upcoming;
            this.completed = completed;
        }
    }

    private final RideDataAccess rideDataAccess;

    public RideScheduleService() {
        this(new RideDataAccess());
    }

    RideScheduleService(RideDataAccess rideDataAccess) {
        this.rideDataAccess = rideDataAccess;
    }

    public void load(String userId, Callback callback) {
        if (userId == null || userId.trim().isEmpty()) {
            callback.onError("You must be signed in to see your rides.");
            return;
        }
        rideDataAccess.getRidesForUser(userId, new RideDataAccess.Callback() {
            @Override
            public void onSuccess(List<Ride> rides) {
                callback.onSuccess(partition(rides, Timestamp.now()));
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load your rides. Please try again.");
            }
        });
    }

    /** Partitions rides into upcoming vs completed; cancelled rides are excluded from both. */
    public static Schedule partition(List<Ride> rides, Timestamp now) {
        List<Ride> upcoming = new ArrayList<>();
        List<Ride> completed = new ArrayList<>();
        if (rides == null) {
            return new Schedule(upcoming, completed);
        }
        for (Ride ride : rides) {
            if (ride == null || Ride.STATUS_CANCELLED.equals(ride.status)) {
                continue;
            }
            if (Ride.STATUS_COMPLETED.equals(ride.status)) {
                completed.add(ride);
            } else {
                upcoming.add(ride);
            }
        }
        return new Schedule(upcoming, completed);
    }

    /** True if an active ride has departed and is waiting for its organizer to complete it. */
    public static boolean awaitingCompletion(Ride ride, Timestamp now) {
        return ride != null && ride.isActive() && ride.hasDeparted(now);
    }
}
