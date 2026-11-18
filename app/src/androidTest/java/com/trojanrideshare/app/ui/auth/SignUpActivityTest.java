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

import android.os.Build;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.trojanrideshare.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

/**
 * Black-box tests for the sign-up form. Every case fails validation before Firebase is called,
 * so no emulator is needed.
 */
@RunWith(AndroidJUnit4.class)
public class SignUpActivityTest {
    private static final int CURRENT_YEAR = Calendar.getInstance().get(Calendar.YEAR);

    /**
     * Opens sign-up with Android's autofill turned off for the screen. Otherwise, after a test
     * types into the password fields, the system "Save password?" sheet can cover the next test's
     * screen and Espresso times out waiting for window focus.
     */
    private static ActivityScenario<SignUpActivity> open() {
        ActivityScenario<SignUpActivity> screen = ActivityScenario.launch(SignUpActivity.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            screen.onActivity(activity -> activity.getWindow().getDecorView()
                    .setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS));
        }
        return screen;
    }

    private static void type(int viewId, String text) {
        onView(withId(viewId)).perform(scrollTo(), replaceText(text), closeSoftKeyboard());
    }

    /** Fills every field with a valid value so each test changes only the one under test. */
    private static void fillValidForm() {
        type(R.id.signUpFullNameEditText, "Tommy Trojan");
        type(R.id.signUpEmailEditText, "ttrojan@usc.edu");
        type(R.id.signUpUscIdEditText, "1234567890");
        type(R.id.signUpPasswordEditText, "fighton1");
        type(R.id.signUpConfirmPasswordEditText, "fighton1");
        type(R.id.signUpGradYearEditText, String.valueOf(CURRENT_YEAR + 1));
    }

    private static void submit() {
        onView(withId(R.id.signUpButton)).perform(scrollTo(), click());
    }

    @Test
    public void signUp_rejectsNonUscEmails() {
        try (ActivityScenario<SignUpActivity> screen = open()) {
            fillValidForm();

            type(R.id.signUpEmailEditText, "ttrojan@gmail.com");
            submit();
            onView(withId(R.id.signUpEmailEditText))
                    .check(matches(hasErrorText("Must be a valid @usc.edu email")));

            type(R.id.signUpEmailEditText, "ttrojan@usc.edu.com");
            submit();
            onView(withId(R.id.signUpEmailEditText))
                    .check(matches(hasErrorText("Must be a valid @usc.edu email")));
            onView(withId(R.id.signUpButton)).check(matches(isEnabled()));
        }
    }

    @Test
    public void signUp_requiresExactlyTenDigitUscId() {
        try (ActivityScenario<SignUpActivity> screen = open()) {
            fillValidForm();

            type(R.id.signUpUscIdEditText, "123456789");
            submit();
            onView(withId(R.id.signUpUscIdEditText))
                    .check(matches(hasErrorText("USC ID must be exactly 10 digits")));

            type(R.id.signUpUscIdEditText, "12345678901");
            submit();
            onView(withId(R.id.signUpUscIdEditText))
                    .check(matches(hasErrorText("USC ID must be exactly 10 digits")));
        }
    }

    @Test
    public void signUp_rejectsMismatchedPasswords() {
        try (ActivityScenario<SignUpActivity> screen = open()) {
            fillValidForm();

            type(R.id.signUpConfirmPasswordEditText, "fighton2");
            submit();

            onView(withId(R.id.signUpConfirmPasswordEditText))
                    .check(matches(hasErrorText("Passwords do not match")));
        }
    }

    @Test
    public void signUp_rejectsGraduationYearsOutsideTheActiveStudentWindow() {
        String expected = "Graduation year must be between "
                + CURRENT_YEAR + " and " + (CURRENT_YEAR + 6);

        try (ActivityScenario<SignUpActivity> screen = open()) {
            fillValidForm();

            type(R.id.signUpGradYearEditText, String.valueOf(CURRENT_YEAR - 1));
            submit();
            onView(withId(R.id.signUpGradYearEditText)).check(matches(hasErrorText(expected)));

            type(R.id.signUpGradYearEditText, String.valueOf(CURRENT_YEAR + 7));
            submit();
            onView(withId(R.id.signUpGradYearEditText)).check(matches(hasErrorText(expected)));
        }
    }
}
