package com.trojanrideshare.app.ui.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.SearchCriteria;
import com.trojanrideshare.app.service.search.SearchService;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_CURRENT_USER_ID = "currentUserId";

    private EditText originInput;
    private EditText destinationInput;
    private ProgressBar loadingIndicator;
    private TextView resultMessage;
    private LinearLayout resultsContainer;
    private SearchService searchService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        originInput = findViewById(R.id.originSearchInput);
        destinationInput = findViewById(R.id.destinationSearchInput);
        loadingIndicator = findViewById(R.id.searchLoadingIndicator);
        resultMessage = findViewById(R.id.searchResultMessage);
        resultsContainer = findViewById(R.id.searchResultsContainer);
        searchService = new SearchService();

        findViewById(R.id.searchButton).setOnClickListener(view -> search());
        search();
    }

    private void search() {
        setLoading(true);
        SearchCriteria criteria = new SearchCriteria(
                originInput.getText().toString(),
                destinationInput.getText().toString()
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
            View item = inflater.inflate(
                    R.layout.item_search_ride,
                    resultsContainer,
                    false
            );
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
                .getQuantityString(
                        R.plurals.search_available_seats,
                        availableSeats,
                        availableSeats
                ));
    }

    private String addressOf(com.trojanrideshare.app.model.PlaceInfo place) {
        if (place == null) {
            return getString(R.string.search_unknown_location);
        }
        return emptyFallback(place.address, getString(R.string.search_unknown_location));
    }

    private String formatDeparture(Timestamp departureTime) {
        Date date = departureTime == null ? null : departureTime.toDate();
        return date == null
                ? getString(R.string.search_unknown_time)
                : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(date);
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
