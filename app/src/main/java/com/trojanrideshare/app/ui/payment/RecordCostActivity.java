package com.trojanrideshare.app.ui.payment;

import android.content.Context;
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

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Payment;
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
    private static final String EXTRA_RIDE_ID = "rideId";

    public static Intent intent(Context context, String rideId) {
        return new Intent(context, RecordCostActivity.class).putExtra(EXTRA_RIDE_ID, rideId);
    }

    private static final class SplitRow {
        String name;
        TextView evenAmount;
        TextInputLayout customLayout;
        TextInputEditText customInput;
    }

    private final PaymentService paymentService = new PaymentService();
    private final List<SplitRow> rows = new ArrayList<>();
    private final TextWatcher previewWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            refreshPreview();
        }
    };

    private String rideId;
    private String uid;
    private PaymentService.RideDetails details;
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

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        if (user == null || rideId == null) {
            showBlocked(getString(R.string.payment_signed_out));
            return;
        }
        uid = user.getUid();

        stateRetry.setOnClickListener(v -> load());
        costInput.addTextChangedListener(previewWatcher);
        splitModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                onSplitModeChanged();
            }
        });
        reviewButton.setOnClickListener(v -> review());
        load();
    }

    private void load() {
        showLoading();
        paymentService.loadRideDetails(rideId, uid).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                showLoadError(PaymentUi.errorMessage(this, task.getException()));
                return;
            }
            details = task.getResult();
            String blocked = PaymentService.splitBlockedReason(details.ride, uid, details.shares);
            if (blocked != null) {
                showBlocked(blocked);
                return;
            }
            bindForm();
        });
    }

    private void bindForm() {
        ((TextView) findViewById(R.id.rideTitle))
                .setText(PaymentUi.rideTitle(this, details.ride.title));
        ((TextView) findViewById(R.id.rideMeta)).setText(PaymentUi.rideMeta(this, details.ride));

        rows.clear();
        splitRows.removeAllViews();
        for (String userId : details.participants) {
            View view = LayoutInflater.from(this).inflate(R.layout.item_split_row, splitRows, false);
            SplitRow row = new SplitRow();
            row.name = PaymentUi.displayName(
                    this, userId, uid, details.participants, details.names);
            row.evenAmount = view.findViewById(R.id.splitEvenAmount);
            row.customLayout = view.findViewById(R.id.splitCustomLayout);
            row.customInput = view.findViewById(R.id.splitCustomInput);
            row.customLayout.setHint(R.string.payment_amount_label);
            row.customInput.setContentDescription(getString(R.string.payment_amount_for, row.name));
            row.customInput.addTextChangedListener(previewWatcher);
            ((TextView) view.findViewById(R.id.splitName)).setText(row.name);
            rows.add(row);
            splitRows.addView(view);
        }

        prefillExistingSplit();
        formScroll.setVisibility(View.VISIBLE);
        formFooter.setVisibility(View.VISIBLE);
        stateContainer.setVisibility(View.GONE);
        refreshPreview();
    }

    // Opening this screen for an existing split starts from what was saved.
    private void prefillExistingSplit() {
        if (details.ride.finalCost == null || details.shares.isEmpty()) {
            return;
        }
        long finalCents = Money.toCents(details.ride.finalCost);
        Map<String, Long> saved = new HashMap<>();
        for (Payment share : details.shares) {
            saved.put(share.userId, Money.toCents(share.amount));
        }
        long[] savedShares = new long[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Long cents = saved.get(details.participants.get(i));
            savedShares[i] = cents == null ? 0 : cents;
            rows.get(i).customInput.setText(Money.formatForInput(savedShares[i]));
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
        boolean custom = isCustom();
        PaymentValidator.Result cost = PaymentValidator.finalCost(text(costInput));
        long[] even = cost.isValid() ? CostSplitter.even(cost.cents, rows.size()) : null;
        for (int i = 0; i < rows.size(); i++) {
            SplitRow row = rows.get(i);
            PaymentUi.show(row.evenAmount, !custom);
            PaymentUi.show(row.customLayout, custom);
            // Start custom amounts from the even split so people adjust instead of retyping.
            if (custom && even != null && text(row.customInput).isEmpty()) {
                row.customInput.setText(Money.formatForInput(even[i]));
            }
        }
        refreshPreview();
    }

    private void refreshPreview() {
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
        long[] even = cost.isValid() ? CostSplitter.even(cost.cents, rows.size()) : null;
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).evenAmount.setText(even == null ? "" : Money.format(even[i]));
        }
        splitSummary.setText(even == null
                ? R.string.payment_enter_cost_hint : R.string.payment_even_note);
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
                    R.string.payment_custom_left, total, Money.format(cost.cents - assigned)));
        } else {
            splitSummary.setText(getString(
                    R.string.payment_custom_over, total, Money.format(assigned - cost.cents)));
        }
    }

    private void review() {
        PaymentValidator.Result cost = PaymentValidator.finalCost(text(costInput));
        costLayout.setError(cost.error);
        if (!cost.isValid()) {
            costInput.requestFocus();
            return;
        }
        long[] shares = isCustom() ? readCustomShares(cost.cents)
                : CostSplitter.even(cost.cents, rows.size());
        if (shares != null) {
            showReview(cost.cents, shares);
        }
    }

    private long[] readCustomShares(long finalCents) {
        long[] shares = new long[rows.size()];
        boolean valid = true;
        for (int i = 0; i < rows.size(); i++) {
            SplitRow row = rows.get(i);
            PaymentValidator.Result share = PaymentValidator.share(text(row.customInput));
            row.customLayout.setError(share.error);
            valid &= share.isValid();
            shares[i] = share.cents;
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
        for (int i = 0; i < rows.size(); i++) {
            message.append(getString(R.string.payment_review_line,
                    rows.get(i).name, Money.format(shares[i]))).append('\n');
        }
        message.append('\n')
                .append(getString(R.string.payment_review_total, Money.format(finalCents)))
                .append("\n\n")
                .append(getString(R.string.payment_review_note));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.payment_review_title)
                .setMessage(message)
                .setNegativeButton(R.string.payment_review_back, null)
                .setPositiveButton(R.string.payment_review_save, (d, w) -> save(finalCents, shares))
                .show();
    }

    private void save(long finalCents, long[] shares) {
        if (saving) {
            return;
        }
        setSaving(true);
        paymentService.saveSplit(rideId, uid, details.participants, shares, finalCents)
                .addOnCompleteListener(this, task -> {
                    setSaving(false);
                    if (!task.isSuccessful()) {
                        showFormError(PaymentUi.errorMessage(this, task.getException()));
                        return;
                    }
                    startActivity(RidePaymentActivity.intent(this, rideId));
                    finish();
                });
    }

    private void setSaving(boolean value) {
        saving = value;
        reviewButton.setEnabled(!value);
    }

    private void showFormError(String message) {
        formError.setText(message);
        formError.setVisibility(View.VISIBLE);
    }

    private void showLoading() {
        formScroll.setVisibility(View.GONE);
        formFooter.setVisibility(View.GONE);
        PaymentUi.show(stateContainer, true);
        PaymentUi.show(stateProgress, true);
        PaymentUi.show(stateMessage, false);
        PaymentUi.show(stateRetry, false);
    }

    private void showLoadError(String message) {
        showBlocked(message);
        PaymentUi.show(stateRetry, true);
    }

    private void showBlocked(String message) {
        formScroll.setVisibility(View.GONE);
        formFooter.setVisibility(View.GONE);
        stateMessage.setText(message);
        PaymentUi.show(stateContainer, true);
        PaymentUi.show(stateProgress, false);
        PaymentUi.show(stateMessage, true);
        PaymentUi.show(stateRetry, false);
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
