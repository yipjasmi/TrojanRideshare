package com.trojanrideshare.app.service.joinrequest;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.joinrequest.JoinRequestDataAccess;
import com.trojanrideshare.app.data.joinrequest.JoinRequestRepository;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.Ride;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JoinRequestService {
    public interface Callback {
        void onSuccess(JoinRequest request);
        void onAlreadyRequested();
        void onError(String message);
    }

    public interface CancelCallback {
        void onSuccess();
        void onNotPending();
        void onError(String message);
    }

    public interface PendingRequestsCallback {
        void onSuccess(Set<String> rideIds);
        void onError(String message);
    }

    private static final int MAXIMUM_MESSAGE_LENGTH = 250;
    private final JoinRequestRepository repository;

    public JoinRequestService() {
        this(new JoinRequestDataAccess());
    }

    JoinRequestService(JoinRequestRepository repository) {
        this.repository = repository;
    }

    public void submit(
            Ride ride,
            String requesterId,
            String message,
            Callback callback
    ) {
        String validationError = validationError(ride, requesterId, message);
        if (validationError != null) {
            callback.onError(validationError);
            return;
        }

        JoinRequest request = new JoinRequest();
        request.rideId = ride.id;
        request.requesterId = requesterId.trim();
        request.message = clean(message);
        request.status = JoinRequest.PENDING;
        request.createdAt = Timestamp.now();

        repository.submit(request, new JoinRequestRepository.Callback() {
            @Override
            public void onSuccess(JoinRequest submittedRequest) {
                callback.onSuccess(submittedRequest);
            }

            @Override
            public void onFailure(JoinRequestRepository.Failure failure) {
                if (failure == JoinRequestRepository.Failure.DUPLICATE_REQUEST) {
                    callback.onAlreadyRequested();
                } else {
                    callback.onError("This ride is no longer available.");
                }
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to send your request. Please try again.");
            }
        });
    }

    public void cancel(
            String rideId,
            String requesterId,
            CancelCallback callback
    ) {
        if (rideId == null || rideId.trim().isEmpty()) {
            callback.onError("This ride is unavailable.");
            return;
        }
        if (requesterId == null || requesterId.trim().isEmpty()) {
            callback.onError("You must be signed in to cancel a request.");
            return;
        }

        repository.cancel(
                rideId.trim(),
                requesterId.trim(),
                new JoinRequestRepository.CancelCallback() {
                    @Override
                    public void onSuccess() {
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(JoinRequestRepository.Failure failure) {
                        callback.onNotPending();
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError("Unable to cancel your request. Please try again.");
                    }
                }
        );
    }

    public void loadPendingRideIds(
            String requesterId,
            PendingRequestsCallback callback
    ) {
        if (requesterId == null || requesterId.trim().isEmpty()) {
            callback.onError("You must be signed in to load your requests.");
            return;
        }
        repository.loadPendingRideIds(
                requesterId.trim(),
                new JoinRequestRepository.PendingRequestsCallback() {
                    @Override
                    public void onSuccess(Set<String> rideIds) {
                        callback.onSuccess(rideIds);
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError("Unable to load your pending requests.");
                    }
                }
        );
    }

    static String validationError(Ride ride, String requesterId, String message) {
        if (requesterId == null || requesterId.trim().isEmpty()) {
            return "You must be signed in to request a ride.";
        }
        if (ride == null || ride.id == null || ride.id.trim().isEmpty()) {
            return "This ride is unavailable.";
        }
        if (requesterId.equals(ride.organizerId)) {
            return "You cannot request to join your own ride.";
        }
        if (!Ride.STATUS_ON_TIME.equals(ride.status)
                && !Ride.STATUS_DELAYED.equals(ride.status)) {
            return "This ride is no longer available.";
        }
        List<String> participants = ride.participantIds == null
                ? Collections.emptyList()
                : ride.participantIds;
        if (participants.contains(requesterId)) {
            return "You have already joined this ride.";
        }
        if (ride.maxRiders <= participants.size()) {
            return "This ride has no available seats.";
        }
        if (clean(message).length() > MAXIMUM_MESSAGE_LENGTH) {
            return "Your message must be 250 characters or fewer.";
        }
        return null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
