package com.trojanrideshare.app.util;

import static org.junit.Assert.assertEquals;

import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RideSeatsTest {
    @Test
    public void dots_fillsOneDotPerTakenSeat() {
        assertEquals("●●●○", RideSeats.dots(3, 4));
    }

    @Test
    public void dots_fillsEveryDotWhenFull() {
        assertEquals("●●●●", RideSeats.dots(4, 4));
    }

    @Test
    public void taken_countsTheOrganizerAsARider() {
        Ride ride = ride(4, 1, 40.00);

        assertEquals(1, RideSeats.taken(ride));
        assertEquals(4, RideSeats.total(ride));
    }

    @Test
    public void taken_neverExceedsTheSeatsOnTheRide() {
        assertEquals(4, RideSeats.taken(ride(4, 6, 40.00)));
    }

    @Test
    public void costEachCents_givesASoleRiderTheWholeCost() {
        assertEquals(2500, RideSeats.costEachCents(ride(4, 1, 25.00)));
    }

    @Test
    public void costEachIfJoinedCents_splitsASoleRidersCostWithTheViewer() {
        assertEquals(1250, RideSeats.costEachIfJoinedCents(ride(4, 1, 25.00)));
    }

    @Test
    public void costEachCents_splitsAFullRideAcrossEverySeat() {
        assertEquals(1000, RideSeats.costEachCents(ride(4, 4, 40.00)));
    }

    @Test
    public void costEachIfJoinedCents_neverSplitsAFullRideAcrossMoreSeatsThanItHas() {
        assertEquals(1000, RideSeats.costEachIfJoinedCents(ride(4, 4, 40.00)));
    }

    @Test
    public void costEachIfJoinedCents_roundsToTheNearestCentWhenTheCostDoesNotDivideEvenly() {
        assertEquals(1333, RideSeats.costEachIfJoinedCents(ride(4, 2, 40.00)));
        assertEquals(667, RideSeats.costEachIfJoinedCents(ride(4, 2, 20.00)));
    }

    @Test
    public void costEachCents_staysPositiveWhenTheCostIsAbsurdlyLarge() {
        // Rules only require expectedCost > 0, so an organizer can write any size.
        assertEquals(Long.MAX_VALUE / 2 + 1, RideSeats.costEachCents(ride(4, 2, 1e300)));
    }

    private static Ride ride(int maxRiders, int riders, double expectedCost) {
        Ride ride = new Ride();
        ride.organizerId = "organizer";
        ride.maxRiders = maxRiders;
        ride.expectedCost = expectedCost;
        List<String> participantIds = new ArrayList<>();
        participantIds.add("organizer");
        for (int index = 1; index < riders; index++) {
            participantIds.add("rider" + index);
        }
        ride.participantIds = participantIds;
        return ride;
    }
}
