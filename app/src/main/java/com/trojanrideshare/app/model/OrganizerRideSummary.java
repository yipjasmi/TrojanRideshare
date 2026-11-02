package com.trojanrideshare.app.model;

public class OrganizerRideSummary {
    public final Ride ride;
    public final boolean needsFinalCost;
    public final int riders;
    public final int ridersPaid;
    public final long outstandingCents;

    public OrganizerRideSummary(Ride ride, int riders, int ridersPaid, long outstandingCents) {
        this.ride = ride;
        this.needsFinalCost = ride.finalCost == null;
        this.riders = riders;
        this.ridersPaid = ridersPaid;
        this.outstandingCents = outstandingCents;
    }
}
