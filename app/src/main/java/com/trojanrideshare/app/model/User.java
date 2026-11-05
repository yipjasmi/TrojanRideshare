package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.List;

public class User {
    @DocumentId public String id;
    public String fullName;
    public String nickname;
    public String email;
    public String uscId;
    public int gradYear;
    public Timestamp createdAt;
    /** Rides this user chose to show on their public profile. Everything else stays private. */
    public List<String> sharedRideIds = new ArrayList<>();
    /** Stored without the leading @ or $. The rules don't check these, PaymentValidator does. */
    public String venmoUsername;
    public String cashtag;

    public User() {}

    @Exclude
    public String getDisplayName() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        if (fullName != null && !fullName.trim().isEmpty()) {
            return fullName.trim();
        }
        return "Trojan User";
    }

    @Exclude
    public boolean isRideShared(String rideId) {
        return rideId != null && sharedRideIds != null && sharedRideIds.contains(rideId);
    }
}
