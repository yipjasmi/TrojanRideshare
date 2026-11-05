package com.trojanrideshare.app.ui.payment;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.PaymentHandles;
import com.trojanrideshare.app.model.RidePaymentDetails;
import com.trojanrideshare.app.service.payment.PaymentLinks;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.service.validation.PaymentValidator;
import com.trojanrideshare.app.ui.profile.ProfileActivity;
import com.trojanrideshare.app.util.Haptics;
import com.trojanrideshare.app.util.Money;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RidePaymentActivity extends AppCompatActivity {
    public static final String EXTRA_RIDE_ID = "rideId";

    private static final String STATE_PAID_HINT_APP = "paidHintApp";

    private PaymentService paymentService;
    private String rideId;
    private String currentUserId;
    private RidePaymentDetails details;
    private String paidHintApp;
    private final Set<String> unpaidUserIds = new HashSet<>();

    private View stateContainer;
    private View stateProgress;
    private TextView stateMessage;
    private View stateRetry;
    private View detailScroll;
    private TextView amountLabel;
    private TextView amountValue;
    private TextView amountDetail;
    private TextView statusText;
    private View paidCheck;
    private TextView actionError;
    private Button payVenmoButton;
    private Button payCashAppButton;
    private TextView paidHint;
    private Button primaryAction;
    private View paymentAppsNudge;
    private TextView sharesTitle;
    private LinearLayout sharesList;
    private TextView footnote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ride_payment);
        PaymentUi.setUpScreen(this);

        paymentService = new PaymentService();
        stateContainer = findViewById(R.id.stateContainer);
        stateProgress = findViewById(R.id.stateProgress);
        stateMessage = findViewById(R.id.stateMessage);
        stateRetry = findViewById(R.id.stateRetry);
        detailScroll = findViewById(R.id.detailScroll);
        amountLabel = findViewById(R.id.amountLabel);
        amountValue = findViewById(R.id.amountValue);
        amountDetail = findViewById(R.id.amountDetail);
        statusText = findViewById(R.id.statusText);
        paidCheck = findViewById(R.id.paidCheck);
        actionError = findViewById(R.id.actionError);
        payVenmoButton = findViewById(R.id.payVenmoButton);
        payCashAppButton = findViewById(R.id.payCashAppButton);
        paidHint = findViewById(R.id.paidHint);
        primaryAction = findViewById(R.id.primaryAction);
        paymentAppsNudge = findViewById(R.id.paymentAppsNudge);
        sharesTitle = findViewById(R.id.sharesTitle);
        sharesList = findViewById(R.id.sharesList);
        footnote = findViewById(R.id.footnote);
        if (savedInstanceState != null) {
            paidHintApp = savedInstanceState.getString(STATE_PAID_HINT_APP);
        }

        stateRetry.setOnClickListener(view -> load());
        // With the user's own id the profile opens as a page with a back arrow, not as the Profile
        // tab, whose bottom nav would close this screen.
        findViewById(R.id.paymentAppsNudgeAction).setOnClickListener(view -> {
            Intent profile = new Intent(this, ProfileActivity.class);
            profile.putExtra(ProfileActivity.EXTRA_USER_ID, currentUserId);
            startActivity(profile);
        });
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        if (user == null || rideId == null) {
            showMessage(getString(R.string.payment_signed_out), false);
            return;
        }
        currentUserId = user.getUid();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUserId != null) {
            load();
        }
    }

    // Android can kill this screen while Venmo is open, and the paid hint has to survive that.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PAID_HINT_APP, paidHintApp);
    }

    private void load() {
        if (details == null) {
            showLoading();
        }
        paymentService.loadRideDetails(
                rideId,
                currentUserId,
                new PaymentService.RideDetailsCallback() {
                    @Override
                    public void onSuccess(RidePaymentDetails loadedDetails) {
                        // Firestore crashes binding a listener to an activity that is gone.
                        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                            showRide(loadedDetails);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        showMessage(message, true);
                    }
                }
        );
    }

    private void showRide(RidePaymentDetails loadedDetails) {
        details = loadedDetails;
        if (!details.participants.contains(currentUserId)) {
            showMessage(getString(R.string.payment_not_participant), false);
            return;
        }
        ((TextView) findViewById(R.id.rideTitle)).setText(
                PaymentUi.rideTitle(this, details.ride.title)
        );
        ((TextView) findViewById(R.id.rideMeta)).setText(
                PaymentUi.rideMeta(this, details.ride)
        );
        paymentService.watchShares(
                this,
                details.ride,
                currentUserId,
                new PaymentService.SharesCallback() {
                    @Override
                    public void onSuccess(List<Payment> shares) {
                        if (isOrganizer()) {
                            renderOrganizer(shares);
                        } else {
                            renderRider(shares.isEmpty() ? null : shares.get(0));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        showMessage(message, true);
                    }
                }
        );
    }

    private boolean isOrganizer() {
        return currentUserId.equals(details.ride.organizerId);
    }

    private String organizerName() {
        String name = details.names.get(details.ride.organizerId);
        return name != null ? name : getString(R.string.payment_the_organizer);
    }

    private void renderRider(Payment share) {
        showDetail();
        sharesList.removeAllViews();
        sharesTitle.setVisibility(View.GONE);
        primaryAction.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
        paidCheck.setVisibility(View.GONE);
        payVenmoButton.setVisibility(View.GONE);
        payCashAppButton.setVisibility(View.GONE);
        paidHint.setVisibility(View.GONE);

        if (share == null) {
            showAmountHeader("", getString(R.string.payment_no_cost_yet), "");
            footnote.setText(getString(R.string.payment_no_cost_yet_rider, organizerName()));
            return;
        }

        long cents = Money.toCents(share.amount);
        showAmountHeader(
                getString(R.string.payment_your_share),
                Money.format(cents),
                details.ride.finalCost == null
                        ? ""
                        : getString(
                                R.string.payment_of_final_cost,
                                Money.format(Money.toCents(details.ride.finalCost))
                        )
        );
        showStatus(PaymentUi.shareStatus(this, share), share.flagged);
        boolean justPaid = justPaid(share);

        if (share.paid) {
            if (share.paidAt != null) {
                PaymentUi.showPaidCheck(paidCheck, justPaid);
            }
            footnote.setText(R.string.payment_outside_app);
            return;
        }
        footnote.setText(getString(R.string.payment_reimburse, organizerName()));
        showPayButtons(cents);
        primaryAction.setText(R.string.payment_mark_paid);
        primaryAction.setOnClickListener(view -> confirmMarkPaid(cents));
        primaryAction.setVisibility(View.VISIBLE);
    }

    private void renderOrganizer(List<Payment> shares) {
        showDetail();
        paymentAppsNudge.setVisibility(hasPaymentApp(currentUserId) ? View.GONE : View.VISIBLE);
        footnote.setText(R.string.payment_outside_app);
        statusText.setVisibility(View.GONE);
        sharesList.removeAllViews();

        if (shares.isEmpty()) {
            showAmountHeader(
                    "",
                    getString(R.string.payment_no_cost_yet),
                    getString(R.string.payment_no_cost_yet_organizer)
            );
            sharesTitle.setVisibility(View.GONE);
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

        showAmountHeader(
                getString(R.string.payment_outstanding),
                Money.format(outstanding),
                getString(R.string.payment_of_final_cost, Money.format(finalCents))
        );
        sharesTitle.setVisibility(View.VISIBLE);
        if (Payment.hasRiderMarkedPaid(shares)) {
            primaryAction.setVisibility(View.GONE);
            footnote.setText(getString(R.string.payment_locked_note) + " "
                    + getString(R.string.payment_outside_app));
        } else {
            showPrimaryAction(R.string.payment_edit_split);
        }
    }

    private void showPrimaryAction(int labelRes) {
        primaryAction.setText(labelRes);
        primaryAction.setOnClickListener(view -> {
            Intent costIntent = new Intent(this, RecordCostActivity.class);
            costIntent.putExtra(RecordCostActivity.EXTRA_RIDE_ID, rideId);
            startActivity(costIntent);
        });
        primaryAction.setVisibility(View.VISIBLE);
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
                this,
                share.userId,
                currentUserId,
                details.participants,
                details.names
        );
        ((TextView) row.findViewById(R.id.shareName)).setText(name);
        ((TextView) row.findViewById(R.id.shareAmount)).setText(Money.format(cents));
        TextView status = row.findViewById(R.id.shareStatus);
        status.setText(PaymentUi.shareStatus(this, share));
        if (share.flagged) {
            status.setTextColor(colorError());
        }
        boolean justPaid = justPaid(share);
        if (share.paidAt != null) {
            PaymentUi.showPaidCheck(row.findViewById(R.id.shareCheck), justPaid);
        }
        if (!share.flagged && share.isOverdue(System.currentTimeMillis())) {
            Button flagButton = row.findViewById(R.id.shareFlag);
            flagButton.setContentDescription(getString(R.string.payment_flag_name, name));
            flagButton.setOnClickListener(view -> confirmFlag(share, name, cents));
            flagButton.setVisibility(View.VISIBLE);
        }
        if (!share.paid) {
            showRequestButton(row.findViewById(R.id.shareRequest), share, name, cents);
        }
        sharesList.addView(row);
    }

    private void showRequestButton(
            Button requestButton,
            Payment share,
            String name,
            long cents
    ) {
        String link = PaymentLinks.venmoCharge(
                handlesOf(share.userId).venmoUsername,
                cents,
                details.ride.title
        );
        if (link == null) {
            return;
        }
        requestButton.setContentDescription(
                getString(R.string.payment_request_venmo_name, Money.format(cents), name)
        );
        requestButton.setOnClickListener(
                view -> openPaymentApp(link, getString(R.string.payment_app_venmo))
        );
        requestButton.setVisibility(View.VISIBLE);
    }

    private void showPayButtons(long cents) {
        PaymentHandles organizer = handlesOf(details.ride.organizerId);
        showPayButton(
                payVenmoButton,
                PaymentLinks.venmoPay(organizer.venmoUsername, cents, details.ride.title),
                R.string.payment_pay_venmo,
                R.string.payment_app_venmo,
                cents
        );
        showPayButton(
                payCashAppButton,
                PaymentLinks.cashApp(organizer.cashtag),
                R.string.payment_pay_cash_app,
                R.string.payment_app_cash_app,
                cents
        );
        showPaidHint();
    }

    private void showPayButton(
            Button button,
            String link,
            int labelRes,
            int appRes,
            long cents
    ) {
        if (link == null) {
            return;
        }
        button.setText(getString(labelRes, organizerName(), Money.format(cents)));
        button.setOnClickListener(view -> {
            String app = getString(appRes);
            if (openPaymentApp(link, app)) {
                paidHintApp = app;
                showPaidHint();
            }
        });
        button.setVisibility(View.VISIBLE);
    }

    // Opening Venmo proves nothing about paying, so this only nudges. Marking paid stays manual.
    private void showPaidHint() {
        if (paidHintApp == null) {
            return;
        }
        paidHint.setText(getString(R.string.payment_paid_hint, paidHintApp));
        paidHint.setVisibility(View.VISIBLE);
    }

    private boolean openPaymentApp(String link, String app) {
        actionError.setVisibility(View.GONE);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            return true;
        } catch (ActivityNotFoundException exception) {
            actionError.setText(getString(R.string.payment_app_missing, app));
            actionError.setVisibility(View.VISIBLE);
            return false;
        }
    }

    private PaymentHandles handlesOf(String userId) {
        PaymentHandles handles = details.handles.get(userId);
        return handles != null ? handles : new PaymentHandles(null, null);
    }

    private boolean hasPaymentApp(String userId) {
        PaymentHandles handles = handlesOf(userId);
        return PaymentValidator.venmoUsername(handles.venmoUsername).handle != null
                || PaymentValidator.cashtag(handles.cashtag).handle != null;
    }

    // True once, when a share this screen showed as unpaid comes back paid.
    private boolean justPaid(Payment share) {
        if (!share.paid) {
            unpaidUserIds.add(share.userId);
            return false;
        }
        return unpaidUserIds.remove(share.userId);
    }

    private void confirmMarkPaid(long cents) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.payment_mark_paid_title, Money.format(cents)))
                .setMessage(getString(R.string.payment_mark_paid_body, organizerName()))
                .setNegativeButton(R.string.payment_mark_paid_cancel, null)
                .setPositiveButton(
                        R.string.payment_mark_paid,
                        (dialog, which) -> markPaid()
                )
                .show();
    }

    private void confirmFlag(Payment share, String name, long cents) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.payment_flag_title, name))
                .setMessage(getString(
                        R.string.payment_flag_body,
                        Money.format(cents),
                        Payment.APPROVAL_PERIOD_DAYS
                ))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        getString(R.string.payment_flag_name, name),
                        (dialog, which) -> flagRider(share.userId)
                )
                .show();
    }

    private void markPaid() {
        startAction();
        paymentService.markPaid(rideId, currentUserId, actionCallback());
    }

    private void flagRider(String riderId) {
        startAction();
        paymentService.flag(rideId, riderId, actionCallback());
    }

    private void startAction() {
        actionError.setVisibility(View.GONE);
        primaryAction.setEnabled(false);
    }

    private PaymentService.UpdateCallback actionCallback() {
        return new PaymentService.UpdateCallback() {
            @Override
            public void onSuccess() {
                primaryAction.setEnabled(true);
                Haptics.confirm(detailScroll);
            }

            @Override
            public void onError(String message) {
                primaryAction.setEnabled(true);
                actionError.setText(message);
                actionError.setVisibility(View.VISIBLE);
                Haptics.reject(detailScroll);
            }
        };
    }

    private void showAmountHeader(String label, String value, String detail) {
        amountLabel.setText(label);
        amountValue.setText(value);
        amountDetail.setText(detail);
        amountLabel.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        amountDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showStatus(String status, boolean alert) {
        statusText.setText(status);
        statusText.setTextColor(alert
                ? colorError()
                : MaterialColors.getColor(
                        statusText,
                        com.google.android.material.R.attr.colorOnSurface
                ));
        statusText.setVisibility(View.VISIBLE);
    }

    private int colorError() {
        return MaterialColors.getColor(statusText, androidx.appcompat.R.attr.colorError);
    }

    private void showDetail() {
        stateContainer.setVisibility(View.GONE);
        detailScroll.setVisibility(View.VISIBLE);
    }

    private void showLoading() {
        detailScroll.setVisibility(View.GONE);
        stateContainer.setVisibility(View.VISIBLE);
        stateProgress.setVisibility(View.VISIBLE);
        stateMessage.setVisibility(View.GONE);
        stateRetry.setVisibility(View.GONE);
    }

    private void showMessage(String message, boolean canRetry) {
        detailScroll.setVisibility(View.GONE);
        stateMessage.setText(message);
        stateContainer.setVisibility(View.VISIBLE);
        stateProgress.setVisibility(View.GONE);
        stateMessage.setVisibility(View.VISIBLE);
        stateRetry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }
}
