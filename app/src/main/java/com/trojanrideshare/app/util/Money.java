package com.trojanrideshare.app.util;

import java.util.Locale;

public final class Money {
    private Money() {}

    public static long toCents(double dollars) {
        return Math.round(dollars * 100);
    }

    public static double toDollars(long cents) {
        return cents / 100.0;
    }

    public static String format(long cents) {
        long absoluteCents = Math.abs(cents);
        String sign = cents < 0 ? "-" : "";
        return String.format(
                Locale.US,
                "%s$%,d.%02d",
                sign,
                absoluteCents / 100,
                absoluteCents % 100
        );
    }

    public static String formatForInput(long cents) {
        return String.format(Locale.US, "%d.%02d", cents / 100, cents % 100);
    }
}
