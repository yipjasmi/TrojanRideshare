package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class JoinRequest {
    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String REJECTED = "rejected";
    public static final String CANCELLED = "cancelled";
    /** The rider was accepted and later left the ride. They may request again. */
    public static final String LEFT = "left";
    /** Pending when the ride was cancelled, so it was closed automatically. */
    public static final String RIDE_CANCELLED = "rideCancelled";

    @DocumentId public String id;
    public String rideId;
    public String requesterId;
    public String message;
    public String status;
    public Timestamp createdAt;

    public JoinRequest() {}
}
