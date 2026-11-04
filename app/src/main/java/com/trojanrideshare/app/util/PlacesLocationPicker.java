package com.trojanrideshare.app.util;

import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.*;
import com.google.android.libraries.places.api.net.*;
import com.trojanrideshare.app.BuildConfig;
import com.trojanrideshare.app.model.PlaceInfo;
import java.util.Arrays;

/** Places API (New) location selection, compatible with the project's Places SDK 4.1.0. */
public final class PlacesLocationPicker {
    private PlacesLocationPicker() {}
    public interface Callback { void onSelected(PlaceInfo location); }

    public static void show(AppCompatActivity activity, Callback callback) {
        if (BuildConfig.PLACES_API_KEY.trim().isEmpty()) {
            Toast.makeText(activity, "Add PLACES_API_KEY to local.properties and rebuild.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!Places.isInitialized())
            Places.initializeWithNewPlacesApiEnabled(activity.getApplicationContext(), BuildConfig.PLACES_API_KEY);
        PlacesClient client = Places.createClient(activity);
        AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * activity.getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);
        EditText query = new EditText(activity); query.setSingleLine(true); query.setHint("Enter a place, such as USC Village");
        panel.addView(query);
        TextView state = new TextView(activity); state.setText("Enter a location, then tap Search."); panel.addView(state);
        TextView attribution = new TextView(activity); attribution.setText("Google Maps"); panel.addView(attribution);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout options = new LinearLayout(activity); options.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(options); panel.addView(scroll);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle("Choose location")
            .setView(panel).setNegativeButton("Cancel", null).setPositiveButton("Search", null).create();
        dialog.setOnShowListener(unused -> {
            Button search = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            search.setOnClickListener(view -> {
                String text = query.getText().toString().trim();
                if (text.length() < 3) { query.setError("Enter at least 3 characters"); return; }
                options.removeAllViews(); state.setText("Searching..."); search.setEnabled(false);
                FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(text).setCountries("US").setSessionToken(token).build();
                client.findAutocompletePredictions(request).addOnSuccessListener(activity, response -> {
                    if (!dialog.isShowing()) return;
                    search.setEnabled(true);
                    state.setText(response.getAutocompletePredictions().isEmpty()
                        ? "No locations found. Try a more specific search." : "Select a result:");
                    for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                        Button option = new Button(activity); option.setAllCaps(false);
                        option.setText(prediction.getFullText(null)); options.addView(option);
                        option.setOnClickListener(selected -> {
                            search.setEnabled(false); query.setEnabled(false);
                            for (int i=0; i<options.getChildCount(); i++) options.getChildAt(i).setEnabled(false);
                            state.setText("Loading selected location...");
                            FetchPlaceRequest details = FetchPlaceRequest.builder(prediction.getPlaceId(),
                                Arrays.asList(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION))
                                .setSessionToken(token).build();
                            client.fetchPlace(details).addOnSuccessListener(activity, responseDetails -> {
                                if (!dialog.isShowing()) return;
                                Place place = responseDetails.getPlace();
                                if (place.getLocation() == null) {
                                    state.setText("This location has no coordinates. Search again.");
                                    search.setEnabled(true); query.setEnabled(true); return;
                                }
                                PlaceInfo value = new PlaceInfo(); value.placeId = place.getId();
                                value.address = place.getFormattedAddress();
                                if (value.address == null) value.address = prediction.getFullText(null).toString();
                                value.lat = place.getLocation().latitude; value.lng = place.getLocation().longitude;
                                value.name = place.getDisplayName() != null ? place.getDisplayName()
                                    : prediction.getPrimaryText(null).toString();
                                value.searchQuery = text;
                                dialog.dismiss(); callback.onSelected(value);
                            }).addOnFailureListener(activity, error -> {
                                if (!dialog.isShowing()) return;
                                search.setEnabled(true); query.setEnabled(true); showError(state, error);
                            });
                        });
                    }
                }).addOnFailureListener(activity, error -> {
                    if (!dialog.isShowing()) return;
                    search.setEnabled(true); showError(state, error);
                });
            });
        });
        dialog.show();
    }
    private static void showError(TextView state, Exception error) {
        android.util.Log.e("HistoryPlaces", "Places API (New) request failed", error);
        state.setText("Location search failed: " + error.getMessage());
    }
}
