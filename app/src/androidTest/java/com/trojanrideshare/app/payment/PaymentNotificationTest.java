package com.trojanrideshare.app.payment;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.trojanrideshare.app.payment.PaymentEmulator.await;
import static com.trojanrideshare.app.payment.PaymentEmulator.firestore;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.payment.PaymentDataAccess;
import com.trojanrideshare.app.model.Notification;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.ui.notification.NotificationsActivity;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PaymentNotificationTest {
    private static final long TIMEOUT_MILLIS = 10_000;
    private static final String TITLE = "\"LAX run before finals\"";

    private PaymentEmulator emulator;
    private PaymentService paymentService;
    private final Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() throws Exception {
        emulator = PaymentEmulator.start();
        paymentService = new PaymentService();
    }

    @Test
    public void saveSplit_notifiesEachRiderWithAShareButNotTheOrganizer() throws Exception {
        String rideId = emulator.seedCompletedRide();

        await(saveSplit(rideId, new long[] {500, 500, 0}, 1000));

        waitForNotification(
                emulator.riderA,
                Notification.TYPE_SHARE_ASSIGNED,
                "Your share of " + TITLE + " is $5.00. Pay Ashley Lan and mark it paid."
        );
        assertTrue(notificationsFor(emulator.organizer).isEmpty());
        assertTrue(notificationsFor(emulator.riderB).isEmpty());
    }

    @Test
    public void saveSplit_tellsRidersWhenTheSplitIsUpdated() throws Exception {
        String rideId = emulator.seedCompletedRide();
        await(saveSplit(rideId, new long[] {334, 333, 333}, 1000));

        await(saveSplit(rideId, new long[] {400, 400, 400}, 1200));

        waitForNotification(
                emulator.riderB,
                Notification.TYPE_SHARE_ASSIGNED,
                "Your share of " + TITLE
                        + " was updated to $4.00. Pay Ashley Lan and mark it paid."
        );
    }

    @Test
    public void markPaid_notifiesTheOrganizer() throws Exception {
        String rideId = emulator.seedCompletedRide();
        await(saveSplit(rideId, new long[] {334, 333, 333}, 1000));
        emulator.signIn(emulator.riderA);

        await(markPaid(rideId, emulator.riderA));

        waitForNotification(
                emulator.organizer,
                Notification.TYPE_SHARE_PAID,
                "Kyle Macasilli-Tan marked their $3.33 share of " + TITLE + " as paid."
        );
    }

    @Test
    public void flag_notifiesTheRider() throws Exception {
        String rideId = emulator.seedCompletedRide();
        await(saveSplit(rideId, new long[] {334, 333, 333}, 1000));
        emulator.backdateShare(PaymentDataAccess.paymentDocumentId(rideId, emulator.riderA), 8);

        await(flag(rideId, emulator.riderA));

        waitForNotification(
                emulator.riderA,
                Notification.TYPE_SHARE_FLAGGED,
                "Ashley Lan flagged your unpaid $3.33 share of " + TITLE + "."
        );
    }

    @Test
    public void notifications_opensTheRidesPaymentsOnTap() throws Exception {
        String rideId = emulator.seedCompletedRide();
        await(saveSplit(rideId, new long[] {334, 333, 333}, 1000));
        String message = "Your share of " + TITLE + " is $3.33. Pay Ashley Lan and mark it paid.";
        waitForNotification(emulator.riderA, Notification.TYPE_SHARE_ASSIGNED, message);
        emulator.signIn(emulator.riderA);
        Intent feed = new Intent(context, NotificationsActivity.class);
        feed.putExtra(NotificationsActivity.EXTRA_CURRENT_USER_ID, emulator.riderA);

        try (ActivityScenario<NotificationsActivity> screen = ActivityScenario.launch(feed)) {
            waitFor(withText(message));
            onView(withText(message)).perform(click());
            waitFor(withText("Your share"));
        }
    }

    @Test
    public void notifications_ignoresATapOnABadRideId() throws Exception {
        emulator.signIn(emulator.riderA);
        Notification planted = new Notification();
        planted.userId = emulator.riderA;
        planted.type = Notification.TYPE_SHARE_FLAGGED;
        planted.rideId = "bad/id";
        planted.message = "Planted by another student";
        planted.createdAt = Timestamp.now();
        await(firestore().collection("notifications").add(planted));
        Intent feed = new Intent(context, NotificationsActivity.class);
        feed.putExtra(NotificationsActivity.EXTRA_CURRENT_USER_ID, emulator.riderA);

        try (ActivityScenario<NotificationsActivity> screen = ActivityScenario.launch(feed)) {
            waitFor(withText(planted.message));
            onView(withText(planted.message)).perform(click());
            onView(withText(planted.message)).check(matches(isDisplayed()));
        }
    }

    private Task<Void> saveSplit(String rideId, long[] shares, long finalCents) {
        TaskCompletionSource<Void> done = new TaskCompletionSource<>();
        paymentService.saveSplit(
                rideId,
                emulator.organizer,
                Arrays.asList(emulator.organizer, emulator.riderA, emulator.riderB),
                shares,
                finalCents,
                completing(done)
        );
        return done.getTask();
    }

    private Task<Void> markPaid(String rideId, String userId) {
        TaskCompletionSource<Void> done = new TaskCompletionSource<>();
        paymentService.markPaid(rideId, userId, completing(done));
        return done.getTask();
    }

    private Task<Void> flag(String rideId, String riderId) {
        TaskCompletionSource<Void> done = new TaskCompletionSource<>();
        paymentService.flag(rideId, riderId, completing(done));
        return done.getTask();
    }

    private static PaymentService.UpdateCallback completing(TaskCompletionSource<Void> done) {
        return new PaymentService.UpdateCallback() {
            @Override
            public void onSuccess() {
                done.setResult(null);
            }

            @Override
            public void onError(String message) {
                done.setException(new IllegalStateException(message));
            }
        };
    }

    private static List<Notification> notificationsFor(String userId) throws Exception {
        return await(firestore().collection("notifications").whereEqualTo("userId", userId).get())
                .toObjects(Notification.class);
    }

    // The service reports the payment before it sends, so a notification lands a moment later.
    private static void waitForNotification(String userId, String type, String message)
            throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS;
        while (true) {
            for (Notification notification : notificationsFor(userId)) {
                if (type.equals(notification.type) && message.equals(notification.message)) {
                    return;
                }
            }
            assertTrue(
                    "no " + type + " notification for " + userId + ": " + message,
                    SystemClock.uptimeMillis() < deadline
            );
            SystemClock.sleep(100);
        }
    }

    private static void waitFor(Matcher<View> view) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS;
        while (true) {
            try {
                onView(view).check(matches(isDisplayed()));
                return;
            } catch (Throwable notYet) {
                if (SystemClock.uptimeMillis() > deadline) {
                    throw notYet;
                }
                SystemClock.sleep(100);
            }
        }
    }
}
