package com.trojanrideshare.app.util;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.color.MaterialColors;

/**
 * Pull to refresh in the app's colors. It is never the only way to reload: each screen with it
 * also reloads on resume or has a retry button.
 */
public final class PullToRefresh {
    private PullToRefresh() {}

    public static void setUp(SwipeRefreshLayout layout, SwipeRefreshLayout.OnRefreshListener reload) {
        layout.setColorSchemeColors(
                MaterialColors.getColor(layout, androidx.appcompat.R.attr.colorPrimary));
        // The default disc is near white, which hides the light primary of the dark theme.
        layout.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(
                layout, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        layout.setOnRefreshListener(reload);
    }
}
