package com.trojanrideshare.app.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.MainActivity;
import com.trojanrideshare.app.R;
import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.ride.RideMembershipDataAccess;
import com.trojanrideshare.app.data.user.UserDataAccess;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.OrganizerJoinRequest;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.User;
import com.trojanrideshare.app.ui.ride.RideDetailActivity;
import com.trojanrideshare.app.service.joinrequest.JoinRequestService;
import com.trojanrideshare.app.util.BottomNavHelper;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrganizerRequestsActivity extends AppCompatActivity {
    public static final String EXTRA_ORGANIZER_ID = "organizerId";

    private JoinRequestService joinRequestService;
    private ProgressBar loadingIndicator;
    private TextView statusMessage;
    private LinearLayout requestsContainer;
    private String organizerId;
    private TextView myRequestsStatus;
    private LinearLayout myRequestsContainer;
    private final UserDataAccess userDataAccess = new UserDataAccess();
    private final RideMembershipDataAccess membershipDataAccess = new RideMembershipDataAccess();
    private final Map<String, String> displayNames = new HashMap<>();

    private String getOrganizerId() {
        String id = getIntent().getStringExtra(EXTRA_ORGANIZER_ID);
        if (id == null || id.isEmpty()) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                id = currentUser.getUid();
            }
        }
        return id;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_requests);

        joinRequestService = new JoinRequestService();
        loadingIndicator = findViewById(R.id.organizerRequestsLoading);
        statusMessage = findViewById(R.id.organizerRequestsStatus);
        requestsContainer = findViewById(R.id.organizerRequestsContainer);
        myRequestsStatus = findViewById(R.id.myRequestsStatus);
        myRequestsContainer = findViewById(R.id.myRequestsContainer);
        organizerId = getOrganizerId();

        findViewById(R.id.organizerRequestsBack).setOnClickListener(view -> navigateToHome());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToHome();
            }
        });
        BottomNavHelper.setupBottomNav(this, R.id.nav_requests);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRequests();
        loadMyRequests();
    }

    private void loadRequests() {
        setLoading(true);
        joinRequestService.loadOrganizerRequests(
                organizerId,
                new JoinRequestService.OrganizerRequestsCallback() {
                    @Override
                    public void onSuccess(List<OrganizerJoinRequest> requests) {
                        setLoading(false);
                        renderRequests(requests);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        statusMessage.setText(message);
                        statusMessage.setVisibility(View.VISIBLE);
                    }
                }
        );
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            statusMessage.setVisibility(View.GONE);
            requestsContainer.removeAllViews();
        }
    }

    private void renderRequests(List<OrganizerJoinRequest> requests) {
        requestsContainer.removeAllViews();
        if (requests.isEmpty()) {
            statusMessage.setText(R.string.organizer_requests_empty);
            statusMessage.setVisibility(View.VISIBLE);
            return;
        }

        statusMessage.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (OrganizerJoinRequest item : requests) {
            View requestView = inflater.inflate(
                    R.layout.item_organizer_join_request,
                    requestsContainer,
                    false
            );
            bindRequest(requestView, item);
            requestsContainer.addView(requestView);
        }
    }

    private void bindRequest(View view, OrganizerJoinRequest item) {
        String title = item.ride.title == null || item.ride.title.trim().isEmpty()
                ? getString(R.string.search_untitled_ride)
                : item.ride.title.trim();
        ((TextView) view.findViewById(R.id.organizerRequestRideTitle)).setText(title);
        TextView riderView = view.findViewById(R.id.organizerRequestRider);
        String requesterId = item.request.requesterId;
        riderView.setText(getString(R.string.organizer_request_rider, nameFor(requesterId))
                + requestedAt(item.request));
        if (!displayNames.containsKey(requesterId)) {
            userDataAccess.getById(requesterId, new UserDataAccess.SingleCallback() {
                @Override
                public void onSuccess(User user) {
                    if (user != null) {
                        displayNames.put(requesterId, user.getDisplayName());
                        riderView.setText(getString(R.string.organizer_request_rider,
                                user.getDisplayName()) + requestedAt(item.request));
                    }
                }

                @Override
                public void onError(Exception exception) {}
            });
        }
        view.setOnClickListener(unused -> openRide(item.ride));

        TextView messageView = view.findViewById(R.id.organizerRequestMessage);
        if (item.request.message == null || item.request.message.trim().isEmpty()) {
            messageView.setVisibility(View.GONE);
        } else {
            messageView.setText(getString(
                    R.string.organizer_request_message,
                    item.request.message.trim()
            ));
            messageView.setVisibility(View.VISIBLE);
        }

        int availableSeats = item.ride.getAvailableSeats();
        boolean departed = item.ride.hasDeparted(Timestamp.now());
        String seatsText = String.format(java.util.Locale.US, "Riders: %d of %d · ",
                item.ride.getRiderCount(), item.ride.maxRiders);
        if (departed) {
            seatsText += "Departed, acceptances are closed";
        } else if (availableSeats <= 0) {
            seatsText += "Full, this request stays pending in case a seat opens";
        } else {
            seatsText += getResources().getQuantityString(
                    R.plurals.search_available_seats, availableSeats, availableSeats);
        }
        ((TextView) view.findViewById(R.id.organizerRequestSeats)).setText(seatsText);

        Button acceptButton = view.findViewById(R.id.organizerRequestAccept);
        Button rejectButton = view.findViewById(R.id.organizerRequestReject);
        acceptButton.setEnabled(availableSeats > 0 && !departed);
        acceptButton.setOnClickListener(unused -> confirmDecision(item, true));
        rejectButton.setOnClickListener(unused -> confirmDecision(item, false));
    }

    private void confirmDecision(OrganizerJoinRequest item, boolean accepting) {
        int title = accepting
                ? R.string.organizer_accept_title
                : R.string.organizer_reject_title;
        int message = accepting
                ? R.string.organizer_accept_message
                : R.string.organizer_reject_message;
        int action = accepting
                ? R.string.organizer_accept_action
                : R.string.organizer_reject_action;

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(getString(message, nameFor(item.request.requesterId)))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(action, (dialog, which) -> updateRequest(item, accepting))
                .show();
    }

    private void updateRequest(OrganizerJoinRequest item, boolean accepting) {
        setLoading(true);
        JoinRequestService.DecisionCallback callback =
                new JoinRequestService.DecisionCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(
                                OrganizerRequestsActivity.this,
                                accepting
                                        ? R.string.organizer_accept_success
                                        : R.string.organizer_reject_success,
                                Toast.LENGTH_LONG
                        ).show();
                        loadRequests();
                    }

                    @Override
                    public void onUnavailable(String message) {
                        Toast.makeText(
                                OrganizerRequestsActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                        loadRequests();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(
                                OrganizerRequestsActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                        loadRequests();
                    }
                };

        if (accepting) {
            joinRequestService.accept(
                    item.ride.id,
                    item.request.requesterId,
                    organizerId,
                    callback
            );
        } else {
            joinRequestService.reject(
                    item.ride.id,
                    item.request.requesterId,
                    organizerId,
                    callback
            );
        }
    }

    private void loadMyRequests() {
        if (organizerId == null) return;
        membershipDataAccess.loadMyRequests(organizerId, new RideMembershipDataAccess.RequestsCallback() {
            @Override
            public void onSuccess(List<OrganizerJoinRequest> requests) {
                renderMyRequests(requests);
            }

            @Override
            public void onError(Exception exception) {
                myRequestsContainer.removeAllViews();
                myRequestsStatus.setText("Unable to load your requests.");
                myRequestsStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    private void renderMyRequests(List<OrganizerJoinRequest> requests) {
        myRequestsContainer.removeAllViews();
        if (requests.isEmpty()) {
            myRequestsStatus.setText("You haven't sent any join requests.");
            myRequestsStatus.setVisibility(View.VISIBLE);
            return;
        }
        myRequestsStatus.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (OrganizerJoinRequest item : requests) {
            View view = inflater.inflate(R.layout.item_organizer_join_request, myRequestsContainer, false);
            String title = item.ride.title == null || item.ride.title.trim().isEmpty()
                    ? getString(R.string.search_untitled_ride) : item.ride.title.trim();
            ((TextView) view.findViewById(R.id.organizerRequestRideTitle)).setText(title);
            ((TextView) view.findViewById(R.id.organizerRequestRider)).setText(
                    "Request: " + requestStatusLabel(item) + requestedAt(item.request));
            TextView messageView = view.findViewById(R.id.organizerRequestMessage);
            if (item.request.message == null || item.request.message.trim().isEmpty()) {
                messageView.setVisibility(View.GONE);
            } else {
                messageView.setText(getString(R.string.organizer_request_message,
                        item.request.message.trim()));
            }
            ((TextView) view.findViewById(R.id.organizerRequestSeats)).setText(
                    "Ride: " + rideStatusLabel(item.ride));

            view.findViewById(R.id.organizerRequestAccept).setVisibility(View.GONE);
            Button cancelButton = view.findViewById(R.id.organizerRequestReject);
            if (JoinRequest.PENDING.equals(item.request.status)) {
                cancelButton.setText(R.string.join_request_cancel_action);
                cancelButton.setOnClickListener(unused -> confirmCancelMine(item, title));
            } else {
                cancelButton.setVisibility(View.GONE);
            }
            view.setOnClickListener(unused -> openRide(item.ride));
            myRequestsContainer.addView(view);
        }
    }

    private void confirmCancelMine(OrganizerJoinRequest item, String title) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.join_request_cancel_title)
                .setMessage(getString(R.string.join_request_cancel_message, title))
                .setNegativeButton(R.string.join_request_keep, null)
                .setPositiveButton(R.string.join_request_cancel_action, (dialog, which) ->
                        joinRequestService.cancel(item.ride.id, organizerId,
                                new JoinRequestService.CancelCallback() {
                                    @Override
                                    public void onSuccess() {
                                        Toast.makeText(OrganizerRequestsActivity.this,
                                                R.string.join_request_cancelled, Toast.LENGTH_LONG).show();
                                        loadMyRequests();
                                    }

                                    @Override
                                    public void onNotPending() {
                                        Toast.makeText(OrganizerRequestsActivity.this,
                                                R.string.join_request_not_pending, Toast.LENGTH_LONG).show();
                                        loadMyRequests();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(OrganizerRequestsActivity.this, message,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
    }

    private static String requestStatusLabel(OrganizerJoinRequest item) {
        String status = item.request.status;
        if (JoinRequest.PENDING.equals(status)) return "Pending";
        if (JoinRequest.ACCEPTED.equals(status)) return "Accepted";
        if (JoinRequest.REJECTED.equals(status)) return "Rejected";
        if (JoinRequest.LEFT.equals(status)) return "Accepted, then you left";
        if (JoinRequest.RIDE_CANCELLED.equals(status)) return "Closed, ride cancelled";
        if (JoinRequest.CANCELLED.equals(status)) return "Cancelled by you";
        return "Unknown";
    }

    private static String rideStatusLabel(Ride ride) {
        if (Ride.STATUS_CANCELLED.equals(ride.status)) return "Cancelled";
        if (Ride.STATUS_COMPLETED.equals(ride.status)) return "Completed";
        String activity = Ride.STATUS_DELAYED.equals(ride.status) ? "Delayed" : "On Time";
        return activity + " · " + (ride.getAvailableSeats() > 0 ? "Open" : "Full");
    }

    private String nameFor(String userId) {
        String name = displayNames.get(userId);
        return name != null ? name : "a student";
    }

    private static String requestedAt(JoinRequest request) {
        if (request.createdAt == null) return "";
        return "\nRequested " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(request.createdAt.toDate());
    }

    private void openRide(Ride ride) {
        if (ride == null || ride.id == null) return;
        Intent intent = new Intent(this, RideDetailActivity.class);
        intent.putExtra(RideDetailActivity.EXTRA_RIDE_ID, ride.id);
        intent.putExtra(RideDetailActivity.EXTRA_CURRENT_USER_ID, organizerId);
        startActivity(intent);
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
