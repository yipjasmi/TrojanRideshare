package com.trojanrideshare.app.service.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.ride.RideTimelineService.Hint;
import com.trojanrideshare.app.service.ride.RideTimelineService.PaymentProgress;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RideTimelineServiceTest {
    private static final long NOW_MILLIS = 1_700_000_000_000L;
    private static final long HOUR = 60L * 60 * 1000;
    private static final long CUTOFF = LeaveRideService.LEAVE_CUTOFF_MILLIS;
    private static final Timestamp NOW = new Timestamp(new Date(NOW_MILLIS));

    @Test
    public void steps_cancelledRideShowsPostedThenCancelled() {
        Ride ride = ride(Ride.STATUS_CANCELLED, -HOUR);
        ride.finalCost = 45.0;

        assertEquals(Arrays.asList("POSTED:DONE", "CANCELLED:CURRENT"),
                describe(RideTimelineService.steps(ride, "rider", NOW, null)));
    }

    @Test
    public void steps_openRideBeforeDepartureIsFillingSeats() {
        assertEquals(Arrays.asList("POSTED:DONE", "SEATS:CURRENT", "DEPARTED:UPCOMING",
                        "COMPLETED:UPCOMING", "COST_SPLIT:UPCOMING", "PAID:UPCOMING"),
                describe(RideTimelineService.steps(ride(Ride.STATUS_ON_TIME, 10 * HOUR),
                        "rider", NOW, null)));
    }

    @Test
    public void steps_fullRideBeforeDepartureIsWaitingToLeave() {
        Ride ride = ride(Ride.STATUS_ON_TIME, 10 * HOUR);
        ride.maxRiders = 2;

        assertEquals(Arrays.asList("POSTED:DONE", "SEATS:DONE", "DEPARTED:CURRENT",
                        "COMPLETED:UPCOMING", "COST_SPLIT:UPCOMING", "PAID:UPCOMING"),
                describe(RideTimelineService.steps(ride, "rider", NOW, null)));
    }

    @Test
    public void steps_delayedRideUsesItsNewDepartureTime() {
        Ride ride = ride(Ride.STATUS_DELAYED, 2 * HOUR);
        ride.originalDepartureTime = at(-HOUR);

        assertEquals("SEATS:CURRENT",
                describe(RideTimelineService.steps(ride, "rider", NOW, null)).get(1));
    }

    @Test
    public void steps_departedRideWaitsForTheOrganizerToComplete() {
        assertEquals(Arrays.asList("POSTED:DONE", "SEATS:DONE", "DEPARTED:DONE",
                        "COMPLETED:CURRENT", "COST_SPLIT:UPCOMING", "PAID:UPCOMING"),
                describe(RideTimelineService.steps(ride(Ride.STATUS_DELAYED, -HOUR),
                        "org", NOW, null)));
    }

    @Test
    public void steps_completedRideWithoutCostWaitsForTheSplit() {
        assertEquals(Arrays.asList("POSTED:DONE", "SEATS:DONE", "DEPARTED:DONE",
                        "COMPLETED:DONE", "COST_SPLIT:CURRENT", "PAID:UPCOMING"),
                describe(RideTimelineService.steps(ride(Ride.STATUS_COMPLETED, -HOUR),
                        "rider", NOW, new PaymentProgress(0, 1))));
    }

    @Test
    public void steps_completedSplitWithUnpaidRidersWaitsForPayment() {
        assertEquals(Arrays.asList("POSTED:DONE", "SEATS:DONE", "DEPARTED:DONE",
                        "COMPLETED:DONE", "COST_SPLIT:DONE", "PAID:CURRENT"),
                describe(RideTimelineService.steps(costRecorded(), "org", NOW,
                        new PaymentProgress(1, 2))));
    }

    @Test
    public void steps_allPaidMarksEveryStepDone() {
        assertEquals(Arrays.asList("POSTED:DONE", "SEATS:DONE", "DEPARTED:DONE",
                        "COMPLETED:DONE", "COST_SPLIT:DONE", "PAID:DONE"),
                describe(RideTimelineService.steps(costRecorded(), "org", NOW,
                        new PaymentProgress(2, 2))));
    }

    @Test
    public void steps_paidStaysCurrentUntilPaymentsLoad() {
        assertEquals("PAID:CURRENT",
                describe(RideTimelineService.steps(costRecorded(), "rider", NOW, null)).get(5));
    }

    @Test
    public void steps_nonParticipantHasNoPaidStep() {
        assertEquals(Arrays.asList("POSTED:DONE", "SEATS:DONE", "DEPARTED:DONE",
                        "COMPLETED:DONE", "COST_SPLIT:DONE"),
                describe(RideTimelineService.steps(costRecorded(), "stranger", NOW, null)));
    }

    @Test
    public void hasPayments_onlyForPeopleOnACompletedRideWithACost() {
        assertTrue(RideTimelineService.hasPayments(costRecorded(), "org"));
        assertTrue(RideTimelineService.hasPayments(costRecorded(), "rider"));
        assertFalse(RideTimelineService.hasPayments(costRecorded(), "stranger"));
        assertFalse(RideTimelineService.hasPayments(costRecorded(), null));
        assertFalse(RideTimelineService.hasPayments(ride(Ride.STATUS_COMPLETED, -HOUR), "org"));

        Ride cancelled = costRecorded();
        cancelled.status = Ride.STATUS_CANCELLED;
        assertFalse(RideTimelineService.hasPayments(cancelled, "org"));
    }

    @Test
    public void paymentProgress_organizerCountsRidersButNotThemselves() {
        Ride ride = costRecorded();
        ride.participantIds = new ArrayList<>(Arrays.asList("org", "rider", "second"));
        List<Payment> shares = Arrays.asList(
                share("org", true), share("rider", true), share("second", false));

        PaymentProgress progress = RideTimelineService.paymentProgress(ride, "org", shares);

        assertEquals(1, progress.paid);
        assertEquals(2, progress.total);
        assertFalse(progress.allPaid());
    }

    @Test
    public void paymentProgress_organizerCountsARiderWithNoShareAsUnpaid() {
        Ride ride = costRecorded();
        ride.participantIds = new ArrayList<>(Arrays.asList("org", "rider", "second"));
        List<Payment> shares = Arrays.asList(share("org", true), share("rider", true));

        PaymentProgress progress = RideTimelineService.paymentProgress(ride, "org", shares);

        assertEquals(1, progress.paid);
        assertEquals(2, progress.total);
        assertFalse(progress.allPaid());
    }

    @Test
    public void hint_givesNoneForARideWithNoDepartureTime() {
        Ride ride = ride(Ride.STATUS_ON_TIME, 10 * HOUR);
        ride.departureTime = null;

        assertEquals(Hint.NONE, RideTimelineService.hint(ride, "rider", NOW));
        assertEquals(Hint.NONE, RideTimelineService.hint(ride, "stranger", NOW));
    }

    @Test
    public void wasDelayed_needsADepartureTimeToCompareWith() {
        Ride ride = ride(Ride.STATUS_DELAYED, 10 * HOUR);
        ride.originalDepartureTime = at(5 * HOUR);
        ride.departureTime = null;

        assertFalse(RideTimelineService.wasDelayed(ride));
    }

    @Test
    public void paymentProgress_organizerWithNoRidersHasNothingLeftToCollect() {
        Ride ride = costRecorded();
        ride.participantIds = new ArrayList<>(Collections.singletonList("org"));

        PaymentProgress progress = RideTimelineService.paymentProgress(
                ride, "org", Collections.singletonList(share("org", true)));

        assertEquals(0, progress.total);
        assertTrue(progress.allPaid());
    }

    @Test
    public void paymentProgress_riderOnlyCountsTheirOwnShare() {
        Ride ride = costRecorded();

        PaymentProgress unpaid = RideTimelineService.paymentProgress(
                ride, "rider", Collections.singletonList(share("rider", false)));
        PaymentProgress paid = RideTimelineService.paymentProgress(
                ride, "rider", Collections.singletonList(share("rider", true)));

        assertEquals(0, unpaid.paid);
        assertEquals(1, unpaid.total);
        assertTrue(paid.allPaid());
        assertNull(RideTimelineService.paymentProgress(ride, "rider", Collections.emptyList()));
    }

    @Test
    public void hint_riderCanLeaveUntilExactlyTheCutoff() {
        assertEquals(Hint.LEAVE_UNTIL,
                RideTimelineService.hint(ride(Ride.STATUS_ON_TIME, CUTOFF), "rider", NOW));
    }

    @Test
    public void hint_riderInsideTheCutoffCanNoLongerLeave() {
        assertEquals(Hint.LEAVING_CLOSED,
                RideTimelineService.hint(ride(Ride.STATUS_ON_TIME, CUTOFF - 1), "rider", NOW));
    }

    @Test
    public void hint_organizerBeforeDepartureLearnsWhenTheyCanComplete() {
        assertEquals(Hint.COMPLETE_AFTER_DEPARTURE,
                RideTimelineService.hint(ride(Ride.STATUS_ON_TIME, HOUR), "org", NOW));
        assertEquals(Hint.NONE,
                RideTimelineService.hint(ride(Ride.STATUS_ON_TIME, -HOUR), "org", NOW));
    }

    @Test
    public void hint_nonParticipantSeesWhenRequestsClose() {
        assertEquals(Hint.REQUESTS_CLOSE,
                RideTimelineService.hint(ride(Ride.STATUS_ON_TIME, HOUR), "stranger", NOW));

        Ride full = ride(Ride.STATUS_ON_TIME, HOUR);
        full.maxRiders = 2;
        assertEquals(Hint.NONE, RideTimelineService.hint(full, "stranger", NOW));
    }

    @Test
    public void hint_noneOnceTheRideHasLeftOrEndedOrNobodyIsSignedIn() {
        assertEquals(Hint.NONE,
                RideTimelineService.hint(ride(Ride.STATUS_ON_TIME, -HOUR), "rider", NOW));
        assertEquals(Hint.NONE,
                RideTimelineService.hint(ride(Ride.STATUS_COMPLETED, 10 * HOUR), "rider", NOW));
        assertEquals(Hint.NONE,
                RideTimelineService.hint(ride(Ride.STATUS_CANCELLED, 10 * HOUR), "rider", NOW));
        assertEquals(Hint.NONE,
                RideTimelineService.hint(ride(Ride.STATUS_ON_TIME, 10 * HOUR), null, NOW));
    }

    @Test
    public void leaveCutoff_isDepartureMinusTheLeaveRideCutoff() {
        Ride ride = ride(Ride.STATUS_ON_TIME, 10 * HOUR);

        assertEquals(at(10 * HOUR - CUTOFF), RideTimelineService.leaveCutoff(ride));
        assertEquals(CUTOFF, TimeUnit.HOURS.toMillis(RideTimelineService.leaveCutoffHours()));
    }

    @Test
    public void wasDelayed_onlyOnceADelayMovedTheDeparture() {
        Ride ride = ride(Ride.STATUS_DELAYED, 2 * HOUR);
        assertFalse(RideTimelineService.wasDelayed(ride));

        ride.originalDepartureTime = at(2 * HOUR);
        assertFalse(RideTimelineService.wasDelayed(ride));

        ride.originalDepartureTime = at(HOUR);
        assertTrue(RideTimelineService.wasDelayed(ride));
    }

    private static List<String> describe(List<RideTimelineService.StepState> steps) {
        List<String> described = new ArrayList<>();
        for (RideTimelineService.StepState step : steps) {
            described.add(step.step + ":" + step.state);
        }
        return described;
    }

    private static Timestamp at(long offsetMillis) {
        return new Timestamp(new Date(NOW_MILLIS + offsetMillis));
    }

    private static Ride costRecorded() {
        Ride ride = ride(Ride.STATUS_COMPLETED, -HOUR);
        ride.finalCost = 45.0;
        ride.completedAt = at(-HOUR / 2);
        return ride;
    }

    private static Payment share(String userId, boolean paid) {
        Payment share = new Payment();
        share.rideId = "ride";
        share.userId = userId;
        share.organizerId = "org";
        share.amount = 15.0;
        share.paid = paid;
        return share;
    }

    private static Ride ride(String status, long departsIn) {
        Ride ride = new Ride();
        ride.id = "ride";
        ride.organizerId = "org";
        ride.participantIds = new ArrayList<>(Arrays.asList("org", "rider"));
        ride.maxRiders = 4;
        ride.status = status;
        ride.departureTime = at(departsIn);
        return ride;
    }
}
