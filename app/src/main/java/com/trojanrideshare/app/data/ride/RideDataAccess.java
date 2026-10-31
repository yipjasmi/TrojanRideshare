package com.trojanrideshare.app.data.ride;

import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.Ride;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RideDataAccess {
    public interface Callback {
        void onSuccess(List<Ride> rides);
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
}
