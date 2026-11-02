package com.trojanrideshare.app.service.ride;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of validating a {@link RideInput}: a map of field key to a human-readable
 * error message. Empty means the input is valid.
 *
 * <p>The UI uses {@link #errorFor(String)} to paint the matching field red with its
 * message (SRS step 3.2). Field keys are the {@code FIELD_*} constants on
 * {@link RideValidator}.
 */
public class ValidationResult {
    private final Map<String, String> errors;

    public ValidationResult(Map<String, String> errors) {
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public String errorFor(String field) {
        return errors.get(field);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
