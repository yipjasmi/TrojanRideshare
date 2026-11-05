package com.trojanrideshare.app.data.joinrequest;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.OrganizerJoinRequest;
import com.trojanrideshare.app.model.Ride;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JoinRequestDataAccess implements JoinRequestRepository {
    private static final String RIDES_COLLECTION = "rides";
    private static final String REQUESTS_COLLECTION = "joinRequests";
    private static final int MAX_WHERE_IN_VALUES = 30;

    private final FirebaseFirestore firestore;

    public JoinRequestDataAccess() {
        this(FirebaseFirestore.getInstance());
    }

    JoinRequestDataAccess(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void submit(JoinRequest request, Callback callback) {
        DocumentReference rideReference = firestore
                .collection(RIDES_COLLECTION)
                .document(request.rideId);
        DocumentReference requestReference = firestore
                .collection(REQUESTS_COLLECTION)
                .document(requestDocumentId(request.rideId, request.requesterId));

        firestore.runTransaction(transaction -> {
            DocumentSnapshot rideSnapshot = transaction.get(rideReference);
            DocumentSnapshot requestSnapshot = transaction.get(requestReference);
            Ride currentRide = rideSnapshot.toObject(Ride.class);

            if (currentRide != null && currentRide.hasDeparted(Timestamp.now())) {
                throw new SubmissionException(Failure.RIDE_DEPARTED);
            }
            if (!isAvailable(currentRide, request.requesterId)) {
                throw new SubmissionException(Failure.RIDE_UNAVAILABLE);
            }
            if (hasActiveRequest(requestSnapshot)) {
                throw new SubmissionException(Failure.DUPLICATE_REQUEST);
            }

            transaction.set(requestReference, request);
            return request;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(exception -> {
                    SubmissionException submissionException = findSubmissionException(exception);
                    if (submissionException != null) {
                        callback.onFailure(submissionException.failure);
                    } else {
                        callback.onError(exception);
                    }
                });
    }

    @Override
    public void cancel(String rideId, String requesterId, CancelCallback callback) {
        DocumentReference requestReference = firestore
                .collection(REQUESTS_COLLECTION)
                .document(requestDocumentId(rideId, requesterId));

        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(requestReference);
            JoinRequest request = snapshot.toObject(JoinRequest.class);
            if (!isPendingRequest(snapshot, request, rideId, requesterId)) {
                throw new SubmissionException(Failure.REQUEST_NOT_PENDING);
            }

            transaction.update(requestReference, "status", JoinRequest.CANCELLED);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(exception -> {
                    SubmissionException submissionException = findSubmissionException(exception);
                    if (submissionException != null) {
                        callback.onFailure(submissionException.failure);
                    } else {
                        callback.onError(exception);
                    }
                });
    }

    @Override
    public void loadPendingRideIds(
            String requesterId,
            PendingRequestsCallback callback
    ) {
        firestore.collection(REQUESTS_COLLECTION)
                .whereEqualTo("requesterId", requesterId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> rideIds = new HashSet<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        JoinRequest request = document.toObject(JoinRequest.class);
                        if (request != null
                                && JoinRequest.PENDING.equals(request.status)
                                && request.rideId != null
                                && !request.rideId.trim().isEmpty()) {
                            rideIds.add(request.rideId);
                        }
                    }
                    callback.onSuccess(rideIds);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void loadOrganizerRequests(
            String organizerId,
            OrganizerRequestsCallback callback
    ) {
        firestore.collection(RIDES_COLLECTION)
                .whereEqualTo("organizerId", organizerId)
                .get()
                .addOnSuccessListener(rideSnapshot -> {
                    Map<String, Ride> ridesById = new HashMap<>();
                    for (DocumentSnapshot document : rideSnapshot.getDocuments()) {
                        Ride ride = document.toObject(Ride.class);
                        if (ride != null && ride.id != null) {
                            ridesById.put(ride.id, ride);
                        }
                    }
                    if (ridesById.isEmpty()) {
                        callback.onSuccess(Collections.emptyList());
                        return;
                    }
                    loadPendingRequestsForRides(ridesById, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    private void loadPendingRequestsForRides(
            Map<String, Ride> ridesById,
            OrganizerRequestsCallback callback
    ) {
        // Only this organizer's rides: reading every pending request in the app made each
        // Requests badge cost grow with everyone's requests. Firestore caps whereIn at 30 values.
        List<String> rideIds = new ArrayList<>(ridesById.keySet());
        List<Task<QuerySnapshot>> queries = new ArrayList<>();
        for (int start = 0; start < rideIds.size(); start += MAX_WHERE_IN_VALUES) {
            queries.add(firestore.collection(REQUESTS_COLLECTION)
                    .whereIn(
                            "rideId",
                            rideIds.subList(start, Math.min(start + MAX_WHERE_IN_VALUES, rideIds.size()))
                    )
                    .get());
        }
        Tasks.<QuerySnapshot>whenAllSuccess(queries)
                .addOnSuccessListener(requestSnapshots -> {
                    List<OrganizerJoinRequest> results = new ArrayList<>();
                    for (QuerySnapshot requestSnapshot : requestSnapshots) {
                        for (DocumentSnapshot document : requestSnapshot.getDocuments()) {
                            JoinRequest request = document.toObject(JoinRequest.class);
                            Ride ride = request == null ? null : ridesById.get(request.rideId);
                            if (request != null && ride != null
                                    && JoinRequest.PENDING.equals(request.status)) {
                                results.add(new OrganizerJoinRequest(request, ride));
                            }
                        }
                    }
                    results.sort((left, right) -> {
                        if (left.request.createdAt == null
                                && right.request.createdAt == null) return 0;
                        if (left.request.createdAt == null) return 1;
                        if (right.request.createdAt == null) return -1;
                        return left.request.createdAt.compareTo(right.request.createdAt);
                    });
                    callback.onSuccess(results);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void accept(
            String rideId,
            String requesterId,
            String organizerId,
            DecisionCallback callback
    ) {
        DocumentReference rideReference = firestore
                .collection(RIDES_COLLECTION)
                .document(rideId);
        DocumentReference requestReference = firestore
                .collection(REQUESTS_COLLECTION)
                .document(requestDocumentId(rideId, requesterId));

        firestore.runTransaction(transaction -> {
            DocumentSnapshot rideSnapshot = transaction.get(rideReference);
            DocumentSnapshot requestSnapshot = transaction.get(requestReference);
            Ride ride = rideSnapshot.toObject(Ride.class);
            JoinRequest request = requestSnapshot.toObject(JoinRequest.class);

            validateOrganizerDecision(
                    ride,
                    requestSnapshot,
                    request,
                    rideId,
                    requesterId,
                    organizerId
            );
            if (!Ride.STATUS_ON_TIME.equals(ride.status)
                    && !Ride.STATUS_DELAYED.equals(ride.status)) {
                throw new SubmissionException(Failure.RIDE_UNAVAILABLE);
            }
            // Acceptances stop at departure (the delayed time for a Delayed ride).
            if (ride.hasDeparted(Timestamp.now())) {
                throw new SubmissionException(Failure.RIDE_DEPARTED);
            }

            List<String> participants = ride.participantIds == null
                    ? new ArrayList<>()
                    : new ArrayList<>(ride.participantIds);
            if (participants.contains(requesterId)) {
                throw new SubmissionException(Failure.REQUEST_NOT_PENDING);
            }
            if (participants.size() >= ride.maxRiders) {
                throw new SubmissionException(Failure.RIDE_FULL);
            }

            participants.add(requesterId);
            transaction.update(rideReference, "participantIds", participants);
            transaction.update(requestReference, "status", JoinRequest.ACCEPTED);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(exception -> handleDecisionFailure(exception, callback));
    }

    @Override
    public void reject(
            String rideId,
            String requesterId,
            String organizerId,
            DecisionCallback callback
    ) {
        DocumentReference rideReference = firestore
                .collection(RIDES_COLLECTION)
                .document(rideId);
        DocumentReference requestReference = firestore
                .collection(REQUESTS_COLLECTION)
                .document(requestDocumentId(rideId, requesterId));

        firestore.runTransaction(transaction -> {
            Ride ride = transaction.get(rideReference).toObject(Ride.class);
            DocumentSnapshot requestSnapshot = transaction.get(requestReference);
            JoinRequest request = requestSnapshot.toObject(JoinRequest.class);
            validateOrganizerDecision(
                    ride,
                    requestSnapshot,
                    request,
                    rideId,
                    requesterId,
                    organizerId
            );
            transaction.update(requestReference, "status", JoinRequest.REJECTED);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(exception -> handleDecisionFailure(exception, callback));
    }

    private static void validateOrganizerDecision(
            Ride ride,
            DocumentSnapshot requestSnapshot,
            JoinRequest request,
            String rideId,
            String requesterId,
            String organizerId
    ) {
        if (ride == null || !organizerId.equals(ride.organizerId)) {
            throw new SubmissionException(Failure.NOT_AUTHORIZED);
        }
        if (!isPendingRequest(requestSnapshot, request, rideId, requesterId)) {
            throw new SubmissionException(Failure.REQUEST_NOT_PENDING);
        }
    }

    private static void handleDecisionFailure(
            Exception exception,
            DecisionCallback callback
    ) {
        SubmissionException submissionException = findSubmissionException(exception);
        if (submissionException != null) {
            callback.onFailure(submissionException.failure);
        } else {
            callback.onError(exception);
        }
    }

    private static boolean isAvailable(Ride ride, String requesterId) {
        if (ride == null || requesterId == null) {
            return false;
        }
        if (!Ride.STATUS_ON_TIME.equals(ride.status)
                && !Ride.STATUS_DELAYED.equals(ride.status)) {
            return false;
        }
        if (requesterId.equals(ride.organizerId)) {
            return false;
        }
        List<String> participants = ride.participantIds == null
                ? Collections.emptyList()
                : ride.participantIds;
        return !participants.contains(requesterId)
                && ride.getAvailableSeats() > 0;
    }

    private static boolean hasActiveRequest(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            return false;
        }
        JoinRequest existing = snapshot.toObject(JoinRequest.class);
        return existing != null
                && (JoinRequest.PENDING.equals(existing.status)
                || JoinRequest.ACCEPTED.equals(existing.status));
    }

    private static boolean isPendingRequest(
            DocumentSnapshot snapshot,
            JoinRequest request,
            String rideId,
            String requesterId
    ) {
        return snapshot.exists()
                && request != null
                && rideId.equals(request.rideId)
                && requesterId.equals(request.requesterId)
                && JoinRequest.PENDING.equals(request.status);
    }

    private static String requestDocumentId(String rideId, String requesterId) {
        return rideId + "_" + requesterId;
    }

    private static SubmissionException findSubmissionException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SubmissionException) {
                return (SubmissionException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static final class SubmissionException extends RuntimeException {
        private final Failure failure;

        private SubmissionException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }
    }
}
