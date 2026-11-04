package com.trojanrideshare.app.service.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class LeaveRideServiceTest {
    private static final long NOW_MILLIS = 1_700_000_000_000L;
    private static final long HOUR = 60L * 60 * 1000;
    private static final Timestamp NOW = new Timestamp(new Date(NOW_MILLIS));

    @Test
    public void leaveError_allowsAtLeastThreeHoursBefore() {
        assertNull(LeaveRideService.leaveError(
                ride("org", Arrays.asList("org", "a"), 3 * HOUR), "a", NOW));
        assertNull(LeaveRideService.leaveError(
                ride("org", Arrays.asList("org", "a"), 5 * HOUR), "org", NOW));
    }

    @Test
    public void leaveError_blocksWithinThreeHoursIncludingOrganizer() {
        Ride ride = ride("org", Arrays.asList("org", "a"), 3 * HOUR - 60_000);
        assertEquals(LeaveRideService.REASON_TOO_LATE, LeaveRideService.leaveError(ride, "a", NOW));
        assertEquals(LeaveRideService.REASON_TOO_LATE, LeaveRideService.leaveError(ride, "org", NOW));
    }

    @Test
    public void leaveError_usesDelayedDepartureTime() {
        Ride ride = ride("org", Arrays.asList("org", "a"), 4 * HOUR);
        ride.status = Ride.STATUS_DELAYED;
        ride.originalDepartureTime = at(HOUR);
        assertNull(LeaveRideService.leaveError(ride, "a", NOW));
    }

    @Test
    public void leaveError_blocksCompletedCancelledAndNonMembers() {
        Ride ride = ride("org", Arrays.asList("org", "a"), 10 * HOUR);
        assertEquals(LeaveRideService.REASON_NOT_MEMBER, LeaveRideService.leaveError(ride, "x", NOW));
        ride.status = Ride.STATUS_COMPLETED;
        assertEquals(LeaveRideService.REASON_COMPLETED, LeaveRideService.leaveError(ride, "a", NOW));
        ride.status = Ride.STATUS_CANCELLED;
        assertEquals(LeaveRideService.REASON_CANCELLED, LeaveRideService.leaveError(ride, "a", NOW));
    }

    @Test
    public void plan_riderLeavingKeepsOwnershipAndStatus() {
        Ride ride = ride("org", Arrays.asList("org", "a", "b"), 10 * HOUR);
        LeaveRideService.Plan plan = LeaveRideService.plan(ride, "a");

        assertEquals(LeaveRideService.Outcome.RIDER_LEFT, plan.outcome);
        assertEquals(Arrays.asList("org", "b"), plan.rideUpdates.get("participantIds"));
        assertFalse(plan.rideUpdates.containsKey("organizerId"));
        assertFalse(plan.rideUpdates.containsKey("status"));
    }

    @Test
    public void plan_soloOrganizerLeavingCancels() {
        Ride ride = ride("org", Collections.singletonList("org"), 10 * HOUR);
        LeaveRideService.Plan plan = LeaveRideService.plan(ride, "org");

        assertEquals(LeaveRideService.Outcome.RIDE_CANCELLED, plan.outcome);
        assertEquals(Ride.STATUS_CANCELLED, plan.rideUpdates.get("status"));
    }

    @Test
    public void plan_organizerLeavingTransfersToEarliestJoined() {
        Ride ride = ride("org", Arrays.asList("org", "first", "second"), 10 * HOUR);
        LeaveRideService.Plan plan = LeaveRideService.plan(ride, "org");

        assertEquals(LeaveRideService.Outcome.OWNERSHIP_TRANSFERRED, plan.outcome);
        assertEquals("first", plan.newOrganizerId);
        assertEquals("first", plan.rideUpdates.get("organizerId"));
        assertEquals(Arrays.asList("first", "second"), plan.rideUpdates.get("participantIds"));
        assertFalse(plan.rideUpdates.containsKey("status"));
    }

    private static Timestamp at(long offsetMillis) {
        return new Timestamp(new Date(NOW_MILLIS + offsetMillis));
    }

    private static Ride ride(String organizerId, List<String> participants, long departsIn) {
        Ride ride = new Ride();
        ride.id = "ride";
        ride.organizerId = organizerId;
        ride.participantIds = new ArrayList<>(participants);
        ride.maxRiders = 4;
        ride.status = Ride.STATUS_ON_TIME;
        ride.departureTime = at(departsIn);
        return ride;
    }
}
