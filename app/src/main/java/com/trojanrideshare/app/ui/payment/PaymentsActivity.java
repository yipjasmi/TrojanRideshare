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
import com.trojanrideshare.app.model.OrganizerRideSummary;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.util.Money;

import java.util.List;

public class PaymentsActivity extends AppCompatActivity {
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
            retry.setOnClickListener(view -> {
                retry.setEnabled(false);
                load.run();
            });
        }

        void showRows() {
            loaded = true;
            empty = rows.getChildCount() == 0;
            error.setVisibility(View.GONE);
            retry.setVisibility(View.GONE);
            root.setVisibility(empty ? View.GONE : View.VISIBLE);
            refreshPageState();
        }

        void showError(String message) {
            loaded = true;
            empty = false;
            error.setText(message);
            retry.setEnabled(true);
            error.setVisibility(View.VISIBLE);
            retry.setVisibility(View.VISIBLE);
            root.setVisibility(View.VISIBLE);
            refreshPageState();
        }
    }

    private PaymentService paymentService;
    private String currentUserId;
    private Section sharesSection;
    private Section ridesSection;
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
        currentUserId = user.getUid();

        paymentService = new PaymentService();
        stateContainer = findViewById(R.id.stateContainer);
        stateProgress = findViewById(R.id.stateProgress);
        stateMessage = findViewById(R.id.stateMessage);
        stateMessage.setText(R.string.payment_empty);
        sharesSection = new Section(
                R.id.sharesSection,
                R.string.payment_section_shares,
                this::loadShares
        );
        ridesSection = new Section(
                R.id.ridesSection,
                R.string.payment_section_rides,
                this::loadRides
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUserId != null) {
            refreshPageState();
            loadShares();
            loadRides();
        }
    }

    private void refreshPageState() {
        boolean allLoaded = sharesSection.loaded && ridesSection.loaded;
        boolean allEmpty = allLoaded && sharesSection.empty && ridesSection.empty;
        stateContainer.setVisibility(!allLoaded || allEmpty ? View.VISIBLE : View.GONE);
        stateProgress.setVisibility(allLoaded ? View.GONE : View.VISIBLE);
        stateMessage.setVisibility(allEmpty ? View.VISIBLE : View.GONE);
    }

    private void loadShares() {
        paymentService.loadMyShares(currentUserId, new PaymentService.SharesCallback() {
            @Override
            public void onSuccess(List<Payment> shares) {
                renderShares(shares);
            }

            @Override
            public void onError(String message) {
                sharesSection.showError(message);
            }
        });
    }

    private void renderShares(List<Payment> shares) {
        sharesSection.rows.removeAllViews();
        for (Payment share : shares) {
            Intent rideIntent = new Intent(this, RidePaymentActivity.class);
            rideIntent.putExtra(RidePaymentActivity.EXTRA_RIDE_ID, share.rideId);
            addRow(
                    sharesSection.rows,
                    PaymentUi.rideTitle(this, share.rideTitle),
                    PaymentUi.shareStatus(this, share),
                    Money.format(Money.toCents(share.amount)),
                    rideIntent
            );
        }
        sharesSection.showRows();
    }

    private void loadRides() {
        paymentService.loadMyRides(currentUserId, new PaymentService.RideSummariesCallback() {
            @Override
            public void onSuccess(List<OrganizerRideSummary> summaries) {
                renderRides(summaries);
            }

            @Override
            public void onError(String message) {
                ridesSection.showError(message);
            }
        });
    }

    private void renderRides(List<OrganizerRideSummary> summaries) {
        ridesSection.rows.removeAllViews();
        for (OrganizerRideSummary summary : summaries) {
            String title = PaymentUi.rideTitle(this, summary.ride.title);
            if (summary.needsFinalCost) {
                Intent costIntent = new Intent(this, RecordCostActivity.class);
                costIntent.putExtra(RecordCostActivity.EXTRA_RIDE_ID, summary.ride.id);
                addRow(
                        ridesSection.rows,
                        title,
                        getString(R.string.payment_needs_cost),
                        "",
                        costIntent
                );
            } else {
                Intent rideIntent = new Intent(this, RidePaymentActivity.class);
                rideIntent.putExtra(RidePaymentActivity.EXTRA_RIDE_ID, summary.ride.id);
                addRow(
                        ridesSection.rows,
                        title,
                        summaryStatus(summary),
                        summary.outstandingCents > 0
                                ? Money.format(summary.outstandingCents)
                                : "",
                        rideIntent
                );
            }
        }
        ridesSection.showRows();
    }

    private void addRow(
            LinearLayout rows,
            String title,
            String detail,
            String amount,
            Intent target
    ) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_payment_row, rows, false);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        ((TextView) row.findViewById(R.id.rowDetail)).setText(detail);
        ((TextView) row.findViewById(R.id.rowAmount)).setText(amount);
        row.setOnClickListener(view -> startActivity(target));
        rows.addView(row);
    }

    private String summaryStatus(OrganizerRideSummary summary) {
        if (summary.riders == 0) {
            return getString(R.string.payment_no_riders);
        }
        if (summary.outstandingCents == 0) {
            return getString(R.string.payment_all_paid);
        }
        return getResources().getQuantityString(
                R.plurals.payment_riders_paid,
                summary.riders,
                summary.ridersPaid,
                summary.riders
        );
    }
}
