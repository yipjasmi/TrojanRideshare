package com.trojanrideshare.app.model;

/**
 * A user's Venmo username and cashtag exactly as stored. Anyone can write anything into their own
 * profile, so these only reach a URL through PaymentLinks, which validates them first.
 */
public class PaymentHandles {
    public final String venmoUsername;
    public final String cashtag;

    public PaymentHandles(String venmoUsername, String cashtag) {
        this.venmoUsername = venmoUsername;
        this.cashtag = cashtag;
    }
}
