package com.trojanrideshare.app.ui.ride;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.not;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.trojanrideshare.app.R;
import com.trojanrideshare.app.service.ride.RideValidator;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Black-box tests for the Create Ride form. Every case fails validation, so nothing is written
 * and no emulator is needed.
 */
@RunWith(AndroidJUnit4.class)
public class CreateRideActivityTest {
    private static final String CURRENT_USER_ID = "create-ride-test-user";

    private static ActivityScenario<CreateRideActivity> open() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, CreateRideActivity.class);
        intent.putExtra(CreateRideActivity.EXTRA_CURRENT_USER_ID, CURRENT_USER_ID);
        return ActivityScenario.launch(intent);
    }

    private static void type(int viewId, String text) {
        onView(withId(viewId)).perform(scrollTo(), replaceText(text), closeSoftKeyboard());
    }

    private static void submit() {
        onView(withId(R.id.submitButton)).perform(scrollTo(), click());
    }

    private static void assertError(int errorViewId, String message) {
        onView(withId(errorViewId)).perform(scrollTo())
                .check(matches(withText(message)))
                .check(matches(isDisplayed()));
    }

    private static void assertNoError(int errorViewId) {
        onView(withId(errorViewId)).check(matches(not(isDisplayed())));
    }


    @Test
    public void createRide_emptyFormShowsEveryRequiredError() {
        try (ActivityScenario<CreateRideActivity> screen = open()) {
            submit();

            assertError(R.id.titleError, RideValidator.ERROR_TITLE_REQUIRED);
            assertError(R.id.originError, RideValidator.ERROR_ORIGIN_INVALID);
            assertError(R.id.destinationError, RideValidator.ERROR_DESTINATION_INVALID);
            assertError(R.id.departureError, RideValidator.ERROR_DEPARTURE_REQUIRED);
            assertError(R.id.maxRidersError, RideValidator.ERROR_RIDERS_RANGE);
            assertError(R.id.costError, RideValidator.ERROR_COST_REQUIRED);
            onView(withId(R.id.submitButton)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void createRide_acceptsTwoToSixRidersOnly() {
        try (ActivityScenario<CreateRideActivity> screen = open()) {
            type(R.id.maxRidersInput, "1");
            submit();
            assertError(R.id.maxRidersError, RideValidator.ERROR_RIDERS_RANGE);

            type(R.id.maxRidersInput, "2");
            submit();
            assertNoError(R.id.maxRidersError);

            type(R.id.maxRidersInput, "6");
            submit();
            assertNoError(R.id.maxRidersError);

            type(R.id.maxRidersInput, "7");
            submit();
            assertError(R.id.maxRidersError, RideValidator.ERROR_RIDERS_RANGE);
        }
    }


    @Test
    public void createRide_rejectsZeroAndNegativeCost() {
        try (ActivityScenario<CreateRideActivity> screen = open()) {
            type(R.id.costInput, "0");
            submit();
            assertError(R.id.costError, RideValidator.ERROR_COST_ZERO);

            type(R.id.costInput, "-5");
            submit();
            assertError(R.id.costError, RideValidator.ERROR_COST_NEGATIVE);

            type(R.id.costInput, "0.01");
            submit();
            assertNoError(R.id.costError);
        }
    }

    @Test
    public void createRide_rejectsCostThatIsNotADollarAmount() {
        try (ActivityScenario<CreateRideActivity> screen = open()) {
            type(R.id.costInput, "12.505");
            submit();
            assertError(R.id.costError, RideValidator.ERROR_COST_DECIMALS);

            type(R.id.costInput, "twelve");
            submit();
            assertError(R.id.costError, RideValidator.ERROR_COST_INVALID);

            type(R.id.costInput, "$1,234.50");
            submit();
            assertNoError(R.id.costError);
        }
    }
}
