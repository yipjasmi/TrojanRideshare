package com.trojanrideshare.app.data.notification;

import com.trojanrideshare.app.model.Notification;

public interface NotificationRepository {
    interface WriteCallback {
        void onSuccess();
        void onError(Exception exception);
    }

    void create(Notification notification, WriteCallback callback);
}
