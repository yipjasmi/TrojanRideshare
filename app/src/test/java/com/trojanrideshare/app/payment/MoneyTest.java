package com.trojanrideshare.app.payment;

import static org.junit.Assert.assertEquals;

import com.trojanrideshare.app.util.Money;

import org.junit.Test;

public class MoneyTest {

    @Test
    public void formatsDollarsAndCents() {
        assertEquals("$0.00", Money.format(0));
        assertEquals("$0.05", Money.format(5));
        assertEquals("$12.50", Money.format(1250));
        assertEquals("$1,234.56", Money.format(123456));
        assertEquals("-$2.00", Money.format(-200));
    }

    @Test
    public void formatsForInputWithoutSymbols() {
        assertEquals("1234.05", Money.formatForInput(123405));
    }

    // Firestore stores dollars as doubles, so this is what keeps shares adding up after a read.
    @Test
    public void everyAmountSurvivesTheRoundTripThroughFirestoreDollars() {
        for (long cents = 0; cents <= 9_999_999; cents++) {
            assertEquals(cents, Money.toCents(Money.toDollars(cents)));
        }
    }
}
