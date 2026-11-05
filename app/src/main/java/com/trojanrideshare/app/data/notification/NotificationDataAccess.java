package com.trojanrideshare.app.data.notification;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.Notification;

import java.util.ArrayList;
import java.util.List;

public class NotificationDataAccess implements NotificationRepository {
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

    @Override
    public void create(Notification notification, WriteCallback callback) {
        firestore.collection(NOTIFICATIONS_COLLECTION)
                .add(notification)
                .addOnSuccessListener(reference -> callback.onSuccess())
                .addOnFailureListener(exception -> {
                    Log.w("Notifications", "Could not send a " + notification.type, exception);
                    callback.onError(exception);
                });
    }

    /** All notifications for the user. Ordering is left to the caller to avoid a composite index. */
    public void getForUser(String userId, ListCallback callback) {
        firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Notification> notifications = new ArrayList<>();
                    snapshot.getDocuments().forEach(document -> {
                        // Any student can write a notification, so one with a wrong field type
                        // is skipped instead of crashing the inbox and the Home bell.
                        try {
                            Notification notification = document.toObject(Notification.class);
                            if (notification != null) {
                                notifications.add(notification);
                            }
                        } catch (RuntimeException exception) {
                            Log.w("Notifications", "Skipped a malformed notification", exception);
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
