package com.trojanrideshare.app.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.trojanrideshare.app.service.validation.PaymentValidator;

import org.junit.Test;

public class PaymentValidatorTest {

    private static void assertCost(String input, long cents) {
        PaymentValidator.Result result = PaymentValidator.finalCost(input);
        assertTrue(input + ": " + result.error, result.isValid());
        assertEquals(cents, result.cents);
    }

    private static void assertCostRejected(String input, String error) {
        PaymentValidator.Result result = PaymentValidator.finalCost(input);
        assertFalse(input, result.isValid());
        assertEquals(error, result.error);
    }

    @Test
    public void acceptsUpToTwoDecimalPlaces() {
        assertCost("24", 2400);
        assertCost("24.5", 2450);
        assertCost("24.50", 2450);
        assertCost("24.", 2400);
        assertCost(".75", 75);
        assertCost("0.01", 1);
        assertCost(" 24.50 ", 2450);
    }

    @Test
    public void floatingPointAmountsParseExactly() {
        assertCost("19.99", 1999);
        assertCost("1.10", 110);
        assertCost("99999.99", 9_999_999);
    }

    @Test
    public void finalCostMustBeGreaterThanZero() {
        assertCostRejected("0", "Final cost must be greater than $0");
        assertCostRejected("0.00", "Final cost must be greater than $0");
    }

    @Test
    public void finalCostIsRequired() {
        assertCostRejected("", "Enter the final cost");
        assertCostRejected("   ", "Enter the final cost");
        assertCostRejected(null, "Enter the final cost");
    }

    @Test
    public void rejectsMoreThanTwoDecimalPlaces() {
        assertCostRejected("24.505", "Use no more than two decimal places");
        assertCostRejected("24.500", "Use no more than two decimal places");
    }

    @Test
    public void rejectsAnythingThatIsNotAPlainAmount() {
        String error = "Enter a dollar amount like 24.50";
        assertCostRejected("-5", error);
        assertCostRejected("1e5", error);
        assertCostRejected("abc", error);
        assertCostRejected(".", error);
        assertCostRejected("1.2.3", error);
        assertCostRejected("1,000", error);
        assertCostRejected("NaN", error);
        assertCostRejected("٣", error);
    }

    @Test
    public void rejectsAmountsThatWouldOverflow() {
        assertCostRejected("100000", "Amount can't be more than $99,999.99");
        assertCostRejected("99999999999999999999999999", "Amount can't be more than $99,999.99");
    }

    @Test
    public void customShareMayBeZeroButNotBlank() {
        assertEquals(0, PaymentValidator.share("0").cents);
        assertTrue(PaymentValidator.share("0").isValid());
        assertEquals("Enter an amount", PaymentValidator.share("").error);
    }

    @Test
    public void customSharesMustAddUpExactly() {
        assertNull(PaymentValidator.splitError(1000, new long[] {500, 300, 200}));
        assertEquals("Shares are $0.01 short of the final cost",
                PaymentValidator.splitError(1000, new long[] {500, 300, 199}));
        assertEquals("Shares are $2.00 over the final cost",
                PaymentValidator.splitError(1000, new long[] {600, 300, 300}));
    }
}
