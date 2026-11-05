package com.trojanrideshare.app.util;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Short touch feedback for actions that just worked or were refused. Follows the phone's setting. */
public final class Haptics {
    private Haptics() {}

    public static void confirm(View view) {
        view.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static void reject(View view) {
        view.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.REJECT
                : HapticFeedbackConstants.LONG_PRESS);
    }
}
