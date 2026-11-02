package com.trojanrideshare.app.ui.ride;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.ride.EditRideService;
import com.trojanrideshare.app.service.ride.RideInput;
import com.trojanrideshare.app.service.ride.RideValidator;
import com.trojanrideshare.app.service.ride.ValidationResult;

import java.text.DateFormat;
import java.util.Calendar;

public class EditRideActivity extends AppCompatActivity {
    public static final String EXTRA_RIDE_ID = "rideId";
    public static final String EXTRA_CURRENT_USER_ID = "currentUserId";

    // Spinner positions map to these ride statuses.
    private static final String[] STATUS_BY_POSITION = {
            Ride.STATUS_ON_TIME, Ride.STATUS_DELAYED, Ride.STATUS_CANCELLED
    };

    private EditText titleInput;
    private EditText originInput;
    private EditText destinationInput;
    private EditText departureInput;
    private EditText maxRidersInput;
    private EditText costInput;
    private Spinner statusSpinner;
    private Button saveButton;
    private ProgressBar loadingIndicator;

    private RideDataAccess rideDataAccess;
    private EditRideService editRideService;

    private String currentUserId;
    private Ride ride;
    private PlaceInfo origin;
    private PlaceInfo destination;
    private Calendar departure;

    private ActivityResultLauncher<Intent> originPicker;
    private ActivityResultLauncher<Intent> destinationPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_ride);

        rideDataAccess = new RideDataAccess();
        editRideService = new EditRideService();
        currentUserId = getIntent().getStringExtra(EXTRA_CURRENT_USER_ID);

        titleInput = findViewById(R.id.titleInput);
        originInput = findViewById(R.id.originInput);
        destinationInput = findViewById(R.id.destinationInput);
        departureInput = findViewById(R.id.departureInput);
        maxRidersInput = findViewById(R.id.maxRidersInput);
        costInput = findViewById(R.id.costInput);
        statusSpinner = findViewById(R.id.statusSpinner);
        saveButton = findViewById(R.id.saveButton);
        loadingIndicator = findViewById(R.id.editLoadingIndicator);

        originPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handlePlaceResult(result.getResultCode(), result.getData(), true));
        destinationPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handlePlaceResult(result.getResultCode(), result.getData(), false));

        originInput.setOnClickListener(v -> originPicker.launch(
                new Intent(this, PlacePickerActivity.class)));
        destinationInput.setOnClickListener(v -> destinationPicker.launch(
                new Intent(this, PlacePickerActivity.class)));
        departureInput.setOnClickListener(v -> pickDeparture());
        saveButton.setOnClickListener(v -> save());

        String rideId = getIntent().getStringExtra(EXTRA_RIDE_ID);
        if (TextUtils.isEmpty(rideId)) {
            Toast.makeText(this, R.string.edit_ride_load_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        loadRide(rideId);
    }

    private void loadRide(String rideId) {
        setLoading(true);
        rideDataAccess.getRideById(rideId, new RideDataAccess.SingleCallback() {
            @Override
            public void onSuccess(Ride loaded) {
                setLoading(false);
                if (loaded == null) {
                    Toast.makeText(
                            EditRideActivity.this, R.string.edit_ride_load_error,
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                ride = loaded;
                prefill(loaded);
            }

            @Override
            public void onError(Exception exception) {
                setLoading(false);
                Toast.makeText(
                        EditRideActivity.this, R.string.edit_ride_load_error,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void prefill(Ride ride) {
        titleInput.setText(ride.title);
        origin = ride.origin;
        destination = ride.destination;
        originInput.setText(ride.origin == null ? "" : ride.origin.address);
        destinationInput.setText(ride.destination == null ? "" : ride.destination.address);
        if (ride.departureTime != null) {
            departure = Calendar.getInstance();
            departure.setTime(ride.departureTime.toDate());
            departureInput.setText(formatDeparture(departure));
        }
        maxRidersInput.setText(String.valueOf(ride.maxRiders));
        costInput.setText(String.format(java.util.Locale.US, "%.2f", ride.expectedCost));
        statusSpinner.setSelection(positionForStatus(ride.status));
    }

    private void save() {
        String newStatus = STATUS_BY_POSITION[statusSpinner.getSelectedItemPosition()];
        setLoading(true);

        EditRideService.Callback callback = new EditRideService.Callback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                Toast.makeText(
                        EditRideActivity.this,
                        Ride.STATUS_CANCELLED.equals(newStatus)
                                ? R.string.edit_ride_cancelled
                                : R.string.edit_ride_success,
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onInvalid(ValidationResult result) {
                setLoading(false);
                showErrors(result);
            }

            @Override
            public void onDenied(String reason) {
                setLoading(false);
                Toast.makeText(EditRideActivity.this, reason, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(EditRideActivity.this, message, Toast.LENGTH_LONG).show();
            }
        };

        if (Ride.STATUS_CANCELLED.equals(newStatus)) {
            editRideService.cancelRide(ride, currentUserId, callback);
        } else {
            RideInput input = new RideInput(
                    titleInput.getText().toString(),
                    origin,
                    destination,
                    departure == null ? null : new Timestamp(departure.getTime()),
                    parseInteger(maxRidersInput.getText().toString()),
                    parseDouble(costInput.getText().toString()));
            editRideService.updateRide(ride, input, newStatus, currentUserId, callback);
        }
    }

    private void handlePlaceResult(int resultCode, @Nullable Intent data, boolean isOrigin) {
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        PlaceInfo info = new PlaceInfo();
        info.placeId = data.getStringExtra(PlacePickerActivity.EXTRA_PLACE_ID);
        info.address = data.getStringExtra(PlacePickerActivity.EXTRA_ADDRESS);
        info.lat = data.getDoubleExtra(PlacePickerActivity.EXTRA_LAT, 0);
        info.lng = data.getDoubleExtra(PlacePickerActivity.EXTRA_LNG, 0);
        if (isOrigin) {
            origin = info;
            originInput.setText(info.address);
        } else {
            destination = info;
            destinationInput.setText(info.address);
        }
    }

    private void pickDeparture() {
        Calendar initial = departure != null ? departure : Calendar.getInstance();
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    pickDepartureTime(picked);
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void pickDepartureTime(Calendar date) {
        Calendar initial = departure != null ? departure : Calendar.getInstance();
        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    date.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    date.set(Calendar.MINUTE, minute);
                    date.set(Calendar.SECOND, 0);
                    date.set(Calendar.MILLISECOND, 0);
                    departure = date;
                    departureInput.setText(formatDeparture(date));
                },
                initial.get(Calendar.HOUR_OF_DAY),
                initial.get(Calendar.MINUTE),
                false
        ).show();
    }

    private void showErrors(ValidationResult result) {
        setError(R.id.titleError, result.errorFor(RideValidator.FIELD_TITLE));
        setError(R.id.originError, result.errorFor(RideValidator.FIELD_ORIGIN));
        setError(R.id.destinationError, result.errorFor(RideValidator.FIELD_DESTINATION));
        setError(R.id.departureError, result.errorFor(RideValidator.FIELD_DEPARTURE_TIME));
        setError(R.id.maxRidersError, result.errorFor(RideValidator.FIELD_MAX_RIDERS));
        setError(R.id.costError, result.errorFor(RideValidator.FIELD_EXPECTED_COST));
    }

    private void setError(int viewId, @Nullable String message) {
        TextView view = findViewById(viewId);
        if (message == null) {
            view.setText("");
            view.setVisibility(View.GONE);
        } else {
            view.setText(message);
            view.setVisibility(View.VISIBLE);
        }
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!loading);
    }

    private static int positionForStatus(String status) {
        for (int i = 0; i < STATUS_BY_POSITION.length; i++) {
            if (STATUS_BY_POSITION[i].equals(status)) {
                return i;
            }
        }
        return 0;
    }

    private static String formatDeparture(Calendar calendar) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(calendar.getTime());
    }

    @Nullable
    private static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static Double parseDouble(String value) {
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
