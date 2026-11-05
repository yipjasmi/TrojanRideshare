package com.trojanrideshare.app.util;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.app.ActivityOptionsCompat;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

/** The app's animations. Each one is skipped or instant when the user has turned animations off. */
public final class Motion {
    private Motion() {}

    /** True when Remove animations is on, or the animator duration scale is off in developer options. */
    public static boolean isReduced(Context context) {
        return Settings.Global.getFloat(
                context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
        ) == 0f;
    }

    /** Animates the next layout change under {@code sceneRoot}. */
    public static void beginTransition(ViewGroup sceneRoot) {
        if (!isReduced(sceneRoot.getContext())) {
            TransitionManager.beginDelayedTransition(sceneRoot);
        }
    }

    /**
     * Options that move {@code shared} into the view with the same transition name on the next
     * screen, or null for a plain start when motion is reduced.
     */
    public static Bundle sharedElementOptions(Activity activity, View shared, String transitionName) {
        if (isReduced(activity)) {
            return null;
        }
        shared.setTransitionName(transitionName);
        return ActivityOptionsCompat
                .makeSceneTransitionAnimation(activity, shared, transitionName)
                .toBundle();
    }

    /** Shrinks {@code fab} to its icon while {@code scroller} moves down and extends it on the way up. */
    public static void shrinkOnScroll(View scroller, ExtendedFloatingActionButton fab) {
        scroller.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            boolean extend = scrollY < oldScrollY;
            if (scrollY == oldScrollY || extend == fab.isExtended()) {
                return;
            }
            if (isReduced(view.getContext())) {
                fab.setExtended(extend);
            } else if (extend) {
                fab.extend();
            } else {
                fab.shrink();
            }
        });
    }
}
