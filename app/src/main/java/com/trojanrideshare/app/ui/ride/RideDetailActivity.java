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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
import com.trojanrideshare.app.ui.payment.RecordCostActivity;
import com.trojanrideshare.app.ui.profile.ProfileActivity;

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

    private RideDataAccess rideDataAccess;
    private UserDataAccess userDataAccess;
    private JoinRequestService joinRequestService;
    private EditRideService editRideService;
    private String currentUserId;
    private Ride ride;

    private ActivityResultLauncher<Intent> editRideLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride_detail);

        rideDataAccess = new RideDataAccess();
        userDataAccess = new UserDataAccess();
        joinRequestService = new JoinRequestService();
        editRideService = new EditRideService();
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
        joinButton.setOnClickListener(v -> requestToJoin());
        completeRideButton = findViewById(R.id.completeRideButton);
        editRideButton.setOnClickListener(v -> openEditRide());
        completeRideButton.setOnClickListener(v -> confirmComplete());

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

        configureAction(ride, availableSeats);
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

        boolean signedIn = currentUserId != null && !currentUserId.trim().isEmpty();
        if (!signedIn) {
            return;
        }
        if (currentUserId.equals(ride.organizerId)) {
            if (ride.isActive()) {
                // Delays are allowed until the ride is completed; completing waits for departure.
                editRideButton.setVisibility(View.VISIBLE);
                if (ride.hasDeparted(Timestamp.now())) {
                    completeRideButton.setVisibility(View.VISIBLE);
                    showInfo(getString(R.string.ride_detail_badge_organizer)
                            + " It has departed; complete it when the trip is done.");
                    return;
                }
            }
            showInfo(getString(R.string.ride_detail_badge_organizer));
            return;
        }
        List<String> participants = ride.participantIds;
        if (participants != null && participants.contains(currentUserId)) {
            showInfo(getString(R.string.ride_detail_badge_joined));
            return;
        }
        boolean open = Ride.STATUS_ON_TIME.equals(ride.status)
                || Ride.STATUS_DELAYED.equals(ride.status);
        if (open && availableSeats > 0) {
            joinButton.setVisibility(View.VISIBLE);
            joinButton.setEnabled(true);
        }
    }

    private void requestToJoin() {
        joinButton.setEnabled(false);
        joinRequestService.submit(ride, currentUserId, null, new JoinRequestService.Callback() {
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
