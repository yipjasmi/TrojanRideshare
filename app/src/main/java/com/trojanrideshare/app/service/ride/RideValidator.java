package com.trojanrideshare.app.service.ride;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.PlaceInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure validation for ride creation/editing, enforcing the acceptance criteria:
 * all required fields present, origin/destination are valid picked locations, and the
 * maximum number of riders is between {@value #MIN_RIDERS} and {@value #MAX_RIDERS}, and the
 * expected cost is greater than zero with at most two decimal places.
 *
 * <p>No Android/Firebase dependencies beyond the plain data types, so it is covered by
 * {@code RideValidatorTest}.
 */
public final class RideValidator {
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_ORIGIN = "origin";
    public static final String FIELD_DESTINATION = "destination";
    public static final String FIELD_DEPARTURE_TIME = "departureTime";
    public static final String FIELD_MAX_RIDERS = "maxRiders";
    public static final String FIELD_EXPECTED_COST = "expectedCost";

    public static final int MIN_RIDERS = 2;
    public static final int MAX_RIDERS = 6;

    public static final String ERROR_TITLE_REQUIRED = "Title is required";
    public static final String ERROR_ORIGIN_INVALID =
            "Select a valid origin from the suggestions";
    public static final String ERROR_DESTINATION_INVALID =
            "Select a valid destination from the suggestions";
    public static final String ERROR_SAME_LOCATION =
            "Origin and destination must be different";
    public static final String ERROR_DEPARTURE_REQUIRED = "Departure time is required";
    public static final String ERROR_DEPARTURE_IN_PAST = "Departure time must be in the future";
    public static final String ERROR_RIDERS_RANGE =
            "Number of riders must be between " + MIN_RIDERS + " and " + MAX_RIDERS;
    public static final String ERROR_COST_REQUIRED = "Expected cost is required";
    public static final String ERROR_COST_ZERO =
            "Expected cost can't be $0.00. Enter an amount greater than zero.";
    public static final String ERROR_COST_NEGATIVE = "Expected cost can't be negative.";
    public static final String ERROR_COST_INVALID = "Enter a dollar amount like 24.50.";
    public static final String ERROR_COST_DECIMALS =
            "Expected cost can have at most two decimal places";

    private RideValidator() {}

    public static ValidationResult validate(RideInput input, Timestamp now) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (input == null) {
            errors.put(FIELD_TITLE, ERROR_TITLE_REQUIRED);
            return new ValidationResult(errors);
        }

        if (isBlank(input.title)) {
            errors.put(FIELD_TITLE, ERROR_TITLE_REQUIRED);
        }

        boolean originValid = isValidPlace(input.origin);
        boolean destinationValid = isValidPlace(input.destination);
        if (!originValid) {
            errors.put(FIELD_ORIGIN, ERROR_ORIGIN_INVALID);
        }
        if (!destinationValid) {
            errors.put(FIELD_DESTINATION, ERROR_DESTINATION_INVALID);
        }
        if (originValid && destinationValid && samePlace(input.origin, input.destination)) {
            errors.put(FIELD_DESTINATION, ERROR_SAME_LOCATION);
        }

        if (input.departureTime == null) {
            errors.put(FIELD_DEPARTURE_TIME, ERROR_DEPARTURE_REQUIRED);
        } else if (now != null && input.departureTime.compareTo(now) <= 0) {
            errors.put(FIELD_DEPARTURE_TIME, ERROR_DEPARTURE_IN_PAST);
        }

        if (input.maxRiders == null
                || input.maxRiders < MIN_RIDERS
                || input.maxRiders > MAX_RIDERS) {
            errors.put(FIELD_MAX_RIDERS, ERROR_RIDERS_RANGE);
        }

        if (input.expectedCost == null) {
            errors.put(FIELD_EXPECTED_COST, ERROR_COST_REQUIRED);
        } else if (input.expectedCost == 0) {
            errors.put(FIELD_EXPECTED_COST, ERROR_COST_ZERO);
        } else if (input.expectedCost < 0) {
            errors.put(FIELD_EXPECTED_COST, ERROR_COST_NEGATIVE);
        } else if (Double.isNaN(input.expectedCost) || Double.isInfinite(input.expectedCost)) {
            errors.put(FIELD_EXPECTED_COST, ERROR_COST_INVALID);
        } else if (!hasAtMostTwoDecimals(input.expectedCost)) {
            errors.put(FIELD_EXPECTED_COST, ERROR_COST_DECIMALS);
        }

        return new ValidationResult(errors);
    }

    /** True for 12, 12.5 and 12.50; false for 12.505. */
    public static boolean hasAtMostTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return false;
        }
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 2;
    }

    private static boolean isValidPlace(PlaceInfo place) {
        return place != null && !isBlank(place.placeId) && !isBlank(place.address);
    }

    private static boolean samePlace(PlaceInfo a, PlaceInfo b) {
        return a.placeId.equals(b.placeId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
