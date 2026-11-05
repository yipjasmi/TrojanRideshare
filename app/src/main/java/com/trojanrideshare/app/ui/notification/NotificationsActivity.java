package com.trojanrideshare.app.ui.notification;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.trojanrideshare.app.MainActivity;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.data.notification.NotificationDataAccess;
import com.trojanrideshare.app.model.Notification;
import com.trojanrideshare.app.ui.payment.RidePaymentActivity;
import com.trojanrideshare.app.util.SystemBarsHelper;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {
    public static final String EXTRA_CURRENT_USER_ID = "currentUserId";

    private ProgressBar loadingIndicator;
    private TextView message;
    private LinearLayout container;

    private NotificationDataAccess notificationDataAccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        SystemBarsHelper.applyPadding(this);

        MaterialToolbar toolbar = findViewById(R.id.notificationsToolbar);
        toolbar.setNavigationOnClickListener(v -> navigateToHome());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToHome();
            }
        });

        notificationDataAccess = new NotificationDataAccess();
        loadingIndicator = findViewById(R.id.notificationsLoadingIndicator);
        message = findViewById(R.id.notificationsMessage);
        container = findViewById(R.id.notificationsContainer);

        load();
    }

    private void load() {
        loadingIndicator.setVisibility(View.VISIBLE);
        message.setVisibility(View.GONE);
        notificationDataAccess.getForUser(
                getIntent().getStringExtra(EXTRA_CURRENT_USER_ID),
                new NotificationDataAccess.ListCallback() {
                    @Override
                    public void onSuccess(List<Notification> notifications) {
                        loadingIndicator.setVisibility(View.GONE);
                        render(notifications);
                    }

                    @Override
                    public void onError(Exception exception) {
                        loadingIndicator.setVisibility(View.GONE);
                        showMessage(getString(R.string.notifications_load_error));
                    }
                });
    }

    private void render(List<Notification> notifications) {
        container.removeAllViews();
        if (notifications.isEmpty()) {
            showMessage(getString(R.string.notifications_empty));
            return;
        }
        message.setVisibility(View.GONE);
        Collections.sort(notifications, byNewestFirst());

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Notification notification : notifications) {
            View row = inflater.inflate(R.layout.item_notification, container, false);
            TextView messageView = row.findViewById(R.id.notificationMessage);
            messageView.setText(emptyFallback(
                    notification.message, getString(R.string.notification_default_message)));
            messageView.setTypeface(null, notification.read ? Typeface.NORMAL : Typeface.BOLD);
            ((TextView) row.findViewById(R.id.notificationTime))
                    .setText(formatTime(notification.createdAt));
            if (opensPayment(notification)) {
                row.setOnClickListener(view -> openPayment(notification.rideId));
            }
            container.addView(row);

            if (!notification.read && notification.id != null) {
                markRead(notification.id);
            }
        }
    }

    private void markRead(String notificationId) {
        // Best effort; the feed is already rendered regardless of the result.
        notificationDataAccess.markRead(notificationId, new NotificationDataAccess.WriteCallback() {
            @Override
            public void onSuccess() {}

            @Override
            public void onError(Exception exception) {}
        });
    }

    // Any student can write a notification, and Firestore throws on a bad document id.
    private static boolean opensPayment(Notification notification) {
        return notification.rideId != null
                && !notification.rideId.isEmpty()
                && !notification.rideId.contains("/")
                && (Notification.TYPE_SHARE_ASSIGNED.equals(notification.type)
                        || Notification.TYPE_SHARE_PAID.equals(notification.type)
                        || Notification.TYPE_SHARE_FLAGGED.equals(notification.type));
    }

    private void openPayment(String rideId) {
        Intent intent = new Intent(this, RidePaymentActivity.class);
        intent.putExtra(RidePaymentActivity.EXTRA_RIDE_ID, rideId);
        startActivity(intent);
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    private static Comparator<Notification> byNewestFirst() {
        return (a, b) -> {
            long left = a.createdAt == null ? 0 : a.createdAt.toDate().getTime();
            long right = b.createdAt == null ? 0 : b.createdAt.toDate().getTime();
            return Long.compare(right, left);
        };
    }

    private String formatTime(Timestamp createdAt) {
        Date date = createdAt == null ? null : createdAt.toDate();
        return date == null
                ? getString(R.string.notification_unknown_time)
                : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date);
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
