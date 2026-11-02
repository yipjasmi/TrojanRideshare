package com.trojanrideshare.app.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyTest {
    @Test
    public void format_showsDollarsAndCents() {
        assertEquals("$0.00", Money.format(0));
        assertEquals("$0.05", Money.format(5));
        assertEquals("$12.50", Money.format(1250));
        assertEquals("$1,234.56", Money.format(123456));
        assertEquals("-$2.00", Money.format(-200));
    }

    @Test
    public void formatForInput_leavesOutSymbols() {
        assertEquals("1234.05", Money.formatForInput(123405));
    }

    @Test
    public void toCents_survivesTheRoundTripThroughDollarsForEveryAmount() {
        for (long cents = 0; cents <= 9_999_999; cents++) {
            assertEquals(cents, Money.toCents(Money.toDollars(cents)));
        }
    }
}
