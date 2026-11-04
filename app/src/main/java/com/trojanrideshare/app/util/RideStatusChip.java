package com.trojanrideshare.app.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.ride.RideScheduleService;

/**
 * The status a ride shows on every screen, with its pill label and colors. The label always
 * names the state, so color is never the only signal.
 */
public enum RideStatusChip {
    ON_TIME(R.string.status_on_time, R.color.status_on_time_container, R.color.status_on_time_text),
    DELAYED(R.string.status_delayed, R.color.status_delayed_container, R.color.status_delayed_text),
    DEPARTED(R.string.status_departed, R.color.status_departed_container, R.color.status_departed_text),
    COMPLETED(R.string.status_completed, R.color.status_completed_container, R.color.status_completed_text),
    CANCELLED(R.string.status_cancelled, R.color.status_cancelled_container, R.color.status_cancelled_text),
    UNKNOWN(R.string.status_unknown, R.color.status_completed_container, R.color.status_completed_text);

    @StringRes public final int labelRes;
    @ColorRes public final int containerColorRes;
    @ColorRes public final int textColorRes;

    RideStatusChip(int labelRes, int containerColorRes, int textColorRes) {
        this.labelRes = labelRes;
        this.containerColorRes = containerColorRes;
        this.textColorRes = textColorRes;
    }

    /** Departed is an active ride past its departure time, waiting for the organizer to complete it. */
    public static RideStatusChip of(Ride ride, Timestamp now) {
        if (ride == null) {
            return UNKNOWN;
        }
        if (Ride.STATUS_CANCELLED.equals(ride.status)) {
            return CANCELLED;
        }
        if (Ride.STATUS_COMPLETED.equals(ride.status)) {
            return COMPLETED;
        }
        if (RideScheduleService.awaitingCompletion(ride, now)) {
            return DEPARTED;
        }
        if (Ride.STATUS_DELAYED.equals(ride.status)) {
            return DELAYED;
        }
        if (Ride.STATUS_ON_TIME.equals(ride.status)) {
            return ON_TIME;
        }
        return UNKNOWN;
    }

    /** Fills a pill styled with {@code @style/StatusChip}. */
    public void applyTo(TextView chip) {
        Context context = chip.getContext();
        chip.setText(labelRes);
        chip.setTextColor(ContextCompat.getColor(context, textColorRes));
        chip.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(context, containerColorRes)));
    }
}
