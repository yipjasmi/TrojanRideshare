package com.trojanrideshare.app.data.history;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.trojanrideshare.app.model.*;
import java.util.*;

/** Read-only queries scoped to the authenticated account. No new indexes required. */
public class HistoryDataAccess {
    public interface Callback {
        void onSuccess(String userId, List<Ride> rides, Map<String, Payment> payments);
        void onError(String message);
    }
    public void load(Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { callback.onError("Sign in to view your ride history."); return; }
        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Tasks.whenAllSuccess(Arrays.asList(
            db.collection("rides").whereEqualTo("organizerId", uid).get(),
            db.collection("rides").whereArrayContains("participantIds", uid).get(),
            db.collection("payments").whereEqualTo("userId", uid).get()
        )).addOnSuccessListener(results -> {
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (current == null || !uid.equals(current.getUid())) {
                callback.onError("Your account changed. Reopen history."); return;
            }
            Map<String, Ride> rides = new LinkedHashMap<>();
            for (int i = 0; i < 2; i++) {
                for (com.google.firebase.firestore.DocumentSnapshot doc : ((QuerySnapshot) results.get(i)).getDocuments()) {
                    Ride ride = doc.toObject(Ride.class);
                    if (ride != null) rides.put(doc.getId(), ride);
                }
            }
            Map<String, Payment> payments = new HashMap<>();
            for (com.google.firebase.firestore.DocumentSnapshot doc : ((QuerySnapshot) results.get(2)).getDocuments()) {
                Payment payment = doc.toObject(Payment.class);
                if (payment != null && uid.equals(payment.userId)) payments.put(payment.rideId, payment);
            }
            callback.onSuccess(uid, new ArrayList<>(rides.values()), payments);
        }).addOnFailureListener(error -> callback.onError(
            "Could not load history. Check your connection and verified USC account, then refresh."));
    }
}
