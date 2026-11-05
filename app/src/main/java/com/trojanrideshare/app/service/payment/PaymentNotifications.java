package com.trojanrideshare.app.service.payment;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.Notification;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.util.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PaymentNotifications {
    // Same text as search_untitled_ride, which the payment screens show, so change both.
    static final String UNTITLED_RIDE = "Untitled ride";

    private PaymentNotifications() {}

    static List<Notification> shareAssigned(
            Ride before,
            List<String> participants,
            long[] shares,
            Map<String, String> names
    ) {
        // Only saving a split sets finalCost, so a ride that already had one is being re-split.
        String change = before.finalCost == null ? " is " : " was updated to ";
        String organizer = nameOf(names, before.organizerId, "the organizer");
        List<Notification> notifications = new ArrayList<>();
        for (int index = 0; index < shares.length; index++) {
            String userId = participants.get(index);
            if (Objects.equals(userId, before.organizerId) || shares[index] <= 0) {
                continue;
            }
            notifications.add(notification(
                    userId,
                    Notification.TYPE_SHARE_ASSIGNED,
                    before.id,
                    "Your share of " + quoted(before.title) + change + Money.format(shares[index])
                            + ". Pay " + organizer + " and mark it paid."
            ));
        }
        return notifications;
    }

    static List<Notification> sharePaid(Payment before, Map<String, String> names) {
        if (before.paid || Objects.equals(before.userId, before.organizerId)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(notification(
                before.organizerId,
                Notification.TYPE_SHARE_PAID,
                before.rideId,
                nameOf(names, before.userId, "A rider") + " marked their "
                        + Money.format(Money.toCents(before.amount)) + " share of "
                        + quoted(before.rideTitle) + " as paid."
        ));
    }

    static List<Notification> shareFlagged(Payment before, Map<String, String> names) {
        if (before.flagged || Objects.equals(before.userId, before.organizerId)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(notification(
                before.userId,
                Notification.TYPE_SHARE_FLAGGED,
                before.rideId,
                nameOf(names, before.organizerId, "The organizer") + " flagged your unpaid "
                        + Money.format(Money.toCents(before.amount)) + " share of "
                        + quoted(before.rideTitle) + "."
        ));
    }

    private static Notification notification(
            String userId,
            String type,
            String rideId,
            String message
    ) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.rideId = rideId;
        notification.message = message;
        notification.read = false;
        notification.createdAt = Timestamp.now();
        return notification;
    }

    private static String quoted(String title) {
        return "\"" + (title == null || title.isEmpty() ? UNTITLED_RIDE : title) + "\"";
    }

    private static String nameOf(Map<String, String> names, String userId, String fallback) {
        String name = names.get(userId);
        return name == null || name.trim().isEmpty() ? fallback : name;
    }
}
