package com.trojanrideshare.app.ui.search;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.trojanrideshare.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SearchActivityTest {
    private static final String CURRENT_USER_ID = "search-test-user";

    @Test
    public void search_requiresBothRouteKeywords() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, SearchActivity.class);
        intent.putExtra(SearchActivity.EXTRA_CURRENT_USER_ID, CURRENT_USER_ID);

        try (ActivityScenario<SearchActivity> screen = ActivityScenario.launch(intent)) {
            onView(withId(R.id.originSearchInput)).perform(
                    replaceText("USC"),
                    closeSoftKeyboard()
            );
            onView(withId(R.id.searchButton)).perform(scrollTo(), click());

            onView(withId(R.id.destinationSearchInput)).check(
                    matches(hasErrorText("Destination is required"))
            );
            onView(allOf(
                    withId(R.id.searchResultMessage),
                    withText("Enter both an origin keyword and a destination keyword.")
            )).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test
    public void seatFilter_stopsAtFiveAndClearsToAny() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, SearchActivity.class);
        intent.putExtra(SearchActivity.EXTRA_CURRENT_USER_ID, CURRENT_USER_ID);

        try (ActivityScenario<SearchActivity> screen = ActivityScenario.launch(intent)) {
            onView(withId(R.id.seatsIncreaseButton)).perform(
                    scrollTo(),
                    click(),
                    click(),
                    click(),
                    click(),
                    click()
            );

            onView(withId(R.id.seatsValueText)).check(matches(withText("5+")));
            onView(withId(R.id.seatsIncreaseButton)).check(matches(not(isEnabled())));

            onView(withId(R.id.clearFiltersButton)).perform(scrollTo(), click());

            onView(withId(R.id.seatsValueText)).check(matches(withText("Any")));
            onView(withId(R.id.seatsDecreaseButton)).check(matches(not(isEnabled())));
            onView(withId(R.id.seatsIncreaseButton)).check(matches(isEnabled()));
        }
    }
}
