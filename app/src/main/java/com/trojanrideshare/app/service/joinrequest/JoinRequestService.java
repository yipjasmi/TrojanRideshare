package com.trojanrideshare.app.service.joinrequest;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.joinrequest.JoinRequestDataAccess;
import com.trojanrideshare.app.data.joinrequest.JoinRequestRepository;
import com.trojanrideshare.app.data.notification.NotificationDataAccess;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.Notification;
import com.trojanrideshare.app.model.OrganizerJoinRequest;
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

    public interface OrganizerRequestsCallback {
        void onSuccess(List<OrganizerJoinRequest> requests);
        void onError(String message);
    }

    public interface DecisionCallback {
        void onSuccess();
        void onUnavailable(String message);
        void onError(String message);
    }

    private static final int MAXIMUM_MESSAGE_LENGTH = 250;
    private final JoinRequestRepository repository;
    // Null in the unit-test constructor so tests do not touch Firebase; notifications are
    // then skipped (they are best-effort anyway).
    private final NotificationDataAccess notificationDataAccess;

    public JoinRequestService() {
        this(new JoinRequestDataAccess(), new NotificationDataAccess());
    }

    JoinRequestService(JoinRequestRepository repository) {
        this(repository, null);
    }

    JoinRequestService(
            JoinRequestRepository repository,
            NotificationDataAccess notificationDataAccess) {
        this.repository = repository;
        this.notificationDataAccess = notificationDataAccess;
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
                sendNotification(ride.organizerId, ride.id, Notification.TYPE_JOIN_REQUEST,
                        "You have a new join request for \"" + ride.title + "\".");
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

    public void loadOrganizerRequests(
            String organizerId,
            OrganizerRequestsCallback callback
    ) {
        if (organizerId == null || organizerId.trim().isEmpty()) {
            callback.onError("You must be signed in to manage requests.");
            return;
        }
        repository.loadOrganizerRequests(
                organizerId.trim(),
                new JoinRequestRepository.OrganizerRequestsCallback() {
                    @Override
                    public void onSuccess(List<OrganizerJoinRequest> requests) {
                        callback.onSuccess(requests);
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError("Unable to load join requests.");
                    }
                }
        );
    }

    public void accept(
            String rideId,
            String requesterId,
            String organizerId,
            DecisionCallback callback
    ) {
        decide(true, rideId, requesterId, organizerId, callback);
    }

    public void reject(
            String rideId,
            String requesterId,
            String organizerId,
            DecisionCallback callback
    ) {
        decide(false, rideId, requesterId, organizerId, callback);
    }

    private void decide(
            boolean accepting,
            String rideId,
            String requesterId,
            String organizerId,
            DecisionCallback callback
    ) {
        if (rideId == null || rideId.trim().isEmpty()
                || requesterId == null || requesterId.trim().isEmpty()) {
            callback.onError("This join request is unavailable.");
            return;
        }
        if (organizerId == null || organizerId.trim().isEmpty()) {
            callback.onError("You must be signed in to manage requests.");
            return;
        }

        JoinRequestRepository.DecisionCallback repositoryCallback =
                new JoinRequestRepository.DecisionCallback() {
                    @Override
                    public void onSuccess() {
                        sendNotification(requesterId.trim(), rideId.trim(),
                                accepting
                                        ? Notification.TYPE_REQUEST_ACCEPTED
                                        : Notification.TYPE_REQUEST_REJECTED,
                                accepting
                                        ? "Your join request was accepted."
                                        : "Your join request was declined.");
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(JoinRequestRepository.Failure failure) {
                        callback.onUnavailable(decisionFailureMessage(failure));
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError("Unable to update this request. Please try again.");
                    }
                };
        if (accepting) {
            repository.accept(
                    rideId.trim(),
                    requesterId.trim(),
                    organizerId.trim(),
                    repositoryCallback
            );
        } else {
            repository.reject(
                    rideId.trim(),
                    requesterId.trim(),
                    organizerId.trim(),
                    repositoryCallback
            );
        }
    }

    private static String decisionFailureMessage(JoinRequestRepository.Failure failure) {
        if (failure == JoinRequestRepository.Failure.RIDE_FULL) {
            return "This ride is already full.";
        }
        if (failure == JoinRequestRepository.Failure.NOT_AUTHORIZED) {
            return "Only the ride organizer can manage this request.";
        }
        if (failure == JoinRequestRepository.Failure.RIDE_UNAVAILABLE) {
            return "This ride is no longer available.";
        }
        return "This request is no longer pending.";
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
        if (ride.getAvailableSeats() == 0) {
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

    /** Best-effort in-app notification. No-op when notifications are unavailable. */
    private void sendNotification(String userId, String rideId, String type, String message) {
        if (notificationDataAccess == null || userId == null || userId.trim().isEmpty()) {
            return;
        }
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
}
