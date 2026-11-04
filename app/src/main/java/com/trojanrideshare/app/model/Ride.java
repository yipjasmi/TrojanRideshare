package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    /** Current departure time. A delay replaces it, so search, sorting and summaries use it. */
    public Timestamp departureTime;
    /** The time originally posted, kept once the ride is first marked Delayed. */
    public Timestamp originalDepartureTime;
    public int maxRiders;
    public double expectedCost;
    public String status;
    @Deprecated @Exclude public String capacityStatus;
    public List<String> participantIds = new ArrayList<>();
    public Double finalCost;
    /** When the organizer marked the ride completed. Summaries still use the scheduled date. */
    public Timestamp completedAt;
    public Timestamp createdAt;

    public Ride() {}

    /** On Time or Delayed. Completed and Cancelled are final. */
    @Exclude
    public boolean isActive() {
        return STATUS_ON_TIME.equals(status) || STATUS_DELAYED.equals(status);
    }

    @Exclude
    public boolean hasDeparted(Timestamp now) {
        return departureTime != null && now != null && departureTime.compareTo(now) <= 0;
    }

    @Exclude
    public int getRiderCount() {
        return participantIds == null ? 0 : participantIds.size();
    }

    @Exclude
    public int getAvailableSeats() {
        int participantCount = participantIds == null ? 0 : participantIds.size();
        return Math.max(0, maxRiders - participantCount);
    }

    @Exclude
    public String getDerivedCapacityStatus() {
        return getAvailableSeats() == 0 ? CAPACITY_FULL : CAPACITY_OPEN;
    }

    @Exclude
    public List<String> getAllParticipantIds() {
        List<String> candidateIds = new ArrayList<>();
        candidateIds.add(organizerId);
        if (participantIds != null) {
            candidateIds.addAll(participantIds);
        }

        // Any student can write participantIds, and Firestore throws on a bad document id.
        Set<String> allParticipantIds = new LinkedHashSet<>();
        for (String candidateId : candidateIds) {
            if (candidateId != null && !candidateId.isEmpty() && !candidateId.contains("/")) {
                allParticipantIds.add(candidateId);
            }
        }
        return new ArrayList<>(allParticipantIds);
    }
}
