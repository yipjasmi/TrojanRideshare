package com.trojanrideshare.app.service.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

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
    public void finalCost_acceptsUpToTwoDecimalPlaces() {
        assertCost("24", 2400);
        assertCost("24.5", 2450);
        assertCost("24.50", 2450);
        assertCost("24.", 2400);
        assertCost(".75", 75);
        assertCost("0.01", 1);
        assertCost(" 24.50 ", 2450);
    }

    @Test
    public void finalCost_parsesFloatingPointAmountsExactly() {
        assertCost("19.99", 1999);
        assertCost("1.10", 110);
        assertCost("99999.99", 9_999_999);
    }

    @Test
    public void finalCost_mustBeGreaterThanZero() {
        assertCostRejected("0", "Final cost can't be $0.00. Enter the actual amount, greater than zero.");
        assertCostRejected("0.00", "Final cost can't be $0.00. Enter the actual amount, greater than zero.");
    }

    @Test
    public void finalCost_isRequired() {
        assertCostRejected("", "Enter the final cost.");
        assertCostRejected("   ", "Enter the final cost.");
        assertCostRejected(null, "Enter the final cost.");
    }

    @Test
    public void finalCost_rejectsMoreThanTwoDecimalPlaces() {
        assertCostRejected("24.505", "Use no more than two decimal places.");
        assertCostRejected("24.500", "Use no more than two decimal places.");
    }

    @Test
    public void finalCost_rejectsAnythingThatIsNotAPlainAmount() {
        String error = "Enter a dollar amount like 24.50.";
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
    public void finalCost_rejectsAmountsThatWouldOverflow() {
        assertCostRejected("100000", "Amount cannot be more than $99,999.99.");
        assertCostRejected(
                "99999999999999999999999999",
                "Amount cannot be more than $99,999.99."
        );
    }

    @Test
    public void share_mayBeZeroButNotBlank() {
        assertEquals(0, PaymentValidator.share("0").cents);
        assertTrue(PaymentValidator.share("0").isValid());
        assertEquals("Enter an amount.", PaymentValidator.share("").error);
    }

    private static void assertHandle(PaymentValidator.HandleResult result, String handle) {
        assertTrue(result.error, result.isValid());
        assertEquals(handle, result.handle);
    }

    private static void assertHandleRejected(PaymentValidator.HandleResult result, String error) {
        assertFalse(result.isValid());
        assertNull(result.handle);
        assertEquals(error, result.error);
    }

    @Test
    public void venmoUsername_stripsOneLeadingAtAndSpaces() {
        assertHandle(PaymentValidator.venmoUsername("@kyle-t_2"), "kyle-t_2");
        assertHandle(PaymentValidator.venmoUsername("  @Kyle-T  "), "Kyle-T");
        assertHandleRejected(
                PaymentValidator.venmoUsername("@@kyle-t"),
                "Use only letters, numbers, - and _ in a Venmo username."
        );
    }

    @Test
    public void venmoUsername_isFiveToThirtyCharacters() {
        String error = "A Venmo username is 5 to 30 characters.";
        assertHandle(PaymentValidator.venmoUsername("abcde"), "abcde");
        assertHandle(
                PaymentValidator.venmoUsername("a23456789012345678901234567890"),
                "a23456789012345678901234567890"
        );
        assertHandleRejected(PaymentValidator.venmoUsername("abcd"), error);
        assertHandleRejected(PaymentValidator.venmoUsername("@abcd"), error);
        assertHandleRejected(
                PaymentValidator.venmoUsername("a234567890123456789012345678901"),
                error
        );
    }

    @Test
    public void venmoUsername_rejectsAnythingThatCouldChangeTheUrl() {
        String error = "Use only letters, numbers, - and _ in a Venmo username.";
        for (String input : Arrays.asList(
                "kyle/t",
                "kyle?txn=charge",
                "kyle&amount=1",
                "kyle#t",
                "kyle.t",
                "kyle t",
                "kyle%2Ft",
                "kylé-t",
                "kyle-٣٣"
        )) {
            assertHandleRejected(PaymentValidator.venmoUsername(input), error);
        }
    }

    @Test
    public void venmoUsername_leftBlankClearsIt() {
        for (String input : Arrays.asList(null, "", "   ", "@")) {
            assertHandle(PaymentValidator.venmoUsername(input), null);
        }
    }

    @Test
    public void cashtag_stripsOneLeadingDollarAndSpaces() {
        assertHandle(PaymentValidator.cashtag("$kyle_2"), "kyle_2");
        assertHandle(PaymentValidator.cashtag(" $K "), "K");
        assertHandleRejected(
                PaymentValidator.cashtag("$$kyle"),
                "Use only letters, numbers and _ in a cashtag."
        );
    }

    @Test
    public void cashtag_isOneToTwentyCharacters() {
        assertHandle(PaymentValidator.cashtag("k"), "k");
        assertHandle(PaymentValidator.cashtag("k2345678901234567890"), "k2345678901234567890");
        assertHandleRejected(
                PaymentValidator.cashtag("k23456789012345678901"),
                "A cashtag is 20 characters or fewer."
        );
    }

    @Test
    public void cashtag_needsALetter() {
        String error = "A cashtag needs at least one letter.";
        assertHandleRejected(PaymentValidator.cashtag("12345"), error);
        assertHandleRejected(PaymentValidator.cashtag("$1_2"), error);
        assertHandle(PaymentValidator.cashtag("1a"), "1a");
    }

    @Test
    public void cashtag_rejectsAnythingThatCouldChangeTheUrl() {
        String error = "Use only letters, numbers and _ in a cashtag.";
        for (String input : Arrays.asList(
                "kyle/t",
                "kyle?amount=1",
                "kyle&t",
                "kyle#t",
                "kyle-t",
                "kyle t",
                "kylé"
        )) {
            assertHandleRejected(PaymentValidator.cashtag(input), error);
        }
    }

    @Test
    public void cashtag_leftBlankClearsIt() {
        for (String input : Arrays.asList(null, "", "  ", "$")) {
            assertHandle(PaymentValidator.cashtag(input), null);
        }
    }

    @Test
    public void splitError_requiresSharesToAddUpExactly() {
        assertNull(PaymentValidator.splitError(1000, new long[] {500, 300, 200}));
        assertEquals("Shares are $0.01 short of the final cost.",
                PaymentValidator.splitError(1000, new long[] {500, 300, 199}));
        assertEquals("Shares are $2.00 over the final cost.",
                PaymentValidator.splitError(1000, new long[] {600, 300, 300}));
    }
}
