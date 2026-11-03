package com.trojanrideshare.app.model;

import java.util.List;
import java.util.Map;

public class RidePaymentDetails {
    public final Ride ride;
    public final List<String> participants;
    public final Map<String, String> names;
    public final List<Payment> shares;

    public RidePaymentDetails(
            Ride ride,
            List<String> participants,
            Map<String, String> names,
            List<Payment> shares
    ) {
        this.ride = ride;
        this.participants = participants;
        this.names = names;
        this.shares = shares;
    }
}
