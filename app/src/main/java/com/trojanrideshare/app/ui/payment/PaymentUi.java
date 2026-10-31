package com.trojanrideshare.app.ui.payment;

import android.content.Context;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.payment.PaymentException;
import com.trojanrideshare.app.service.payment.PaymentService;

import java.text.DateFormat;
import java.util.List;
import java.util.Map;

final class PaymentUi {
    private PaymentUi() {}

    static void setUpScreen(AppCompatActivity activity) {
        // The IME inset keeps the footer button above the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> activity.finish());
    }

    static String errorMessage(Context context, Exception error) {
        if (error != null && error.getCause() instanceof Exception) {
            Exception cause = (Exception) error.getCause();
            if (cause instanceof PaymentException || cause instanceof FirebaseFirestoreException) {
                error = cause;
            }
        }
        if (error instanceof PaymentException) {
            return error.getMessage();
        }
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) error).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return context.getString(R.string.payment_error_offline);
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return context.getString(R.string.payment_error_denied);
            }
        }
        return context.getString(R.string.payment_error_generic);
    }

    static String rideTitle(Context context, String title) {
        return title == null || title.isEmpty()
                ? context.getString(R.string.payment_ride_fallback) : title;
    }

    static String rideMeta(Context context, Ride ride) {
        String date = ride.departureTime == null ? "" : formatDate(ride.departureTime);
        if (ride.origin == null || ride.destination == null) {
            return date;
        }
        String route = context.getString(
                R.string.payment_route, ride.origin.address, ride.destination.address);
        return date.isEmpty() ? route : route + " · " + date;
    }

    static String formatDate(Timestamp timestamp) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(timestamp.toDate());
    }

    static String displayName(Context context, String userId, String currentUserId,
                              List<String> participants, Map<String, String> names) {
        if (userId.equals(currentUserId)) {
            return context.getString(R.string.payment_you);
        }
        return nameOf(context, userId, participants, names);
    }

    static String nameOf(Context context, String userId, List<String> participants,
                         Map<String, String> names) {
        String name = names.get(userId);
        if (name != null) {
            return name;
        }
        int position = participants.indexOf(userId);
        return position == 0
                ? context.getString(R.string.payment_organizer_fallback)
                : context.getString(R.string.payment_rider_fallback, position);
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
                    R.string.payment_status_flagged, PaymentService.APPROVAL_PERIOD_DAYS);
        }
        if (PaymentService.isOverdue(share, System.currentTimeMillis())) {
            return context.getString(
                    R.string.payment_status_overdue, PaymentService.APPROVAL_PERIOD_DAYS);
        }
        return context.getString(R.string.payment_status_unpaid);
    }

    static void show(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
