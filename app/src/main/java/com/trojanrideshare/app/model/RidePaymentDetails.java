package com.trojanrideshare.app.model;

import java.util.List;
import java.util.Map;

public class RidePaymentDetails {
    public final Ride ride;
    public final List<String> participants;
    public final Map<String, String> names;
    public final Map<String, PaymentHandles> handles;
    public final List<Payment> shares;

    public RidePaymentDetails(
            Ride ride,
            List<String> participants,
            Map<String, String> names,
            Map<String, PaymentHandles> handles,
            List<Payment> shares
    ) {
        this.ride = ride;
        this.participants = participants;
        this.names = names;
        this.handles = handles;
        this.shares = shares;
    }
}
