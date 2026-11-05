package com.trojanrideshare.app.service.payment;

import com.trojanrideshare.app.service.validation.PaymentValidator;
import com.trojanrideshare.app.util.Money;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Venmo and Cash App links. Handles come from other users' profiles, which the rules don't
 * check, so each one is validated here and an invalid one gets no link at all.
 */
public final class PaymentLinks {
    private PaymentLinks() {}

    public static String venmoPay(String username, long cents, String rideTitle) {
        return venmo(username, "pay", cents, rideTitle);
    }

    public static String venmoCharge(String username, long cents, String rideTitle) {
        return venmo(username, "charge", cents, rideTitle);
    }

    // Cash App only documents cash.app/$cashtag, so the amount goes on the button instead.
    public static String cashApp(String cashtag) {
        String handle = PaymentValidator.cashtag(cashtag).handle;
        return handle == null ? null : "https://cash.app/$" + handle;
    }

    private static String venmo(
            String username,
            String transaction,
            long cents,
            String rideTitle
    ) {
        String handle = PaymentValidator.venmoUsername(username).handle;
        if (handle == null || cents <= 0) {
            return null;
        }
        return "https://venmo.com/" + handle
                + "?txn=" + transaction
                + "&amount=" + Money.formatForInput(cents)
                + "&note=" + encode(note(rideTitle));
    }

    static String note(String rideTitle) {
        return rideTitle == null || rideTitle.trim().isEmpty()
                ? "TrojanRideshare ride"
                : "TrojanRideshare: " + rideTitle.trim();
    }

    static String encode(String text) {
        try {
            // URLEncoder does form encoding, which writes a space as "+". Not every parser
            // reads that back as a space, but every one reads %20.
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
