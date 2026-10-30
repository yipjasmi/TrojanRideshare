package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class JoinRequest {
    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String REJECTED = "rejected";

    @DocumentId public String id;
    public String rideId;
    public String requesterId;
    public String message;
    public String status;
    public Timestamp createdAt;

    public JoinRequest() {}
}
