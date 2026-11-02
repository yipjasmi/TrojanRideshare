package com.trojanrideshare.app.ui.payment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.RidePaymentDetails;
import com.trojanrideshare.app.service.payment.CostSplitter;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.service.validation.PaymentValidator;
import com.trojanrideshare.app.util.Money;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordCostActivity extends AppCompatActivity {
    public static final String EXTRA_RIDE_ID = "rideId";

    private static final String STATE_CUSTOM_AMOUNTS = "customAmounts";

    private static final class SplitRow {
        String name;
        TextView evenAmount;
        TextInputLayout customLayout;
        TextInputEditText customInput;
    }

    private final List<SplitRow> rows = new ArrayList<>();
    private final TextWatcher previewWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence text, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable text) {
            refreshPreview();
        }
    };

    private PaymentService paymentService;
    private String rideId;
    private String currentUserId;
    private RidePaymentDetails details;
    private ArrayList<String> restoredCustomAmounts;
    private boolean saving;

    private View stateContainer;
    private View stateProgress;
    private TextView stateMessage;
    private View stateRetry;
    private View formScroll;
    private View formFooter;
    private TextInputLayout costLayout;
    private TextInputEditText costInput;
    private MaterialButtonToggleGroup splitModeGroup;
    private LinearLayout splitRows;
    private TextView splitSummary;
    private TextView formError;
    private Button reviewButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_record_cost);
        PaymentUi.setUpScreen(this);

        paymentService = new PaymentService();
        stateContainer = findViewById(R.id.stateContainer);
        stateProgress = findViewById(R.id.stateProgress);
        stateMessage = findViewById(R.id.stateMessage);
        stateRetry = findViewById(R.id.stateRetry);
        formScroll = findViewById(R.id.formScroll);
        formFooter = findViewById(R.id.formFooter);
        costLayout = findViewById(R.id.costLayout);
        costInput = findViewById(R.id.costInput);
        splitModeGroup = findViewById(R.id.splitModeGroup);
        splitRows = findViewById(R.id.splitRows);
        splitSummary = findViewById(R.id.splitSummary);
        formError = findViewById(R.id.formError);
        reviewButton = findViewById(R.id.reviewButton);

        if (savedInstanceState != null) {
            restoredCustomAmounts = savedInstanceState.getStringArrayList(STATE_CUSTOM_AMOUNTS);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        if (user == null || rideId == null) {
            showBlocked(getString(R.string.payment_signed_out));
            return;
        }
        currentUserId = user.getUid();

        stateRetry.setOnClickListener(view -> load());
        costInput.addTextChangedListener(previewWatcher);
        splitModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                onSplitModeChanged();
            }
        });
        reviewButton.setOnClickListener(view -> review());
        load();
    }

    // The rows are built after loading, so Android can't restore what was typed into them.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> amounts = new ArrayList<>();
        for (SplitRow row : rows) {
            amounts.add(text(row.customInput));
        }
        outState.putStringArrayList(STATE_CUSTOM_AMOUNTS, amounts);
    }

    private void load() {
        showLoading();
        paymentService.loadRideDetails(
                rideId,
                currentUserId,
                new PaymentService.RideDetailsCallback() {
                    @Override
                    public void onSuccess(RidePaymentDetails loadedDetails) {
                        details = loadedDetails;
                        String blocked = PaymentService.splitBlockedReason(
                                details.ride,
                                currentUserId,
                                details.shares
                        );
                        if (blocked != null) {
                            showBlocked(blocked);
                            return;
                        }
                        bindForm();
                    }

                    @Override
                    public void onError(String message) {
                        showLoadError(message);
                    }
                }
        );
    }

    private void bindForm() {
        ((TextView) findViewById(R.id.rideTitle)).setText(
                PaymentUi.rideTitle(this, details.ride.title)
        );
        ((TextView) findViewById(R.id.rideMeta)).setText(
                PaymentUi.rideMeta(this, details.ride)
        );

        rows.clear();
        splitRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String userId : details.participants) {
            View view = inflater.inflate(R.layout.item_split_row, splitRows, false);
            SplitRow row = new SplitRow();
            row.name = PaymentUi.displayName(
                    this,
                    userId,
                    currentUserId,
                    details.participants,
                    details.names
            );
            row.evenAmount = view.findViewById(R.id.splitEvenAmount);
            row.customLayout = view.findViewById(R.id.splitCustomLayout);
            row.customInput = view.findViewById(R.id.splitCustomInput);
            row.customLayout.setHint(R.string.payment_amount_label);
            row.customInput.setContentDescription(
                    getString(R.string.payment_amount_for, row.name)
            );
            row.customInput.addTextChangedListener(previewWatcher);
            ((TextView) view.findViewById(R.id.splitName)).setText(row.name);
            rows.add(row);
            splitRows.addView(view);
        }

        if (restoredCustomAmounts != null && restoredCustomAmounts.size() == rows.size()) {
            for (int index = 0; index < rows.size(); index++) {
                rows.get(index).customInput.setText(restoredCustomAmounts.get(index));
            }
        } else {
            prefillExistingSplit();
        }
        formScroll.setVisibility(View.VISIBLE);
        formFooter.setVisibility(View.VISIBLE);
        stateContainer.setVisibility(View.GONE);
        onSplitModeChanged();
    }

    private void prefillExistingSplit() {
        if (details.ride.finalCost == null || details.shares.isEmpty()) {
            return;
        }
        long finalCents = Money.toCents(details.ride.finalCost);
        Map<String, Long> savedCents = new HashMap<>();
        for (Payment share : details.shares) {
            savedCents.put(share.userId, Money.toCents(share.amount));
        }
        long[] savedShares = new long[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            Long cents = savedCents.get(details.participants.get(index));
            savedShares[index] = cents == null ? 0 : cents;
            rows.get(index).customInput.setText(Money.formatForInput(savedShares[index]));
        }
        costInput.setText(Money.formatForInput(finalCents));
        if (!Arrays.equals(savedShares, CostSplitter.even(finalCents, rows.size()))) {
            splitModeGroup.check(R.id.splitCustomButton);
        }
    }

    private boolean isCustom() {
        return splitModeGroup.getCheckedButtonId() == R.id.splitCustomButton;
    }

    private void onSplitModeChanged() {
        // Android restores the fields on rotation before the ride has loaded.
        if (rows.isEmpty()) {
            return;
        }
        boolean custom = isCustom();
        PaymentValidator.Result cost = PaymentValidator.finalCost(text(costInput));
        long[] evenShares = cost.isValid() ? CostSplitter.even(cost.cents, rows.size()) : null;
        for (int index = 0; index < rows.size(); index++) {
            SplitRow row = rows.get(index);
            row.evenAmount.setVisibility(custom ? View.GONE : View.VISIBLE);
            row.customLayout.setVisibility(custom ? View.VISIBLE : View.GONE);
            if (custom && evenShares != null && text(row.customInput).isEmpty()) {
                row.customInput.setText(Money.formatForInput(evenShares[index]));
            }
        }
        refreshPreview();
    }

    private void refreshPreview() {
        if (rows.isEmpty()) {
            return;
        }
        PaymentValidator.Result cost = PaymentValidator.finalCost(text(costInput));
        formError.setVisibility(View.GONE);
        costLayout.setError(null);
        for (SplitRow row : rows) {
            row.customLayout.setError(null);
        }
        if (isCustom()) {
            showCustomSummary(cost);
            return;
        }
        long[] evenShares = cost.isValid() ? CostSplitter.even(cost.cents, rows.size()) : null;
        for (int index = 0; index < rows.size(); index++) {
            rows.get(index).evenAmount.setText(
                    evenShares == null ? "" : Money.format(evenShares[index])
            );
        }
        splitSummary.setText(evenShares == null
                ? R.string.payment_enter_cost_hint
                : R.string.payment_even_note);
    }

    private void showCustomSummary(PaymentValidator.Result cost) {
        if (!cost.isValid()) {
            splitSummary.setText(R.string.payment_enter_cost_hint);
            return;
        }
        long assigned = 0;
        for (SplitRow row : rows) {
            PaymentValidator.Result share = PaymentValidator.share(text(row.customInput));
            if (share.isValid()) {
                assigned += share.cents;
            }
        }
        String total = Money.format(assigned);
        if (assigned == cost.cents) {
            splitSummary.setText(getString(R.string.payment_custom_exact, total));
        } else if (assigned < cost.cents) {
            splitSummary.setText(getString(
                    R.string.payment_custom_left,
                    total,
                    Money.format(cost.cents - assigned)
            ));
        } else {
            splitSummary.setText(getString(
                    R.string.payment_custom_over,
                    total,
                    Money.format(assigned - cost.cents)
            ));
        }
    }

    private void review() {
        PaymentValidator.Result cost = PaymentValidator.finalCost(text(costInput));
        costLayout.setError(cost.error);
        if (!cost.isValid()) {
            costInput.requestFocus();
            return;
        }
        long[] shares = isCustom()
                ? readCustomShares(cost.cents)
                : CostSplitter.even(cost.cents, rows.size());
        if (shares != null) {
            showReview(cost.cents, shares);
        }
    }

    private long[] readCustomShares(long finalCents) {
        long[] shares = new long[rows.size()];
        boolean valid = true;
        for (int index = 0; index < rows.size(); index++) {
            SplitRow row = rows.get(index);
            PaymentValidator.Result share = PaymentValidator.share(text(row.customInput));
            row.customLayout.setError(share.error);
            valid &= share.isValid();
            shares[index] = share.cents;
        }
        if (!valid) {
            return null;
        }
        String splitError = PaymentValidator.splitError(finalCents, shares);
        if (splitError != null) {
            showFormError(splitError);
            return null;
        }
        return shares;
    }

    private void showReview(long finalCents, long[] shares) {
        StringBuilder message = new StringBuilder();
        for (int index = 0; index < rows.size(); index++) {
            message.append(getString(
                    R.string.payment_review_line,
                    rows.get(index).name,
                    Money.format(shares[index])
            )).append('\n');
        }
        message.append('\n')
                .append(getString(R.string.payment_review_total, Money.format(finalCents)))
                .append("\n\n")
                .append(getString(R.string.payment_review_note));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.payment_review_title)
                .setMessage(message)
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(
                        R.string.payment_review_save,
                        (dialog, which) -> save(finalCents, shares)
                )
                .show();
    }

    private void save(long finalCents, long[] shares) {
        if (saving) {
            return;
        }
        setSaving(true);
        paymentService.saveSplit(
                rideId,
                currentUserId,
                details.participants,
                shares,
                finalCents,
                new PaymentService.UpdateCallback() {
                    @Override
                    public void onSuccess() {
                        setSaving(false);
                        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                            showSavedRide();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        setSaving(false);
                        showFormError(message);
                    }
                }
        );
    }

    private void showSavedRide() {
        Intent rideIntent = new Intent(this, RidePaymentActivity.class);
        rideIntent.putExtra(RidePaymentActivity.EXTRA_RIDE_ID, rideId);
        rideIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(rideIntent);
        finish();
    }

    private void setSaving(boolean saving) {
        this.saving = saving;
        reviewButton.setEnabled(!saving);
        reviewButton.setText(saving ? R.string.payment_saving : R.string.payment_review);
    }

    private void showFormError(String message) {
        formError.setText(message);
        formError.setVisibility(View.VISIBLE);
    }

    private void showLoading() {
        formScroll.setVisibility(View.GONE);
        formFooter.setVisibility(View.GONE);
        stateContainer.setVisibility(View.VISIBLE);
        stateProgress.setVisibility(View.VISIBLE);
        stateMessage.setVisibility(View.GONE);
        stateRetry.setVisibility(View.GONE);
    }

    private void showLoadError(String message) {
        showBlocked(message);
        stateRetry.setVisibility(View.VISIBLE);
    }

    private void showBlocked(String message) {
        formScroll.setVisibility(View.GONE);
        formFooter.setVisibility(View.GONE);
        stateMessage.setText(message);
        stateContainer.setVisibility(View.VISIBLE);
        stateProgress.setVisibility(View.GONE);
        stateMessage.setVisibility(View.VISIBLE);
        stateRetry.setVisibility(View.GONE);
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
