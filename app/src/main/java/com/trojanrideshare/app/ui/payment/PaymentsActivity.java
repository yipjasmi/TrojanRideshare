package com.trojanrideshare.app.ui.payment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.util.Money;

import java.util.List;

public class PaymentsActivity extends AppCompatActivity {

    // Each list owns its rows, error and retry, so one failed load can't blank the other.
    private final class Section {
        final View root;
        final TextView error;
        final Button retry;
        final LinearLayout rows;
        boolean loaded;
        boolean empty = true;

        Section(int rootId, int titleRes, Runnable load) {
            root = findViewById(rootId);
            error = root.findViewById(R.id.sectionError);
            retry = root.findViewById(R.id.sectionRetry);
            rows = root.findViewById(R.id.sectionRows);
            ((TextView) root.findViewById(R.id.sectionTitle)).setText(titleRes);
            retry.setOnClickListener(v -> {
                retry.setEnabled(false);
                load.run();
            });
        }

        void showRows() {
            loaded = true;
            empty = rows.getChildCount() == 0;
            PaymentUi.show(error, false);
            PaymentUi.show(retry, false);
            PaymentUi.show(root, !empty);
            refreshPageState();
        }

        void showError(int messageRes, Exception failure) {
            loaded = true;
            empty = false;
            error.setText(getString(messageRes) + " "
                    + PaymentUi.errorMessage(PaymentsActivity.this, failure));
            retry.setEnabled(true);
            PaymentUi.show(error, true);
            PaymentUi.show(retry, true);
            PaymentUi.show(root, true);
            refreshPageState();
        }
    }

    private final PaymentService paymentService = new PaymentService();
    private String uid;
    private Section shares;
    private Section rides;
    private View stateContainer;
    private View stateProgress;
    private TextView stateMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_payments);
        PaymentUi.setUpScreen(this);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        uid = user.getUid();

        stateContainer = findViewById(R.id.stateContainer);
        stateProgress = findViewById(R.id.stateProgress);
        stateMessage = findViewById(R.id.stateMessage);
        stateMessage.setText(R.string.payment_empty);
        shares = new Section(R.id.sharesSection, R.string.payment_section_shares, this::loadShares);
        rides = new Section(R.id.ridesSection, R.string.payment_section_rides, this::loadRides);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (uid != null) {
            refreshPageState();
            loadShares();
            loadRides();
        }
    }

    private void refreshPageState() {
        boolean allLoaded = shares.loaded && rides.loaded;
        boolean allEmpty = allLoaded && shares.empty && rides.empty;
        PaymentUi.show(stateContainer, !allLoaded || allEmpty);
        PaymentUi.show(stateProgress, !allLoaded);
        PaymentUi.show(stateMessage, allEmpty);
    }

    private void loadShares() {
        paymentService.loadMyShares(uid).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                shares.showError(R.string.payment_shares_load_failed, task.getException());
                return;
            }
            shares.rows.removeAllViews();
            for (Payment share : task.getResult()) {
                addRow(shares.rows,
                        PaymentUi.rideTitle(this, share.rideTitle),
                        PaymentUi.shareStatus(this, share),
                        Money.format(Money.toCents(share.amount)),
                        RidePaymentActivity.intent(this, share.rideId));
            }
            shares.showRows();
        });
    }

    private void loadRides() {
        paymentService.loadMyRides(uid).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                rides.showError(R.string.payment_rides_load_failed, task.getException());
                return;
            }
            rides.rows.removeAllViews();
            for (PaymentService.RideCollection collection : task.getResult()) {
                String title = PaymentUi.rideTitle(this, collection.ride.title);
                if (collection.needsFinalCost) {
                    addRow(rides.rows, title, getString(R.string.payment_needs_cost), "",
                            RecordCostActivity.intent(this, collection.ride.id));
                } else {
                    addRow(rides.rows, title, collectionStatus(collection),
                            collection.outstandingCents > 0
                                    ? Money.format(collection.outstandingCents) : "",
                            RidePaymentActivity.intent(this, collection.ride.id));
                }
            }
            rides.showRows();
        });
    }

    private void addRow(LinearLayout rows, String title, String detail, String amount, Intent target) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_payment_row, rows, false);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        ((TextView) row.findViewById(R.id.rowDetail)).setText(detail);
        ((TextView) row.findViewById(R.id.rowAmount)).setText(amount);
        row.setOnClickListener(v -> startActivity(target));
        rows.addView(row);
    }

    private String collectionStatus(PaymentService.RideCollection collection) {
        if (collection.riders == 0) {
            return getString(R.string.payment_no_riders);
        }
        if (collection.outstandingCents == 0) {
            return getString(R.string.payment_all_paid);
        }
        return getResources().getQuantityString(R.plurals.payment_riders_paid, collection.riders,
                collection.ridersPaid, collection.riders);
    }
}
