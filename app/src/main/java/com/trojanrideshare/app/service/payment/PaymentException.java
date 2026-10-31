package com.trojanrideshare.app.service.payment;

// A payment rule stopped the action. The message is written for the person using the app.
public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }
}
