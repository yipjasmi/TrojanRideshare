package com.trojanrideshare.app.ui.ride;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.trojanrideshare.app.BuildConfig;
import com.trojanrideshare.app.R;

import java.util.Arrays;
import java.util.List;

/**
 * Lightweight place picker built on the <em>new</em> Places API
 * ({@code findAutocompletePredictions} + {@code fetchPlace}). The legacy prebuilt
 * Autocomplete widget is not usable here because the project only has "Places API (New)"
 * enabled.
 *
 * <p>Returns the chosen place to the caller as intent extras
 * ({@link #EXTRA_PLACE_ID}, {@link #EXTRA_ADDRESS}, {@link #EXTRA_LAT}, {@link #EXTRA_LNG}).
 */
public class PlacePickerActivity extends AppCompatActivity {
    public static final String EXTRA_PLACE_ID = "placeId";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";

    private static final long DEBOUNCE_MS = 300L;
    private static final int MIN_QUERY_LENGTH = 3;
    private static final List<Place.Field> FETCH_FIELDS = Arrays.asList(
            Place.Field.ID,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION
    );

    private LinearLayout resultsContainer;
    private TextView messageView;
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_picker);

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(
                    getApplicationContext(), BuildConfig.PLACES_API_KEY);
        }
        placesClient = Places.createClient(this);
        sessionToken = AutocompleteSessionToken.newInstance();

        resultsContainer = findViewById(R.id.placeResultsContainer);
        messageView = findViewById(R.id.placePickerMessage);

        EditText queryInput = findViewById(R.id.placeQueryInput);
        queryInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                scheduleQuery(s.toString().trim());
            }
        });
    }

    private void scheduleQuery(String query) {
        if (pendingQuery != null) {
            handler.removeCallbacks(pendingQuery);
        }
        if (query.length() < MIN_QUERY_LENGTH) {
            resultsContainer.removeAllViews();
            showMessage(R.string.place_picker_prompt);
            return;
        }
        pendingQuery = () -> runQuery(query);
        handler.postDelayed(pendingQuery, DEBOUNCE_MS);
    }

    private void runQuery(String query) {
        FindAutocompletePredictionsRequest request =
                FindAutocompletePredictionsRequest.builder()
                        .setSessionToken(sessionToken)
                        .setQuery(query)
                        .build();
        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response ->
                        renderPredictions(response.getAutocompletePredictions()))
                .addOnFailureListener(e -> showMessage(R.string.place_picker_error));
    }

    private void renderPredictions(List<AutocompletePrediction> predictions) {
        resultsContainer.removeAllViews();
        if (predictions.isEmpty()) {
            showMessage(R.string.place_picker_prompt);
            return;
        }
        messageView.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AutocompletePrediction prediction : predictions) {
            View row = inflater.inflate(R.layout.item_place_prediction, resultsContainer, false);
            ((TextView) row.findViewById(R.id.predictionPrimary))
                    .setText(prediction.getPrimaryText(null));
            ((TextView) row.findViewById(R.id.predictionSecondary))
                    .setText(prediction.getSecondaryText(null));
            String placeId = prediction.getPlaceId();
            row.setOnClickListener(v -> fetchAndReturn(placeId));
            resultsContainer.addView(row);
        }
    }

    private void fetchAndReturn(String placeId) {
        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, FETCH_FIELDS)
                .setSessionToken(sessionToken)
                .build();
        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    Intent data = new Intent();
                    data.putExtra(EXTRA_PLACE_ID, place.getId());
                    data.putExtra(EXTRA_ADDRESS, place.getFormattedAddress());
                    LatLng location = place.getLocation();
                    if (location != null) {
                        data.putExtra(EXTRA_LAT, location.latitude);
                        data.putExtra(EXTRA_LNG, location.longitude);
                    }
                    setResult(RESULT_OK, data);
                    finish();
                })
                .addOnFailureListener(e -> showMessage(R.string.place_picker_error));
    }

    private void showMessage(int stringRes) {
        messageView.setText(stringRes);
        messageView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (pendingQuery != null) {
            handler.removeCallbacks(pendingQuery);
        }
        super.onDestroy();
    }
}
