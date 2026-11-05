package com.trojanrideshare.app.service.ride;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * The lifecycle timeline on ride details and the rule hint shown above its action buttons.
 *
 * <p>Steps run Posted, Seats filling, Departed, Completed, Cost split, Paid. The first step that
 * isn't done is current and the rest are upcoming. A cancelled ride shows Posted, then
 * Cancelled. Paid is only shown to people on the ride, since nobody else can read its payments.
 */
public final class RideTimelineService {
    public enum Step { POSTED, SEATS, DEPARTED, COMPLETED, COST_SPLIT, PAID, CANCELLED }

    public enum State { DONE, CURRENT, UPCOMING }

    public enum Hint { NONE, LEAVE_UNTIL, LEAVING_CLOSED, REQUESTS_CLOSE, COMPLETE_AFTER_DEPARTURE }

    public static final class StepState {
        public final Step step;
        public final State state;

        StepState(Step step, State state) {
            this.step = step;
            this.state = state;
        }
    }

    /** How many of the people who owe a share have paid, as far as the signed-in user can see. */
    public static final class PaymentProgress {
        public final int paid;
        public final int total;

        PaymentProgress(int paid, int total) {
            this.paid = paid;
            this.total = total;
        }

        public boolean allPaid() {
            return paid >= total;
        }
    }

    private RideTimelineService() {}

    /** {@code payments} is null until the shares load; Paid stays current until then. */
    public static List<StepState> steps(
            Ride ride,
            String userId,
            Timestamp now,
            PaymentProgress payments
    ) {
        Map<Step, Boolean> done = new LinkedHashMap<>();
        done.put(Step.POSTED, true);
        if (Ride.STATUS_CANCELLED.equals(ride.status)) {
            done.put(Step.CANCELLED, false);
            return withStates(done);
        }
        boolean completed = Ride.STATUS_COMPLETED.equals(ride.status);
        boolean departed = completed || ride.hasDeparted(now);
        boolean costRecorded = completed && ride.finalCost != null;
        done.put(Step.SEATS, departed || ride.getAvailableSeats() == 0);
        done.put(Step.DEPARTED, departed);
        done.put(Step.COMPLETED, completed);
        done.put(Step.COST_SPLIT, costRecorded);
        if (isMember(ride, userId)) {
            done.put(Step.PAID, costRecorded && payments != null && payments.allPaid());
        }
        return withStates(done);
    }

    /** Payments exist once a completed ride has a final cost; only people on it can read them. */
    public static boolean hasPayments(Ride ride, String userId) {
        return Ride.STATUS_COMPLETED.equals(ride.status)
                && ride.finalCost != null
                && isMember(ride, userId);
    }

    /**
     * The organizer counts every rider but themselves, like the Payments screen does. A rider only
     * gets their own share, so it's null until that share is in {@code shares}.
     */
    public static PaymentProgress paymentProgress(Ride ride, String userId, List<Payment> shares) {
        if (userId.equals(ride.organizerId)) {
            int riders = 0;
            int paid = 0;
            for (Payment share : shares) {
                if (Objects.equals(share.userId, ride.organizerId)) {
                    continue;
                }
                riders++;
                if (share.paid) {
                    paid++;
                }
            }
            return new PaymentProgress(paid, riders);
        }
        Payment share = shareOf(shares, userId);
        return share == null ? null : new PaymentProgress(share.paid ? 1 : 0, 1);
    }

    public static Payment shareOf(List<Payment> shares, String userId) {
        for (Payment share : shares) {
            if (Objects.equals(share.userId, userId)) {
                return share;
            }
        }
        return null;
    }

    /** The rule worth knowing before tapping an action. Nothing once the ride has left or ended. */
    public static Hint hint(Ride ride, String userId, Timestamp now) {
        boolean signedIn = userId != null && !userId.trim().isEmpty();
        if (!signedIn || !ride.isActive() || ride.hasDeparted(now)) {
            return Hint.NONE;
        }
        if (userId.equals(ride.organizerId)) {
            return Hint.COMPLETE_AFTER_DEPARTURE;
        }
        if (isMember(ride, userId)) {
            String error = LeaveRideService.leaveError(ride, userId, now);
            if (error == null) {
                return Hint.LEAVE_UNTIL;
            }
            return LeaveRideService.REASON_TOO_LATE.equals(error) ? Hint.LEAVING_CLOSED : Hint.NONE;
        }
        return ride.getAvailableSeats() > 0 ? Hint.REQUESTS_CLOSE : Hint.NONE;
    }

    /** The last moment {@link LeaveRideService} lets someone leave. */
    public static Timestamp leaveCutoff(Ride ride) {
        long departureMillis = ride.departureTime.toDate().getTime();
        return new Timestamp(new Date(departureMillis - LeaveRideService.LEAVE_CUTOFF_MILLIS));
    }

    public static int leaveCutoffHours() {
        return (int) TimeUnit.MILLISECONDS.toHours(LeaveRideService.LEAVE_CUTOFF_MILLIS);
    }

    /** The original time is kept from the first delay, so it only differs once a delay moved it. */
    public static boolean wasDelayed(Ride ride) {
        return ride.originalDepartureTime != null
                && !ride.originalDepartureTime.equals(ride.departureTime);
    }

    private static List<StepState> withStates(Map<Step, Boolean> done) {
        List<StepState> steps = new ArrayList<>();
        boolean currentFound = false;
        for (Map.Entry<Step, Boolean> entry : done.entrySet()) {
            State state;
            if (currentFound) {
                state = State.UPCOMING;
            } else if (entry.getValue()) {
                state = State.DONE;
            } else {
                state = State.CURRENT;
                currentFound = true;
            }
            steps.add(new StepState(entry.getKey(), state));
        }
        return steps;
    }

    private static boolean isMember(Ride ride, String userId) {
        return userId != null
                && (userId.equals(ride.organizerId)
                        || (ride.participantIds != null && ride.participantIds.contains(userId)));
    }
}
