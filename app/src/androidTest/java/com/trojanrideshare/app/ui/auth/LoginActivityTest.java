package com.trojanrideshare.app.ui.auth;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.auth.FirebaseAuth;
import com.trojanrideshare.app.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Black-box tests for the login form's input checks. Both cases stop before Firebase is called.
 */
@RunWith(AndroidJUnit4.class)
public class LoginActivityTest {

    @Before
    public void signOut() {
        // A user left signed in by another test would skip this screen and open Home.
        FirebaseAuth.getInstance().signOut();
    }

    private static void type(int viewId, String text) {
        onView(withId(viewId)).perform(scrollTo(), replaceText(text), closeSoftKeyboard());
    }

    @Test
    public void login_rejectsNonUscEmail() {
        try (ActivityScenario<LoginActivity> screen =
                     ActivityScenario.launch(LoginActivity.class)) {
            type(R.id.loginEmailEditText, "ttrojan@ucla.edu");
            type(R.id.loginPasswordEditText, "fighton1");
            onView(withId(R.id.loginButton)).perform(scrollTo(), click());

            onView(withId(R.id.loginEmailEditText))
                    .check(matches(hasErrorText("Enter your @usc.edu email")));
            onView(withId(R.id.loginButton)).check(matches(isEnabled()));
        }
    }

    @Test
    public void login_requiresAPassword() {
        try (ActivityScenario<LoginActivity> screen =
                     ActivityScenario.launch(LoginActivity.class)) {
            type(R.id.loginEmailEditText, "TTrojan@USC.edu");
            type(R.id.loginPasswordEditText, "");
            onView(withId(R.id.loginButton)).perform(scrollTo(), click());

            onView(withId(R.id.loginPasswordEditText))
                    .check(matches(hasErrorText("Enter your password")));
            onView(withId(R.id.loginButton)).check(matches(isEnabled()));
        }
    }
}
