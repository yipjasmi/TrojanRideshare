package com.trojanrideshare.app.service.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class EditRideServiceTest {
    private static final String ORGANIZER = "organizer";
    private static final Timestamp NOW = new Timestamp(new Date(1_700_000_000_000L));
    private static final Timestamp FUTURE =
            new Timestamp(new Date(1_700_000_000_000L + 86_400_000L));

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
    public void validateForEdit_acceptsValidChange() {
        ValidationResult result = EditRideService.validateForEdit(
                input(4), ride(4, one(ORGANIZER)), NOW);

        assertTrue(result.isValid());
    }

    @Test
    public void validateForEdit_rejectsMaxBelowCurrentRiders() {
        // 3 riders already aboard; organizer tries to lower the cap to 2.
        Ride existing = ride(4, Arrays.asList(ORGANIZER, "rider-a", "rider-b"));

        ValidationResult result = EditRideService.validateForEdit(input(2), existing, NOW);

        assertFalse(result.isValid());
        assertEquals(
                "Max riders cannot be fewer than the current riders (3)",
                result.errorFor(RideValidator.FIELD_MAX_RIDERS));
    }

    @Test
    public void validateForEdit_stillEnforcesBaseRules() {
        // maxRiders of 1 is below the allowed range, caught by the base validator.
        ValidationResult result = EditRideService.validateForEdit(
                input(1), ride(4, one(ORGANIZER)), NOW);

        assertEquals(
                RideValidator.ERROR_RIDERS_RANGE,
                result.errorFor(RideValidator.FIELD_MAX_RIDERS));
    }

    private static List<String> one(String id) {
        return Collections.singletonList(id);
    }

    private static Ride ride(int maxRiders, List<String> participantIds) {
        Ride ride = new Ride();
        ride.id = "ride-1";
        ride.organizerId = ORGANIZER;
        ride.maxRiders = maxRiders;
        ride.participantIds = new ArrayList<>(participantIds);
        return ride;
    }

    private static RideInput input(int maxRiders) {
        return new RideInput("USC to LAX", place("a"), place("b"), FUTURE, maxRiders, 20.0);
    }

    private static PlaceInfo place(String placeId) {
        PlaceInfo place = new PlaceInfo();
        place.placeId = placeId;
        place.address = placeId + " address";
        return place;
    }
}
