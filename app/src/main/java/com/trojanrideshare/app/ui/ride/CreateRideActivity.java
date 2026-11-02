package com.trojanrideshare.app.ui.ride;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.service.ride.CreateRideService;
import com.trojanrideshare.app.service.ride.RideInput;
import com.trojanrideshare.app.service.ride.RideValidator;
import com.trojanrideshare.app.service.ride.ValidationResult;

import java.text.DateFormat;
import java.util.Calendar;

public class CreateRideActivity extends AppCompatActivity {
    public static final String EXTRA_CURRENT_USER_ID = "currentUserId";

    private EditText titleInput;
    private EditText originInput;
    private EditText destinationInput;
    private EditText departureInput;
    private EditText maxRidersInput;
    private EditText costInput;
    private Button submitButton;
    private ProgressBar loadingIndicator;

    private CreateRideService createRideService;

    private PlaceInfo origin;
    private PlaceInfo destination;
    private Calendar departure;

    private ActivityResultLauncher<Intent> originPicker;
    private ActivityResultLauncher<Intent> destinationPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_ride);

        createRideService = new CreateRideService();

        titleInput = findViewById(R.id.titleInput);
        originInput = findViewById(R.id.originInput);
        destinationInput = findViewById(R.id.destinationInput);
        departureInput = findViewById(R.id.departureInput);
        maxRidersInput = findViewById(R.id.maxRidersInput);
        costInput = findViewById(R.id.costInput);
        submitButton = findViewById(R.id.submitButton);
        loadingIndicator = findViewById(R.id.createLoadingIndicator);

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
        submitButton.setOnClickListener(v -> submit());
    }

    private void handlePlaceResult(int resultCode, @Nullable Intent data, boolean isOrigin) {
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        PlaceInfo info = toPlaceInfo(data);
        if (isOrigin) {
            origin = info;
            originInput.setText(info.address);
        } else {
            destination = info;
            destinationInput.setText(info.address);
        }
    }

    private static PlaceInfo toPlaceInfo(Intent data) {
        PlaceInfo info = new PlaceInfo();
        info.placeId = data.getStringExtra(PlacePickerActivity.EXTRA_PLACE_ID);
        info.address = data.getStringExtra(PlacePickerActivity.EXTRA_ADDRESS);
        info.lat = data.getDoubleExtra(PlacePickerActivity.EXTRA_LAT, 0);
        info.lng = data.getDoubleExtra(PlacePickerActivity.EXTRA_LNG, 0);
        return info;
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
                    departureInput.setText(DateFormat
                            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(date.getTime()));
                },
                initial.get(Calendar.HOUR_OF_DAY),
                initial.get(Calendar.MINUTE),
                false
        ).show();
    }

    private void submit() {
        RideInput input = new RideInput(
                titleInput.getText().toString(),
                origin,
                destination,
                departure == null ? null : new Timestamp(departure.getTime()),
                parseInteger(maxRidersInput.getText().toString()),
                parseDouble(costInput.getText().toString())
        );

        setLoading(true);
        String currentUserId = getIntent().getStringExtra(EXTRA_CURRENT_USER_ID);
        createRideService.createRide(
                input,
                currentUserId,
                new CreateRideService.Callback() {
                    @Override
                    public void onInvalid(ValidationResult result) {
                        setLoading(false);
                        showErrors(result);
                    }

                    @Override
                    public void onSuccess(String rideId) {
                        setLoading(false);
                        Toast.makeText(
                                CreateRideActivity.this,
                                R.string.create_ride_success,
                                Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(
                                CreateRideActivity.this, RideDetailActivity.class);
                        intent.putExtra(RideDetailActivity.EXTRA_RIDE_ID, rideId);
                        intent.putExtra(RideDetailActivity.EXTRA_CURRENT_USER_ID, currentUserId);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(
                                CreateRideActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
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
        submitButton.setEnabled(!loading);
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
