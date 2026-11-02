package com.trojanrideshare.app.data.notification;

import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.Notification;

import java.util.ArrayList;
import java.util.List;

public class NotificationDataAccess {
    public interface WriteCallback {
        void onSuccess();
        void onError(Exception exception);
    }

    public interface ListCallback {
        void onSuccess(List<Notification> notifications);
        void onError(Exception exception);
    }

    private static final String NOTIFICATIONS_COLLECTION = "notifications";
    private final FirebaseFirestore firestore;

    public NotificationDataAccess() {
        this(FirebaseFirestore.getInstance());
    }

    NotificationDataAccess(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void create(Notification notification, WriteCallback callback) {
        firestore.collection(NOTIFICATIONS_COLLECTION)
                .add(notification)
                .addOnSuccessListener(reference -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    /** All notifications for the user. Ordering is left to the caller to avoid a composite index. */
    public void getForUser(String userId, ListCallback callback) {
        firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Notification> notifications = new ArrayList<>();
                    snapshot.getDocuments().forEach(document -> {
                        Notification notification = document.toObject(Notification.class);
                        if (notification != null) {
                            notifications.add(notification);
                        }
                    });
                    callback.onSuccess(notifications);
                })
                .addOnFailureListener(callback::onError);
    }

    public void markRead(String notificationId, WriteCallback callback) {
        firestore.collection(NOTIFICATIONS_COLLECTION)
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }
}
