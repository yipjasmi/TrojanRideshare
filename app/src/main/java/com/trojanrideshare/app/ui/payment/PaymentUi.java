package com.trojanrideshare.app.ui.payment;

import android.content.Context;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.util.Motion;

import java.text.DateFormat;
import java.util.List;
import java.util.Map;

final class PaymentUi {
    private static final long PAID_CHECK_MILLIS = 350;

    private PaymentUi() {}

    static void setUpScreen(AppCompatActivity activity) {
        ViewCompat.setOnApplyWindowInsetsListener(
                activity.findViewById(R.id.main),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
                    );
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                }
        );
        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(view -> activity.finish());
    }

    static String rideTitle(Context context, String title) {
        return title == null || title.isEmpty()
                ? context.getString(R.string.search_untitled_ride)
                : title;
    }

    static String rideMeta(Context context, Ride ride) {
        String date = ride.departureTime == null ? "" : formatDate(ride.departureTime);
        if (ride.origin == null || ride.destination == null) {
            return date;
        }
        String route = context.getString(
                R.string.search_route_format,
                ride.origin.address,
                ride.destination.address
        );
        return date.isEmpty() ? route : route + " · " + date;
    }

    static String formatDate(Timestamp timestamp) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(timestamp.toDate());
    }

    static String displayName(
            Context context,
            String userId,
            String currentUserId,
            List<String> participants,
            Map<String, String> names
    ) {
        if (userId.equals(currentUserId)) {
            return context.getString(R.string.payment_you);
        }
        return nameOf(context, userId, participants, names);
    }

    static String nameOf(
            Context context,
            String userId,
            List<String> participants,
            Map<String, String> names
    ) {
        String name = names.get(userId);
        if (name != null) {
            return name;
        }
        int position = participants.indexOf(userId);
        return position == 0
                ? context.getString(R.string.payment_organizer_fallback)
                : context.getString(R.string.payment_rider_fallback, position);
    }

    static void showPaidCheck(View check, boolean animate) {
        check.setVisibility(View.VISIBLE);
        if (!animate || Motion.isReduced(check.getContext())) {
            return;
        }
        check.setAlpha(0f);
        check.setScaleX(0.4f);
        check.setScaleY(0.4f);
        check.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(PAID_CHECK_MILLIS)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    static String shareStatus(Context context, Payment share) {
        if (share.userId.equals(share.organizerId)) {
            return context.getString(R.string.payment_status_fronted);
        }
        if (share.paidAt != null) {
            return context.getString(R.string.payment_status_paid_on, formatDate(share.paidAt));
        }
        if (share.paid) {
            return context.getString(R.string.payment_status_nothing_owed);
        }
        if (share.flagged) {
            return context.getString(
                    R.string.payment_status_flagged,
                    Payment.APPROVAL_PERIOD_DAYS
            );
        }
        if (share.isOverdue(System.currentTimeMillis())) {
            return context.getString(
                    R.string.payment_status_overdue,
                    Payment.APPROVAL_PERIOD_DAYS
            );
        }
        return context.getString(R.string.payment_status_unpaid);
    }
}
