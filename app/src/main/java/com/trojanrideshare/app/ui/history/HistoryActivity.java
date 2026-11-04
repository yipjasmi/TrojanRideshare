package com.trojanrideshare.app.ui.history;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.trojanrideshare.app.BuildConfig;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.data.user.UserDataAccess;
import com.trojanrideshare.app.model.*;
import com.trojanrideshare.app.service.history.HistoryService;
import com.trojanrideshare.app.util.BottomNavHelper;
import com.trojanrideshare.app.util.Money;
import java.text.DateFormat;
import java.util.*;

public class HistoryActivity extends AppCompatActivity {
    private final HistoryService service = new HistoryService();
    private List<Ride> rides = new ArrayList<>();
    private Map<String, Payment> payments = new HashMap<>();
    private String uid;
    private LinearLayout content, results;
    private TextView message, summary;
    private EditText origin, destination, radius, year;
    private Spinner status, sort, participant, month;
    private final List<String> participantIds = new ArrayList<>();
    private final Map<String, String> participantNames = new HashMap<>();
    private final UserDataAccess userDataAccess = new UserDataAccess();
    /** Used when a point is picked for a radius search but no radius is typed. */
    private static final double DEFAULT_RADIUS_MILES = 5.0;
    public static final String NO_ACTIVITY_MESSAGE = "No activity! Please select another month.";
    private PlaceInfo originCenter, destinationCenter;
    private boolean pickingOrigin;
    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                Place place = Autocomplete.getPlaceFromIntent(data);
                if (place.getLatLng() == null) { message.setText("Selected location has no coordinates."); return; }
                PlaceInfo value = new PlaceInfo(); value.address = place.getFormattedAddress();
                value.placeId = place.getId(); value.lat = place.getLatLng().latitude;
                value.lng = place.getLatLng().longitude;
                if (pickingOrigin) { originCenter = value; origin.setText(value.address); }
                else { destinationCenter = value; destination.setText(value.address); }
            } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                if (data == null) {
                    message.setText("Places returned an error without details.");
                    return;
                }
                com.google.android.gms.common.api.Status error =
                        Autocomplete.getStatusFromIntent(data);
                android.util.Log.e("HistoryPlaces", "Places error: " + error);
                message.setText("Places error " + error.getStatusCode()
                        + ": " + error.getStatusMessage());
            }
        });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_history);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.historyRoot), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        BottomNavHelper.setupBottomNav(this, R.id.nav_history);
        content = findViewById(R.id.historyContent);
        label("Ride history", 26);
        label("Completed and cancelled rides you took part in. Every search input is optional: "
            + "type an origin and/or destination keyword, or pick a point to search by radius "
            + "(default " + (int) DEFAULT_RADIUS_MILES + " miles).", 14);
        origin = input("Origin keyword (optional)", false); destination = input("Destination keyword (optional)", false);
        button("Pick origin point for radius", () -> choosePlace(true));
        button("Pick destination point for radius", () -> choosePlace(false));
        radius = input("Radius in miles (default " + (int) DEFAULT_RADIUS_MILES + ")", true);
        status = spinner(new String[]{"Completed and cancelled", "Completed", "Cancelled"});
        sort = spinner(new String[]{"Newest first", "Oldest first"});
        participant = spinner(new String[]{"All participants"});
        button("Apply history filters", this::render);
        button("Clear history filters", () -> {
            origin.setText(""); destination.setText(""); radius.setText("");
            originCenter = null; destinationCenter = null;
            status.setSelection(0); sort.setSelection(0); participant.setSelection(0); render();
        });
        label("Monthly summary", 22);
        Calendar now = Calendar.getInstance();
        month = spinner(new String[]{"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"});
        month.setSelection(now.get(Calendar.MONTH));
        year = input("Year", true); year.setText(String.valueOf(now.get(Calendar.YEAR)));
        button("Show monthly summary", this::renderSummary);
        summary = label("", 16);
        button("Refresh history and payments", this::load);
        message = label("", 16);
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); content.addView(results);
        if (!BuildConfig.PLACES_API_KEY.trim().isEmpty() && !Places.isInitialized())
            Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), BuildConfig.PLACES_API_KEY);
    }

    @Override protected void onResume() { super.onResume(); load(); }
    private void load() {
        message.setText("Loading history..."); results.removeAllViews(); summary.setText("");
        service.load(new HistoryService.Callback() {
            public void onSuccess(String userId, List<Ride> loaded, Map<String, Payment> shares) {
                if (isFinishing() || isDestroyed()) return;
                uid = userId; rides = loaded; payments = shares;
                updateParticipants(); render(); renderSummary();
            }
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                uid = null; rides.clear(); payments.clear(); results.removeAllViews();
                summary.setText(""); message.setText(error);
            }
        });
    }
    private void updateParticipants() {
        String selected = participant.getSelectedItemPosition() < participantIds.size()
            && participant.getSelectedItemPosition() >= 0 ? participantIds.get(participant.getSelectedItemPosition()) : "";
        Set<String> others = new TreeSet<>();
        for (Ride ride : rides) others.addAll(ride.getAllParticipantIds());
        others.remove(uid); participantIds.clear(); participantIds.add(""); participantIds.addAll(others);
        List<String> labels = new ArrayList<>(); labels.add("All participants");
        for (String id : others) labels.add(participantLabel(id));
        participant.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        participant.setSelection(Math.max(0, participantIds.indexOf(selected)));
        for (String id : others) {
            if (participantNames.containsKey(id)) continue;
            userDataAccess.getById(id, new UserDataAccess.SingleCallback() {
                public void onSuccess(User user) {
                    if (user == null || isFinishing() || isDestroyed()) return;
                    participantNames.put(id, user.getDisplayName());
                    refreshParticipantLabels();
                }
                public void onError(Exception exception) {}
            });
        }
    }
    private String participantLabel(String id) {
        String name = participantNames.get(id);
        return name != null ? "With " + name : "With another student";
    }
    /** Relabels the spinner as names arrive, keeping the current selection. */
    private void refreshParticipantLabels() {
        int position = participant.getSelectedItemPosition();
        List<String> labels = new ArrayList<>(); labels.add("All participants");
        for (int i = 1; i < participantIds.size(); i++) labels.add(participantLabel(participantIds.get(i)));
        participant.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        participant.setSelection(Math.max(0, position));
    }
    private void render() {
        if (uid == null) return;
        HistoryFilter f = new HistoryFilter();
        f.origin = origin.getText().toString(); f.destination = destination.getText().toString();
        // Editing an address invalidates its earlier Places selection.
        if (originCenter != null && !f.origin.equals(originCenter.address)) originCenter = null;
        if (destinationCenter != null && !f.destination.equals(destinationCenter.address)) destinationCenter = null;
        f.originCenter = originCenter; f.destinationCenter = destinationCenter;
        f.status = new String[]{"", Ride.STATUS_COMPLETED,
            Ride.STATUS_CANCELLED}[status.getSelectedItemPosition()];
        f.oldestFirst = sort.getSelectedItemPosition() == 1;
        int index = participant.getSelectedItemPosition();
        f.participantId = index >= 0 && index < participantIds.size() ? participantIds.get(index) : "";
        try {
            String value = radius.getText().toString().trim();
            if (!value.isEmpty()) f.radiusMiles = Double.parseDouble(value);
            else if (originCenter != null || destinationCenter != null) f.radiusMiles = DEFAULT_RADIUS_MILES;
            List<Ride> displayed = HistoryService.filter(rides, uid, f, System.currentTimeMillis());
            results.removeAllViews();
            message.setText(displayed.isEmpty() ? "No rides match your history filters." : displayed.size() + " rides");
            for (Ride ride : displayed) {
                TextView row = new TextView(this); row.setTextSize(16); row.setPadding(0, 20, 0, 20);
                String text = fallback(ride.title, "Ride") + "\n" + address(ride.origin) + " → " + address(ride.destination)
                    + "\n" + DateFormat.getDateTimeInstance().format(ride.departureTime.toDate())
                    + "\nStatus: " + statusLabel(ride.status) + " · Participants: " + ride.getAllParticipantIds().size()
                    + (uid.equals(ride.organizerId) ? " · You organized" : "");
                if (ride.completedAt != null) {
                    text += "\nMarked completed " + DateFormat.getDateTimeInstance().format(ride.completedAt.toDate());
                }
                if (Ride.STATUS_COMPLETED.equals(ride.status)) {
                    Payment p = payments.get(ride.id);
                    text += p == null ? "\nYour share: not assigned yet" : "\nYour share: "
                        + Money.format(Money.toCents(p.amount)) + " · " + (p.paid ? "Paid" : "Unpaid");
                }
                row.setText(text); results.addView(row);
            }
        } catch (IllegalArgumentException error) {
            results.removeAllViews(); message.setText("Enter a positive radius and choose at least one location using the location buttons.");
        }
    }
    private void renderSummary() {
        if (uid == null) return;
        try {
            int selectedYear = Integer.parseInt(year.getText().toString().trim());
            if (selectedYear < 1 || selectedYear > 9999) throw new NumberFormatException();
            MonthlySummary s = HistoryService.summarize(rides, payments, uid, selectedYear, month.getSelectedItemPosition() + 1);
            summary.setText(!s.hasActivity() ? NO_ACTIVITY_MESSAGE
                : "Completed rides: " + s.completedRides + "\nOrganized: " + s.ownedRides + " · Joined: " + s.joinedRides
                + "\nYour total assigned cost: " + Money.format(s.totalCostCents)
                + "\n  Paid: " + Money.format(s.paidCostCents) + " · Unpaid: " + Money.format(s.unpaidCostCents)
                + (s.unassignedShares == 0 ? "" : "\n" + s.unassignedShares + " ride(s) awaiting your cost share; total is incomplete."));
        } catch (NumberFormatException error) { year.setError("Enter a year from 1 to 9999"); }
    }
    private void choosePlace(boolean isOrigin) {
        com.trojanrideshare.app.util.PlacesLocationPicker.show(this, selected -> {
            if (isOrigin) { originCenter = selected; origin.setText(selected.address); }
            else { destinationCenter = selected; destination.setText(selected.address); }
        });
    }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); content.addView(v); return v;
    }
    private EditText input(String hint, boolean numeric) {
        EditText v = new EditText(this); v.setHint(hint); v.setSingleLine(true);
        if (numeric) v.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        v.setId(View.generateViewId()); content.addView(v); return v;
    }
    private Spinner spinner(String[] options) {
        Spinner v = new Spinner(this); v.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        content.addView(v); return v;
    }
    private void button(String text, Runnable action) {
        Button v = new Button(this); v.setText(text); v.setOnClickListener(view -> action.run()); content.addView(v);
    }
    private static String fallback(String value, String fallback) { return value == null || value.isEmpty() ? fallback : value; }
    private static String address(PlaceInfo p) { return p == null ? "Unknown location" : fallback(p.address, "Unknown location"); }
    private static String statusLabel(String value) {
        if (Ride.STATUS_COMPLETED.equals(value)) return "Completed";
        if (Ride.STATUS_CANCELLED.equals(value)) return "Cancelled";
        if (Ride.STATUS_DELAYED.equals(value)) return "Delayed";
        if (Ride.STATUS_ON_TIME.equals(value)) return "On time";
        return fallback(value, "Unknown");
    }
}
