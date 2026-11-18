package com.trojanrideshare.app.data.ride;

import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.Ride;

/**
 * Records the ride a service tries to publish instead of writing to Firestore. Lives in this
 * package because the Firestore-taking constructor is package-private.
 */
public class FakeRideDataAccess extends RideDataAccess {
    public Ride createdRide;
    public int createCalls;
    public String nextRideId = "ride-1";
    public Exception nextError;

    public FakeRideDataAccess() {
        super((FirebaseFirestore) null);
    }

    @Override
    public void createRide(Ride ride, CreateCallback callback) {
        createCalls++;
        createdRide = ride;
        if (nextError != null) {
            callback.onError(nextError);
        } else {
            callback.onSuccess(nextRideId);
        }
    }
}
