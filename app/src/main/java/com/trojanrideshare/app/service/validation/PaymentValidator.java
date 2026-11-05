package com.trojanrideshare.app.service.validation;

import com.trojanrideshare.app.util.Money;

import java.math.BigDecimal;

public final class PaymentValidator {
    private static final BigDecimal MAXIMUM_DOLLARS = new BigDecimal("99999.99");

    public static final class Result {
        public final long cents;
        public final String error;

        private Result(long cents, String error) {
            this.cents = cents;
            this.error = error;
        }

        public boolean isValid() {
            return error == null;
        }
    }

    public static final class HandleResult {
        /** Null when the input was blank, which clears the field, and when it is invalid. */
        public final String handle;
        public final String error;

        private HandleResult(String handle, String error) {
            this.handle = handle;
            this.error = error;
        }

        public boolean isValid() {
            return error == null;
        }
    }

    private PaymentValidator() {}

    public static Result finalCost(String input) {
        Result result = parse(input, "Enter the final cost.");
        if (result.isValid() && result.cents == 0) {
            return invalid("Final cost can't be $0.00. Enter the actual amount, greater than zero.");
        }
        return result;
    }

    public static Result share(String input) {
        return parse(input, "Enter an amount.");
    }

    public static String splitError(long finalCents, long[] shares) {
        long assigned = 0;
        for (long share : shares) {
            assigned += share;
        }
        if (assigned == finalCents) {
            return null;
        }
        String gap = Money.format(Math.abs(finalCents - assigned));
        return assigned < finalCents
                ? "Shares are " + gap + " short of the final cost."
                : "Shares are " + gap + " over the final cost.";
    }

    public static HandleResult venmoUsername(String input) {
        String handle = withoutPrefix(input, '@');
        if (handle.isEmpty()) {
            return new HandleResult(null, null);
        }
        if (!handle.matches("[A-Za-z0-9_-]+")) {
            return invalidHandle("Use only letters, numbers, - and _ in a Venmo username.");
        }
        if (handle.length() < 5 || handle.length() > 30) {
            return invalidHandle("A Venmo username is 5 to 30 characters.");
        }
        return new HandleResult(handle, null);
    }

    public static HandleResult cashtag(String input) {
        String handle = withoutPrefix(input, '$');
        if (handle.isEmpty()) {
            return new HandleResult(null, null);
        }
        if (!handle.matches("[A-Za-z0-9_]+")) {
            return invalidHandle("Use only letters, numbers and _ in a cashtag.");
        }
        if (handle.length() > 20) {
            return invalidHandle("A cashtag is 20 characters or fewer.");
        }
        if (!handle.matches(".*[A-Za-z].*")) {
            return invalidHandle("A cashtag needs at least one letter.");
        }
        return new HandleResult(handle, null);
    }

    private static String withoutPrefix(String input, char prefix) {
        String text = input == null ? "" : input.trim();
        return !text.isEmpty() && text.charAt(0) == prefix ? text.substring(1) : text;
    }

    private static HandleResult invalidHandle(String error) {
        return new HandleResult(null, error);
    }

    private static Result parse(String input, String emptyMessage) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            return invalid(emptyMessage);
        }
        if (!text.matches("\\d*\\.?\\d*") || text.equals(".")) {
            return invalid("Enter a dollar amount like 24.50.");
        }
        int dot = text.indexOf('.');
        if (dot >= 0 && text.length() - dot - 1 > 2) {
            return invalid("Use no more than two decimal places.");
        }
        BigDecimal dollars = new BigDecimal(text);
        if (dollars.compareTo(MAXIMUM_DOLLARS) > 0) {
            return invalid("Amount cannot be more than " + Money.format(9_999_999) + ".");
        }
        return new Result(dollars.movePointRight(2).longValueExact(), null);
    }

    private static Result invalid(String error) {
        return new Result(0, error);
    }
}
