package com.trojanrideshare.app.model;

public class MonthlySummary {
    public int completedRides, ownedRides, joinedRides, unassignedShares;
    /** Paid plus unpaid assigned shares. */
    public long totalCostCents;
    public long paidCostCents, unpaidCostCents;

    public boolean hasActivity() {
        return completedRides > 0;
    }
}
