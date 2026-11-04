package com.trojanrideshare.app.util;

import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SystemBarsHelper {
    /**
     * Keeps the screen's content clear of the status bar, the navigation buttons and the keyboard.
     * Android 15+ draws every app edge to edge, so without this the top of a screen sits under the
     * clock. EdgeToEdge also gives dark status bar icons on the light theme.
     */
    public static void applyPadding(ComponentActivity activity) {
        EdgeToEdge.enable(activity);
        View content = activity.findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            // Consumed so the bottom navigation doesn't pad for the same bar a second time.
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
