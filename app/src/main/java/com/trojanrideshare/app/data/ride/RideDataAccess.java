package com.trojanrideshare.app.data.ride;

import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.Ride;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RideDataAccess {
    public interface Callback {
        void onSuccess(List<Ride> rides);
        void onError(Exception exception);
    }

    public interface CreateCallback {
        void onSuccess(String rideId);
        void onError(Exception exception);
    }

    public interface SingleCallback {
        void onSuccess(Ride ride);
        void onError(Exception exception);
    }

    public interface WriteCallback {
        void onSuccess();
        void onError(Exception exception);
    }

    private static final String RIDES_COLLECTION = "rides";
    private final FirebaseFirestore firestore;

    public RideDataAccess() {
        this(FirebaseFirestore.getInstance());
    }

    RideDataAccess(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void getActiveRides(Callback callback) {
        firestore.collection(RIDES_COLLECTION)
                .whereIn("status", Arrays.asList(
                        Ride.STATUS_ON_TIME,
                        Ride.STATUS_DELAYED
                ))
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Ride> rides = new ArrayList<>();
                    snapshot.getDocuments().forEach(document -> {
                        Ride ride = document.toObject(Ride.class);
                        if (ride != null) {
                            rides.add(ride);
                        }
                    });
                    callback.onSuccess(rides);
                })
                .addOnFailureListener(callback::onError);
    }

    public void createRide(Ride ride, CreateCallback callback) {
        firestore.collection(RIDES_COLLECTION)
                .add(ride)
                .addOnSuccessListener(reference -> callback.onSuccess(reference.getId()))
                .addOnFailureListener(callback::onError);
    }

    public void getRideById(String rideId, SingleCallback callback) {
        firestore.collection(RIDES_COLLECTION)
                .document(rideId)
                .get()
                .addOnSuccessListener(document -> callback.onSuccess(document.toObject(Ride.class)))
                .addOnFailureListener(callback::onError);
    }

    public void getRidesForUser(String userId, Callback callback) {
        firestore.collection(RIDES_COLLECTION)
                .whereArrayContains("participantIds", userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Ride> rides = new ArrayList<>();
                    snapshot.getDocuments().forEach(document -> {
                        Ride ride = document.toObject(Ride.class);
                        if (ride != null) {
                            rides.add(ride);
                        }
                    });
                    callback.onSuccess(rides);
                })
                .addOnFailureListener(callback::onError);
    }

    public void updateRide(String rideId, Map<String, Object> updates, WriteCallback callback) {
        firestore.collection(RIDES_COLLECTION)
                .document(rideId)
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void updateStatus(String rideId, String status, WriteCallback callback) {
        firestore.collection(RIDES_COLLECTION)
                .document(rideId)
                .update("status", status)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }
}
