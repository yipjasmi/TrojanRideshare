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

public class PaymentRepository {
    private static final String RIDES = "rides";
    private static final String PAYMENTS = "payments";
    private static final String USERS = "users";

    // Runs inside the transaction on fresh data. Throws to cancel the write.
    public interface SplitGuard {
        void check(Ride ride, List<Payment> existing);
    }

    // Runs inside the transaction on fresh data. Returns false when there is nothing to write.
    public interface ShareGuard {
        boolean shouldWrite(Payment share);
    }

    public interface SharesListener {
        void onShares(List<Payment> shares, Exception error);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // One document per person per ride, so saving a split twice can't create duplicates.
    public static String paymentId(String rideId, String userId) {
        return rideId + "_" + userId;
    }

    public Task<Ride> getRide(String rideId) {
        return db.collection(RIDES).document(rideId).get()
                .onSuccessTask(snapshot -> Tasks.forResult(snapshot.toObject(Ride.class)));
    }

    public Task<List<Ride>> getCompletedRidesOrganizedBy(String organizerId) {
        return db.collection(RIDES)
                .whereEqualTo("organizerId", organizerId)
                .whereEqualTo("status", Ride.STATUS_COMPLETED)
                .get()
                .onSuccessTask(snapshot -> isUnknown(snapshot)
                        ? Tasks.forException(offline())
                        : Tasks.forResult(snapshot.toObjects(Ride.class)));
    }

    public Task<List<Payment>> getSharesFor(String userId) {
        return getShares(db.collection(PAYMENTS).whereEqualTo("userId", userId));
    }

    public Task<List<Payment>> getSharesOrganizedBy(String organizerId) {
        return getShares(db.collection(PAYMENTS).whereEqualTo("organizerId", organizerId));
    }

    public Task<List<Payment>> getRideShares(String rideId, String organizerId) {
        return getShares(rideSharesQuery(rideId, organizerId));
    }

    public void listenToRideShares(Activity owner, String rideId, String organizerId,
                                   SharesListener listener) {
        rideSharesQuery(rideId, organizerId).addSnapshotListener(owner, MetadataChanges.INCLUDE,
                (snapshot, error) -> {
                    if (error != null) {
                        listener.onShares(Collections.emptyList(), error);
                    } else if (isUnknown(snapshot)) {
                        listener.onShares(Collections.emptyList(), offline());
                    } else {
                        listener.onShares(snapshot.toObjects(Payment.class), null);
                    }
                });
    }

    public void listenToShare(Activity owner, String rideId, String userId,
                              SharesListener listener) {
        shareRef(paymentId(rideId, userId)).addSnapshotListener(owner, MetadataChanges.INCLUDE,
                (snapshot, error) -> {
                    if (error != null) {
                        listener.onShares(Collections.emptyList(), error);
                    } else if (snapshot.exists()) {
                        listener.onShares(
                                Collections.singletonList(snapshot.toObject(Payment.class)), null);
                    } else if (snapshot.getMetadata().isFromCache()) {
                        listener.onShares(Collections.emptyList(), offline());
                    } else {
                        listener.onShares(Collections.emptyList(), null);
                    }
                });
    }

    public Task<Map<String, String>> getNames(Collection<String> userIds) {
        List<Task<DocumentSnapshot>> lookups = new ArrayList<>();
        for (String userId : userIds) {
            lookups.add(db.collection(USERS).document(userId).get());
        }
        // A missing profile shouldn't stop anyone seeing what they owe, so failures are skipped.
        return Tasks.whenAllComplete(lookups).continueWith(done -> {
            Map<String, String> names = new HashMap<>();
            for (Task<DocumentSnapshot> lookup : lookups) {
                if (lookup.isSuccessful() && lookup.getResult().getString("fullName") != null) {
                    names.put(lookup.getResult().getId(), lookup.getResult().getString("fullName"));
                }
            }
            return names;
        });
    }

    // The ride total and every share change together or not at all.
    public Task<Void> saveSplit(String rideId, List<String> userIds, long[] shares, long finalCents,
                                SplitGuard guard) {
        DocumentReference rideRef = db.collection(RIDES).document(rideId);
        return db.runTransaction(transaction -> {
            Ride ride = transaction.get(rideRef).toObject(Ride.class);
            List<Payment> existing = new ArrayList<>();
            for (String userId : userIds) {
                Payment share = transaction.get(shareRef(paymentId(rideId, userId)))
                        .toObject(Payment.class);
                if (share != null) {
                    existing.add(share);
                }
            }
            guard.check(ride, existing);

            transaction.update(rideRef, "finalCost", Money.toDollars(finalCents));
            for (int i = 0; i < shares.length; i++) {
                transaction.set(shareRef(paymentId(rideId, userIds.get(i))),
                        shareFields(ride, rideId, userIds.get(i), shares[i]));
            }
            return null;
        });
    }

    public Task<Void> markPaid(String paymentId, ShareGuard guard) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("paid", true);
        changes.put("paidAt", FieldValue.serverTimestamp());
        changes.put("flagged", false);
        changes.put("flaggedAt", null);
        return updateShare(paymentId, guard, changes);
    }

    public Task<Void> flag(String paymentId, ShareGuard guard) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("flagged", true);
        changes.put("flaggedAt", FieldValue.serverTimestamp());
        return updateShare(paymentId, guard, changes);
    }

    private Task<Void> updateShare(String paymentId, ShareGuard guard, Map<String, Object> changes) {
        DocumentReference ref = shareRef(paymentId);
        return db.runTransaction(transaction -> {
            Payment share = transaction.get(ref).toObject(Payment.class);
            if (guard.shouldWrite(share)) {
                transaction.update(ref, changes);
            }
            return null;
        });
    }

    private Map<String, Object> shareFields(Ride ride, String rideId, String userId, long cents) {
        // The organizer fronted the fare and a $0 share owes nothing, so neither waits on anyone.
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

    private DocumentReference shareRef(String paymentId) {
        return db.collection(PAYMENTS).document(paymentId);
    }

    private Query rideSharesQuery(String rideId, String organizerId) {
        return db.collection(PAYMENTS)
                .whereEqualTo("rideId", rideId)
                .whereEqualTo("organizerId", organizerId);
    }

    private Task<List<Payment>> getShares(Query query) {
        return query.get().onSuccessTask(snapshot -> isUnknown(snapshot)
                ? Tasks.forException(offline())
                : Tasks.forResult(snapshot.toObjects(Payment.class)));
    }

    // Offline with nothing cached, Firestore answers with an empty result instead of an error.
    // Passing that on would tell someone they owe nothing when we just can't check.
    private static boolean isUnknown(QuerySnapshot snapshot) {
        return snapshot.isEmpty() && snapshot.getMetadata().isFromCache();
    }

    private static FirebaseFirestoreException offline() {
        return new FirebaseFirestoreException(
                "Offline with nothing cached", FirebaseFirestoreException.Code.UNAVAILABLE);
    }
}
