package com.trojanrideshare.app.payment;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withChild;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.trojanrideshare.app.payment.PaymentEmulator.await;
import static com.trojanrideshare.app.payment.PaymentEmulator.firestore;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.firestore.DocumentSnapshot;
import com.trojanrideshare.app.MainActivity;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.data.payment.PaymentDataAccess;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.ui.payment.PaymentsActivity;
import com.trojanrideshare.app.ui.payment.RecordCostActivity;
import com.trojanrideshare.app.ui.payment.RidePaymentActivity;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class PaymentFlowTest {
    private static final long TIMEOUT_MILLIS = 10_000;

    private PaymentEmulator emulator;
    private final Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() throws Exception {
        emulator = PaymentEmulator.start();
    }

    private static void waitFor(Matcher<View> view) {
        waitFor(view, TIMEOUT_MILLIS);
    }

    private static void waitFor(Matcher<View> view, long timeoutMillis) {
        long deadline = SystemClock.uptimeMillis() + timeoutMillis;
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

    private static Matcher<View> shareRow(String name, String text) {
        return allOf(withId(R.id.shareName), withText(name),
                withParent(withParent(hasDescendant(withText(containsString(text))))));
    }

    private static Matcher<View> splitAmount(String name) {
        return allOf(withId(R.id.splitEvenAmount), withParent(withChild(withText(name))));
    }

    private static Matcher<View> customInput(String name) {
        return allOf(
                withId(R.id.splitCustomInput),
                isDescendantOfA(allOf(
                        withId(R.id.splitCustomLayout),
                        withParent(withChild(withText(name)))
                ))
        );
    }

    private static void enterCost(String cost) {
        waitFor(withId(R.id.costInput));
        onView(withId(R.id.costInput)).perform(replaceText(cost), closeSoftKeyboard());
    }

    private static void enterCustomAmount(String name, String amount) {
        onView(customInput(name)).perform(replaceText(amount), closeSoftKeyboard());
    }

    private void reviewAndSave() {
        onView(withId(R.id.reviewButton)).perform(click());
        onView(withText("Save split")).inRoot(isDialog()).perform(click());
    }

    private ActivityScenario<RecordCostActivity> openRecordCost(String rideId) {
        Intent costIntent = new Intent(context, RecordCostActivity.class);
        costIntent.putExtra(RecordCostActivity.EXTRA_RIDE_ID, rideId);
        return ActivityScenario.launch(costIntent);
    }

    private ActivityScenario<RidePaymentActivity> openRidePayment(String rideId) {
        Intent rideIntent = new Intent(context, RidePaymentActivity.class);
        rideIntent.putExtra(RidePaymentActivity.EXTRA_RIDE_ID, rideId);
        return ActivityScenario.launch(rideIntent);
    }

    private String splitRide() throws Exception {
        String rideId = emulator.seedCompletedRide();
        await(PaymentEmulator.saveSplit(
                rideId,
                emulator.organizer,
                Arrays.asList(emulator.organizer, emulator.riderA, emulator.riderB),
                new long[] {334, 333, 333},
                1000
        ));
        return rideId;
    }

    private double finalCost(String rideId) throws Exception {
        return await(firestore().collection("rides").document(rideId).get())
                .getDouble("finalCost");
    }

    private DocumentSnapshot share(String rideId, String userId) throws Exception {
        return await(firestore()
                .collection("payments")
                .document(PaymentDataAccess.paymentDocumentId(rideId, userId))
                .get());
    }

    @Test
    public void recordCost_previewsAnEvenSplitThenSaves() throws Exception {
        String rideId = emulator.seedCompletedRide();

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("10");
            onView(splitAmount("You")).check(matches(withText("$3.34")));
            onView(splitAmount("Kyle Macasilli-Tan")).check(matches(withText("$3.33")));
            onView(splitAmount("Yucheng Zhao")).check(matches(withText("$3.33")));

            onView(withId(R.id.reviewButton)).perform(click());
            onView(withText(containsString("Kyle Macasilli-Tan: $3.33"))).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(containsString("You: $3.34"))).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(containsString("Final cost: $10.00"))).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            assertFalse(share(rideId, emulator.riderA).exists());
            onView(withText("Save split")).inRoot(isDialog()).perform(click());

            waitFor(withText("Still owed to you"));
            onView(withId(R.id.amountValue)).check(matches(withText("$6.66")));
        }

        assertEquals(3.33, share(rideId, emulator.riderA).getDouble("amount"), 0);
        assertEquals(10.0, finalCost(rideId), 0);
    }

    @Test
    public void recordCost_blocksACustomSplitThatDoesNotAddUp() throws Exception {
        String rideId = emulator.seedCompletedRide();

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("48.01");
            onView(withId(R.id.splitCustomButton)).perform(click());
            onView(customInput("You")).check(matches(withText("16.01")));

            enterCustomAmount("Kyle Macasilli-Tan", "20");
            onView(withId(R.id.splitSummary)).check(matches(withText(containsString("over"))));
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withId(R.id.formError))
                    .check(matches(withText("Shares are $4.00 over the final cost.")));
            assertFalse(share(rideId, emulator.riderA).exists());

            enterCustomAmount("Kyle Macasilli-Tan", "");
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withText("Enter an amount.")).check(matches(isDisplayed()));
            assertFalse(share(rideId, emulator.riderA).exists());

            enterCustomAmount("Kyle Macasilli-Tan", "16.00");
            enterCustomAmount("Yucheng Zhao", "16.00");
            onView(withId(R.id.splitSummary)).check(matches(withText(containsString("matches"))));
            reviewAndSave();
            waitFor(withText("Still owed to you"));
        }

        assertEquals(16.0, share(rideId, emulator.riderA).getDouble("amount"), 0);
        assertEquals(16.01, share(rideId, emulator.organizer).getDouble("amount"), 0);
    }

    @Test
    public void recordCost_keepsCustomAmountsAcrossARotation() throws Exception {
        String rideId = emulator.seedCompletedRide();

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("30");
            onView(withId(R.id.splitCustomButton)).perform(click());
            enterCustomAmount("Kyle Macasilli-Tan", "12.34");

            screen.recreate();

            waitFor(customInput("Kyle Macasilli-Tan"));
            onView(customInput("Kyle Macasilli-Tan")).check(matches(withText("12.34")));
            onView(withId(R.id.costInput)).check(matches(withText("30")));
        }
    }

    @Test
    public void recordCost_reportsOfflineSavesAndWritesNothing() throws Exception {
        String rideId = emulator.seedCompletedRide();

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("10");
            PaymentEmulator.setAirplaneMode(true);
            reviewAndSave();
            waitFor(allOf(
                    withId(R.id.formError),
                    withText("Unable to save this split. You are offline. Connect and try again.")
            ), 90_000);
        } finally {
            PaymentEmulator.setAirplaneMode(false);
        }

        assertFalse(share(rideId, emulator.riderA).exists());
    }

    @Test
    public void paymentsList_treatsOfflineAsAFailureNotAnEmptyList() throws Exception {
        emulator.seedCompletedRide();
        splitRide();
        Intent list = new Intent(context, PaymentsActivity.class);
        await(firestore().disableNetwork());

        try (ActivityScenario<PaymentsActivity> screen = ActivityScenario.launch(list)) {
            waitFor(withText(containsString("Unable to load your rides.")));
            waitFor(withText(containsString("Unable to load your shares.")));
            onView(withText(containsString("No ride costs yet")))
                    .check(matches(not(isDisplayed())));

            await(firestore().enableNetwork());
            onView(allOf(withId(R.id.sectionRetry), isDescendantOfA(withId(R.id.ridesSection))))
                    .perform(click());
            waitFor(withText("0 of 2 riders paid"));
        } finally {
            await(firestore().enableNetwork());
        }
    }

    @Test
    public void ridePayment_doesNotTellAnOfflineRiderThereIsNoCost() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);
        await(firestore().collection("rides").document(rideId).get());
        await(firestore().disableNetwork());

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(withText(containsString("You are offline. Connect and try again.")));
            onView(withId(R.id.amountValue)).check(matches(not(isDisplayed())));
        } finally {
            await(firestore().enableNetwork());
        }
    }

    @Test
    public void recordCost_showsFieldErrorsForBadCosts() throws Exception {
        String rideId = emulator.seedCompletedRide();

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("0");
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withText("Final cost can't be $0.00. Enter the actual amount, greater than zero."))
                    .check(matches(isDisplayed()));

            enterCost("12.345");
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withText("Use no more than two decimal places.")).check(matches(isDisplayed()));

            enterCost("");
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withText("Enter the final cost.")).check(matches(isDisplayed()));
        }

        assertFalse(share(rideId, emulator.riderA).exists());
    }

    @Test
    public void recordCost_blocksEveryoneButTheOrganizer() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.riderA);

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            waitFor(withText("Only the ride organizer can record the final cost."));
            onView(withId(R.id.reviewButton)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void recordCost_blocksARideThatIsNotCompleted() throws Exception {
        String rideId = emulator.seedRide(Ride.STATUS_CANCELLED);

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            waitFor(withText("Mark the ride completed before recording the final cost."));
        }
    }

    @Test
    public void ridePayment_letsARiderMarkTheirSharePaid() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(withText("Your share"));
            onView(withId(R.id.amountValue)).check(matches(withText("$3.33")));
            onView(withId(R.id.amountDetail)).check(matches(withText("of the $10.00 final cost")));
            onView(withId(R.id.statusText)).check(matches(withText("Unpaid")));
            onView(withText("Participants")).check(matches(not(isDisplayed())));

            onView(withId(R.id.primaryAction)).perform(click());
            onView(withText("Not yet")).inRoot(isDialog()).perform(click());
            assertFalse(share(rideId, emulator.riderA).getBoolean("paid"));

            onView(withId(R.id.primaryAction)).perform(click());
            onView(withText("Mark as paid")).inRoot(isDialog()).perform(click());
            waitFor(allOf(withId(R.id.statusText), withText(containsString("Paid "))));
            onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())));
        }

        assertTrue(share(rideId, emulator.riderA).getBoolean("paid"));
    }

    @Test
    public void ridePayment_updatesTheOrganizerLive() throws Exception {
        String rideId = splitRide();

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(shareRow("You", "Paid the fare"));
            waitFor(shareRow("Kyle Macasilli-Tan", "Unpaid"));
            waitFor(shareRow("Yucheng Zhao", "Unpaid"));
            onView(withId(R.id.amountValue)).check(matches(withText("$6.66")));
            onView(withId(R.id.primaryAction)).check(matches(withText("Edit split")));

            emulator.markPaidFromAnotherDevice(
                    PaymentDataAccess.paymentDocumentId(rideId, emulator.riderA)
            );

            waitFor(shareRow("Kyle Macasilli-Tan", "Paid "));
            waitFor(shareRow("Yucheng Zhao", "Unpaid"));
            onView(withId(R.id.amountValue)).check(matches(withText("$3.33")));
            onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())));
            onView(withId(R.id.footnote)).check(matches(withText(containsString("locked"))));
        }
    }

    @Test
    public void ridePayment_letsTheOrganizerEditUntilARiderPays() throws Exception {
        String rideId = splitRide();

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(allOf(withId(R.id.primaryAction), withText("Edit split")));
            onView(withId(R.id.primaryAction)).perform(click());
            waitFor(allOf(withId(R.id.costInput), withText("10.00")));
            enterCost("13");
            reviewAndSave();

            waitFor(allOf(withId(R.id.amountValue), withText("$8.66")));
            screen.onActivity(activity -> assertFalse(activity.isFinishing()));
        }

        waitForShareAmount(rideId, 4.33);
        assertEquals(13.0, finalCost(rideId), 0);

        emulator.signIn(emulator.riderA);
        await(PaymentEmulator.markPaid(rideId, emulator.riderA));
        emulator.signIn(emulator.organizer);
        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            waitFor(withText("A rider already marked their share paid, so this split is locked."));
        }
    }

    private void waitForShareAmount(String rideId, double amount) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS;
        while (share(rideId, emulator.riderA).getDouble("amount") != amount) {
            assertTrue("share never reached " + amount, SystemClock.uptimeMillis() < deadline);
            SystemClock.sleep(100);
        }
    }

    @Test
    public void ridePayment_survivesBeingRecreatedWhileItLoads() throws Exception {
        String rideId = splitRide();

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            screen.recreate();
            screen.recreate();
            waitFor(shareRow("Kyle Macasilli-Tan", "Unpaid"));
        }
    }

    @Test
    public void ridePayment_survivesBadParticipantIds() throws Exception {
        String rideId = emulator.seedRide(
                Ride.STATUS_COMPLETED,
                Arrays.asList(emulator.riderA, "bad/id", "", null)
        );

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(withText("No final cost yet"));
        }
    }

    @Test
    public void recordCost_survivesBadParticipantIds() throws Exception {
        String rideId = emulator.seedRide(
                Ride.STATUS_COMPLETED,
                Arrays.asList(emulator.riderA, "bad/id", "", null)
        );

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("10");
            onView(splitAmount("You")).check(matches(withText("$5.00")));
            onView(splitAmount("Kyle Macasilli-Tan")).check(matches(withText("$5.00")));
            reviewAndSave();
            waitFor(withText("Still owed to you"));
        }
    }

    @Test
    public void ridePayment_showsFlagOnlyOnceOverdue() throws Exception {
        String rideId = splitRide();

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(shareRow("Kyle Macasilli-Tan", "Unpaid"));
            onView(allOf(withText("Flag"), isDisplayed())).check(doesNotExist());
        }

        emulator.backdateShare(
                PaymentDataAccess.paymentDocumentId(rideId, emulator.riderA),
                8
        );

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(shareRow("Kyle Macasilli-Tan", "Overdue"));
            onView(allOf(withText("Flag"), isDisplayed())).perform(scrollTo(), click());
            onView(withText("Flag Kyle Macasilli-Tan")).inRoot(isDialog()).perform(click());
            waitFor(shareRow("Kyle Macasilli-Tan", "Flagged"));
        }

        emulator.signIn(emulator.riderA);
        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(allOf(withId(R.id.statusText), withText(containsString("Flagged"))));
        }
    }

    @Test
    public void paymentsList_showsWhatIsOwedOnBothSides() throws Exception {
        emulator.seedCompletedRide();
        splitRide();
        Intent list = new Intent(context, PaymentsActivity.class);

        try (ActivityScenario<PaymentsActivity> screen = ActivityScenario.launch(list)) {
            waitFor(withText("Rides you organized"));
            waitFor(withText("Needs final cost"));
            waitFor(withText("0 of 2 riders paid"));
            onView(withText("Your shares")).check(matches(not(isDisplayed())));
        }

        emulator.signIn(emulator.riderA);
        try (ActivityScenario<PaymentsActivity> screen = ActivityScenario.launch(list)) {
            waitFor(withText("Your shares"));
            waitFor(withText("$3.33"));
            waitFor(withText("Unpaid"));
            onView(withText("Rides you organized")).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void paymentsList_explainsItselfWhenEmpty() throws Exception {
        emulator.signIn(emulator.stranger);
        Intent list = new Intent(context, PaymentsActivity.class);

        try (ActivityScenario<PaymentsActivity> screen = ActivityScenario.launch(list)) {
            waitFor(withText(containsString("No ride costs yet")));
        }
    }

    @Test
    public void home_opensPaymentsFromTheMoneyStrip() throws Exception {
        emulator.signIn(emulator.stranger);
        Intent home = new Intent(context, MainActivity.class);

        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(home)) {
            onView(withId(R.id.moneyCard)).perform(scrollTo());
            waitFor(allOf(withId(R.id.moneyYouOwe), withText("$0.00")));
            onView(withId(R.id.moneyOwedToYou)).check(matches(withText("$0.00")));
            onView(withId(R.id.moneyCard)).perform(click());
            waitFor(withText(containsString("No ride costs yet")));
            pressBack();
        }
    }
}
