package com.trojanrideshare.app.data.payment;

import android.app.Activity;

import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.PaymentHandles;
import com.trojanrideshare.app.model.Ride;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PaymentRepository {
    enum Failure {
        RIDE_NOT_FOUND,
        NOT_AUTHORIZED,
        RIDE_NOT_COMPLETED,
        SPLIT_LOCKED,
        RIDERS_CHANGED,
        SHARE_NOT_FOUND,
        ALREADY_PAID,
        NOT_OVERDUE
    }

    interface RideCallback {
        void onSuccess(Ride ride);
        void onError(Exception exception);
    }

    interface RidesCallback {
        void onSuccess(List<Ride> rides);
        void onError(Exception exception);
    }

    interface SharesCallback {
        void onSuccess(List<Payment> shares);
        void onError(Exception exception);
    }

    interface NamesCallback {
        void onSuccess(Map<String, String> names, Map<String, PaymentHandles> handles);
    }

    interface UpdateCallback {
        void onSuccess();
        void onFailure(Failure failure);
        void onError(Exception exception);
    }

    // Gets the document as the transaction read it, before writing. Marking a paid share paid, or
    // flagging a flagged one, succeeds without a write, and this is the only way to tell.
    interface ChangeCallback<T> {
        void onSuccess(T before);
        void onFailure(Failure failure);
        void onError(Exception exception);
    }

    void loadRide(String rideId, RideCallback callback);
    void loadCompletedRides(String organizerId, RidesCallback callback);
    void loadSharesFor(String userId, SharesCallback callback);
    void loadSharesOrganizedBy(String organizerId, SharesCallback callback);
    void loadRideShares(String rideId, String organizerId, SharesCallback callback);
    void loadNames(Collection<String> userIds, NamesCallback callback);
    void watchRideShares(
            Activity owner,
            String rideId,
            String organizerId,
            SharesCallback callback
    );
    void watchShare(Activity owner, String rideId, String userId, SharesCallback callback);
    void saveSplit(
            String rideId,
            String organizerId,
            List<String> userIds,
            long[] shares,
            long finalCents,
            ChangeCallback<Ride> callback
    );
    void markPaid(String rideId, String userId, ChangeCallback<Payment> callback);
    void flag(String rideId, String riderId, ChangeCallback<Payment> callback);
    void saveHandles(
            String userId,
            String venmoUsername,
            String cashtag,
            UpdateCallback callback
    );
}
