package com.trojanrideshare.app.ui.ride;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.OneShotPreDrawListener;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.data.user.UserDataAccess;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.User;
import com.trojanrideshare.app.service.joinrequest.JoinRequestService;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.service.ride.EditRideService;
import com.trojanrideshare.app.service.ride.LeaveRideService;
import com.trojanrideshare.app.service.ride.RideTimelineService;
import com.trojanrideshare.app.ui.payment.RecordCostActivity;
import com.trojanrideshare.app.ui.profile.ProfileActivity;
import com.trojanrideshare.app.util.BottomNavHelper;
import com.trojanrideshare.app.util.InitialsAvatarView;
import com.trojanrideshare.app.util.Money;
import com.trojanrideshare.app.util.Motion;
import com.trojanrideshare.app.util.RideSeats;
import com.trojanrideshare.app.util.RideStatusChip;
import com.trojanrideshare.app.util.SystemBarsHelper;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Shows a single ride. The organizer gets an Edit entry point; another signed-in student
 * gets a "Request to join" action that delegates to the shared {@link JoinRequestService}.
 * Organizer accept/reject lives in {@code OrganizerRequestsActivity}, not here.
 */
public class RideDetailActivity extends AppCompatActivity {
    public static final String EXTRA_RIDE_ID = "rideId";
    public static final String EXTRA_CURRENT_USER_ID = "currentUserId";
    /** The title the tapped card showed, so the shared title has text before the ride loads. */
    public static final String EXTRA_RIDE_TITLE = "rideTitle";

    private View loadingIndicator;
    private LinearLayout content;
    private TextView detailTitle;
    private InitialsAvatarView organizerAvatar;
    private TextView infoMessage;
    private MaterialButton joinButton;
    private Button editRideButton;
    private Button completeRideButton;
    private Button leaveRideButton;
    private Button cancelRideButton;
    private RideTimelineView timeline;
    private TextView ruleHint;

    private RideDataAccess rideDataAccess;
    private UserDataAccess userDataAccess;
    private JoinRequestService joinRequestService;
    private EditRideService editRideService;
    private LeaveRideService leaveRideService;
    private PaymentService paymentService;
    private String currentUserId;
    private Ride ride;
    /** Null until the payment listener answers. */
    private List<Payment> shares;
    private String sharesError;
    private boolean watchingPayments;

