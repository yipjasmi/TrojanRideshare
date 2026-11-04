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

    private RideDataAccess rideDataAccess;
    private UserDataAccess userDataAccess;
    private JoinRequestService joinRequestService;
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
        editRideButton.setOnClickListener(v -> openEditRide());

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
        setText(R.id.detailDeparture, getString(
                R.string.search_departure_format, formatDeparture(ride.departureTime)));

        int availableSeats = ride.getAvailableSeats();
        setText(R.id.detailSeats, getResources().getQuantityString(
                R.plurals.search_available_seats, availableSeats, availableSeats));
        setText(R.id.detailCost, getString(
                R.string.ride_detail_cost_format,
                String.format(Locale.US, "$%.2f", ride.expectedCost)));
        setText(R.id.detailStatus, getString(
                R.string.ride_detail_status_format, statusLabel(ride.status)));

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

        boolean signedIn = currentUserId != null && !currentUserId.trim().isEmpty();
        if (!signedIn) {
            return;
        }
        if (currentUserId.equals(ride.organizerId)) {
            editRideButton.setVisibility(View.VISIBLE);
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
