package com.trojanrideshare.app.ui.payment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.util.Money;

import java.util.List;

public class RidePaymentActivity extends AppCompatActivity {
    private static final String EXTRA_RIDE_ID = "rideId";

    public static Intent intent(Context context, String rideId) {
        return new Intent(context, RidePaymentActivity.class).putExtra(EXTRA_RIDE_ID, rideId);
    }

    private final PaymentService paymentService = new PaymentService();
    private String rideId;
    private String uid;
    private PaymentService.RideDetails details;

    private View stateContainer;
    private View stateProgress;
    private TextView stateMessage;
    private View stateRetry;
    private View detailScroll;
    private TextView amountLabel;
    private TextView amountValue;
    private TextView amountDetail;
    private TextView statusText;
    private TextView actionError;
    private Button primaryAction;
    private TextView sharesTitle;
    private LinearLayout sharesList;
    private TextView footnote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ride_payment);
        PaymentUi.setUpScreen(this);

        stateContainer = findViewById(R.id.stateContainer);
        stateProgress = findViewById(R.id.stateProgress);
        stateMessage = findViewById(R.id.stateMessage);
        stateRetry = findViewById(R.id.stateRetry);
        detailScroll = findViewById(R.id.detailScroll);
        amountLabel = findViewById(R.id.amountLabel);
        amountValue = findViewById(R.id.amountValue);
        amountDetail = findViewById(R.id.amountDetail);
        statusText = findViewById(R.id.statusText);
        actionError = findViewById(R.id.actionError);
        primaryAction = findViewById(R.id.primaryAction);
        sharesTitle = findViewById(R.id.sharesTitle);
        sharesList = findViewById(R.id.sharesList);
        footnote = findViewById(R.id.footnote);

        stateRetry.setOnClickListener(v -> load());
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        if (user == null || rideId == null) {
            showMessage(getString(R.string.payment_signed_out), false);
            return;
        }
        uid = user.getUid();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (uid != null) {
            load();
        }
    }

    private void load() {
        if (details == null) {
            showLoading();
        }
        paymentService.loadRideDetails(rideId, uid).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                showMessage(PaymentUi.errorMessage(this, task.getException()), true);
                return;
            }
            details = task.getResult();
            Ride ride = details.ride;
            if (!details.participants.contains(uid)) {
                showMessage(getString(R.string.payment_not_participant), false);
                return;
            }
            ((TextView) findViewById(R.id.rideTitle)).setText(PaymentUi.rideTitle(this, ride.title));
            ((TextView) findViewById(R.id.rideMeta)).setText(PaymentUi.rideMeta(this, ride));
            paymentService.watchShares(this, ride, uid, (shares, error) -> {
                if (error != null) {
                    showMessage(PaymentUi.errorMessage(this, error), true);
                } else if (isOrganizer()) {
                    renderOrganizer(shares);
                } else {
                    renderRider(shares.isEmpty() ? null : shares.get(0));
                }
            });
        });
    }

    private boolean isOrganizer() {
        return uid.equals(details.ride.organizerId);
    }

    private String organizerName() {
        String name = details.names.get(details.ride.organizerId);
        return name != null ? name : getString(R.string.payment_the_organizer);
    }

    private void renderRider(Payment share) {
        showDetail();
        sharesList.removeAllViews();
        PaymentUi.show(sharesTitle, false);
        PaymentUi.show(primaryAction, false);
        PaymentUi.show(statusText, false);

        if (share == null) {
            showAmountHeader("", getString(R.string.payment_no_cost_yet), "");
            footnote.setText(getString(R.string.payment_no_cost_yet_rider, organizerName()));
            return;
        }

        long cents = Money.toCents(share.amount);
        showAmountHeader(getString(R.string.payment_your_share), Money.format(cents),
                details.ride.finalCost == null ? "" : getString(R.string.payment_of_final_cost,
                        Money.format(Money.toCents(details.ride.finalCost))));
        showStatus(PaymentUi.shareStatus(this, share), share.flagged);

        if (share.paid) {
            footnote.setText(R.string.payment_outside_app);
            return;
        }
        footnote.setText(getString(R.string.payment_reimburse, organizerName()));
        primaryAction.setText(R.string.payment_mark_paid);
        primaryAction.setOnClickListener(v -> confirmMarkPaid(cents));
        PaymentUi.show(primaryAction, true);
    }

    private void renderOrganizer(List<Payment> shares) {
        showDetail();
        footnote.setText(R.string.payment_outside_app);
        PaymentUi.show(statusText, false);
        sharesList.removeAllViews();

        if (shares.isEmpty()) {
            showAmountHeader("", getString(R.string.payment_no_cost_yet),
                    getString(R.string.payment_no_cost_yet_organizer));
            PaymentUi.show(sharesTitle, false);
            showPrimaryAction(R.string.payment_record_cost);
            return;
        }

        long outstanding = 0;
        long finalCents = 0;
        for (String userId : details.participants) {
            Payment share = shareOf(shares, userId);
            if (share == null) {
                continue;
            }
            long cents = Money.toCents(share.amount);
            finalCents += cents;
            if (!share.paid) {
                outstanding += cents;
            }
            addShareRow(share, cents);
        }

        showAmountHeader(getString(R.string.payment_outstanding), Money.format(outstanding),
                getString(R.string.payment_of_final_cost, Money.format(finalCents)));
        PaymentUi.show(sharesTitle, true);
        if (PaymentService.hasRiderMarkedPaid(shares)) {
            PaymentUi.show(primaryAction, false);
            footnote.setText(getString(R.string.payment_locked_note) + " "
                    + getString(R.string.payment_outside_app));
        } else {
            showPrimaryAction(R.string.payment_edit_split);
        }
    }

    private void showPrimaryAction(int labelRes) {
        primaryAction.setText(labelRes);
        primaryAction.setOnClickListener(
                v -> startActivity(RecordCostActivity.intent(this, rideId)));
        PaymentUi.show(primaryAction, true);
    }

    private static Payment shareOf(List<Payment> shares, String userId) {
        for (Payment share : shares) {
            if (userId.equals(share.userId)) {
                return share;
            }
        }
        return null;
    }

    private void addShareRow(Payment share, long cents) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_share_row, sharesList, false);
        String name = PaymentUi.displayName(
                this, share.userId, uid, details.participants, details.names);
        ((TextView) row.findViewById(R.id.shareName)).setText(name);
        ((TextView) row.findViewById(R.id.shareAmount)).setText(Money.format(cents));
        TextView status = row.findViewById(R.id.shareStatus);
        status.setText(PaymentUi.shareStatus(this, share));
        if (share.flagged) {
            status.setTextColor(colorError());
        }
        if (!share.flagged && PaymentService.isOverdue(share, System.currentTimeMillis())) {
            Button flag = row.findViewById(R.id.shareFlag);
            flag.setContentDescription(getString(R.string.payment_flag_name, name));
            flag.setOnClickListener(v -> confirmFlag(share, name, cents));
            PaymentUi.show(flag, true);
        }
        sharesList.addView(row);
    }

    private void confirmMarkPaid(long cents) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.payment_mark_paid_title, Money.format(cents)))
                .setMessage(getString(R.string.payment_mark_paid_body, organizerName()))
                .setNegativeButton(R.string.payment_mark_paid_cancel, null)
                .setPositiveButton(R.string.payment_mark_paid, (d, w) ->
                        runAction(paymentService.markPaid(rideId, uid)))
                .show();
    }

    private void confirmFlag(Payment share, String name, long cents) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.payment_flag_title, name))
                .setMessage(getString(R.string.payment_flag_body, Money.format(cents),
                        PaymentService.APPROVAL_PERIOD_DAYS))
                .setNegativeButton(R.string.payment_flag_cancel, null)
                .setPositiveButton(getString(R.string.payment_flag_name, name), (d, w) ->
                        runAction(paymentService.flag(rideId, share.userId)))
                .show();
    }

    // The share listener redraws the screen on success, so only failure needs handling here.
    private void runAction(Task<Void> action) {
        PaymentUi.show(actionError, false);
        primaryAction.setEnabled(false);
        action.addOnCompleteListener(this, task -> {
            primaryAction.setEnabled(true);
            if (!task.isSuccessful()) {
                actionError.setText(PaymentUi.errorMessage(this, task.getException()));
                PaymentUi.show(actionError, true);
            }
        });
    }

    private void showAmountHeader(String label, String value, String detail) {
        amountLabel.setText(label);
        amountValue.setText(value);
        amountDetail.setText(detail);
        PaymentUi.show(amountLabel, !label.isEmpty());
        PaymentUi.show(amountDetail, !detail.isEmpty());
    }

    private void showStatus(String status, boolean alert) {
        statusText.setText(status);
        statusText.setTextColor(alert ? colorError()
                : MaterialColors.getColor(statusText, com.google.android.material.R.attr.colorOnSurface));
        PaymentUi.show(statusText, true);
    }

    private int colorError() {
        return MaterialColors.getColor(statusText, androidx.appcompat.R.attr.colorError);
    }

    private void showDetail() {
        PaymentUi.show(stateContainer, false);
        PaymentUi.show(detailScroll, true);
    }

    private void showLoading() {
        PaymentUi.show(detailScroll, false);
        PaymentUi.show(stateContainer, true);
        PaymentUi.show(stateProgress, true);
        PaymentUi.show(stateMessage, false);
        PaymentUi.show(stateRetry, false);
    }

    private void showMessage(String message, boolean canRetry) {
        PaymentUi.show(detailScroll, false);
        stateMessage.setText(message);
        PaymentUi.show(stateContainer, true);
        PaymentUi.show(stateProgress, false);
        PaymentUi.show(stateMessage, true);
        PaymentUi.show(stateRetry, canRetry);
    }
}
