package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import java.util.ArrayList;
import java.util.List;

public class Ride {
    public static final String STATUS_ON_TIME = "onTime";
    public static final String STATUS_DELAYED = "delayed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_COMPLETED = "completed";
    public static final String CAPACITY_OPEN = "open";
    public static final String CAPACITY_FULL = "full";

    @DocumentId public String id;
    public String title;
    public String organizerId;
    public PlaceInfo origin;
    public PlaceInfo destination;
    public Timestamp departureTime;
    public int maxRiders;
    public double expectedCost;
    public String status;
    public String capacityStatus;
    public List<String> participantIds = new ArrayList<>();
    public Double finalCost;
    public Timestamp createdAt;

    public Ride() {}
}
