package com.trojanrideshare.app.util;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.trojanrideshare.app.R;

/**
 * Placeholder blocks shaped like a ride card (a title line with a chip, then two text lines)
 * for a first load. It pulses while on screen and holds still when motion is reduced.
 */
public class SkeletonView extends LinearLayout {
    private static final long PULSE_MILLIS = 1000;
    private static final float DIM_ALPHA = 0.5f;

    private ObjectAnimator pulse;

    public SkeletonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_skeleton, this, true);
    }

    /** Also called when an ancestor or the window is hidden, so a skeleton off screen stops. */
    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) {
            startPulse();
        } else {
            stopPulse();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    private void startPulse() {
        if (pulse != null || Motion.isReduced(getContext())) {
            return;
        }
        pulse = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, DIM_ALPHA);
        pulse.setDuration(PULSE_MILLIS);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();
    }

    private void stopPulse() {
        if (pulse == null) {
            return;
        }
        pulse.cancel();
        pulse = null;
        setAlpha(1f);
    }
}
