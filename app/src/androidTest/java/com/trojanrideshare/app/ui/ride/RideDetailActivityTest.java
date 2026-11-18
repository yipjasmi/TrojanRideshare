package com.trojanrideshare.app.ui.ride;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.trojanrideshare.app.payment.PaymentEmulator.await;
import static com.trojanrideshare.app.payment.PaymentEmulator.firestore;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.firestore.QuerySnapshot;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.payment.PaymentEmulator;
import com.trojanrideshare.app.service.joinrequest.JoinRequestService;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Black-box tests for what each kind of user can do from a ride's detail screen: request to
 * join, see that a ride is closed, and leave. Needs the Firebase emulators (see README).
 */
@RunWith(AndroidJUnit4.class)
public class RideDetailActivityTest {
    private static final long TIMEOUT_MILLIS = 10_000;
    private static final long ONE_DAY = TimeUnit.DAYS.toMillis(1);

    private final Context context = ApplicationProvider.getApplicationContext();
    private PaymentEmulator emulator;

    @Before
    public void setUp() throws Exception {
        emulator = PaymentEmulator.start();
    }

    private ActivityScenario<RideDetailActivity> open(String rideId, String viewerId)
            throws Exception {
        emulator.signIn(viewerId);
        Intent intent = new Intent(context, RideDetailActivity.class);
        intent.putExtra(RideDetailActivity.EXTRA_RIDE_ID, rideId);
        intent.putExtra(RideDetailActivity.EXTRA_CURRENT_USER_ID, viewerId);
        return ActivityScenario.launch(intent);
    }

    /** An On Time ride with riderA and riderB that leaves {@code fromNowMillis} from now. */
    private String rideLeavingIn(long fromNowMillis) throws Exception {
        return rideLeavingIn(fromNowMillis, Arrays.asList(emulator.riderA, emulator.riderB));
    }

    private String rideLeavingIn(long fromNowMillis, List<String> riders) throws Exception {
        String rideId = emulator.seedRide(Ride.STATUS_ON_TIME, riders);
        emulator.moveDeparture(rideId, fromNowMillis);
        return rideId;
    }

    /** The detail screen loads from Firestore and scrolls, so retry scroll-and-check. */
    private static void waitForShown(Matcher<View> view) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS;
        while (true) {
            try {
                onView(view).perform(scrollTo()).check(matches(isDisplayed()));
                return;
            } catch (Throwable notYet) {
                if (SystemClock.uptimeMillis() > deadline) {
                    throw notYet;
                }
                SystemClock.sleep(100);
            }
        }
    }

    private static Matcher<View> info(String message) {
        return allOf(withId(R.id.detailInfoMessage), withText(message));
    }


    @Test
    public void rideDetail_strangerCanRequestToJoinAnOpenRide() throws Exception {
        String rideId = rideLeavingIn(ONE_DAY);

        try (ActivityScenario<RideDetailActivity> screen = open(rideId, emulator.stranger)) {
            waitForShown(allOf(withId(R.id.joinButton),
                    withText(context.getString(R.string.join_request_action))));
            onView(withId(R.id.joinButton)).perform(scrollTo(), click());
            onView(withText(context.getString(R.string.join_request_send)))
                    .inRoot(isDialog()).perform(click());

            waitForShown(info(context.getString(R.string.join_request_success)));
            onView(withId(R.id.joinButton))
                    .check(matches(withText(context.getString(R.string.join_request_pending))));
        }

        QuerySnapshot requests = await(firestore().collection("joinRequests")
                .whereEqualTo("rideId", rideId)
                .whereEqualTo("requesterId", emulator.stranger)
                .get());
        assertEquals(1, requests.size());
    }

    @Test
    public void rideDetail_fullRideHidesTheJoinButton() throws Exception {
        // maxRiders is 4 on a seeded ride, so four participants leave no open seat.
        String rideId = rideLeavingIn(ONE_DAY, Arrays.asList(
                emulator.organizer, emulator.riderA, emulator.riderB, "fourth-rider"));

        try (ActivityScenario<RideDetailActivity> screen = open(rideId, emulator.stranger)) {
            waitForShown(info("This ride is full."));
            onView(withId(R.id.joinButton)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void rideDetail_departedRideRefusesNewRequests() throws Exception {
        String rideId = rideLeavingIn(-TimeUnit.MINUTES.toMillis(30));

        try (ActivityScenario<RideDetailActivity> screen = open(rideId, emulator.stranger)) {
            waitForShown(info(JoinRequestService.MESSAGE_DEPARTED));
            onView(withId(R.id.joinButton)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void rideDetail_joinedRiderSeesLeaveInsteadOfJoin() throws Exception {
        String rideId = rideLeavingIn(ONE_DAY);

        try (ActivityScenario<RideDetailActivity> screen = open(rideId, emulator.riderA)) {
            waitForShown(info(context.getString(R.string.ride_detail_badge_joined)));
            waitForShown(withId(R.id.leaveRideButton));
            onView(withId(R.id.joinButton)).check(matches(not(isDisplayed())));
            onView(withId(R.id.editRideButton)).check(matches(not(isDisplayed())));
        }
    }

}
