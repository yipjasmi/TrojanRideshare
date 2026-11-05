package com.trojanrideshare.app.service.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.payment.PaymentRepository;
import com.trojanrideshare.app.model.OrganizerRideSummary;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.PaymentHandles;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.RidePaymentDetails;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentServiceTest {
    private static final String ORGANIZER_ID = "organizer";
    private static final String RIDER_ID = "rider";

    @Test
    public void splitBlockedReason_allowsTheOrganizerOfACompletedRide() {
        Ride ride = ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);

        assertNull(PaymentService.splitBlockedReason(ride, ORGANIZER_ID, Collections.emptyList()));
    }

    @Test
    public void splitBlockedReason_blocksEveryoneButTheOrganizer() {
        Ride ride = ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);

        assertEquals(
                "Only the ride organizer can record the final cost.",
                PaymentService.splitBlockedReason(ride, RIDER_ID, Collections.emptyList())
        );
    }

    @Test
    public void splitBlockedReason_blocksARideThatIsNotCompleted() {
        for (String status : Arrays.asList(
                Ride.STATUS_ON_TIME,
                Ride.STATUS_DELAYED,
                Ride.STATUS_CANCELLED
        )) {
            Ride ride = ride("ride-1", ORGANIZER_ID, status, RIDER_ID);

            assertEquals(
                    "Mark the ride completed before recording the final cost.",
                    PaymentService.splitBlockedReason(ride, ORGANIZER_ID, Collections.emptyList())
            );
        }
    }

    @Test
    public void splitBlockedReason_blocksADeletedRide() {
        assertEquals(
                "This ride no longer exists.",
                PaymentService.splitBlockedReason(null, ORGANIZER_ID, Collections.emptyList())
        );
    }

    @Test
    public void splitBlockedReason_blocksOnceARiderMarksPaid() {
        Ride ride = ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);
        Payment riderShare = share("ride-1", RIDER_ID, 10, true);
        riderShare.paidAt = new Timestamp(new Date());

        assertEquals(
                "A rider already marked their share paid, so this split is locked.",
                PaymentService.splitBlockedReason(
                        ride,
                        ORGANIZER_ID,
                        Collections.singletonList(riderShare)
                )
        );
    }

    @Test
    public void splitBlockedReason_ignoresTheOrganizersSettledShare() {
        Ride ride = ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);
        Payment organizerShare = share("ride-1", ORGANIZER_ID, 10, true);

        assertNull(PaymentService.splitBlockedReason(
                ride,
                ORGANIZER_ID,
                Collections.singletonList(organizerShare)
        ));
    }

    @Test
    public void summarize_countsRidersOnlyAndAddsUpWhatIsOwed() {
        Ride ride = ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, "a", "b", "c");
        ride.finalCost = 40.0;
        List<Payment> shares = Arrays.asList(
                share("ride-1", ORGANIZER_ID, 10.01, true),
                share("ride-1", "a", 10.01, true),
                share("ride-1", "b", 9.99, false),
                share("ride-1", "c", 9.99, false),
                share("another-ride", "z", 99, false)
        );

        OrganizerRideSummary summary =
                PaymentService.summarize(Collections.singletonList(ride), shares).get(0);

        assertFalse(summary.needsFinalCost);
        assertEquals(3, summary.riders);
        assertEquals(1, summary.ridersPaid);
        assertEquals(1998, summary.outstandingCents);
    }

    @Test
    public void summarize_putsRidesNeedingACostFirstAndFinishedRidesLast() {
        Ride finished = ride("finished", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);
        finished.finalCost = 10.0;
        Ride owing = ride("owing", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);
        owing.finalCost = 10.0;
        Ride noCost = ride("no-cost", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);

        List<OrganizerRideSummary> sorted = PaymentService.summarize(
                Arrays.asList(finished, owing, noCost),
                Arrays.asList(
                        share("finished", RIDER_ID, 5, true),
                        share("owing", RIDER_ID, 5, false)
                )
        );

        assertEquals("no-cost", sorted.get(0).ride.id);
        assertTrue(sorted.get(0).needsFinalCost);
        assertEquals("owing", sorted.get(1).ride.id);
        assertEquals("finished", sorted.get(2).ride.id);
    }

    @Test
    public void summarize_handlesAShareWithNoUserId() {
        Ride ride = ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);
        Payment share = share("ride-1", null, 5, false);

        OrganizerRideSummary summary = PaymentService
                .summarize(Collections.singletonList(ride), Collections.singletonList(share))
                .get(0);

        assertEquals(500, summary.outstandingCents);
    }

    @Test
    public void loadMyShares_leavesOutOrganizedRidesAndListsUnpaidFirst() {
        FakeRepository repository = new FakeRepository();
        Payment organized = share("organized", RIDER_ID, 5, true);
        organized.organizerId = RIDER_ID;
        Payment paid = share("paid", RIDER_ID, 5, true);
        Payment unpaid = share("unpaid", RIDER_ID, 5, false);
        repository.shares = Arrays.asList(organized, paid, unpaid);
        SharesResult result = new SharesResult();

        new PaymentService(repository).loadMyShares(RIDER_ID, result);

        assertEquals(Arrays.asList(unpaid, paid), result.shares);
        assertNull(result.error);
    }

    @Test
    public void loadMyShares_reportsUnexpectedFailures() {
        FakeRepository repository = new FakeRepository();
        repository.exception = new IllegalStateException("network");
        SharesResult result = new SharesResult();

        new PaymentService(repository).loadMyShares(RIDER_ID, result);

        assertNull(result.shares);
        assertEquals("Unable to load your shares. Please try again.", result.error);
    }

    @Test
    public void loadMyRides_summarizesTheOrganizersRides() {
        FakeRepository repository = new FakeRepository();
        repository.rides = Collections.singletonList(
                ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID)
        );
        repository.shares = Collections.singletonList(share("ride-1", RIDER_ID, 5, false));
        SummariesResult result = new SummariesResult();

        new PaymentService(repository).loadMyRides(ORGANIZER_ID, result);

        assertEquals(1, result.summaries.size());
        assertEquals(500, result.summaries.get(0).outstandingCents);
    }

    @Test
    public void loadMyRides_reportsAFailureLoadingTheShares() {
        FakeRepository repository = new FakeRepository();
        repository.sharesException = new IllegalStateException("network");
        SummariesResult result = new SummariesResult();

        new PaymentService(repository).loadMyRides(ORGANIZER_ID, result);

        assertNull(result.summaries);
        assertEquals("Unable to load your rides. Please try again.", result.error);
    }

    @Test
    public void loadRideDetails_reportsADeletedRide() {
        DetailsResult result = new DetailsResult();

        new PaymentService(new FakeRepository()).loadRideDetails("ride-1", RIDER_ID, result);

        assertEquals("This ride no longer exists.", result.error);
    }

    @Test
    public void loadRideDetails_loadsEveryShareForTheOrganizer() {
        FakeRepository repository = new FakeRepository();
        repository.ride = ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);
        repository.shares = Collections.singletonList(share("ride-1", RIDER_ID, 5, false));
        DetailsResult result = new DetailsResult();

        new PaymentService(repository).loadRideDetails("ride-1", ORGANIZER_ID, result);

        assertEquals(Arrays.asList(ORGANIZER_ID, RIDER_ID), result.details.participants);
        assertEquals(1, result.details.shares.size());
    }

    @Test
    public void loadRideDetails_loadsNoSharesForARider() {
        FakeRepository repository = new FakeRepository();
        repository.ride = ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);
        repository.shares = Collections.singletonList(share("ride-1", RIDER_ID, 5, false));
        DetailsResult result = new DetailsResult();

        new PaymentService(repository).loadRideDetails("ride-1", RIDER_ID, result);

        assertNotNull(result.details);
        assertTrue(result.details.shares.isEmpty());
        assertEquals(0, repository.rideShareLoads);
    }

    @Test
    public void saveSplit_explainsEachFailure() {
        assertEquals(
                "A rider already marked their share paid, so this split is locked.",
                saveSplitError(PaymentRepository.Failure.SPLIT_LOCKED)
        );
        assertEquals(
                "The riders on this ride changed. Reopen it and try again.",
                saveSplitError(PaymentRepository.Failure.RIDERS_CHANGED)
        );
        assertEquals(
                "This ride no longer exists.",
                saveSplitError(PaymentRepository.Failure.RIDE_NOT_FOUND)
        );
    }

    @Test
    public void saveSplit_reportsSuccess() {
        UpdateResult result = new UpdateResult();

        new PaymentService(new FakeRepository()).saveSplit(
                "ride-1",
                ORGANIZER_ID,
                Arrays.asList(ORGANIZER_ID, RIDER_ID),
                new long[] {500, 500},
                1000,
                result
        );

        assertEquals(1, result.successCount);
        assertNull(result.error);
    }

    @Test
    public void markPaid_reportsUnexpectedFailures() {
        FakeRepository repository = new FakeRepository();
        repository.exception = new IllegalStateException("network");
        UpdateResult result = new UpdateResult();

        new PaymentService(repository).markPaid("ride-1", RIDER_ID, result);

        assertEquals("Unable to mark this share paid. Please try again.", result.error);
    }

    @Test
    public void flag_namesTheApprovalPeriodWhenTooEarly() {
        FakeRepository repository = new FakeRepository();
        repository.failure = PaymentRepository.Failure.NOT_OVERDUE;
        UpdateResult result = new UpdateResult();

        new PaymentService(repository).flag("ride-1", RIDER_ID, result);

        assertEquals("You can flag a rider once 7 days have passed.", result.error);
    }

    @Test
    public void loadRideDetails_carriesEachParticipantsPaymentHandles() {
        FakeRepository repository = new FakeRepository();
        repository.ride = ride("ride-1", ORGANIZER_ID, Ride.STATUS_COMPLETED, RIDER_ID);
        repository.handles.put(ORGANIZER_ID, new PaymentHandles("ashley-lan", "ashley"));
        DetailsResult result = new DetailsResult();

        new PaymentService(repository).loadRideDetails("ride-1", RIDER_ID, result);

        assertEquals("ashley-lan", result.details.handles.get(ORGANIZER_ID).venmoUsername);
        assertEquals("ashley", result.details.handles.get(ORGANIZER_ID).cashtag);
    }

    @Test
    public void saveHandles_writesTheHandlesWithoutTheirPrefixes() {
        FakeRepository repository = new FakeRepository();
        UpdateResult result = new UpdateResult();

        new PaymentService(repository).saveHandles(RIDER_ID, " @kyle-t ", "$kyle", result);

        assertEquals("kyle-t", repository.savedVenmoUsername);
        assertEquals("kyle", repository.savedCashtag);
        assertEquals(1, result.successCount);
    }

    @Test
    public void saveHandles_clearsABlankHandle() {
        FakeRepository repository = new FakeRepository();
        UpdateResult result = new UpdateResult();

        new PaymentService(repository).saveHandles(RIDER_ID, "", "  ", result);

        assertEquals(1, repository.handleSaves);
        assertNull(repository.savedVenmoUsername);
        assertNull(repository.savedCashtag);
    }

    @Test
    public void saveHandles_writesNothingWhenAHandleIsInvalid() {
        FakeRepository repository = new FakeRepository();
        UpdateResult venmoResult = new UpdateResult();
        UpdateResult cashtagResult = new UpdateResult();

        new PaymentService(repository).saveHandles(RIDER_ID, "kyle/t", "kyle", venmoResult);
        new PaymentService(repository).saveHandles(RIDER_ID, "kyle-t", "12345", cashtagResult);

        assertEquals(0, repository.handleSaves);
        assertEquals("Use only letters, numbers, - and _ in a Venmo username.", venmoResult.error);
        assertEquals("A cashtag needs at least one letter.", cashtagResult.error);
    }

    @Test
    public void saveHandles_reportsUnexpectedFailures() {
        FakeRepository repository = new FakeRepository();
        repository.exception = new IllegalStateException("network");
        UpdateResult result = new UpdateResult();

        new PaymentService(repository).saveHandles(RIDER_ID, "kyle-t", "", result);

        assertEquals("Unable to save your payment apps. Please try again.", result.error);
    }

    private static String saveSplitError(PaymentRepository.Failure failure) {
        FakeRepository repository = new FakeRepository();
        repository.failure = failure;
        UpdateResult result = new UpdateResult();

        new PaymentService(repository).saveSplit(
                "ride-1",
                ORGANIZER_ID,
                Collections.singletonList(ORGANIZER_ID),
                new long[] {1000},
                1000,
                result
        );
        return result.error;
    }

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
        share.organizerId = ORGANIZER_ID;
        share.amount = amount;
        share.paid = paid;
        return share;
    }

    private static final class FakeRepository implements PaymentRepository {
        private Ride ride;
        private List<Ride> rides = new ArrayList<>();
        private List<Payment> shares = new ArrayList<>();
        private Failure failure;
        private Exception exception;
        private Exception sharesException;
        private int rideShareLoads;
        private Map<String, PaymentHandles> handles = new HashMap<>();
        private int handleSaves;
        private String savedVenmoUsername;
        private String savedCashtag;

        @Override
        public void loadRide(String rideId, RideCallback callback) {
            callback.onSuccess(ride);
        }

        @Override
        public void loadCompletedRides(String organizerId, RidesCallback callback) {
            callback.onSuccess(rides);
        }

        @Override
        public void loadSharesFor(String userId, SharesCallback callback) {
            if (exception != null) {
                callback.onError(exception);
            } else {
                callback.onSuccess(shares);
            }
        }

        @Override
        public void loadSharesOrganizedBy(String organizerId, SharesCallback callback) {
            if (sharesException != null) {
                callback.onError(sharesException);
            } else {
                callback.onSuccess(shares);
            }
        }

        @Override
        public void loadRideShares(String rideId, String organizerId, SharesCallback callback) {
            rideShareLoads++;
            callback.onSuccess(shares);
        }

        @Override
        public void loadNames(Collection<String> userIds, NamesCallback callback) {
            Map<String, String> names = new HashMap<>();
            callback.onSuccess(names, handles);
        }

        @Override
        public void watchRideShares(
                Activity owner,
                String rideId,
                String organizerId,
                SharesCallback callback
        ) {
            callback.onSuccess(shares);
        }

        @Override
        public void watchShare(
                Activity owner,
                String rideId,
                String userId,
                SharesCallback callback
        ) {
            callback.onSuccess(shares);
        }

        @Override
        public void saveSplit(
                String rideId,
                String organizerId,
                List<String> userIds,
                long[] shareCents,
                long finalCents,
                UpdateCallback callback
        ) {
            completeUpdate(callback);
        }

        @Override
        public void markPaid(String rideId, String userId, UpdateCallback callback) {
            completeUpdate(callback);
        }

        @Override
        public void flag(String rideId, String riderId, UpdateCallback callback) {
            completeUpdate(callback);
        }

        @Override
        public void saveHandles(
                String userId,
                String venmoUsername,
                String cashtag,
                UpdateCallback callback
        ) {
            handleSaves++;
            savedVenmoUsername = venmoUsername;
            savedCashtag = cashtag;
            completeUpdate(callback);
        }

        private void completeUpdate(UpdateCallback callback) {
            if (exception != null) {
                callback.onError(exception);
            } else if (failure != null) {
                callback.onFailure(failure);
            } else {
                callback.onSuccess();
            }
        }
    }

    private static final class SharesResult implements PaymentService.SharesCallback {
        private List<Payment> shares;
        private String error;

        @Override
        public void onSuccess(List<Payment> result) {
            shares = result;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }

    private static final class SummariesResult implements PaymentService.RideSummariesCallback {
        private List<OrganizerRideSummary> summaries;
        private String error;

        @Override
        public void onSuccess(List<OrganizerRideSummary> result) {
            summaries = result;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }

    private static final class DetailsResult implements PaymentService.RideDetailsCallback {
        private RidePaymentDetails details;
        private String error;

        @Override
        public void onSuccess(RidePaymentDetails result) {
            details = result;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }

    private static final class UpdateResult implements PaymentService.UpdateCallback {
        private int successCount;
        private String error;

        @Override
        public void onSuccess() {
            successCount++;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }
}
