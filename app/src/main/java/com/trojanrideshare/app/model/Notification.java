package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class Notification {
    @DocumentId public String id;
    public String userId;
    public String type;
    public String rideId;
    public boolean read;
    public Timestamp createdAt;

    public Notification() {}
}
