package com.trojanrideshare.app.ui.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.OrganizerJoinRequest;
import com.trojanrideshare.app.service.joinrequest.JoinRequestService;

import java.util.List;

public class OrganizerRequestsActivity extends AppCompatActivity {
    public static final String EXTRA_ORGANIZER_ID = "organizerId";

    private JoinRequestService joinRequestService;
    private ProgressBar loadingIndicator;
    private TextView statusMessage;
    private LinearLayout requestsContainer;
    private String organizerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_requests);

        joinRequestService = new JoinRequestService();
        loadingIndicator = findViewById(R.id.organizerRequestsLoading);
        statusMessage = findViewById(R.id.organizerRequestsStatus);
        requestsContainer = findViewById(R.id.organizerRequestsContainer);
        organizerId = getIntent().getStringExtra(EXTRA_ORGANIZER_ID);

        findViewById(R.id.organizerRequestsBack).setOnClickListener(view -> finish());
        loadRequests();
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
        ((TextView) view.findViewById(R.id.organizerRequestRider)).setText(getString(
                R.string.organizer_request_rider,
                item.request.requesterId
        ));

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
        ((TextView) view.findViewById(R.id.organizerRequestSeats)).setText(getResources()
                .getQuantityString(
                        R.plurals.search_available_seats,
                        availableSeats,
                        availableSeats
                ));

        Button acceptButton = view.findViewById(R.id.organizerRequestAccept);
        Button rejectButton = view.findViewById(R.id.organizerRequestReject);
        acceptButton.setEnabled(availableSeats > 0);
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
                .setMessage(getString(message, item.request.requesterId))
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
}
