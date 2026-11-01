package com.trojanrideshare.app.payment;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.PositionAssertions.isCompletelyBelow;
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
import static com.trojanrideshare.app.payment.PaymentEmulator.db;
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
import com.trojanrideshare.app.data.payment.PaymentRepository;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.ui.payment.PaymentsActivity;
import com.trojanrideshare.app.ui.payment.RecordCostActivity;
import com.trojanrideshare.app.ui.payment.RidePaymentActivity;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

// Runs the real screens against the emulator with firestore.rules loaded.
@RunWith(AndroidJUnit4.class)
public class PaymentFlowTest {
    private static final long TIMEOUT_MILLIS = 10_000;

    private PaymentEmulator emulator;
    private final PaymentService service = new PaymentService();
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
        return allOf(withId(R.id.splitCustomInput),
                isDescendantOfA(allOf(withId(R.id.splitCustomLayout), withParent(withChild(withText(name))))));
    }

    private static void enterCost(String cost) {
        waitFor(withId(R.id.costInput));
        onView(withId(R.id.costInput)).perform(replaceText(cost), closeSoftKeyboard());
    }

    private void reviewAndSave() {
        onView(withId(R.id.reviewButton)).perform(click());
        onView(withText("Save split")).inRoot(isDialog()).perform(click());
    }

    private ActivityScenario<RecordCostActivity> openRecordCost(String rideId) {
        return ActivityScenario.launch(RecordCostActivity.intent(context, rideId));
    }

    private ActivityScenario<RidePaymentActivity> openRidePayment(String rideId) {
        return ActivityScenario.launch(RidePaymentActivity.intent(context, rideId));
    }

    private String splitRide() throws Exception {
        String rideId = emulator.seedCompletedRide();
        await(service.saveSplit(rideId, emulator.organizer,
                Arrays.asList(emulator.organizer, emulator.riderA, emulator.riderB),
                new long[] {334, 333, 333}, 1000));
        return rideId;
    }

    private DocumentSnapshot share(String rideId, String userId) throws Exception {
        return await(db().collection("payments")
                .document(PaymentRepository.paymentId(rideId, userId)).get());
    }

    @Test
    public void evenSplitPreviewsEachShareThenSaves() throws Exception {
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
        assertEquals(10.0, await(db().collection("rides").document(rideId).get())
                .getDouble("finalCost"), 0);
    }

    @Test
    public void customSplitMustAddUpBeforeItCanBeSaved() throws Exception {
        String rideId = emulator.seedCompletedRide();

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("48.01");
            onView(withId(R.id.splitCustomButton)).perform(click());
            onView(customInput("You")).check(matches(withText("16.01")));

            onView(customInput("Kyle Macasilli-Tan")).perform(replaceText("20"), closeSoftKeyboard());
            onView(withId(R.id.splitSummary)).check(matches(withText(containsString("over"))));
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withId(R.id.formError))
                    .check(matches(withText("Shares are $4.00 over the final cost")));
            assertFalse(share(rideId, emulator.riderA).exists());

            onView(customInput("Kyle Macasilli-Tan")).perform(replaceText(""), closeSoftKeyboard());
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withText("Enter an amount")).check(matches(isDisplayed()));
            assertFalse(share(rideId, emulator.riderA).exists());

            onView(customInput("Kyle Macasilli-Tan")).perform(replaceText("16.00"), closeSoftKeyboard());
            onView(customInput("Yucheng Zhao")).perform(replaceText("16.00"), closeSoftKeyboard());
            onView(withId(R.id.splitSummary)).check(matches(withText(containsString("matches"))));
            reviewAndSave();
            waitFor(withText("Still owed to you"));
        }

        assertEquals(16.0, share(rideId, emulator.riderA).getDouble("amount"), 0);
        assertEquals(16.01, share(rideId, emulator.organizer).getDouble("amount"), 0);
    }

    @Test
    public void customAmountsSurviveARotation() throws Exception {
        String rideId = emulator.seedCompletedRide();

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("30");
            onView(withId(R.id.splitCustomButton)).perform(click());
            onView(customInput("Kyle Macasilli-Tan")).perform(replaceText("12.34"), closeSoftKeyboard());

            screen.recreate();

            waitFor(customInput("Kyle Macasilli-Tan"));
            onView(customInput("Kyle Macasilli-Tan")).check(matches(withText("12.34")));
            onView(withId(R.id.costInput)).check(matches(withText("30")));
        }
    }

    @Test
    public void savingOfflineSaysSoAndWritesNothing() throws Exception {
        String rideId = emulator.seedCompletedRide();

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("10");
            PaymentEmulator.setAirplaneMode(true);
            reviewAndSave();
            waitFor(allOf(withId(R.id.formError),
                    withText("You're offline. Connect and try again.")), 90_000);
        } finally {
            PaymentEmulator.setAirplaneMode(false);
        }

        assertFalse(share(rideId, emulator.riderA).exists());
    }

    @Test
    public void offlineListIsAFailureNotAnEmptyList() throws Exception {
        emulator.seedCompletedRide();
        splitRide();
        Intent list = new Intent(context, PaymentsActivity.class);
        await(db().disableNetwork());

        try (ActivityScenario<PaymentsActivity> screen = ActivityScenario.launch(list)) {
            waitFor(withText(containsString("Couldn't load your rides.")));
            waitFor(withText(containsString("Couldn't load your shares.")));
            onView(withText(containsString("No ride costs yet"))).check(matches(not(isDisplayed())));

            await(db().enableNetwork());
            onView(allOf(withId(R.id.sectionRetry), isDescendantOfA(withId(R.id.ridesSection))))
                    .perform(click());
            waitFor(withText("0 of 2 riders paid"));
        } finally {
            await(db().enableNetwork());
        }
    }

    @Test
    public void offlineRiderIsNotToldThereIsNoCostYet() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);
        // Enough for the ride itself to load, then the share listener is what goes offline.
        await(db().collection("rides").document(rideId).get());
        await(db().disableNetwork());

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(withText("You're offline. Connect and try again."));
            onView(withId(R.id.amountValue)).check(matches(not(isDisplayed())));
        } finally {
            await(db().enableNetwork());
        }
    }

    @Test
    public void badFinalCostsShowAFieldErrorAndWriteNothing() throws Exception {
        String rideId = emulator.seedCompletedRide();

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            enterCost("0");
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withText("Final cost must be greater than $0")).check(matches(isDisplayed()));

            enterCost("12.345");
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withText("Use no more than two decimal places")).check(matches(isDisplayed()));

            enterCost("");
            onView(withId(R.id.reviewButton)).perform(click());
            onView(withText("Enter the final cost")).check(matches(isDisplayed()));
        }

        assertFalse(share(rideId, emulator.riderA).exists());
    }

    @Test
    public void onlyTheOrganizerGetsTheCostForm() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.riderA);

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            waitFor(withText("Only the ride organizer can record the final cost."));
            onView(withId(R.id.reviewButton)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void aRideThatIsNotCompletedHasNoCostForm() throws Exception {
        String rideId = emulator.seedRide(Ride.STATUS_CANCELLED);

        try (ActivityScenario<RecordCostActivity> screen = openRecordCost(rideId)) {
            waitFor(withText("Mark the ride completed before recording the final cost."));
        }
    }

    @Test
    public void riderSeesTheirShareAndMarksItPaid() throws Exception {
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
    public void organizerSeesEachRidersStatusUpdateLive() throws Exception {
        String rideId = splitRide();

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(shareRow("You", "Paid the fare"));
            waitFor(shareRow("Kyle Macasilli-Tan", "Unpaid"));
            waitFor(shareRow("Yucheng Zhao", "Unpaid"));
            onView(withId(R.id.amountValue)).check(matches(withText("$6.66")));
            onView(withId(R.id.primaryAction)).check(matches(withText("Edit split")));

            // Same open screen, no reload: the rider's change has to arrive on its own.
            emulator.markPaidFromAnotherDevice(
                    PaymentRepository.paymentId(rideId, emulator.riderA));

            waitFor(shareRow("Kyle Macasilli-Tan", "Paid "));
            waitFor(shareRow("Yucheng Zhao", "Unpaid"));
            onView(withId(R.id.amountValue)).check(matches(withText("$3.33")));
            onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())));
            onView(withId(R.id.footnote)).check(matches(withText(containsString("locked"))));
        }
    }

    @Test
    public void organizerCanEditTheSplitUntilARiderPays() throws Exception {
        String rideId = splitRide();

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(allOf(withId(R.id.primaryAction), withText("Edit split")));
            onView(withId(R.id.primaryAction)).perform(click());
            waitFor(allOf(withId(R.id.costInput), withText("10.00")));
            enterCost("13");
            reviewAndSave();

            // Back from the saved split lands on the list, not a stale copy of this screen.
            waitFor(allOf(withId(R.id.amountValue), withText("$8.66")));
            screen.onActivity(activity -> assertFalse(activity.isFinishing()));
        }

        waitForShareAmount(rideId, 4.33);
        assertEquals(13.0, await(db().collection("rides").document(rideId).get())
                .getDouble("finalCost"), 0);

        emulator.signIn(emulator.riderA);
        await(service.markPaid(rideId, emulator.riderA));
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
    public void flagOnlyAppearsOnceTheShareIsOverdue() throws Exception {
        String rideId = splitRide();

        try (ActivityScenario<RidePaymentActivity> screen = openRidePayment(rideId)) {
            waitFor(shareRow("Kyle Macasilli-Tan", "Unpaid"));
            onView(allOf(withText("Flag"), isDisplayed())).check(doesNotExist());
        }

        emulator.backdateShare(PaymentRepository.paymentId(rideId, emulator.riderA), 8);

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
    public void paymentsListShowsWhatIsOwedOnBothSides() throws Exception {
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
    public void paymentsListExplainsItselfWhenThereIsNothingYet() throws Exception {
        emulator.signIn(emulator.stranger);
        Intent list = new Intent(context, PaymentsActivity.class);

        try (ActivityScenario<PaymentsActivity> screen = ActivityScenario.launch(list)) {
            waitFor(withText(containsString("No ride costs yet")));
        }
    }

    @Test
    public void theHomeScreenOpensPaymentsBelowTheOtherButtons() throws Exception {
        emulator.signIn(emulator.stranger);
        Intent home = new Intent(context, MainActivity.class);

        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(home)) {
            onView(withId(R.id.paymentsButton))
                    .check(isCompletelyBelow(withId(R.id.manageRequestsButton)));
            onView(withId(R.id.paymentsButton)).perform(click());
            waitFor(withText(containsString("No ride costs yet")));
            pressBack();
        }
    }
}
