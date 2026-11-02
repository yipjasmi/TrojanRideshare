package com.trojanrideshare.app.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RideTest {
    @Test
    public void availableSeats_isDerivedFromParticipants() {
        Ride ride = new Ride();
        ride.maxRiders = 4;
        ride.participantIds = Arrays.asList("organizer", "rider");

        assertEquals(2, ride.getAvailableSeats());
        assertEquals(Ride.CAPACITY_OPEN, ride.getDerivedCapacityStatus());
    }

    @Test
    public void availableSeats_neverDropsBelowZeroAndFullIsDerived() {
        Ride ride = new Ride();
        ride.maxRiders = 1;
        ride.participantIds = Arrays.asList("organizer", "rider");

        assertEquals(0, ride.getAvailableSeats());
        assertEquals(Ride.CAPACITY_FULL, ride.getDerivedCapacityStatus());
    }

    @Test
    public void availableSeats_handlesMissingParticipants() {
        Ride ride = new Ride();
        ride.maxRiders = 3;
        ride.participantIds = null;

        assertEquals(3, ride.getAvailableSeats());
    }

    @Test
    public void allParticipantIds_putsTheOrganizerFirstAndNeverCountsThemTwice() {
        Ride ride = new Ride();
        ride.organizerId = "organizer";

        ride.participantIds = Arrays.asList("first", "second");
        assertEquals(
                Arrays.asList("organizer", "first", "second"),
                ride.getAllParticipantIds()
        );

        ride.participantIds = Arrays.asList("first", "organizer", "second");
        assertEquals(
                Arrays.asList("organizer", "first", "second"),
                ride.getAllParticipantIds()
        );
    }

    @Test
    public void allParticipantIds_handlesMissingParticipants() {
        Ride ride = new Ride();
        ride.organizerId = "organizer";
        ride.participantIds = null;

        assertEquals(Collections.singletonList("organizer"), ride.getAllParticipantIds());
    }

    @Test
    public void allParticipantIds_skipsIdsThatCannotBeDocuments() {
        Ride ride = new Ride();
        ride.organizerId = "organizer";
        ride.participantIds = Arrays.asList("rider", "bad/id", "", null);

        assertEquals(Arrays.asList("organizer", "rider"), ride.getAllParticipantIds());
    }
}
