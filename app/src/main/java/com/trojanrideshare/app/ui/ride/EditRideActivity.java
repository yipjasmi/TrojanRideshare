package com.trojanrideshare.app.ui.ride;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.ride.EditRideService;
import com.trojanrideshare.app.util.SystemBarsHelper;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Organizer screen for marking a ride Delayed with a new departure time.
 *
 * <p>The posted title, date, locations, expected cost and maximum riders are shown read-only:
 * they stay fixed after posting.
 */
public class EditRideActivity extends AppCompatActivity {
    public static final String EXTRA_RIDE_ID = "rideId";
    public static final String EXTRA_CURRENT_USER_ID = "currentUserId";

    private EditText newDepartureInput;
    private TextView delayError;
    private Button delayButton;
    private ProgressBar loadingIndicator;

    private RideDataAccess rideDataAccess;
    private EditRideService editRideService;

    private String currentUserId;
    private Ride ride;
    private Calendar newDeparture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_ride);
        SystemBarsHelper.applyPadding(this);

        rideDataAccess = new RideDataAccess();
        editRideService = new EditRideService();
        currentUserId = getIntent().getStringExtra(EXTRA_CURRENT_USER_ID);

        newDepartureInput = findViewById(R.id.newDepartureInput);
        delayError = findViewById(R.id.delayError);
        delayButton = findViewById(R.id.markDelayedButton);
        loadingIndicator = findViewById(R.id.editLoadingIndicator);

        newDepartureInput.setOnClickListener(v -> pickDeparture());
        delayButton.setOnClickListener(v -> markDelayed());
        MaterialToolbar toolbar = findViewById(R.id.editRideToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

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
                    Toast.makeText(EditRideActivity.this, R.string.edit_ride_load_error,
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                ride = loaded;
                bind(loaded);
            }

            @Override
            public void onError(Exception exception) {
                setLoading(false);
                Toast.makeText(EditRideActivity.this, R.string.edit_ride_load_error,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void bind(Ride ride) {
        setText(R.id.fixedTitle, "Title: " + ride.title);
        setText(R.id.fixedRoute, "Route: " + addressOf(ride.origin) + " → " + addressOf(ride.destination));
        Timestamp posted = ride.originalDepartureTime != null ? ride.originalDepartureTime : ride.departureTime;
        setText(R.id.fixedDate, "Posted departure: " + format(posted));
        setText(R.id.fixedMaxRiders, "Max riders (incl. organizer): " + ride.maxRiders);
        setText(R.id.fixedCost, String.format(Locale.US, "Expected total cost: $%.2f", ride.expectedCost));
        setText(R.id.currentStatus, "Current status: "
                + (Ride.STATUS_DELAYED.equals(ride.status) ? "Delayed" : statusName(ride.status))
                + " · departs " + format(ride.departureTime));

        boolean canDelay = ride.isActive();
        newDepartureInput.setEnabled(canDelay);
        delayButton.setEnabled(canDelay);
        if (!canDelay) {
            showError(EditRideService.REASON_FINAL);
        }
    }

    private void markDelayed() {
        if (ride == null) return;
        Timestamp chosen = newDeparture == null ? null : new Timestamp(newDeparture.getTime());
        String error = EditRideService.delayError(ride, chosen, currentUserId, Timestamp.now());
        if (error != null) {
            showError(error);
            return;
        }
        showError(null);
        setLoading(true);
        editRideService.markDelayed(ride, chosen, currentUserId, new EditRideService.Callback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                Toast.makeText(EditRideActivity.this, "Ride marked Delayed.", Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onDenied(String reason) {
                setLoading(false);
                showError(reason);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(EditRideActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void pickDeparture() {
        Calendar initial = Calendar.getInstance();
        if (newDeparture != null) {
            initial = newDeparture;
        } else if (ride != null && ride.departureTime != null) {
            initial.setTime(ride.departureTime.toDate());
        }
        final Calendar base = initial;
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth);
                    new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {
                                picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                picked.set(Calendar.MINUTE, minute);
                                picked.set(Calendar.SECOND, 0);
                                picked.set(Calendar.MILLISECOND, 0);
                                newDeparture = picked;
                                newDepartureInput.setText(format(new Timestamp(picked.getTime())));
                                showError(null);
                            },
                            base.get(Calendar.HOUR_OF_DAY),
                            base.get(Calendar.MINUTE),
                            false
                    ).show();
                },
                base.get(Calendar.YEAR),
                base.get(Calendar.MONTH),
                base.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showError(String message) {
        if (message == null) {
            delayError.setText("");
            delayError.setVisibility(View.GONE);
        } else {
            delayError.setText(message);
            delayError.setVisibility(View.VISIBLE);
        }
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        delayButton.setEnabled(!loading && ride != null && ride.isActive());
    }

    private void setText(int id, String text) {
        ((TextView) findViewById(id)).setText(text);
    }

    private static String statusName(String status) {
        if (Ride.STATUS_ON_TIME.equals(status)) return "On Time";
        if (Ride.STATUS_COMPLETED.equals(status)) return "Completed";
        if (Ride.STATUS_CANCELLED.equals(status)) return "Cancelled";
        return "Unknown";
    }

    private static String addressOf(PlaceInfo place) {
        return place == null || place.address == null ? "?" : place.address;
    }

    private static String format(Timestamp timestamp) {
        if (timestamp == null) return "—";
        Date date = timestamp.toDate();
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date);
    }
}
