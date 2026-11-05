package com.trojanrideshare.app.service.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;

public class PaymentLinksTest {
    private static String note(String link) {
        return link.substring(link.indexOf("&note=") + "&note=".length());
    }

    @Test
    public void venmoPay_buildsVenmosDocumentedPaymentLink() {
        assertEquals(
                "https://venmo.com/ashley-lan?txn=pay&amount=12.50"
                        + "&note=TrojanRideshare%3A%20LAX%20run",
                PaymentLinks.venmoPay("ashley-lan", 1250, "LAX run")
        );
    }

    @Test
    public void venmoCharge_requestsInsteadOfPaying() {
        assertEquals(
                "https://venmo.com/kyle_t?txn=charge&amount=3.33"
                        + "&note=TrojanRideshare%3A%20LAX%20run",
                PaymentLinks.venmoCharge("kyle_t", 333, "LAX run")
        );
    }

    @Test
    public void venmoPay_formatsAmountsToTheCentWithoutGrouping() {
        assertEquals(
                "https://venmo.com/ashley-lan?txn=pay&amount=0.01&note=TrojanRideshare%20ride",
                PaymentLinks.venmoPay("ashley-lan", 1, null)
        );
        assertEquals(
                "https://venmo.com/ashley-lan?txn=pay&amount=3.33&note=TrojanRideshare%20ride",
                PaymentLinks.venmoPay("ashley-lan", 333, null)
        );
        assertEquals(
                "https://venmo.com/ashley-lan?txn=pay&amount=99999.99&note=TrojanRideshare%20ride",
                PaymentLinks.venmoPay("ashley-lan", 9_999_999, null)
        );
    }

    @Test
    public void venmoPay_encodesEverythingInTheTitleThatCouldBreakTheUrl() {
        String link = PaymentLinks.venmoPay(
                "ashley-lan",
                1250,
                "Tom & Jerry's \"LAX\" #1 run?txn=charge&amount=999"
        );

        assertEquals(
                "TrojanRideshare%3A%20Tom%20%26%20Jerry%27s%20%22LAX%22%20%231%20run"
                        + "%3Ftxn%3Dcharge%26amount%3D999",
                note(link)
        );
        assertEquals(3, link.split("&").length);
        assertFalse(link.contains("#"));
        assertFalse(link.contains(" "));
        assertFalse(link.contains("+"));
    }

    @Test
    public void venmoPay_encodesEmojiAsUtf8() {
        assertEquals(
                "TrojanRideshare%3A%20%F0%9F%9A%97%20to%20LAX",
                note(PaymentLinks.venmoPay("ashley-lan", 1250, "🚗 to LAX"))
        );
    }

    @Test
    public void venmoPay_keepsALiteralPlusApartFromASpace() {
        assertEquals(
                "TrojanRideshare%3A%201%2B1%20seats",
                note(PaymentLinks.venmoPay("ashley-lan", 1250, "1+1 seats"))
        );
    }

    @Test
    public void venmoPay_fallsBackWhenTheRideHasNoTitle() {
        for (String title : Arrays.asList(null, "", "   ")) {
            assertEquals(
                    "TrojanRideshare%20ride",
                    note(PaymentLinks.venmoPay("ashley-lan", 1250, title))
            );
        }
    }

    @Test
    public void note_capsALongTitleSoTheLinkStaysSmall() {
        StringBuilder title = new StringBuilder();
        StringBuilder kept = new StringBuilder();
        for (int index = 0; index < 5000; index++) {
            title.append('a');
            if (index < PaymentLinks.MAX_NOTE_TITLE_LENGTH) {
                kept.append('a');
            }
        }

        assertEquals("TrojanRideshare: " + kept + "…", PaymentLinks.note(title.toString()));
    }

    @Test
    public void note_dropsControlAndDirectionCharacters() {
        assertEquals(
                "TrojanRideshare: LAX evil run",
                PaymentLinks.note("LAX‮ evil\nrun")
        );
    }

    @Test
    public void venmoPay_dropsTheAtSignFromAStoredUsername() {
        assertEquals(
                "https://venmo.com/ashley-lan?txn=pay&amount=12.50&note=TrojanRideshare%20ride",
                PaymentLinks.venmoPay("@ashley-lan", 1250, null)
        );
    }

    @Test
    public void venmoPay_buildsNoLinkForAnInvalidStoredUsername() {
        for (String username : Arrays.asList(
                null,
                "",
                "abcd",
                "a234567890123456789012345678901",
                "evil.com/pay",
                "ashley/../../login",
                "ashley-lan?txn=charge",
                "ashley-lan&amount=999",
                "ashley-lan#",
                "ashley lan",
                "@@ashley-lan",
                "javascript:alert(1)"
        )) {
            assertNull(username, PaymentLinks.venmoPay(username, 1250, "LAX run"));
            assertNull(username, PaymentLinks.venmoCharge(username, 1250, "LAX run"));
        }
    }

    @Test
    public void venmoPay_buildsNoLinkWhenNothingIsOwed() {
        assertNull(PaymentLinks.venmoPay("ashley-lan", 0, "LAX run"));
        assertNull(PaymentLinks.venmoCharge("ashley-lan", -100, "LAX run"));
    }

    @Test
    public void cashApp_linksThePlainCashtag() {
        assertEquals("https://cash.app/$ashley", PaymentLinks.cashApp("ashley"));
        assertEquals("https://cash.app/$ashley_2", PaymentLinks.cashApp("$ashley_2"));
    }

    @Test
    public void cashApp_buildsNoLinkForAnInvalidStoredCashtag() {
        for (String cashtag : Arrays.asList(
                null,
                "",
                "12345",
                "a23456789012345678901",
                "ashley/evil",
                "ashley?amount=999",
                "ashley&x",
                "ashley-lan",
                "$$ashley"
        )) {
            assertNull(cashtag, PaymentLinks.cashApp(cashtag));
        }
    }
}
