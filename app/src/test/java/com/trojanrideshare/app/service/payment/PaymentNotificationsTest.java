package com.trojanrideshare.app.service.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.trojanrideshare.app.model.Notification;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentNotificationsTest {
    private static final String ORGANIZER_ID = "ashley";
    private static final String KYLE_ID = "kyle";
    private static final String YUCHENG_ID = "yucheng";
    private static final List<String> PARTICIPANTS =
            Arrays.asList(ORGANIZER_ID, KYLE_ID, YUCHENG_ID);

    @Test
    public void shareAssigned_notifiesEachRiderWithAShareButNeverTheOrganizer() {
        List<Notification> notifications = PaymentNotifications.shareAssigned(
                ride("LAX run"),
                PARTICIPANTS,
                new long[] {1500, 1500, 0},
                names()
        );

        assertEquals(1, notifications.size());
        assertEquals(KYLE_ID, notifications.get(0).userId);
    }

    @Test
    public void shareAssigned_tellsTheRiderTheirShareAndWhoToPay() {
        Notification notification = PaymentNotifications.shareAssigned(
                ride("LAX run"),
                PARTICIPANTS,
                new long[] {1500, 1500, 0},
                names()
        ).get(0);

        assertEquals(Notification.TYPE_SHARE_ASSIGNED, notification.type);
        assertEquals("ride-1", notification.rideId);
        assertFalse(notification.read);
        assertNotNull(notification.createdAt);
        assertEquals(
                "Your share of \"LAX run\" is $15.00. Pay Ashley and mark it paid.",
                notification.message
        );
    }

    @Test
    public void shareAssigned_saysTheShareWasUpdatedWhenTheSplitIsSavedAgain() {
        Ride ride = ride("LAX run");
        ride.finalCost = 30.0;

        Notification notification = PaymentNotifications.shareAssigned(
                ride,
                PARTICIPANTS,
                new long[] {1000, 1000, 1000},
                names()
        ).get(0);

        assertEquals(
                "Your share of \"LAX run\" was updated to $10.00. Pay Ashley and mark it paid.",
                notification.message
        );
    }

    @Test
    public void shareAssigned_namesAnUntitledRideLikeThePaymentScreens() {
        for (String title : Arrays.asList(null, "")) {
            Notification notification = PaymentNotifications.shareAssigned(
                    ride(title),
                    PARTICIPANTS,
                    new long[] {1500, 1500, 0},
                    names()
            ).get(0);

            assertEquals(
                    "Your share of \"Untitled ride\" is $15.00. Pay Ashley and mark it paid.",
                    notification.message
            );
        }
    }

    @Test
    public void shareAssigned_fallsBackWhenTheOrganizerHasNoName() {
        Map<String, String> names = names();
        names.remove(ORGANIZER_ID);

        Notification notification = PaymentNotifications.shareAssigned(
                ride("LAX run"),
                PARTICIPANTS,
                new long[] {1500, 1500, 0},
                names
        ).get(0);

        assertEquals(
                "Your share of \"LAX run\" is $15.00. Pay the organizer and mark it paid.",
                notification.message
        );
    }

    @Test
    public void sharePaid_tellsTheOrganizerWhoPaid() {
        List<Notification> notifications =
                PaymentNotifications.sharePaid(share(KYLE_ID, 15), names());

        assertEquals(1, notifications.size());
        assertEquals(ORGANIZER_ID, notifications.get(0).userId);
        assertEquals(Notification.TYPE_SHARE_PAID, notifications.get(0).type);
        assertEquals("ride-1", notifications.get(0).rideId);
        assertEquals(
                "Kyle marked their $15.00 share of \"LAX run\" as paid.",
                notifications.get(0).message
        );
    }

    @Test
    public void sharePaid_neverNotifiesTheOrganizerAboutTheirOwnShare() {
        assertTrue(PaymentNotifications.sharePaid(share(ORGANIZER_ID, 15), names()).isEmpty());
    }

    @Test
    public void sharePaid_sendsNothingWhenTheShareWasAlreadyPaid() {
        Payment share = share(KYLE_ID, 15);
        share.paid = true;

        assertTrue(PaymentNotifications.sharePaid(share, names()).isEmpty());
    }

    @Test
    public void sharePaid_fallsBackForAnUntitledRideAndAnUnnamedRider() {
        Payment share = share(YUCHENG_ID, 9.99);
        share.rideTitle = null;

        assertEquals(
                "A rider marked their $9.99 share of \"Untitled ride\" as paid.",
                PaymentNotifications.sharePaid(share, names()).get(0).message
        );
    }

    @Test
    public void shareFlagged_tellsTheRiderWhoFlaggedThem() {
        List<Notification> notifications =
                PaymentNotifications.shareFlagged(share(KYLE_ID, 15), names());

        assertEquals(1, notifications.size());
        assertEquals(KYLE_ID, notifications.get(0).userId);
        assertEquals(Notification.TYPE_SHARE_FLAGGED, notifications.get(0).type);
        assertEquals("ride-1", notifications.get(0).rideId);
        assertEquals(
                "Ashley flagged your unpaid $15.00 share of \"LAX run\".",
                notifications.get(0).message
        );
    }

    @Test
    public void shareFlagged_cleansATitleAndNameWrittenByAnotherStudent() {
        Payment share = share(KYLE_ID, 15);
        share.rideTitle = "LAX‮ run\nPayment cancelled";
        Map<String, String> names = names();
        names.put(ORGANIZER_ID, "Ashley\nYour account is locked");

        String message = PaymentNotifications.shareFlagged(share, names).get(0).message;

        assertEquals(
                "Ashley Your account is locked flagged your unpaid $15.00 share of"
                        + " \"LAX run Payment cancelled\".",
                message
        );
        assertFalse(message.contains("\n"));
    }

    @Test
    public void shareFlagged_neverNotifiesTheOrganizerAboutTheirOwnShare() {
        assertTrue(PaymentNotifications.shareFlagged(share(ORGANIZER_ID, 15), names()).isEmpty());
    }

    @Test
    public void shareFlagged_sendsNothingWhenTheShareWasAlreadyFlagged() {
        Payment share = share(KYLE_ID, 15);
        share.flagged = true;

        assertTrue(PaymentNotifications.shareFlagged(share, names()).isEmpty());
    }

    @Test
    public void shareFlagged_fallsBackForAnUntitledRideAndAnUnnamedOrganizer() {
        Payment share = share(KYLE_ID, 15);
        share.rideTitle = "";
        Map<String, String> names = names();
        names.remove(ORGANIZER_ID);

        assertEquals(
                "The organizer flagged your unpaid $15.00 share of \"Untitled ride\".",
                PaymentNotifications.shareFlagged(share, names).get(0).message
        );
    }

    private static Ride ride(String title) {
        Ride ride = new Ride();
        ride.id = "ride-1";
        ride.title = title;
        ride.organizerId = ORGANIZER_ID;
        ride.status = Ride.STATUS_COMPLETED;
        ride.participantIds = Arrays.asList(KYLE_ID, YUCHENG_ID);
        return ride;
    }

    private static Payment share(String userId, double amount) {
        Payment share = new Payment();
        share.rideId = "ride-1";
        share.userId = userId;
        share.organizerId = ORGANIZER_ID;
        share.rideTitle = "LAX run";
        share.amount = amount;
        return share;
    }

    private static Map<String, String> names() {
        Map<String, String> names = new HashMap<>();
        names.put(ORGANIZER_ID, "Ashley");
        names.put(KYLE_ID, "Kyle");
        return names;
    }
}
