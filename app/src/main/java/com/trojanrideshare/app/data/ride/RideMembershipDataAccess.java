package com.trojanrideshare.app.data.ride;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.OrganizerJoinRequest;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.ride.LeaveRideService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Firestore access for leaving rides and for a requester's own join requests. */
public class RideMembershipDataAccess {
    public interface LeaveCallback {
        /** {@code before} is the ride as it was just before leaving. */
        void onSuccess(Ride before, LeaveRideService.Plan plan);
        void onDenied(String reason);
        void onError(Exception exception);
    }

    public interface RequestsCallback {
        void onSuccess(List<OrganizerJoinRequest> requests);
        void onError(Exception exception);
    }

    private static final String RIDES = "rides";
    private static final String REQUESTS = "joinRequests";

    private final FirebaseFirestore firestore;

    public RideMembershipDataAccess() {
        this(FirebaseFirestore.getInstance());
    }

    RideMembershipDataAccess(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    /** Re-checks the rules on the latest ride data and applies the leave atomically. */
    public void leave(String rideId, String userId, LeaveCallback callback) {
        DocumentReference rideRef = firestore.collection(RIDES).document(rideId);
        DocumentReference requestRef = firestore.collection(REQUESTS).document(rideId + "_" + userId);
        final Ride[] before = new Ride[1];

        firestore.runTransaction(transaction -> {
            DocumentSnapshot rideSnap = transaction.get(rideRef);
            DocumentSnapshot requestSnap = transaction.get(requestRef);
            Ride ride = rideSnap.toObject(Ride.class);
            String error = LeaveRideService.leaveError(ride, userId, Timestamp.now());
            if (error != null) {
                throw new DeniedException(error);
            }
            before[0] = ride;
            LeaveRideService.Plan plan = LeaveRideService.plan(ride, userId);
            transaction.update(rideRef, plan.rideUpdates);
            if (requestSnap.exists()) {
                transaction.update(requestRef, "status", JoinRequest.LEFT);
            }
            return plan;
        }).addOnSuccessListener(plan -> callback.onSuccess(before[0], plan))
                .addOnFailureListener(exception -> {
                    DeniedException denied = findDenied(exception);
                    if (denied != null) {
                        callback.onDenied(denied.getMessage());
                    } else {
                        callback.onError(exception);
                    }
                });
    }

    /** Closes every Pending request on a cancelled ride; reports whose requests were closed. */
    public void closePendingRequests(String rideId, Consumer<List<String>> onClosed) {
        firestore.collection(REQUESTS)
                .whereEqualTo("rideId", rideId)
                .whereEqualTo("status", JoinRequest.PENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        onClosed.accept(new ArrayList<>());
                        return;
                    }
                    WriteBatch batch = firestore.batch();
                    List<String> requesterIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        batch.update(doc.getReference(), "status", JoinRequest.RIDE_CANCELLED);
                        String requesterId = doc.getString("requesterId");
                        if (requesterId != null) requesterIds.add(requesterId);
                    }
                    batch.commit().addOnSuccessListener(unused -> onClosed.accept(requesterIds));
                });
    }

    /** All of a user's join requests (any status) with their rides, newest first. */
    public void loadMyRequests(String requesterId, RequestsCallback callback) {
        firestore.collection(REQUESTS)
                .whereEqualTo("requesterId", requesterId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<JoinRequest> requests = new ArrayList<>();
                    List<Task<DocumentSnapshot>> rideLookups = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        JoinRequest request = doc.toObject(JoinRequest.class);
                        if (request == null || request.rideId == null || request.rideId.isEmpty()) {
                            continue;
                        }
                        requests.add(request);
                        rideLookups.add(firestore.collection(RIDES).document(request.rideId).get());
                    }
                    if (requests.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }
                    Tasks.whenAllComplete(rideLookups).addOnSuccessListener(unused -> {
                        List<OrganizerJoinRequest> results = new ArrayList<>();
                        for (int i = 0; i < requests.size(); i++) {
                            Task<DocumentSnapshot> lookup = rideLookups.get(i);
                            Ride ride = lookup.isSuccessful() ? lookup.getResult().toObject(Ride.class) : null;
                            if (ride != null) {
                                results.add(new OrganizerJoinRequest(requests.get(i), ride));
                            }
                        }
                        results.sort((a, b) -> {
                            Timestamp left = a.request.createdAt;
                            Timestamp right = b.request.createdAt;
                            if (left == null && right == null) return 0;
                            if (left == null) return 1;
                            if (right == null) return -1;
                            return right.compareTo(left);
                        });
                        callback.onSuccess(results);
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    private static DeniedException findDenied(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DeniedException) return (DeniedException) current;
            current = current.getCause();
        }
        return null;
    }

    private static final class DeniedException extends RuntimeException {
        DeniedException(String reason) {
            super(reason);
        }
    }
}
