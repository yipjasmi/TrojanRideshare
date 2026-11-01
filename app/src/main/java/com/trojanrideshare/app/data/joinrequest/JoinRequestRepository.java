package com.trojanrideshare.app.data.joinrequest;

import com.trojanrideshare.app.model.JoinRequest;

import java.util.Set;

public interface JoinRequestRepository {
    enum Failure {
        DUPLICATE_REQUEST,
        RIDE_UNAVAILABLE,
        REQUEST_NOT_PENDING
    }

    interface Callback {
        void onSuccess(JoinRequest request);
        void onFailure(Failure failure);
        void onError(Exception exception);
    }

    interface CancelCallback {
        void onSuccess();
        void onFailure(Failure failure);
        void onError(Exception exception);
    }

    interface PendingRequestsCallback {
        void onSuccess(Set<String> rideIds);
        void onError(Exception exception);
    }

    void submit(JoinRequest request, Callback callback);
    void cancel(String rideId, String requesterId, CancelCallback callback);
    void loadPendingRideIds(String requesterId, PendingRequestsCallback callback);
}
