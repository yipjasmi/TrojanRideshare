package com.trojanrideshare.app.data.joinrequest;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.Ride;

import java.util.Collections;
import java.util.List;

public class JoinRequestDataAccess implements JoinRequestRepository {
    private static final String RIDES_COLLECTION = "rides";
    private static final String REQUESTS_COLLECTION = "joinRequests";

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
                && ride.maxRiders > participants.size();
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
