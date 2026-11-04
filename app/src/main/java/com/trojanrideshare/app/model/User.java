package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class User {
    @DocumentId public String id;
    public String fullName;
    public String nickname;
    public String email;
    public String uscId;
    public int gradYear;
    public Timestamp createdAt;

    public User() {}

    public String getDisplayName() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        if (fullName != null && !fullName.trim().isEmpty()) {
            return fullName.trim();
        }
        return "Trojan User";
    }
}
