package com.trojanrideshare.app.data.payment;

import android.app.Activity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.util.Money;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentDataAccess implements PaymentRepository {
    private static final String RIDES_COLLECTION = "rides";
    private static final String PAYMENTS_COLLECTION = "payments";
    private static final String USERS_COLLECTION = "users";

    private final FirebaseFirestore firestore;

    public PaymentDataAccess() {
        this(FirebaseFirestore.getInstance());
    }

    PaymentDataAccess(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public static String paymentDocumentId(String rideId, String userId) {
        return rideId + "_" + userId;
    }

    public static Failure splitFailure(Ride ride, String organizerId, List<Payment> existing) {
        if (ride == null) {
            return Failure.RIDE_NOT_FOUND;
        }
        if (!organizerId.equals(ride.organizerId)) {
            return Failure.NOT_AUTHORIZED;
        }
        if (!Ride.STATUS_COMPLETED.equals(ride.status)) {
            return Failure.RIDE_NOT_COMPLETED;
        }
        if (Payment.hasRiderMarkedPaid(existing)) {
            return Failure.SPLIT_LOCKED;
        }
        return null;
    }

    @Override
    public void loadRide(String rideId, RideCallback callback) {
        firestore.collection(RIDES_COLLECTION)
                .document(rideId)
                .get()
                .continueWith(task -> task.getResult().toObject(Ride.class))
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void loadCompletedRides(String organizerId, RidesCallback callback) {
        firestore.collection(RIDES_COLLECTION)
                .whereEqualTo("organizerId", organizerId)
                .whereEqualTo("status", Ride.STATUS_COMPLETED)
                .get()
                .continueWith(task -> toObjects(task.getResult(), Ride.class))
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void loadSharesFor(String userId, SharesCallback callback) {
        loadShares(
                firestore.collection(PAYMENTS_COLLECTION).whereEqualTo("userId", userId),
                callback
        );
    }

    @Override
    public void loadSharesOrganizedBy(String organizerId, SharesCallback callback) {
        loadShares(
                firestore.collection(PAYMENTS_COLLECTION).whereEqualTo("organizerId", organizerId),
                callback
        );
    }

    @Override
    public void loadRideShares(String rideId, String organizerId, SharesCallback callback) {
        loadShares(rideSharesQuery(rideId, organizerId), callback);
    }

    private static void loadShares(Query query, SharesCallback callback) {
        query.get()
                .continueWith(task -> toObjects(task.getResult(), Payment.class))
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void loadNames(Collection<String> userIds, NamesCallback callback) {
        List<Task<DocumentSnapshot>> lookups = new ArrayList<>();
        for (String userId : userIds) {
            lookups.add(firestore.collection(USERS_COLLECTION).document(userId).get());
        }

        Tasks.whenAllComplete(lookups).addOnSuccessListener(unused -> {
            Map<String, String> names = new HashMap<>();
            for (Task<DocumentSnapshot> lookup : lookups) {
                Object name = lookup.isSuccessful() ? lookup.getResult().get("fullName") : null;
                if (name instanceof String) {
                    names.put(lookup.getResult().getId(), (String) name);
                }
            }
            callback.onSuccess(names);
        });
    }

    @Override
    public void watchRideShares(
            Activity owner,
            String rideId,
            String organizerId,
            SharesCallback callback
    ) {
        rideSharesQuery(rideId, organizerId).addSnapshotListener(
                owner,
                MetadataChanges.INCLUDE,
                (snapshot, exception) -> {
                    if (exception != null) {
                        callback.onError(exception);
                    } else if (isUnknown(snapshot)) {
                        callback.onError(offline());
                    } else {
                        callback.onSuccess(snapshot.toObjects(Payment.class));
                    }
                }
        );
    }

    @Override
    public void watchShare(
            Activity owner,
            String rideId,
            String userId,
            SharesCallback callback
    ) {
        shareDocument(rideId, userId).addSnapshotListener(
                owner,
                MetadataChanges.INCLUDE,
                (snapshot, exception) -> {
                    if (exception != null) {
                        callback.onError(exception);
                    } else if (snapshot.exists()) {
                        callback.onSuccess(
                                Collections.singletonList(snapshot.toObject(Payment.class))
                        );
                    } else if (snapshot.getMetadata().isFromCache()) {
                        callback.onError(offline());
                    } else {
                        callback.onSuccess(Collections.emptyList());
                    }
                }
        );
    }

    @Override
    public void saveSplit(
            String rideId,
            String organizerId,
            List<String> userIds,
            long[] shares,
            long finalCents,
            UpdateCallback callback
    ) {
        DocumentReference rideReference = firestore
                .collection(RIDES_COLLECTION)
                .document(rideId);

        firestore.runTransaction(transaction -> {
            Ride ride = transaction.get(rideReference).toObject(Ride.class);
            List<Payment> existing = new ArrayList<>();
            for (String userId : userIds) {
                Payment share = transaction.get(shareDocument(rideId, userId))
                        .toObject(Payment.class);
                if (share != null) {
                    existing.add(share);
                }
            }

            Failure failure = splitFailure(ride, organizerId, existing);
            if (failure != null) {
                throw new SubmissionException(failure);
            }
            if (!ride.getAllParticipantIds().equals(userIds)) {
                throw new SubmissionException(Failure.RIDERS_CHANGED);
            }

            transaction.update(rideReference, "finalCost", Money.toDollars(finalCents));
            for (int index = 0; index < shares.length; index++) {
                String userId = userIds.get(index);
                transaction.set(
                        shareDocument(rideId, userId),
                        shareFields(ride, rideId, userId, shares[index])
                );
            }
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(exception -> handleUpdateFailure(exception, callback));
    }

    @Override
    public void markPaid(String rideId, String userId, UpdateCallback callback) {
        DocumentReference shareReference = shareDocument(rideId, userId);
        Map<String, Object> changes = new HashMap<>();
        changes.put("paid", true);
        changes.put("paidAt", FieldValue.serverTimestamp());
        changes.put("flagged", false);
        changes.put("flaggedAt", null);

        firestore.runTransaction(transaction -> {
            Payment share = transaction.get(shareReference).toObject(Payment.class);
            if (share == null) {
                throw new SubmissionException(Failure.SHARE_NOT_FOUND);
            }

            if (!share.paid) {
                transaction.update(shareReference, changes);
            }
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(exception -> handleUpdateFailure(exception, callback));
    }

    @Override
    public void flag(String rideId, String riderId, UpdateCallback callback) {
        DocumentReference shareReference = shareDocument(rideId, riderId);
        long nowMillis = System.currentTimeMillis();
        Map<String, Object> changes = new HashMap<>();
        changes.put("flagged", true);
        changes.put("flaggedAt", FieldValue.serverTimestamp());

        firestore.runTransaction(transaction -> {
            Payment share = transaction.get(shareReference).toObject(Payment.class);
            if (share == null) {
                throw new SubmissionException(Failure.SHARE_NOT_FOUND);
            }
            if (share.paid) {
                throw new SubmissionException(Failure.ALREADY_PAID);
            }
            if (!share.isOverdue(nowMillis)) {
                throw new SubmissionException(Failure.NOT_OVERDUE);
            }

            if (!share.flagged) {
                transaction.update(shareReference, changes);
            }
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(exception -> handleUpdateFailure(exception, callback));
    }

    private static Map<String, Object> shareFields(
            Ride ride,
            String rideId,
            String userId,
            long cents
    ) {
        boolean settled = userId.equals(ride.organizerId) || cents == 0;
        Map<String, Object> fields = new HashMap<>();
        fields.put("rideId", rideId);
        fields.put("userId", userId);
        fields.put("organizerId", ride.organizerId);
        fields.put("rideTitle", ride.title);
        fields.put("amount", Money.toDollars(cents));
        fields.put("paid", settled);
        fields.put("paidAt", null);
        fields.put("assignedAt", FieldValue.serverTimestamp());
        fields.put("flagged", false);
        fields.put("flaggedAt", null);
        return fields;
    }

    private DocumentReference shareDocument(String rideId, String userId) {
        return firestore.collection(PAYMENTS_COLLECTION)
                .document(paymentDocumentId(rideId, userId));
    }

    private Query rideSharesQuery(String rideId, String organizerId) {
        return firestore.collection(PAYMENTS_COLLECTION)
                .whereEqualTo("rideId", rideId)
                .whereEqualTo("organizerId", organizerId);
    }

    private static void handleUpdateFailure(Exception exception, UpdateCallback callback) {
        SubmissionException submissionException = findSubmissionException(exception);
        if (submissionException != null) {
            callback.onFailure(submissionException.failure);
        } else {
            callback.onError(exception);
        }
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

    private static <T> List<T> toObjects(QuerySnapshot snapshot, Class<T> type)
            throws FirebaseFirestoreException {
        if (isUnknown(snapshot)) {
            throw offline();
        }
        return snapshot.toObjects(type);
    }

    // Offline with nothing cached, Firestore answers with an empty result instead of an error.
    // Passing that on would tell someone they owe nothing when we just can't check.
    private static boolean isUnknown(QuerySnapshot snapshot) {
        return snapshot.isEmpty() && snapshot.getMetadata().isFromCache();
    }

    private static FirebaseFirestoreException offline() {
        return new FirebaseFirestoreException(
                "Offline with nothing cached",
                FirebaseFirestoreException.Code.UNAVAILABLE
        );
    }

    private static final class SubmissionException extends RuntimeException {
        private final Failure failure;

        private SubmissionException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }
    }
}