    private ActivityResultLauncher<Intent> editRideLauncher;
    private ActivityResultLauncher<Intent> recordCostLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride_detail);
        SystemBarsHelper.applyPadding(this);
        BottomNavHelper.setupBottomNav(this, BottomNavHelper.NO_TAB);

        rideDataAccess = new RideDataAccess();
        userDataAccess = new UserDataAccess();
        joinRequestService = new JoinRequestService();
        editRideService = new EditRideService();
        leaveRideService = new LeaveRideService();
        paymentService = new PaymentService();
        currentUserId = getIntent().getStringExtra(EXTRA_CURRENT_USER_ID);
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            // Fall back to the signed-in user when the caller didn't pass an id
            // (e.g. opened from search via bottom navigation).
            FirebaseUser signedIn = FirebaseAuth.getInstance().getCurrentUser();
            if (signedIn != null) {
                currentUserId = signedIn.getUid();
            }
        }

        loadingIndicator = findViewById(R.id.detailLoadingIndicator);
        content = findViewById(R.id.detailContent);
        detailTitle = findViewById(R.id.detailTitle);
        organizerAvatar = findViewById(R.id.detailOrganizerAvatar);
        infoMessage = findViewById(R.id.detailInfoMessage);
        joinButton = findViewById(R.id.joinButton);
        editRideButton = findViewById(R.id.editRideButton);
        MaterialToolbar toolbar = findViewById(R.id.rideDetailToolbar);
        toolbar.setNavigationOnClickListener(v -> supportFinishAfterTransition());
        joinButton.setOnClickListener(v -> showJoinDialog());
        completeRideButton = findViewById(R.id.completeRideButton);
        editRideButton.setOnClickListener(v -> openEditRide());
        completeRideButton.setOnClickListener(v -> confirmComplete());
        leaveRideButton = findViewById(R.id.leaveRideButton);
        leaveRideButton.setOnClickListener(v -> confirmLeave());
        cancelRideButton = findViewById(R.id.cancelRideButton);
        cancelRideButton.setOnClickListener(v -> confirmCancel());
        timeline = findViewById(R.id.detailTimeline);
        ruleHint = findViewById(R.id.detailRuleHint);

        editRideLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && ride != null) {
                        loadRide(ride.id);
                    }
                });
        // Reload on any return so the timeline picks up a cost that was just recorded.
        recordCostLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (ride != null) {
                        loadRide(ride.id);
                    }
                });

        String rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        String sharedTitle = getIntent().getStringExtra(EXTRA_RIDE_TITLE);
        if (sharedTitle != null) {
            showTitle(sharedTitle);
            detailTitle.setTransitionName(titleTransitionName(rideId));
            supportPostponeEnterTransition();
            OneShotPreDrawListener.add(detailTitle, this::supportStartPostponedEnterTransition);
        }
        if (rideId == null || rideId.trim().isEmpty()) {
            showLoadError();
            return;
        }
        loadRide(rideId);
    }

    /**
     * One name per ride, set on the tapped title only, so a list of rides never holds two views
     * with the same name and Back finds the right card to return to.
     */
    public static String titleTransitionName(String rideId) {
        return "ride_title_" + rideId;
    }

    private void loadRide(String rideId) {
        loadingIndicator.setVisibility(View.VISIBLE);
        content.setVisibility(View.GONE);
        rideDataAccess.getRideById(rideId, new RideDataAccess.SingleCallback() {
            @Override
            public void onSuccess(Ride loaded) {
                if (loaded == null) {
                    showLoadError();
                    return;
                }
                ride = loaded;
                bind(loaded);
            }

            @Override
            public void onError(Exception exception) {
                showLoadError();
            }
        });
    }

    private void bind(Ride ride) {
        loadingIndicator.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        infoMessage.setVisibility(View.GONE);

        showTitle(emptyFallback(ride.title, getString(R.string.search_untitled_ride)));
        TextView status = findViewById(R.id.detailStatus);
        RideStatusChip.of(ride, Timestamp.now()).applyTo(status);
        status.setVisibility(View.VISIBLE);
        setText(R.id.detailOrganizer, getString(
                R.string.ride_detail_organizer_format,
                getString(R.string.ride_detail_unknown_organizer)));
        organizerAvatar.setUser(null);
        findViewById(R.id.detailOrganizerRow).setOnClickListener(v -> {
            if (ride != null && ride.organizerId != null) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_USER_ID, ride.organizerId);
                startActivity(intent);
            }
        });
        loadOrganizerName(ride.organizerId);
        setText(R.id.detailRoute, getString(
                R.string.search_route_format, addressOf(ride.origin), addressOf(ride.destination)));
        String departureText = getString(
                R.string.search_departure_format, formatDeparture(ride.departureTime));
        if (Ride.STATUS_DELAYED.equals(ride.status) && ride.originalDepartureTime != null) {
            departureText += " (delayed from " + formatDeparture(ride.originalDepartureTime) + ")";
        }
        setText(R.id.detailDeparture, departureText);

        int availableSeats = ride.getAvailableSeats();
        TextView seats = findViewById(R.id.detailSeats);
        RideSeats.bind(seats, ride);
        String cost = getString(
                R.string.ride_detail_cost_format, Money.format(Money.toCents(ride.expectedCost)));
        if (ride.isActive()) {
            cost += " · " + getString(
                    R.string.cost_estimate_each, Money.format(RideSeats.costEachCents(ride)));
        }
        setText(R.id.detailCost, cost);

        renderRiders(ride);
        configureAction(ride, availableSeats);
        renderRuleHint(ride);
        renderTimeline(ride);
        watchPayments(ride);
    }

    private void renderTimeline(Ride ride) {
        Timestamp now = Timestamp.now();
        RideTimelineService.PaymentProgress progress = shares == null
                ? null : RideTimelineService.paymentProgress(ride, currentUserId, shares);
        List<RideTimelineView.Row> rows = new ArrayList<>();
        for (RideTimelineService.StepState step
                : RideTimelineService.steps(ride, currentUserId, now, progress)) {
            rows.add(timelineRow(ride, step, now, progress));
        }
        timeline.setRows(rows);
    }

    private RideTimelineView.Row timelineRow(
            Ride ride,
            RideTimelineService.StepState step,
            Timestamp now,
            RideTimelineService.PaymentProgress progress
    ) {
        int titleRes;
        String detail;
        switch (step.step) {
            case POSTED:
                titleRes = R.string.ride_timeline_posted;
                detail = ride.createdAt == null ? "" : DateUtils.formatDateTime(this,
                        ride.createdAt.toDate().getTime(),
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
                break;
            case SEATS:
                return seatsRow(ride, now, step.state);
            case DEPARTED:
                titleRes = R.string.ride_timeline_departed;
                detail = getString(step.state == RideTimelineService.State.DONE
                        ? R.string.ride_timeline_left
                        : R.string.ride_timeline_leaves, formatWhen(ride.departureTime));
                if (RideTimelineService.wasDelayed(ride)) {
                    detail = getString(R.string.ride_timeline_delayed_from,
                            detail, formatWhen(ride.originalDepartureTime));
                }
                break;
            case COMPLETED:
                titleRes = R.string.ride_timeline_completed;
                detail = completedDetail(ride, step.state);
                break;
            case COST_SPLIT:
                titleRes = R.string.ride_timeline_cost_split;
                detail = costSplitDetail(ride);
                break;
            case PAID:
                titleRes = R.string.ride_timeline_paid;
                detail = paidDetail(ride, progress);
                break;
            case CANCELLED:
            default:
                titleRes = R.string.ride_timeline_cancelled;
                detail = "";
                break;
        }
        return new RideTimelineView.Row(getString(titleRes), detail, detail, step.state);
    }

    /** Dots are read out as "3 of 4 seats taken" instead of symbol names. */
    private RideTimelineView.Row seatsRow(
            Ride ride,
            Timestamp now,
            RideTimelineService.State state
    ) {
        int taken = RideSeats.taken(ride);
        int total = RideSeats.total(ride);
        int availableSeats = ride.getAvailableSeats();
        String takenDescription = getString(R.string.seats_taken_description, taken, total);
        String label;
        if (availableSeats == 0) {
            label = getString(R.string.seats_full);
        } else if (ride.isActive() && !ride.hasDeparted(now)) {
            label = getResources().getQuantityString(
                    R.plurals.ride_timeline_seats_left, availableSeats, availableSeats);
        } else {
            label = takenDescription;
        }
        String spoken = label.equals(takenDescription)
                ? takenDescription : takenDescription + ", " + label;
        return new RideTimelineView.Row(getString(R.string.ride_timeline_seats),
                RideSeats.dotsText(this, taken, total).append("  ").append(label), spoken, state);
    }

    private String completedDetail(Ride ride, RideTimelineService.State state) {
        if (Ride.STATUS_COMPLETED.equals(ride.status)) {
            return ride.completedAt == null ? "" : formatWhen(ride.completedAt);
        }
        if (state != RideTimelineService.State.CURRENT) {
            return "";
        }
        boolean isOrganizer = currentUserId != null && currentUserId.equals(ride.organizerId);
        return getString(isOrganizer
                ? R.string.ride_timeline_complete_now
                : R.string.ride_timeline_waiting_for_organizer);
    }

    private String costSplitDetail(Ride ride) {
        if (!Ride.STATUS_COMPLETED.equals(ride.status) || ride.finalCost == null) {
            return getString(R.string.ride_timeline_not_recorded);
        }
        int ways = ride.getAllParticipantIds().size();
        return getResources().getQuantityString(R.plurals.ride_timeline_cost_split_ways, ways,
                Money.format(Money.toCents(ride.finalCost)), ways);
    }

    private String paidDetail(Ride ride, RideTimelineService.PaymentProgress progress) {
        if (!RideTimelineService.hasPayments(ride, currentUserId)) {
            return "";
        }
        if (shares == null) {
            return sharesError != null
                    ? sharesError : getString(R.string.ride_timeline_checking_payments);
        }
        if (currentUserId.equals(ride.organizerId)) {
            return progress.total == 0
                    ? getString(R.string.payment_no_riders)
                    : getResources().getQuantityString(R.plurals.payment_riders_paid,
                            progress.total, progress.paid, progress.total);
        }
        Payment share = RideTimelineService.shareOf(shares, currentUserId);
        if (share == null) {
            return getString(R.string.ride_timeline_not_recorded);
        }
        long cents = Money.toCents(share.amount);
        if (cents == 0) {
            return getString(R.string.ride_timeline_nothing_owed);
        }
        return getString(share.paid
                ? R.string.ride_timeline_you_paid
                : R.string.ride_timeline_you_owe, Money.format(cents));
    }

    /** Organizers get every share on the ride and riders only their own, as the rules allow. */
    private void watchPayments(Ride ride) {
        // Firestore crashes binding a listener to an activity that is gone.
        if (watchingPayments || isDestroyed()
                || !RideTimelineService.hasPayments(ride, currentUserId)) {
            return;
        }
        watchingPayments = true;
        paymentService.watchShares(this, ride, currentUserId, new PaymentService.SharesCallback() {
            @Override
            public void onSuccess(List<Payment> loaded) {
                shares = loaded;
                sharesError = null;
                renderTimeline(ride);
            }

            @Override
            public void onError(String message) {
                shares = null;
                sharesError = message;
                renderTimeline(ride);
            }
        });
    }

    private void renderRuleHint(Ride ride) {
        String hint;
        switch (RideTimelineService.hint(ride, currentUserId, Timestamp.now())) {
            case LEAVE_UNTIL:
                hint = getString(R.string.ride_hint_leave_until,
                        formatWhen(RideTimelineService.leaveCutoff(ride)));
                break;
            case LEAVING_CLOSED:
                hint = getResources().getQuantityString(R.plurals.ride_hint_leaving_closed,
                        RideTimelineService.leaveCutoffHours(),
                        RideTimelineService.leaveCutoffHours());
                break;
            case REQUESTS_CLOSE:
                hint = getString(R.string.ride_hint_requests_close, formatWhen(ride.departureTime));
                break;
            case COMPLETE_AFTER_DEPARTURE:
                hint = getString(R.string.ride_hint_complete_after_departure);
                break;
            case NONE:
            default:
                hint = "";
                break;
        }
        ruleHint.setText(hint);
        ruleHint.setVisibility(hint.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** One tappable row per rider (organizer first), opening that person's profile. */
    private void renderRiders(Ride ride) {
        LinearLayout container = findViewById(R.id.detailRidersContainer);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String riderId : ride.getAllParticipantIds()) {
            boolean organizer = riderId.equals(ride.organizerId);
            boolean me = riderId.equals(currentUserId);
            View row = inflater.inflate(R.layout.item_ride_rider, container, false);
            TextView name = row.findViewById(R.id.riderName);
            InitialsAvatarView avatar = row.findViewById(R.id.riderAvatar);
            String suffix = (organizer ? " · Organizer" : "") + (me ? " (you)" : "");
            name.setText("Loading…" + suffix);
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_USER_ID, riderId);
                startActivity(intent);
            });
            container.addView(row);
            userDataAccess.getById(riderId, new UserDataAccess.SingleCallback() {
                @Override
                public void onSuccess(User user) {
                    name.setText((user == null ? "A student" : user.getDisplayName()) + suffix);
                    avatar.setUser(user);
                }

                @Override
                public void onError(Exception exception) {
                    name.setText("A student" + suffix);
                }
            });
        }
    }

    private void loadOrganizerName(String organizerId) {
        if (organizerId == null) {
            return;
        }
        userDataAccess.getById(organizerId, new UserDataAccess.SingleCallback() {
            @Override
            public void onSuccess(User user) {
                if (user != null) {
                    setText(R.id.detailOrganizer, getString(
                            R.string.ride_detail_organizer_format, user.getDisplayName()));
                    organizerAvatar.setUser(user);
                }
            }

            @Override
            public void onError(Exception exception) {
                // Leave the "the organizer" fallback already shown.
            }
        });
    }

    private void configureAction(Ride ride, int availableSeats) {
        joinButton.setVisibility(View.GONE);
        editRideButton.setVisibility(View.GONE);
        completeRideButton.setVisibility(View.GONE);
        leaveRideButton.setVisibility(View.GONE);
        cancelRideButton.setVisibility(View.GONE);

        boolean signedIn = currentUserId != null && !currentUserId.trim().isEmpty();
        if (!signedIn) {
            return;
        }
        Timestamp now = Timestamp.now();
        boolean departed = ride.hasDeparted(now);
        boolean isOrganizer = currentUserId.equals(ride.organizerId);
        List<String> participants = ride.participantIds;
        boolean isMember = isOrganizer || (participants != null && participants.contains(currentUserId));

        if (isMember && Ride.STATUS_CANCELLED.equals(ride.status)) {
            showInfo("This ride was cancelled.");
            return;
        }
        if (isMember && Ride.STATUS_COMPLETED.equals(ride.status)) {
            showInfo("This ride is completed.");
            return;
        }

        if (isOrganizer) {
            // Delays and cancelling are allowed until the ride is completed; completing waits
            // for departure.
            editRideButton.setVisibility(View.VISIBLE);
            cancelRideButton.setVisibility(View.VISIBLE);
            if (departed) {
                completeRideButton.setVisibility(View.VISIBLE);
                showInfo(getString(R.string.ride_detail_badge_organizer)
                        + " It has departed; complete it when the trip is done.");
            } else {
                leaveRideButton.setVisibility(View.VISIBLE);
                showInfo(getString(R.string.ride_detail_badge_organizer));
            }
            return;
        }
        if (isMember) {
            if (!departed) {
                leaveRideButton.setVisibility(View.VISIBLE);
            }
            showInfo(getString(R.string.ride_detail_badge_joined));
            return;
        }
        if (!ride.isActive()) {
            showInfo("This ride is no longer accepting riders.");
            return;
        }
        if (departed) {
            showInfo(JoinRequestService.MESSAGE_DEPARTED);
            return;
        }
        if (availableSeats <= 0) {
            showInfo("This ride is full.");
            return;
        }
        joinButton.setVisibility(View.VISIBLE);
        joinButton.setEnabled(true);
        joinButton.setActivated(false);
        joinButton.setIcon(null);
        joinButton.setText(R.string.join_request_action);
    }

    private void showJoinDialog() {
        if (ride == null) {
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.dialog_join_request, null, false);
        TextInputEditText messageInput = content.findViewById(R.id.joinRequestMessageInput);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.join_request_dialog_title)
                .setMessage(getString(R.string.join_request_dialog_message,
                        emptyFallback(ride.title, getString(R.string.search_untitled_ride))))
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.join_request_send, (dialog, which) ->
                        requestToJoin(messageInput.getText() == null
                                ? "" : messageInput.getText().toString()))
                .show();
    }

    private void confirmCancel() {
        if (ride == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel this ride?")
                .setMessage("Everyone on the ride will see it as Cancelled, and all pending "
                        + "requests will be closed. This can't be undone.")
                .setNegativeButton("Keep ride", null)
                .setPositiveButton("Cancel ride", (dialog, which) -> cancelRide())
                .show();
    }

    private void cancelRide() {
        cancelRideButton.setEnabled(false);
        editRideService.cancelRide(ride, currentUserId, new EditRideService.Callback() {
            @Override
            public void onSuccess() {
                cancelRideButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, "Ride cancelled.", Toast.LENGTH_LONG).show();
                loadRide(ride.id);
            }

            @Override
            public void onDenied(String reason) {
                cancelRideButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, reason, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                cancelRideButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmLeave() {
        if (ride == null) {
            return;
        }
        String error = LeaveRideService.leaveError(ride, currentUserId, Timestamp.now());
        if (error != null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Can't leave this ride")
                    .setMessage(error)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        LeaveRideService.Plan plan = LeaveRideService.plan(ride, currentUserId);
        if (plan.outcome == LeaveRideService.Outcome.OWNERSHIP_TRANSFERRED) {
            userDataAccess.getById(plan.newOrganizerId, new UserDataAccess.SingleCallback() {
                @Override
                public void onSuccess(User user) {
                    showLeaveDialog(plan, user == null ? "the earliest-joined rider" : user.getDisplayName());
                }

                @Override
                public void onError(Exception exception) {
                    showLeaveDialog(plan, "the earliest-joined rider");
                }
            });
        } else {
            showLeaveDialog(plan, null);
        }
    }

    private void showLeaveDialog(LeaveRideService.Plan plan, String newOrganizerName) {
        String message;
        switch (plan.outcome) {
            case RIDE_CANCELLED:
                message = "You're the only rider, so leaving cancels this ride. "
                        + "Any pending requests will be closed. This can't be undone.";
                break;
            case OWNERSHIP_TRANSFERRED:
                message = "You'll leave and " + newOrganizerName
                        + " (the earliest-joined rider) becomes the organizer.";
                break;
            case RIDER_LEFT:
            default:
                message = "You'll be removed from this ride and your seat opens up.";
                break;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Leave this ride?")
                .setMessage(message)
                .setNegativeButton("Stay", null)
                .setPositiveButton("Leave", (dialog, which) -> leaveRide())
                .show();
    }

    private void leaveRide() {
        leaveRideButton.setEnabled(false);
        leaveRideService.leave(ride, currentUserId, new LeaveRideService.Callback() {
            @Override
            public void onSuccess(LeaveRideService.Plan plan) {
                leaveRideButton.setEnabled(true);
                String done = plan.outcome == LeaveRideService.Outcome.RIDE_CANCELLED
                        ? "You left, and the ride was cancelled."
                        : plan.outcome == LeaveRideService.Outcome.OWNERSHIP_TRANSFERRED
                        ? "You left. The ride has a new organizer."
                        : "You left the ride.";
                Toast.makeText(RideDetailActivity.this, done, Toast.LENGTH_LONG).show();
                loadRide(ride.id);
            }

            @Override
            public void onDenied(String reason) {
                leaveRideButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, reason, Toast.LENGTH_LONG).show();
                loadRide(ride.id);
            }

            @Override
            public void onError(String message) {
                leaveRideButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestToJoin(String message) {
        joinButton.setEnabled(false);
        joinRequestService.submit(ride, currentUserId, message, new JoinRequestService.Callback() {
            @Override
            public void onSuccess(JoinRequest request) {
                showRequestPending();
                showInfo(getString(R.string.join_request_success));
            }

            @Override
            public void onAlreadyRequested() {
                showRequestPending();
                showInfo(getString(R.string.join_request_duplicate));
            }

            @Override
            public void onError(String message) {
                joinButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** The join button stays disabled and turns into a tonal "pending" label. */
    private void showRequestPending() {
        Motion.beginTransition(content);
        joinButton.setActivated(true);
        joinButton.setIconResource(R.drawable.ic_check);
        joinButton.setText(R.string.join_request_pending);
    }

    private void openEditRide() {
        if (ride == null) {
            return;
        }
        Intent intent = new Intent(this, EditRideActivity.class);
        intent.putExtra(EditRideActivity.EXTRA_RIDE_ID, ride.id);
        intent.putExtra(EditRideActivity.EXTRA_CURRENT_USER_ID, currentUserId);
        editRideLauncher.launch(intent);
    }

    private void confirmComplete() {
        if (ride == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Complete this ride?")
                .setMessage("Completed rides can't be reopened or changed. "
                        + "You'll enter the actual cost next.")
                .setNegativeButton("Not yet", null)
                .setPositiveButton("Complete", (dialog, which) -> completeRide())
                .show();
    }

    private void completeRide() {
        completeRideButton.setEnabled(false);
        editRideService.completeRide(ride, currentUserId, new EditRideService.Callback() {
            @Override
            public void onSuccess() {
                completeRideButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, "Ride completed.", Toast.LENGTH_SHORT).show();
                Intent costIntent = new Intent(RideDetailActivity.this, RecordCostActivity.class);
                costIntent.putExtra(RecordCostActivity.EXTRA_RIDE_ID, ride.id);
                recordCostLauncher.launch(costIntent);
                loadRide(ride.id);
            }

            @Override
            public void onDenied(String reason) {
                completeRideButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, reason, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                completeRideButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showInfo(String message) {
        infoMessage.setText(message);
        infoMessage.setVisibility(View.VISIBLE);
    }

    private void showLoadError() {
        loadingIndicator.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        showTitle(getString(R.string.ride_detail_title));
        showInfo(getString(R.string.ride_detail_load_error));
    }

    private void showTitle(String title) {
        detailTitle.setText(title);
        detailTitle.setVisibility(View.VISIBLE);
    }

    private void setText(int viewId, String text) {
        ((TextView) findViewById(viewId)).setText(text);
    }

    private String addressOf(PlaceInfo place) {
        if (place == null) {
            return getString(R.string.search_unknown_location);
        }
        return emptyFallback(place.address, getString(R.string.search_unknown_location));
    }

    /** "Nov 14, 3:19 PM"; the year only shows when it isn't this year. */
    private String formatWhen(Timestamp time) {
        if (time == null) {
            return getString(R.string.search_unknown_time);
        }
        return DateUtils.formatDateTime(this, time.toDate().getTime(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_ABBREV_MONTH);
    }

    private String formatDeparture(Timestamp departureTime) {
        Date date = departureTime == null ? null : departureTime.toDate();
        return date == null
                ? getString(R.string.search_unknown_time)
                : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date);
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
