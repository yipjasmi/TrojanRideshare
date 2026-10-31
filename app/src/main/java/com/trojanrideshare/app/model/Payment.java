package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class Payment {
    @DocumentId public String id;
    public String rideId;
    public String userId;
    public String organizerId;
    public String rideTitle;
    public double amount;
    public boolean paid;
    public Timestamp paidAt;
    public Timestamp assignedAt;
    public boolean flagged;
    public Timestamp flaggedAt;

    public Payment() {}
}
