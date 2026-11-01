package com.trojanrideshare.app.service.joinrequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.trojanrideshare.app.data.joinrequest.JoinRequestRepository;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class JoinRequestServiceTest {
    private static final String REQUESTER_ID = "requester";

    @Test
    public void submit_createsPendingRequestWithTrimmedMessage() {
        FakeRepository repository = new FakeRepository();
        JoinRequestService service = new JoinRequestService(repository);
        Result result = new Result();

        service.submit(availableRide(), REQUESTER_ID, "  Can I join?  ", result);

        assertNotNull(repository.submittedRequest);
        assertEquals("ride-1", repository.submittedRequest.rideId);
        assertEquals(REQUESTER_ID, repository.submittedRequest.requesterId);
        assertEquals("Can I join?", repository.submittedRequest.message);
        assertEquals(JoinRequest.PENDING, repository.submittedRequest.status);
        assertNotNull(repository.submittedRequest.createdAt);
        assertNotNull(result.success);
        assertNull(result.error);
    }

    @Test
    public void submit_rejectsOwnJoinedAndFullRidesBeforeWriting() {
        assertValidationError(ride("organizer", 4, Collections.emptyList()), "organizer");
        assertValidationError(
                ride("organizer", 4, Collections.singletonList(REQUESTER_ID)),
                REQUESTER_ID
        );
        assertValidationError(
                ride("organizer", 2, Arrays.asList("one", "two")),
                REQUESTER_ID
        );
    }

    @Test
    public void submit_rejectsUnavailableRideAndLongMessage() {
        Ride cancelled = availableRide();
        cancelled.status = Ride.STATUS_CANCELLED;
        assertValidationError(cancelled, REQUESTER_ID);

        StringBuilder longMessage = new StringBuilder();
        for (int index = 0; index < 251; index++) {
            longMessage.append('a');
        }
        FakeRepository repository = new FakeRepository();
        Result result = new Result();
        new JoinRequestService(repository).submit(
                availableRide(),
                REQUESTER_ID,
                longMessage.toString(),
                result
        );

        assertNull(repository.submittedRequest);
        assertEquals("Your message must be 250 characters or fewer.", result.error);
    }

    @Test
    public void submit_reportsExistingRequestAsPendingState() {
        FakeRepository repository = new FakeRepository();
        repository.failure = JoinRequestRepository.Failure.DUPLICATE_REQUEST;
        Result result = new Result();

        new JoinRequestService(repository).submit(
                availableRide(),
                REQUESTER_ID,
                "",
                result
        );

        assertEquals(1, result.alreadyRequestedCount);
        assertNull(result.error);
    }

    @Test
    public void submit_mapsUnavailableAndUnexpectedFailures() {
        FakeRepository unavailableRepository = new FakeRepository();
        unavailableRepository.failure = JoinRequestRepository.Failure.RIDE_UNAVAILABLE;
        Result unavailableResult = new Result();
        new JoinRequestService(unavailableRepository).submit(
                availableRide(), REQUESTER_ID, "", unavailableResult
        );

        FakeRepository failingRepository = new FakeRepository();
        failingRepository.exception = new IllegalStateException("network");
        Result failingResult = new Result();
        new JoinRequestService(failingRepository).submit(
                availableRide(), REQUESTER_ID, "", failingResult
        );

        assertEquals("This ride is no longer available.", unavailableResult.error);
        assertEquals("Unable to send your request. Please try again.", failingResult.error);
    }

    @Test
    public void cancel_sendsTrimmedIdentifiersToRepository() {
        FakeRepository repository = new FakeRepository();
        CancelResult result = new CancelResult();

        new JoinRequestService(repository).cancel(
                "  ride-1  ",
                "  requester  ",
                result
        );

        assertEquals("ride-1", repository.cancelledRideId);
        assertEquals(REQUESTER_ID, repository.cancelledRequesterId);
        assertEquals(1, result.successCount);
        assertNull(result.error);
    }

    @Test
    public void cancel_rejectsMissingRideOrUserBeforeWriting() {
        FakeRepository repository = new FakeRepository();
        CancelResult missingRide = new CancelResult();
        CancelResult missingUser = new CancelResult();
        JoinRequestService service = new JoinRequestService(repository);

        service.cancel("", REQUESTER_ID, missingRide);
        service.cancel("ride-1", " ", missingUser);

        assertNull(repository.cancelledRideId);
        assertEquals("This ride is unavailable.", missingRide.error);
        assertEquals("You must be signed in to cancel a request.", missingUser.error);
    }

    @Test
    public void cancel_mapsNotPendingAndUnexpectedFailures() {
        FakeRepository notPendingRepository = new FakeRepository();
        notPendingRepository.cancelFailure =
                JoinRequestRepository.Failure.REQUEST_NOT_PENDING;
        CancelResult notPendingResult = new CancelResult();
        new JoinRequestService(notPendingRepository).cancel(
                "ride-1", REQUESTER_ID, notPendingResult
        );

        FakeRepository failingRepository = new FakeRepository();
        failingRepository.cancelException = new IllegalStateException("network");
        CancelResult failingResult = new CancelResult();
        new JoinRequestService(failingRepository).cancel(
                "ride-1", REQUESTER_ID, failingResult
        );

        assertEquals(1, notPendingResult.notPendingCount);
        assertEquals("Unable to cancel your request. Please try again.", failingResult.error);
    }

    @Test
    public void loadPendingRideIds_returnsRepositoryResultsAndMapsErrors() {
        FakeRepository repository = new FakeRepository();
        repository.pendingRideIds = new HashSet<>(Arrays.asList("ride-1", "ride-2"));
        PendingResult result = new PendingResult();
        new JoinRequestService(repository).loadPendingRideIds(REQUESTER_ID, result);

        FakeRepository failingRepository = new FakeRepository();
        failingRepository.pendingException = new IllegalStateException("network");
        PendingResult failingResult = new PendingResult();
        new JoinRequestService(failingRepository).loadPendingRideIds(
                REQUESTER_ID,
                failingResult
        );

        assertEquals(repository.pendingRideIds, result.rideIds);
        assertEquals("Unable to load your pending requests.", failingResult.error);
    }

    private static void assertValidationError(Ride ride, String requesterId) {
        FakeRepository repository = new FakeRepository();
        Result result = new Result();

        new JoinRequestService(repository).submit(ride, requesterId, "", result);

        assertNull(repository.submittedRequest);
        assertNotNull(result.error);
    }

    private static Ride availableRide() {
        return ride("organizer", 4, Collections.singletonList("organizer"));
    }

    private static Ride ride(
            String organizerId,
            int maxRiders,
            java.util.List<String> participants
    ) {
        Ride ride = new Ride();
        ride.id = "ride-1";
        ride.organizerId = organizerId;
        ride.status = Ride.STATUS_ON_TIME;
        ride.maxRiders = maxRiders;
        ride.participantIds = new ArrayList<>(participants);
        return ride;
    }

    private static final class FakeRepository implements JoinRequestRepository {
        private JoinRequest submittedRequest;
        private Failure failure;
        private Exception exception;
        private String cancelledRideId;
        private String cancelledRequesterId;
        private Failure cancelFailure;
        private Exception cancelException;
        private Set<String> pendingRideIds = Collections.emptySet();
        private Exception pendingException;

        @Override
        public void submit(JoinRequest request, Callback callback) {
            submittedRequest = request;
            if (failure != null) {
                callback.onFailure(failure);
            } else if (exception != null) {
                callback.onError(exception);
            } else {
                callback.onSuccess(request);
            }
        }

        @Override
        public void cancel(
                String rideId,
                String requesterId,
                CancelCallback callback
        ) {
            cancelledRideId = rideId;
            cancelledRequesterId = requesterId;
            if (cancelFailure != null) {
                callback.onFailure(cancelFailure);
            } else if (cancelException != null) {
                callback.onError(cancelException);
            } else {
                callback.onSuccess();
            }
        }

        @Override
        public void loadPendingRideIds(
                String requesterId,
                PendingRequestsCallback callback
        ) {
            if (pendingException != null) {
                callback.onError(pendingException);
            } else {
                callback.onSuccess(pendingRideIds);
            }
        }
    }

    private static final class Result implements JoinRequestService.Callback {
        private JoinRequest success;
        private int alreadyRequestedCount;
        private String error;

        @Override
        public void onSuccess(JoinRequest request) {
            success = request;
        }

        @Override
        public void onAlreadyRequested() {
            alreadyRequestedCount++;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }

    private static final class CancelResult implements JoinRequestService.CancelCallback {
        private int successCount;
        private int notPendingCount;
        private String error;

        @Override
        public void onSuccess() {
            successCount++;
        }

        @Override
        public void onNotPending() {
            notPendingCount++;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }

    private static final class PendingResult
            implements JoinRequestService.PendingRequestsCallback {
        private Set<String> rideIds;
        private String error;

        @Override
        public void onSuccess(Set<String> result) {
            rideIds = result;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }
}
