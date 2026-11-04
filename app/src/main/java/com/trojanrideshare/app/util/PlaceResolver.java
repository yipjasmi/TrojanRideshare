package com.trojanrideshare.app.util;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.trojanrideshare.app.BuildConfig;
import com.trojanrideshare.app.model.PlaceInfo;

import java.util.Arrays;

/**
 * Turns typed text such as "USC" into a map point: Google's top autocomplete match, biased to
 * the Los Angeles area. Used for radius searches so the user doesn't have to pick a point.
 */
public final class PlaceResolver {
    public interface Callback {
        void onResolved(PlaceInfo place);
        void onError(String message);
    }

    // Greater Los Angeles, so "USC" or "UCLA" resolve to the campuses.
    private static final RectangularBounds LA_BIAS = RectangularBounds.newInstance(
            new LatLng(33.60, -118.75), new LatLng(34.35, -117.85));

    private PlaceResolver() {}

    public static void resolve(AppCompatActivity activity, String query, Callback callback) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            callback.onError("Enter a location.");
            return;
        }
        if (BuildConfig.PLACES_API_KEY.trim().isEmpty()) {
            callback.onError("Location search is unavailable. Add PLACES_API_KEY to local.properties.");
            return;
        }
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(activity.getApplicationContext(),
                    BuildConfig.PLACES_API_KEY);
        }
        PlacesClient client = Places.createClient(activity);
        AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setQuery(text)
                .setCountries("US")
                .setLocationBias(LA_BIAS)
                .setSessionToken(token)
                .build();

        client.findAutocompletePredictions(request).addOnSuccessListener(activity, response -> {
            if (response.getAutocompletePredictions().isEmpty()) {
                callback.onError("Couldn't find a place for \"" + text + "\". Try a more specific name.");
                return;
            }
            AutocompletePrediction top = response.getAutocompletePredictions().get(0);
            FetchPlaceRequest details = FetchPlaceRequest.builder(top.getPlaceId(), Arrays.asList(
                            Place.Field.ID, Place.Field.DISPLAY_NAME,
                            Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION))
                    .setSessionToken(token)
                    .build();
            client.fetchPlace(details).addOnSuccessListener(activity, fetched -> {
                Place place = fetched.getPlace();
                if (place.getLocation() == null) {
                    callback.onError("\"" + text + "\" has no map location. Try a more specific name.");
                    return;
                }
                PlaceInfo info = new PlaceInfo();
                info.placeId = place.getId();
                info.name = place.getDisplayName() != null
                        ? place.getDisplayName() : top.getPrimaryText(null).toString();
                info.address = place.getFormattedAddress() != null
                        ? place.getFormattedAddress() : top.getFullText(null).toString();
                info.lat = place.getLocation().latitude;
                info.lng = place.getLocation().longitude;
                info.searchQuery = text;
                callback.onResolved(info);
            }).addOnFailureListener(activity, error ->
                    callback.onError("Location lookup failed: " + error.getMessage()));
        }).addOnFailureListener(activity, error ->
                callback.onError("Location lookup failed: " + error.getMessage()));
    }

    /** Short label for the point being used, e.g. "University of Southern California". */
    public static String label(PlaceInfo place) {
        if (place == null) return "";
        if (place.name != null && !place.name.trim().isEmpty()) {
            return place.address != null && !place.address.startsWith(place.name)
                    ? place.name + " · " + place.address : place.name;
        }
        return place.address == null ? "" : place.address;
    }
}
