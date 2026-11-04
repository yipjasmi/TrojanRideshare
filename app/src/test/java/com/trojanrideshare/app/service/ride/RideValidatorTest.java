package com.trojanrideshare.app.service.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.PlaceInfo;

import org.junit.Test;

import java.util.Date;

public class RideValidatorTest {
    private static final Timestamp NOW = new Timestamp(new Date(1_700_000_000_000L));
    private static final Timestamp FUTURE =
            new Timestamp(new Date(1_700_000_000_000L + 86_400_000L));
    private static final Timestamp PAST =
            new Timestamp(new Date(1_700_000_000_000L - 86_400_000L));

    @Test
    public void validate_acceptsFullyValidInput() {
        ValidationResult result = RideValidator.validate(
                input("USC to LAX", place("usc"), place("lax"), FUTURE, 4, 25.0), NOW);

        assertTrue(result.isValid());
        assertEquals(0, result.errors().size());
    }

    @Test
    public void validate_flagsEveryMissingRequiredField() {
        ValidationResult result = RideValidator.validate(
                input("  ", null, null, null, null, null), NOW);

        assertFalse(result.isValid());
        assertEquals(
                RideValidator.ERROR_TITLE_REQUIRED,
                result.errorFor(RideValidator.FIELD_TITLE));
        assertEquals(
                RideValidator.ERROR_ORIGIN_INVALID,
                result.errorFor(RideValidator.FIELD_ORIGIN));
        assertEquals(
                RideValidator.ERROR_DESTINATION_INVALID,
                result.errorFor(RideValidator.FIELD_DESTINATION));
        assertEquals(
                RideValidator.ERROR_DEPARTURE_REQUIRED,
                result.errorFor(RideValidator.FIELD_DEPARTURE_TIME));
        assertEquals(
                RideValidator.ERROR_RIDERS_RANGE,
                result.errorFor(RideValidator.FIELD_MAX_RIDERS));
        assertEquals(
                RideValidator.ERROR_COST_REQUIRED,
                result.errorFor(RideValidator.FIELD_EXPECTED_COST));
    }

    @Test
    public void validate_rejectsRidersBelowMinimum() {
        ValidationResult result = RideValidator.validate(
                input("t", place("a"), place("b"), FUTURE, 1, 10.0), NOW);

        assertEquals(
                RideValidator.ERROR_RIDERS_RANGE,
                result.errorFor(RideValidator.FIELD_MAX_RIDERS));
    }

    @Test
    public void validate_rejectsRidersAboveMaximum() {
        ValidationResult result = RideValidator.validate(
                input("t", place("a"), place("b"), FUTURE, 7, 10.0), NOW);

        assertEquals(
                RideValidator.ERROR_RIDERS_RANGE,
                result.errorFor(RideValidator.FIELD_MAX_RIDERS));
    }

    @Test
    public void validate_acceptsRidersAtBoundaries() {
        assertNull(RideValidator.validate(
                        input("t", place("a"), place("b"), FUTURE, 2, 10.0), NOW)
                .errorFor(RideValidator.FIELD_MAX_RIDERS));
        assertNull(RideValidator.validate(
                        input("t", place("a"), place("b"), FUTURE, 6, 10.0), NOW)
                .errorFor(RideValidator.FIELD_MAX_RIDERS));
    }

    @Test
    public void validate_rejectsPlaceWithoutPlaceId() {
        PlaceInfo typedButNotPicked = new PlaceInfo();
        typedButNotPicked.address = "Somewhere I typed";
        // placeId left null -> not a real picked location

        ValidationResult result = RideValidator.validate(
                input("t", typedButNotPicked, place("b"), FUTURE, 4, 10.0), NOW);

        assertEquals(
                RideValidator.ERROR_ORIGIN_INVALID,
                result.errorFor(RideValidator.FIELD_ORIGIN));
    }

    @Test
    public void validate_rejectsIdenticalOriginAndDestination() {
        ValidationResult result = RideValidator.validate(
                input("t", place("same"), place("same"), FUTURE, 4, 10.0), NOW);

        assertEquals(
                RideValidator.ERROR_SAME_LOCATION,
                result.errorFor(RideValidator.FIELD_DESTINATION));
    }

    @Test
    public void validate_rejectsDepartureInPast() {
        ValidationResult result = RideValidator.validate(
                input("t", place("a"), place("b"), PAST, 4, 10.0), NOW);

        assertEquals(
                RideValidator.ERROR_DEPARTURE_IN_PAST,
                result.errorFor(RideValidator.FIELD_DEPARTURE_TIME));
    }

    @Test
    public void validate_rejectsNegativeCost() {
        ValidationResult result = RideValidator.validate(
                input("t", place("a"), place("b"), FUTURE, 4, -1.0), NOW);

        assertEquals(
                RideValidator.ERROR_COST_NOT_POSITIVE,
                result.errorFor(RideValidator.FIELD_EXPECTED_COST));
    }

    @Test
    public void validate_rejectsZeroCost() {
        ValidationResult result = RideValidator.validate(
                input("t", place("a"), place("b"), FUTURE, 4, 0.0), NOW);

        assertEquals(
                RideValidator.ERROR_COST_NOT_POSITIVE,
                result.errorFor(RideValidator.FIELD_EXPECTED_COST));
    }

    @Test
    public void validate_rejectsMoreThanTwoDecimals() {
        ValidationResult result = RideValidator.validate(
                input("t", place("a"), place("b"), FUTURE, 4, 10.555), NOW);

        assertEquals(
                RideValidator.ERROR_COST_DECIMALS,
                result.errorFor(RideValidator.FIELD_EXPECTED_COST));
    }

    @Test
    public void validate_acceptsTwoDecimalCost() {
        assertTrue(RideValidator.validate(
                input("t", place("a"), place("b"), FUTURE, 4, 10.55), NOW).isValid());
        assertTrue(RideValidator.validate(
                input("t", place("a"), place("b"), FUTURE, 4, 0.01), NOW).isValid());
        assertTrue(RideValidator.validate(
                input("t", place("a"), place("b"), FUTURE, 4, 12.0), NOW).isValid());
    }

    private static RideInput input(
            String title,
            PlaceInfo origin,
            PlaceInfo destination,
            Timestamp departureTime,
            Integer maxRiders,
            Double expectedCost
    ) {
        return new RideInput(title, origin, destination, departureTime, maxRiders, expectedCost);
    }

    private static PlaceInfo place(String placeId) {
        PlaceInfo place = new PlaceInfo();
        place.placeId = placeId;
        place.address = placeId + " address";
        place.lat = 34.0;
        place.lng = -118.0;
        return place;
    }
}
