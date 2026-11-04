package com.trojanrideshare.app.service.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class EditRideServiceTest {
    private static final String ORGANIZER = "organizer";
    private static final Timestamp NOW = new Timestamp(new Date(1_700_000_000_000L));
    private static final Timestamp FUTURE =
            new Timestamp(new Date(1_700_000_000_000L + 86_400_000L));
    private static final Timestamp LATER =
            new Timestamp(new Date(1_700_000_000_000L + 2 * 86_400_000L));
    private static final Timestamp PAST =
            new Timestamp(new Date(1_700_000_000_000L - 86_400_000L));

    @Test
    public void authorizationError_allowsOrganizer() {
        assertNull(EditRideService.authorizationError(ride(4, one(ORGANIZER)), ORGANIZER));
    }

    @Test
    public void authorizationError_blocksNonOrganizer() {
        assertEquals(
                EditRideService.REASON_NOT_ORGANIZER,
                EditRideService.authorizationError(ride(4, one(ORGANIZER)), "someone-else"));
    }

    @Test
    public void authorizationError_rejectsMissingInputs() {
        assertEquals(
                EditRideService.REASON_UNAVAILABLE,
                EditRideService.authorizationError(null, ORGANIZER));
        assertEquals(
                EditRideService.REASON_UNAVAILABLE,
                EditRideService.authorizationError(ride(4, one(ORGANIZER)), "  "));
    }

    @Test
    public void delayError_allowsLaterFutureTime() {
        Ride ride = ride(4, one(ORGANIZER));
        ride.departureTime = FUTURE;

        assertNull(EditRideService.delayError(ride, LATER, ORGANIZER, NOW));
    }

    @Test
    public void delayError_rejectsTimeNotLaterThanCurrent() {
        Ride ride = ride(4, one(ORGANIZER));
        ride.departureTime = LATER;

        assertEquals(EditRideService.REASON_DELAY_NOT_LATER,
                EditRideService.delayError(ride, FUTURE, ORGANIZER, NOW));
    }

    @Test
    public void delayError_rejectsPastTimeAndMissingTime() {
        Ride ride = ride(4, one(ORGANIZER));
        ride.departureTime = PAST;

        assertEquals(EditRideService.REASON_DELAY_IN_PAST,
                EditRideService.delayError(ride, PAST, ORGANIZER, NOW));
        assertEquals(EditRideService.REASON_DELAY_TIME_REQUIRED,
                EditRideService.delayError(ride, null, ORGANIZER, NOW));
    }

    @Test
    public void delayError_blocksNonOrganizerAndFinalRides() {
        Ride ride = ride(4, one(ORGANIZER));
        ride.departureTime = FUTURE;
        assertEquals(EditRideService.REASON_NOT_ORGANIZER,
                EditRideService.delayError(ride, LATER, "someone-else", NOW));

        ride.status = Ride.STATUS_COMPLETED;
        assertEquals(EditRideService.REASON_FINAL,
                EditRideService.delayError(ride, LATER, ORGANIZER, NOW));
        ride.status = Ride.STATUS_CANCELLED;
        assertEquals(EditRideService.REASON_FINAL,
                EditRideService.delayError(ride, LATER, ORGANIZER, NOW));
    }

    @Test
    public void completeError_onlyAfterDeparture() {
        Ride ride = ride(4, one(ORGANIZER));
        ride.departureTime = FUTURE;
        assertEquals(EditRideService.REASON_NOT_DEPARTED,
                EditRideService.completeError(ride, ORGANIZER, NOW));

        ride.departureTime = PAST;
        assertNull(EditRideService.completeError(ride, ORGANIZER, NOW));

        ride.status = Ride.STATUS_DELAYED;
        assertNull(EditRideService.completeError(ride, ORGANIZER, NOW));
    }

    @Test
    public void completeError_blocksNonOrganizerAndFinalRides() {
        Ride ride = ride(4, one(ORGANIZER));
        ride.departureTime = PAST;
        assertEquals(EditRideService.REASON_NOT_ORGANIZER,
                EditRideService.completeError(ride, "someone-else", NOW));

        ride.status = Ride.STATUS_COMPLETED;
        assertEquals(EditRideService.REASON_FINAL,
                EditRideService.completeError(ride, ORGANIZER, NOW));
        ride.status = Ride.STATUS_CANCELLED;
        assertEquals(EditRideService.REASON_FINAL,
                EditRideService.completeError(ride, ORGANIZER, NOW));
    }

    private static List<String> one(String id) {
        return Collections.singletonList(id);
    }

    private static Ride ride(int maxRiders, List<String> participantIds) {
        Ride ride = new Ride();
        ride.id = "ride-1";
        ride.organizerId = ORGANIZER;
        ride.maxRiders = maxRiders;
        ride.status = Ride.STATUS_ON_TIME;
        ride.participantIds = new ArrayList<>(participantIds);
        return ride;
    }
}
