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
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.firebase.Timestamp;
import com.trojanrideshare.app.BuildConfig;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.SearchCriteria;
import com.trojanrideshare.app.model.SortOption;
import com.trojanrideshare.app.service.search.SearchService;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        bindViews();
        searchService = new SearchService();
        initializePlaces();
        updateTimeButtons();

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

        search();
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
            Places.initialize(getApplicationContext(), BuildConfig.PLACES_API_KEY);
        }
    }

    private void launchPlacePicker(ActivityResultLauncher<Intent> launcher) {
        if (!Places.isInitialized()) {
            Toast.makeText(this, R.string.search_places_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LAT_LNG
        );
        Intent intent = new Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                fields
        ).setCountries(Collections.singletonList("US")).build(this);
        launcher.launch(intent);
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
                originInput.setText(selected.address);
            } else {
                preferredDestination = selected;
                destinationInput.setText(selected.address);
            }
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
        refreshSelectedLocations();
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
                getIntent().getStringExtra(EXTRA_CURRENT_USER_ID),
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

    private void refreshSelectedLocations() {
        if (preferredOrigin != null
                && !sameText(originInput.getText().toString(), preferredOrigin.address)) {
            preferredOrigin = null;
        }
        if (preferredDestination != null
                && !sameText(destinationInput.getText().toString(), preferredDestination.address)) {
            preferredDestination = null;
        }
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
        updateTimeButtons();
        search();
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            resultMessage.setVisibility(View.GONE);
            resultsContainer.removeAllViews();
        }
    }

    private void renderResults(List<Ride> rides) {
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
        ((TextView) item.findViewById(R.id.rideTitle)).setText(
                emptyFallback(ride.title, getString(R.string.search_untitled_ride))
        );
        ((TextView) item.findViewById(R.id.rideRoute)).setText(getString(
                R.string.search_route_format,
                addressOf(ride.origin),
                addressOf(ride.destination)
        ));
        ((TextView) item.findViewById(R.id.rideDeparture)).setText(getString(
                R.string.search_departure_format,
                formatDeparture(ride.departureTime)
        ));

        int participantCount = ride.participantIds == null ? 0 : ride.participantIds.size();
        int availableSeats = Math.max(0, ride.maxRiders - participantCount);
        ((TextView) item.findViewById(R.id.rideSeats)).setText(getResources()
                .getQuantityString(R.plurals.search_available_seats, availableSeats, availableSeats));
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
