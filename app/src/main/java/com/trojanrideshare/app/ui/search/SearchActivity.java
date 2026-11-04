package com.trojanrideshare.app.ui.search;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.BuildConfig;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.SearchCriteria;
import com.trojanrideshare.app.model.SortOption;
import com.trojanrideshare.app.service.joinrequest.JoinRequestService;
import com.trojanrideshare.app.service.search.SearchService;
import com.trojanrideshare.app.ui.ride.CreateRideActivity;
import com.trojanrideshare.app.ui.ride.RideDetailActivity;
import com.trojanrideshare.app.util.BottomNavHelper;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_CURRENT_USER_ID = "currentUserId";

    private EditText originInput;
    private EditText destinationInput;
    private EditText maximumDistanceInput;
    private Button startDateButton;
    private Button endDateButton;
    private Button startTimeButton;
    private Button endTimeButton;
    private CheckBox timeFilterCheckBox;
    private CheckBox distanceFilterCheckBox;
    private Spinner minimumSeatsSpinner;
    private Spinner sortSpinner;
    private ProgressBar loadingIndicator;
    private TextView resultMessage;
    private LinearLayout resultsContainer;
    private SearchService searchService;
    private JoinRequestService joinRequestService;
    private final Set<String> requestedRideIds = new HashSet<>();
    private List<Ride> displayedRides = Collections.emptyList();

    private PlaceInfo preferredOrigin;
    private PlaceInfo preferredDestination;
    private Long startDateMillis;
    private Long endDateMillis;
    private int startTimeMinute;
    private int endTimeMinute = 23 * 60 + 59;

    private final ActivityResultLauncher<Intent> originPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> handlePlaceResult(result.getResultCode(), result.getData(), true)
    );
    private final ActivityResultLauncher<Intent> destinationPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> handlePlaceResult(result.getResultCode(), result.getData(), false)
    );

    private String getCurrentUserId() {
        String userId = getIntent().getStringExtra(EXTRA_CURRENT_USER_ID);
        if (userId == null || userId.isEmpty()) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
            }
        }
        return userId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        bindViews();
        BottomNavHelper.setupBottomNav(this, R.id.nav_find);
        searchService = new SearchService();
        joinRequestService = new JoinRequestService();
        initializePlaces();
        updateTimeButtons();

        View createFab = findViewById(R.id.createRideFab);
        if (createFab != null) {
            createFab.setOnClickListener(v -> {
                Intent createIntent = new Intent(SearchActivity.this, CreateRideActivity.class);
                String currentUserId = getCurrentUserId();
                if (currentUserId != null) {
                    createIntent.putExtra(
                            CreateRideActivity.EXTRA_CURRENT_USER_ID,
                            currentUserId
                    );
                }
                startActivity(createIntent);
            });
        }

        findViewById(R.id.selectOriginButton).setOnClickListener(
                view -> launchPlacePicker(originPicker)
        );
        findViewById(R.id.selectDestinationButton).setOnClickListener(
                view -> launchPlacePicker(destinationPicker)
        );
        startDateButton.setOnClickListener(view -> showDatePicker(true));
        endDateButton.setOnClickListener(view -> showDatePicker(false));
        startTimeButton.setOnClickListener(view -> showTimePicker(true));
        endTimeButton.setOnClickListener(view -> showTimePicker(false));
        timeFilterCheckBox.setOnCheckedChangeListener((button, checked) -> {
            startTimeButton.setEnabled(checked);
            endTimeButton.setEnabled(checked);
        });
        distanceFilterCheckBox.setOnCheckedChangeListener(
                (button, checked) -> maximumDistanceInput.setEnabled(checked)
        );
        findViewById(R.id.searchButton).setOnClickListener(view -> search());
        findViewById(R.id.clearFiltersButton).setOnClickListener(view -> clearFilters());

        loadPendingJoinRequests();
        showSearchPrompt();
    }

    private void showSearchPrompt() {
        resultsContainer.removeAllViews();
        resultMessage.setText("Enter an origin and a destination keyword, then tap Search.");
        resultMessage.setVisibility(View.VISIBLE);
    }

    private void showPreferredLocation(boolean isOrigin, PlaceInfo selected) {
        Button button = findViewById(isOrigin ? R.id.selectOriginButton : R.id.selectDestinationButton);
        if (selected == null) {
            button.setText(isOrigin ? R.string.search_select_origin : R.string.search_select_destination);
        } else {
            button.setText((isOrigin ? "Origin point: " : "Destination point: ") + selected.address);
        }
    }

    private void bindViews() {
        originInput = findViewById(R.id.originSearchInput);
        destinationInput = findViewById(R.id.destinationSearchInput);
        maximumDistanceInput = findViewById(R.id.maximumDistanceInput);
        startDateButton = findViewById(R.id.startDateButton);
        endDateButton = findViewById(R.id.endDateButton);
        startTimeButton = findViewById(R.id.startTimeButton);
        endTimeButton = findViewById(R.id.endTimeButton);
        timeFilterCheckBox = findViewById(R.id.timeFilterCheckBox);
        distanceFilterCheckBox = findViewById(R.id.distanceFilterCheckBox);
        minimumSeatsSpinner = findViewById(R.id.minimumSeatsSpinner);
        sortSpinner = findViewById(R.id.sortSpinner);
        loadingIndicator = findViewById(R.id.searchLoadingIndicator);
        resultMessage = findViewById(R.id.searchResultMessage);
        resultsContainer = findViewById(R.id.searchResultsContainer);
    }

    private void initializePlaces() {
        if (!BuildConfig.PLACES_API_KEY.trim().isEmpty() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), BuildConfig.PLACES_API_KEY);
        }
    }

    private void launchPlacePicker(ActivityResultLauncher<Intent> launcher) {
        com.trojanrideshare.app.util.PlacesLocationPicker.show(this, selected -> {
            // The picked point drives the radius filter and proximity sort; the keyword inputs
            // stay as typed so both filters apply independently.
            if (launcher == originPicker) { preferredOrigin = selected; showPreferredLocation(true, selected); }
            else { preferredDestination = selected; showPreferredLocation(false, selected); }
        });
    }

    private void handlePlaceResult(int resultCode, Intent data, boolean isOrigin) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Place place = Autocomplete.getPlaceFromIntent(data);
            LatLng coordinates = place.getLatLng();
            if (coordinates == null) {
                Toast.makeText(this, R.string.search_location_error, Toast.LENGTH_LONG).show();
                return;
            }

            PlaceInfo selected = new PlaceInfo();
            selected.placeId = place.getId();
            selected.address = place.getFormattedAddress();
            selected.lat = coordinates.latitude;
            selected.lng = coordinates.longitude;
            if (isOrigin) {
                preferredOrigin = selected;
            } else {
                preferredDestination = selected;
            }
            showPreferredLocation(isOrigin, selected);
        } else if (resultCode == AutocompleteActivity.RESULT_ERROR && data != null) {
            Status status = Autocomplete.getStatusFromIntent(data);
            String message = status.getStatusMessage();
            Toast.makeText(
                    this,
                    message == null ? getString(R.string.search_location_error) : message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showDatePicker(boolean selectingStart) {
        Calendar initial = Calendar.getInstance();
        Long current = selectingStart ? startDateMillis : endDateMillis;
        if (current != null) {
            initial.setTimeInMillis(current);
        }

        new DatePickerDialog(
                this,
                (picker, year, month, day) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, day);
                    if (selectingStart) {
                        selected.set(Calendar.HOUR_OF_DAY, 0);
                        selected.set(Calendar.MINUTE, 0);
                        selected.set(Calendar.SECOND, 0);
                        selected.set(Calendar.MILLISECOND, 0);
                        startDateMillis = selected.getTimeInMillis();
                        startDateButton.setText(formatDate(startDateMillis));
                    } else {
                        selected.set(Calendar.HOUR_OF_DAY, 23);
                        selected.set(Calendar.MINUTE, 59);
                        selected.set(Calendar.SECOND, 59);
                        selected.set(Calendar.MILLISECOND, 999);
                        endDateMillis = selected.getTimeInMillis();
                        endDateButton.setText(formatDate(endDateMillis));
                    }
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showTimePicker(boolean selectingStart) {
        int current = selectingStart ? startTimeMinute : endTimeMinute;
        new TimePickerDialog(
                this,
                (picker, hour, minute) -> {
                    if (selectingStart) {
                        startTimeMinute = hour * 60 + minute;
                    } else {
                        endTimeMinute = hour * 60 + minute;
                    }
                    updateTimeButtons();
                },
                current / 60,
                current % 60,
                false
        ).show();
    }

    private void updateTimeButtons() {
        startTimeButton.setText(getString(
                R.string.search_start_time,
                formatTime(startTimeMinute)
        ));
        endTimeButton.setText(getString(
                R.string.search_end_time,
                formatTime(endTimeMinute)
        ));
    }

    private void search() {
        String keywordError = SearchService.keywordError(
                originInput.getText().toString(), destinationInput.getText().toString());
        if (keywordError != null) {
            if (originInput.getText().toString().trim().isEmpty()) {
                originInput.setError("Origin keyword is required");
            }
            if (destinationInput.getText().toString().trim().isEmpty()) {
                destinationInput.setError("Destination keyword is required");
            }
            resultsContainer.removeAllViews();
            resultMessage.setText(keywordError);
            resultMessage.setVisibility(View.VISIBLE);
            return;
        }
        if (startDateMillis != null && endDateMillis != null
                && endDateMillis < startDateMillis) {
            Toast.makeText(this, R.string.search_invalid_date_range, Toast.LENGTH_LONG).show();
            return;
        }
        boolean requiresLocations = distanceFilterCheckBox.isChecked()
                || sortSpinner.getSelectedItemPosition() >= 2;
        if (requiresLocations && (preferredOrigin == null || preferredDestination == null)) {
            Toast.makeText(this, R.string.search_locations_required, Toast.LENGTH_LONG).show();
            return;
        }

        double maximumDistance = 0;
        if (distanceFilterCheckBox.isChecked()) {
            try {
                maximumDistance = Double.parseDouble(
                        maximumDistanceInput.getText().toString().trim()
                );
            } catch (NumberFormatException exception) {
                maximumDistanceInput.setError(getString(R.string.search_invalid_distance));
                return;
            }
            if (maximumDistance <= 0) {
                maximumDistanceInput.setError(getString(R.string.search_invalid_distance));
                return;
            }
        }

        setLoading(true);
        SearchCriteria criteria = new SearchCriteria(
                originInput.getText().toString(),
                destinationInput.getText().toString(),
                preferredOrigin,
                preferredDestination,
                maximumDistance,
                startDateMillis,
                endDateMillis,
                timeFilterCheckBox.isChecked() ? startTimeMinute : null,
                timeFilterCheckBox.isChecked() ? endTimeMinute : null,
                minimumSeatsSpinner.getSelectedItemPosition(),
                selectedSortOption()
        );

        searchService.search(
                criteria,
                getCurrentUserId(),
                new SearchService.Callback() {
                    @Override
                    public void onSuccess(List<Ride> rides) {
                        setLoading(false);
                        renderResults(rides);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        resultsContainer.removeAllViews();
                        resultMessage.setText(message);
                        resultMessage.setVisibility(View.VISIBLE);
                    }
                }
        );
    }

    private SortOption selectedSortOption() {
        switch (sortSpinner.getSelectedItemPosition()) {
            case 1:
                return SortOption.DEPARTURE_LATEST;
            case 2:
                return SortOption.ROUTE_PROXIMITY;
            case 3:
                return SortOption.PROXIMITY_AND_TIME;
            default:
                return SortOption.DEPARTURE_EARLIEST;
        }
    }

    private void clearFilters() {
        originInput.setText("");
        destinationInput.setText("");
        preferredOrigin = null;
        preferredDestination = null;
        startDateMillis = null;
        endDateMillis = null;
        startDateButton.setText(R.string.search_any_start_date);
        endDateButton.setText(R.string.search_any_end_date);
        startTimeMinute = 0;
        endTimeMinute = 23 * 60 + 59;
        timeFilterCheckBox.setChecked(false);
        distanceFilterCheckBox.setChecked(false);
        maximumDistanceInput.setText("");
        maximumDistanceInput.setError(null);
        minimumSeatsSpinner.setSelection(0);
        sortSpinner.setSelection(0);
        originInput.setError(null);
        destinationInput.setError(null);
        showPreferredLocation(true, null);
        showPreferredLocation(false, null);
        updateTimeButtons();
        showSearchPrompt();
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            resultMessage.setVisibility(View.GONE);
            resultsContainer.removeAllViews();
        }
    }

    private void renderResults(List<Ride> rides) {
        displayedRides = new ArrayList<>(rides);
        resultsContainer.removeAllViews();
        if (rides.isEmpty()) {
            resultMessage.setText(R.string.search_no_results);
            resultMessage.setVisibility(View.VISIBLE);
            return;
        }

        resultMessage.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Ride ride : rides) {
            View item = inflater.inflate(R.layout.item_search_ride, resultsContainer, false);
            bindRide(item, ride);
            resultsContainer.addView(item);
        }
    }

    private void bindRide(View item, Ride ride) {
        item.setOnClickListener(view -> openRideDetail(ride));
        ((TextView) item.findViewById(R.id.rideTitle)).setText(
                emptyFallback(ride.title, getString(R.string.search_untitled_ride))
        );
        ((TextView) item.findViewById(R.id.rideRoute)).setText(getString(
                R.string.search_route_format,
                addressOf(ride.origin),
                addressOf(ride.destination)
        ));
        String departure = getString(
                R.string.search_departure_format,
                formatDeparture(ride.departureTime)
        );
        departure += Ride.STATUS_DELAYED.equals(ride.status) ? " · Delayed (updated time)" : " · On Time";
        ((TextView) item.findViewById(R.id.rideDeparture)).setText(departure);

        int availableSeats = ride.getAvailableSeats();
        ((TextView) item.findViewById(R.id.rideSeats)).setText(getResources()
                .getQuantityString(R.plurals.search_available_seats, availableSeats, availableSeats));

        Button requestButton = item.findViewById(R.id.joinRequestButton);
        TextView requestStatus = item.findViewById(R.id.joinRequestStatus);
        if (requestedRideIds.contains(ride.id)) {
            showPendingState(ride, requestButton, requestStatus);
        } else {
            showAvailableState(ride, requestButton, requestStatus);
        }
    }

    private void openRideDetail(Ride ride) {
        Intent intent = new Intent(this, RideDetailActivity.class);
        intent.putExtra(RideDetailActivity.EXTRA_RIDE_ID, ride.id);
        intent.putExtra(
                RideDetailActivity.EXTRA_CURRENT_USER_ID,
                getIntent().getStringExtra(EXTRA_CURRENT_USER_ID));
        startActivity(intent);
    }

    private void showJoinRequestDialog(
            Ride ride,
            Button requestButton,
            TextView requestStatus
    ) {
        View dialogContent = LayoutInflater.from(this)
                .inflate(R.layout.dialog_join_request, null, false);
        TextInputEditText messageInput = dialogContent.findViewById(
                R.id.joinRequestMessageInput
        );
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.join_request_dialog_title)
                .setMessage(getString(
                        R.string.join_request_dialog_message,
                        emptyFallback(ride.title, getString(R.string.search_untitled_ride))
                ))
                .setView(dialogContent)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.join_request_send, null)
                .create();

        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String message = messageInput.getText() == null
                            ? ""
                            : messageInput.getText().toString();
                    dialog.dismiss();
                    submitJoinRequest(ride, message, requestButton, requestStatus);
                }));
        dialog.show();
    }

    private void submitJoinRequest(
            Ride ride,
            String message,
            Button requestButton,
            TextView requestStatus
    ) {
        requestButton.setEnabled(false);
        requestButton.setText(R.string.join_request_sending);
        joinRequestService.submit(
                ride,
                getCurrentUserId(),
                message,
                new JoinRequestService.Callback() {
                    @Override
                    public void onSuccess(com.trojanrideshare.app.model.JoinRequest request) {
                        requestedRideIds.add(ride.id);
                        showPendingState(ride, requestButton, requestStatus);
                        Toast.makeText(
                                SearchActivity.this,
                                R.string.join_request_success,
                                Toast.LENGTH_LONG
                        ).show();
                    }

                    @Override
                    public void onAlreadyRequested() {
                        requestedRideIds.add(ride.id);
                        showPendingState(ride, requestButton, requestStatus);
                        Toast.makeText(
                                SearchActivity.this,
                                R.string.join_request_duplicate,
                                Toast.LENGTH_LONG
                        ).show();
                    }

                    @Override
                    public void onError(String message) {
                        requestButton.setEnabled(true);
                        requestButton.setText(R.string.join_request_action);
                        Toast.makeText(SearchActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void loadPendingJoinRequests() {
        joinRequestService.loadPendingRideIds(
                getCurrentUserId(),
                new JoinRequestService.PendingRequestsCallback() {
                    @Override
                    public void onSuccess(Set<String> rideIds) {
                        requestedRideIds.clear();
                        requestedRideIds.addAll(rideIds);
                        if (!displayedRides.isEmpty()) {
                            renderResults(displayedRides);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(
                                SearchActivity.this,
                                R.string.join_request_status_error,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void showCancelRequestDialog(
            Ride ride,
            Button requestButton,
            TextView requestStatus
    ) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.join_request_cancel_title)
                .setMessage(getString(
                        R.string.join_request_cancel_message,
                        emptyFallback(ride.title, getString(R.string.search_untitled_ride))
                ))
                .setNegativeButton(R.string.join_request_keep, null)
                .setPositiveButton(
                        R.string.join_request_cancel_action,
                        (dialog, which) -> cancelJoinRequest(
                                ride,
                                requestButton,
                                requestStatus
                        )
                )
                .show();
    }

    private void cancelJoinRequest(
            Ride ride,
            Button requestButton,
            TextView requestStatus
    ) {
        requestButton.setEnabled(false);
        requestButton.setText(R.string.join_request_cancelling);
        joinRequestService.cancel(
                ride.id,
                getCurrentUserId(),
                new JoinRequestService.CancelCallback() {
                    @Override
                    public void onSuccess() {
                        requestedRideIds.remove(ride.id);
                        showAvailableState(ride, requestButton, requestStatus);
                        Toast.makeText(
                                SearchActivity.this,
                                R.string.join_request_cancelled,
                                Toast.LENGTH_LONG
                        ).show();
                    }

                    @Override
                    public void onNotPending() {
                        requestedRideIds.remove(ride.id);
                        Toast.makeText(
                                SearchActivity.this,
                                R.string.join_request_not_pending,
                                Toast.LENGTH_LONG
                        ).show();
                        search();
                    }

                    @Override
                    public void onError(String message) {
                        showPendingState(ride, requestButton, requestStatus);
                        Toast.makeText(SearchActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void showPendingState(Ride ride, Button button, TextView status) {
        button.setEnabled(true);
        button.setText(R.string.join_request_cancel_action);
        button.setOnClickListener(
                view -> showCancelRequestDialog(ride, button, status)
        );
        status.setVisibility(View.VISIBLE);
    }

    private void showAvailableState(Ride ride, Button button, TextView status) {
        button.setEnabled(true);
        button.setText(R.string.join_request_action);
        button.setOnClickListener(
                view -> showJoinRequestDialog(ride, button, status)
        );
        status.setVisibility(View.GONE);
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

    private String formatDate(long millis) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(millis));
    }

    private String formatTime(int minuteOfDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        calendar.set(Calendar.MINUTE, minuteOfDay % 60);
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.getTime());
    }

    private static boolean sameText(String first, String second) {
        return first != null && second != null
                && first.trim().toLowerCase(Locale.ROOT)
                .equals(second.trim().toLowerCase(Locale.ROOT));
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
