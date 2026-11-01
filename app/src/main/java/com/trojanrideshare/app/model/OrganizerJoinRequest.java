package com.trojanrideshare.app.model;

public class OrganizerJoinRequest {
    public final JoinRequest request;
    public final Ride ride;

    public OrganizerJoinRequest(JoinRequest request, Ride ride) {
        this.request = request;
        this.ride = ride;
    }
}
