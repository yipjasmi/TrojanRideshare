package com.trojanrideshare.app.ui.ride;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.data.user.UserDataAccess;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.User;
import com.trojanrideshare.app.service.joinrequest.JoinRequestService;
import com.trojanrideshare.app.service.ride.EditRideService;
import com.trojanrideshare.app.service.ride.LeaveRideService;
import com.trojanrideshare.app.ui.payment.RecordCostActivity;
import com.trojanrideshare.app.ui.profile.ProfileActivity;
import com.trojanrideshare.app.util.BottomNavHelper;
import com.trojanrideshare.app.util.SystemBarsHelper;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shows a single ride. The organizer gets an Edit entry point; another signed-in student
 * gets a "Request to join" action that delegates to the shared {@link JoinRequestService}.
 * Organizer accept/reject lives in {@code OrganizerRequestsActivity}, not here.
 */
public class RideDetailActivity extends AppCompatActivity {
    public static final String EXTRA_RIDE_ID = "rideId";
    public static final String EXTRA_CURRENT_USER_ID = "currentUserId";

    private ProgressBar loadingIndicator;
    private LinearLayout content;
    private TextView infoMessage;
    private Button joinButton;
    private Button editRideButton;
    private Button completeRideButton;
    private Button leaveRideButton;
    private Button cancelRideButton;

    private RideDataAccess rideDataAccess;
    private UserDataAccess userDataAccess;
    private JoinRequestService joinRequestService;
    private EditRideService editRideService;
    private LeaveRideService leaveRideService;
    private String currentUserId;
    private Ride ride;

    private ActivityResultLauncher<Intent> editRideLauncher;

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
        infoMessage = findViewById(R.id.detailInfoMessage);
        joinButton = findViewById(R.id.joinButton);
        editRideButton = findViewById(R.id.editRideButton);
        MaterialToolbar toolbar = findViewById(R.id.rideDetailToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        joinButton.setOnClickListener(v -> showJoinDialog());
        completeRideButton = findViewById(R.id.completeRideButton);
        editRideButton.setOnClickListener(v -> openEditRide());
        completeRideButton.setOnClickListener(v -> confirmComplete());
        leaveRideButton = findViewById(R.id.leaveRideButton);
        leaveRideButton.setOnClickListener(v -> confirmLeave());
        cancelRideButton = findViewById(R.id.cancelRideButton);
        cancelRideButton.setOnClickListener(v -> confirmCancel());

        editRideLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && ride != null) {
                        loadRide(ride.id);
                    }
                });

        String rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        if (rideId == null || rideId.trim().isEmpty()) {
            showLoadError();
            return;
        }
        loadRide(rideId);
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

        setText(R.id.detailTitle, emptyFallback(
                ride.title, getString(R.string.search_untitled_ride)));
        setText(R.id.detailOrganizer, getString(
                R.string.ride_detail_organizer_format,
                getString(R.string.ride_detail_unknown_organizer)));
        findViewById(R.id.detailOrganizer).setOnClickListener(v -> {
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
        setText(R.id.detailSeats, ridersLine(ride, availableSeats));
        setText(R.id.detailCost, getString(
                R.string.ride_detail_cost_format,
                String.format(Locale.US, "$%.2f", ride.expectedCost)));
        setText(R.id.detailStatus, getString(
                R.string.ride_detail_status_format, statusLine(ride)));

        renderRiders(ride);
        configureAction(ride, availableSeats);
    }

    /** One tappable row per rider (organizer first), opening that person's profile. */
    private void renderRiders(Ride ride) {
        LinearLayout container = findViewById(R.id.detailRidersContainer);
        container.removeAllViews();
        for (String riderId : ride.getAllParticipantIds()) {
            boolean organizer = riderId.equals(ride.organizerId);
            boolean me = riderId.equals(currentUserId);
            TextView row = new TextView(this);
            row.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            int pad = (int) (10 * getResources().getDisplayMetrics().density);
            row.setPadding(0, pad, 0, pad);
            android.util.TypedValue ripple = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            row.setBackgroundResource(ripple.resourceId);
            row.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setCompoundDrawablePadding((int) (12 * getResources().getDisplayMetrics().density));
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_person, 0, R.drawable.ic_chevron_right, 0);
            String suffix = (organizer ? " · Organizer" : "") + (me ? " (you)" : "");
            row.setText("Loading…" + suffix);
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_USER_ID, riderId);
                startActivity(intent);
            });
            container.addView(row);
            userDataAccess.getById(riderId, new UserDataAccess.SingleCallback() {
                @Override
                public void onSuccess(User user) {
                    row.setText((user == null ? "A student" : user.getDisplayName()) + suffix);
                }

                @Override
                public void onError(Exception exception) {
                    row.setText("A student" + suffix);
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
                joinButton.setVisibility(View.GONE);
                showInfo(getString(R.string.join_request_success));
            }

            @Override
            public void onAlreadyRequested() {
                joinButton.setVisibility(View.GONE);
                showInfo(getString(R.string.join_request_duplicate));
            }

            @Override
            public void onError(String message) {
                joinButton.setEnabled(true);
                Toast.makeText(RideDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
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
                startActivity(costIntent);
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

    /** "On Time · Open", "Delayed · Full", or just "Completed"/"Cancelled". */
    private String statusLine(Ride ride) {
        String activity = statusLabel(ride.status);
        if (!ride.isActive()) {
            return activity;
        }
        String capacity = Ride.CAPACITY_FULL.equals(ride.getDerivedCapacityStatus()) ? "Full" : "Open";
        return activity + " · " + capacity;
    }

    /** Rider count including the organizer; capacity is N/A once completed or cancelled. */
    private String ridersLine(Ride ride, int availableSeats) {
        String count = String.format(Locale.US, "Riders: %d of %d (including organizer)",
                ride.getRiderCount(), ride.maxRiders);
        if (!ride.isActive()) {
            return count;
        }
        return count + " · " + getResources().getQuantityString(
                R.plurals.search_available_seats, availableSeats, availableSeats);
    }

    private void showInfo(String message) {
        infoMessage.setText(message);
        infoMessage.setVisibility(View.VISIBLE);
    }

    private void showLoadError() {
        loadingIndicator.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        setText(R.id.detailTitle, getString(R.string.ride_detail_title));
        showInfo(getString(R.string.ride_detail_load_error));
    }

    private void setText(int viewId, String text) {
        ((TextView) findViewById(viewId)).setText(text);
    }

    private String statusLabel(String status) {
        if (Ride.STATUS_ON_TIME.equals(status)) {
            return getString(R.string.status_on_time);
        }
        if (Ride.STATUS_DELAYED.equals(status)) {
            return getString(R.string.status_delayed);
        }
        if (Ride.STATUS_CANCELLED.equals(status)) {
            return getString(R.string.status_cancelled);
        }
        if (Ride.STATUS_COMPLETED.equals(status)) {
            return getString(R.string.status_completed);
        }
        return getString(R.string.status_unknown);
    }

    private String addressOf(PlaceInfo place) {
        if (place == null) {
            return getString(R.string.search_unknown_location);
        }
        return emptyFallback(place.address, getString(R.string.search_unknown_location));
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
