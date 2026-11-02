package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class Notification {
    public static final String TYPE_JOIN_REQUEST = "joinRequest";
    public static final String TYPE_REQUEST_ACCEPTED = "requestAccepted";
    public static final String TYPE_REQUEST_REJECTED = "requestRejected";
    public static final String TYPE_RIDE_UPDATED = "rideUpdated";
    public static final String TYPE_RIDE_CANCELLED = "rideCancelled";

    @DocumentId public String id;
    public String userId;
    public String type;
    public String rideId;
    public String message;
    public boolean read;
    public Timestamp createdAt;

    public Notification() {}
}
