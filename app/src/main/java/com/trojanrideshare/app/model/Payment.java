package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Payment {
    // firestore.rules has the same 7 days, so change both together.
    public static final int APPROVAL_PERIOD_DAYS = 7;
    private static final long APPROVAL_PERIOD_MILLIS =
            TimeUnit.DAYS.toMillis(APPROVAL_PERIOD_DAYS);

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

    public boolean isOverdue(long nowMillis) {
        return !paid
                && assignedAt != null
                && nowMillis - assignedAt.toDate().getTime() >= APPROVAL_PERIOD_MILLIS;
    }

    public static boolean hasRiderMarkedPaid(List<Payment> shares) {
        for (Payment share : shares) {
            if (share.paidAt != null) {
                return true;
            }
        }
        return false;
    }
}
