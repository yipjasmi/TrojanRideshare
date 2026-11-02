package com.trojanrideshare.app.data.user;

import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.User;

public class UserDataAccess {
    public interface SingleCallback {
        void onSuccess(User user);
        void onError(Exception exception);
    }

    private static final String USERS_COLLECTION = "users";
    private final FirebaseFirestore firestore;

    public UserDataAccess() {
        this(FirebaseFirestore.getInstance());
    }

    UserDataAccess(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void getById(String userId, SingleCallback callback) {
        firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(document -> callback.onSuccess(document.toObject(User.class)))
                .addOnFailureListener(callback::onError);
    }
}
