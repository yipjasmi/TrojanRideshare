package com.trojanrideshare.app.service.ride;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.Ride;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a user's rides into upcoming and completed for their profile (SRS step 12).
 *
 * <p>Completion is a client-side lazy transition: a ride whose departure time has passed is
 * treated as completed even if its stored status has not yet been flipped, and
 * {@link #load} best-effort writes the "completed" status back. Cancelled rides appear in
 * neither list.
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
                Timestamp now = Timestamp.now();
                flipLazilyCompleted(rides, now);
                callback.onSuccess(partition(rides, now));
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load your rides. Please try again.");
            }
        });
    }

    private void flipLazilyCompleted(List<Ride> rides, Timestamp now) {
        if (rides == null) {
            return;
        }
        for (Ride ride : rides) {
            if (ride != null && ride.id != null && needsCompletion(ride, now)) {
                // Best effort: the profile already treats it as completed regardless.
                rideDataAccess.updateStatus(ride.id, Ride.STATUS_COMPLETED,
                        new RideDataAccess.WriteCallback() {
                            @Override
                            public void onSuccess() {}

                            @Override
                            public void onError(Exception exception) {}
                        });
            }
        }
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
            if (Ride.STATUS_COMPLETED.equals(ride.status) || hasDeparted(ride, now)) {
                completed.add(ride);
            } else {
                upcoming.add(ride);
            }
        }
        return new Schedule(upcoming, completed);
    }

    /** True if an active ride has already departed and should be flipped to completed. */
    public static boolean needsCompletion(Ride ride, Timestamp now) {
        if (ride == null) {
            return false;
        }
        boolean active = Ride.STATUS_ON_TIME.equals(ride.status)
                || Ride.STATUS_DELAYED.equals(ride.status);
        return active && hasDeparted(ride, now);
    }

    private static boolean hasDeparted(Ride ride, Timestamp now) {
        return ride.departureTime != null && now != null
                && ride.departureTime.compareTo(now) <= 0;
    }
}
