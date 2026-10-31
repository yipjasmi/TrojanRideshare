package com.trojanrideshare.app.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.service.payment.PaymentService.RideCollection;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PaymentRulesTest {
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    private static Ride ride(String id, String organizerId, String status, String... riders) {
        Ride ride = new Ride();
        ride.id = id;
        ride.organizerId = organizerId;
        ride.status = status;
        ride.participantIds = new ArrayList<>(Arrays.asList(riders));
        return ride;
    }

    private static Payment share(String rideId, String userId, double amount, boolean paid) {
        Payment share = new Payment();
        share.rideId = rideId;
        share.userId = userId;
        share.amount = amount;
        share.paid = paid;
        return share;
    }

    @Test
    public void organizerIsAlwaysFirstAndNeverCountedTwice() {
        Ride without = ride("r", "org", Ride.STATUS_COMPLETED, "a", "b");
        Ride with = ride("r", "org", Ride.STATUS_COMPLETED, "a", "org", "b");
        assertEquals(Arrays.asList("org", "a", "b"), PaymentService.participantsOf(without));
        assertEquals(Arrays.asList("org", "a", "b"), PaymentService.participantsOf(with));
    }

    @Test
    public void organizerCanSplitACompletedRide() {
        Ride ride = ride("r", "org", Ride.STATUS_COMPLETED, "a");
        assertNull(PaymentService.splitBlockedReason(ride, "org", Collections.emptyList()));
    }

    @Test
    public void onlyTheOrganizerCanSplit() {
        Ride ride = ride("r", "org", Ride.STATUS_COMPLETED, "a");
        assertEquals("Only the ride organizer can record the final cost.",
                PaymentService.splitBlockedReason(ride, "a", Collections.emptyList()));
    }

    @Test
    public void rideMustBeCompletedBeforeSplitting() {
        for (String status : Arrays.asList(Ride.STATUS_ON_TIME, Ride.STATUS_DELAYED,
                Ride.STATUS_CANCELLED)) {
            Ride ride = ride("r", "org", status, "a");
            assertEquals("Mark the ride completed before recording the final cost.",
                    PaymentService.splitBlockedReason(ride, "org", Collections.emptyList()));
        }
    }

    @Test
    public void deletedRideCannotBeSplit() {
        assertEquals("This ride no longer exists.",
                PaymentService.splitBlockedReason(null, "org", Collections.emptyList()));
    }

    @Test
    public void aRiderMarkingPaidLocksTheSplit() {
        Ride ride = ride("r", "org", Ride.STATUS_COMPLETED, "a");
        Payment riderPaid = share("r", "a", 10, true);
        riderPaid.paidAt = new Timestamp(new Date());
        assertEquals("A rider already marked their share paid, so this split is locked.",
                PaymentService.splitBlockedReason(ride, "org", Collections.singletonList(riderPaid)));
    }

    @Test
    public void theOrganizersAutoSettledShareDoesNotLockTheSplit() {
        Ride ride = ride("r", "org", Ride.STATUS_COMPLETED, "a");
        Payment organizerShare = share("r", "org", 10, true);
        assertNull(PaymentService.splitBlockedReason(
                ride, "org", Collections.singletonList(organizerShare)));
    }

    @Test
    public void overdueStartsExactlySevenDaysAfterTheSplit() {
        assertFalse(PaymentService.isOverdue(false, 0, 7 * DAY - 1));
        assertTrue(PaymentService.isOverdue(false, 0, 7 * DAY));
        assertTrue(PaymentService.isOverdue(false, 0, 30 * DAY));
    }

    @Test
    public void aPaidShareIsNeverOverdue() {
        assertFalse(PaymentService.isOverdue(true, 0, 30 * DAY));
    }

    @Test
    public void summaryCountsRidersOnlyAndAddsUpWhatIsStillOwed() {
        Ride ride = ride("r", "org", Ride.STATUS_COMPLETED, "a", "b", "c");
        ride.finalCost = 40.0;
        List<Payment> shares = Arrays.asList(
                share("r", "org", 10.01, true),
                share("r", "a", 10.01, true),
                share("r", "b", 9.99, false),
                share("r", "c", 9.99, false),
                share("another-ride", "z", 99, false));

        RideCollection collection =
                PaymentService.summarize(Collections.singletonList(ride), shares).get(0);

        assertFalse(collection.needsFinalCost);
        assertEquals(3, collection.riders);
        assertEquals(1, collection.ridersPaid);
        assertEquals(1998, collection.outstandingCents);
    }

    @Test
    public void ridesNeedingACostComeFirstAndFinishedRidesLast() {
        Ride done = ride("done", "org", Ride.STATUS_COMPLETED, "a");
        done.finalCost = 10.0;
        Ride owing = ride("owing", "org", Ride.STATUS_COMPLETED, "a");
        owing.finalCost = 10.0;
        Ride noCost = ride("no-cost", "org", Ride.STATUS_COMPLETED, "a");

        List<RideCollection> sorted = PaymentService.summarize(
                Arrays.asList(done, owing, noCost),
                Arrays.asList(share("done", "a", 5, true), share("owing", "a", 5, false)));

        assertEquals("no-cost", sorted.get(0).ride.id);
        assertTrue(sorted.get(0).needsFinalCost);
        assertEquals("owing", sorted.get(1).ride.id);
        assertEquals("done", sorted.get(2).ride.id);
    }
}
