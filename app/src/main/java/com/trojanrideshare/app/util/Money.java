package com.trojanrideshare.app.util;

import java.util.Locale;

// Amounts are whole cents everywhere except the Firestore fields, which stay in dollars.
public final class Money {
    private Money() {}

    public static long toCents(double dollars) {
        return Math.round(dollars * 100);
    }

    public static double toDollars(long cents) {
        return cents / 100.0;
    }

    public static String format(long cents) {
        long abs = Math.abs(cents);
        String sign = cents < 0 ? "-" : "";
        return String.format(Locale.US, "%s$%,d.%02d", sign, abs / 100, abs % 100);
    }

    public static String formatForInput(long cents) {
        return String.format(Locale.US, "%d.%02d", cents / 100, cents % 100);
    }
}
