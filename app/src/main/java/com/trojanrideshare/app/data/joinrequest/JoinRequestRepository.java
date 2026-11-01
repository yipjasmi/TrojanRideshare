package com.trojanrideshare.app.data.joinrequest;

import com.trojanrideshare.app.model.JoinRequest;

public interface JoinRequestRepository {
    enum Failure {
        DUPLICATE_REQUEST,
        RIDE_UNAVAILABLE
    }

    interface Callback {
        void onSuccess(JoinRequest request);
        void onFailure(Failure failure);
        void onError(Exception exception);
    }

    void submit(JoinRequest request, Callback callback);
}
