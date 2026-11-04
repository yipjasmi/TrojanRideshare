package com.trojanrideshare.app.ui.history;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.data.user.UserDataAccess;
import com.trojanrideshare.app.model.HistoryFilter;
import com.trojanrideshare.app.model.MonthlySummary;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.User;
import com.trojanrideshare.app.service.history.HistoryService;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.ui.ride.RideDetailActivity;
import com.trojanrideshare.app.util.BottomNavHelper;
import com.trojanrideshare.app.util.Money;
import com.trojanrideshare.app.util.PlaceResolver;
import com.trojanrideshare.app.util.PlacesLocationPicker;
import com.trojanrideshare.app.util.SystemBarsHelper;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ride history (completed and cancelled rides the user took part in) with search, filters and
 * sorting, plus a separate monthly summary at the bottom.
 */
public class HistoryActivity extends AppCompatActivity {
    public static final String NO_ACTIVITY_MESSAGE = "No activity! Please select another month.";

    private final HistoryService service = new HistoryService();
    private final PaymentService paymentService = new PaymentService();
    private final UserDataAccess userDataAccess = new UserDataAccess();

    private List<Ride> rides = new ArrayList<>();
    private Map<String, Payment> payments = new HashMap<>();
    private String uid;

    private final List<String> participantIds = new ArrayList<>();
    private final Map<String, String> participantNames = new HashMap<>();

    // Map points looked up from the typed origin/destination (radius searches only).
    private PlaceInfo originCenter, destinationCenter;
    private String originCenterFor, destinationCenterFor;

    private EditText origin, destination, radius;
    private TextView originUsing, destinationUsing;
    private ChipGroup statusChips;
    private Spinner participant;
    private MaterialButtonToggleGroup sortToggle;
    private TextView resultsHeader, resultsNote, message;
    private ProgressBar loading;
    private LinearLayout results;

    private final Calendar summaryMonth = Calendar.getInstance();
    private TextView summaryMonthLabel, summaryEmpty, summaryNote;
    private LinearLayout summaryContent, summaryRides;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_history);
        SystemBarsHelper.applyPadding(this);
        BottomNavHelper.setupBottomNav(this, R.id.nav_history);

        origin = findViewById(R.id.historyOriginInput);
        destination = findViewById(R.id.historyDestinationInput);
        radius = findViewById(R.id.historyRadiusInput);
        originUsing = findViewById(R.id.historyOriginUsing);
        destinationUsing = findViewById(R.id.historyDestinationUsing);
        statusChips = findViewById(R.id.historyStatusChips);
        participant = findViewById(R.id.historyParticipantSpinner);
        sortToggle = findViewById(R.id.historySortToggle);
        resultsHeader = findViewById(R.id.historyResultsHeader);
        resultsNote = findViewById(R.id.historyResultsNote);
        message = findViewById(R.id.historyMessage);
        loading = findViewById(R.id.historyLoading);
        results = findViewById(R.id.historyResults);
        summaryMonthLabel = findViewById(R.id.summaryMonthLabel);
        summaryEmpty = findViewById(R.id.summaryEmpty);
        summaryNote = findViewById(R.id.summaryNote);
        summaryContent = findViewById(R.id.summaryContent);
        summaryRides = findViewById(R.id.summaryRides);

        setParticipantLabels();
        originUsing.setOnClickListener(v -> choosePlace(true));
        destinationUsing.setOnClickListener(v -> choosePlace(false));
        findViewById(R.id.historyApplyButton).setOnClickListener(v -> render());
        findViewById(R.id.historyClearButton).setOnClickListener(v -> clearFilters());
        findViewById(R.id.historyRefreshButton).setOnClickListener(v -> load());
        radius.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { render(); return true; }
            return false;
        });
        // Choice filters apply immediately; typed ones apply on Search.
        statusChips.setOnCheckedStateChangeListener((group, checked) -> render());
        sortToggle.addOnButtonCheckedListener((group, id, isChecked) -> { if (isChecked) render(); });
        participant.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { render(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        findViewById(R.id.summaryPrevMonth).setOnClickListener(v -> shiftMonth(-1));
        findViewById(R.id.summaryNextMonth).setOnClickListener(v -> shiftMonth(1));
        summaryMonthLabel.setOnClickListener(v -> pickMonth());
        setStatLabel(R.id.statCompleted, "Completed");
        setStatLabel(R.id.statOrganized, "Organized");
        setStatLabel(R.id.statJoined, "Joined");
        setStatLabel(R.id.statTotal, "Your total assigned cost");
        summaryMonth.set(Calendar.DAY_OF_MONTH, 1);
        updateMonthLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        loading.setVisibility(View.VISIBLE);
        message.setVisibility(View.GONE);
        results.removeAllViews();
        service.load(new HistoryService.Callback() {
            public void onSuccess(String userId, List<Ride> loaded, Map<String, Payment> shares) {
                if (isFinishing() || isDestroyed()) return;
                loading.setVisibility(View.GONE);
                uid = userId; rides = loaded; payments = shares;
                updateParticipants();
                render();
                renderSummary();
            }

            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                loading.setVisibility(View.GONE);
                uid = null; rides.clear(); payments.clear();
                showMessage(error);
                resultsNote.setText("");
            }
        });
    }

    // ---------- Filters ----------

    private void clearFilters() {
        origin.setText(""); destination.setText(""); radius.setText("");
        origin.setError(null); destination.setError(null);
        originCenter = null; destinationCenter = null;
        originCenterFor = null; destinationCenterFor = null;
        statusChips.check(R.id.historyStatusAll);
        sortToggle.check(R.id.historySortNewest);
        participant.setSelection(0);
        render();
    }

    private void updateParticipants() {
        int position = participant.getSelectedItemPosition();
        String selected = position > 0 && position < participantIds.size() ? participantIds.get(position) : "";
        Set<String> others = new TreeSet<>();
        for (Ride ride : rides) others.addAll(ride.getAllParticipantIds());
        others.remove(uid);
        participantIds.clear();
        participantIds.add("");
        participantIds.addAll(others);
        setParticipantLabels();
        participant.setSelection(Math.max(0, participantIds.indexOf(selected)), false);
        for (String id : others) {
            if (participantNames.containsKey(id)) continue;
            userDataAccess.getById(id, new UserDataAccess.SingleCallback() {
                public void onSuccess(User user) {
                    if (user == null || isFinishing() || isDestroyed()) return;
                    participantNames.put(id, user.getDisplayName());
                    int current = participant.getSelectedItemPosition();
                    setParticipantLabels();
                    participant.setSelection(Math.max(0, current), false);
                }

                public void onError(Exception exception) {}
            });
        }
    }

    private void setParticipantLabels() {
        List<String> labels = new ArrayList<>();
        labels.add("Anyone");
        for (int i = 1; i < participantIds.size(); i++) labels.add(nameOf(participantIds.get(i)));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        participant.setAdapter(adapter);
    }

    private String nameOf(String id) {
        String name = participantNames.get(id);
        return name != null ? name : "Another student";
    }

    private String selectedStatus() {
        int id = statusChips.getCheckedChipId();
        if (id == R.id.historyStatusCompleted) return Ride.STATUS_COMPLETED;
        if (id == R.id.historyStatusCancelled) return Ride.STATUS_CANCELLED;
        return "";
    }

    /**
     * No radius: match by the typed words. Radius: look up each typed place and match rides
     * within the radius of it instead (an empty box doesn't limit that end).
     */
    private void render() {
        if (uid == null) return;
        origin.setError(null); destination.setError(null);
        String radiusText = text(radius);
        String originText = text(origin);
        String destinationText = text(destination);
        if (radiusText.isEmpty()) {
            originUsing.setVisibility(View.GONE);
            destinationUsing.setVisibility(View.GONE);
            renderWith(null, null, null);
            return;
        }
        double miles;
        try {
            miles = Double.parseDouble(radiusText);
        } catch (NumberFormatException e) {
            miles = -1;
        }
        if (!(miles > 0) || Double.isInfinite(miles)) {
            showResultsError("Enter a radius greater than 0 miles.");
            return;
        }
        if (originText.isEmpty() && destinationText.isEmpty()) {
            showResultsError("Enter an origin or destination to search by distance.");
            return;
        }
        if (!originText.isEmpty() && (originCenter == null || !originText.equalsIgnoreCase(originCenterFor))) {
            resolve(true, originText);
            return;
        }
        if (!destinationText.isEmpty()
                && (destinationCenter == null || !destinationText.equalsIgnoreCase(destinationCenterFor))) {
            resolve(false, destinationText);
            return;
        }
        PlaceInfo o = originText.isEmpty() ? null : originCenter;
        PlaceInfo d = destinationText.isEmpty() ? null : destinationCenter;
        showUsing(originUsing, o);
        showUsing(destinationUsing, d);
        renderWith(o, d, miles);
    }

    private void resolve(boolean isOrigin, String query) {
        loading.setVisibility(View.VISIBLE);
        PlaceResolver.resolve(this, query, new PlaceResolver.Callback() {
            public void onResolved(PlaceInfo place) {
                if (isFinishing() || isDestroyed()) return;
                loading.setVisibility(View.GONE);
                if (isOrigin) { originCenter = place; originCenterFor = query; }
                else { destinationCenter = place; destinationCenterFor = query; }
                render();
            }

            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                loading.setVisibility(View.GONE);
                (isOrigin ? origin : destination).setError(error);
                showResultsError(error);
            }
        });
    }

    private void showUsing(TextView label, PlaceInfo place) {
        if (place == null) { label.setVisibility(View.GONE); return; }
        label.setText("📍 Using " + PlaceResolver.label(place) + "  (tap to change)");
        label.setVisibility(View.VISIBLE);
    }

    /** Lets the user pick a different place when the looked-up one isn't what they meant. */
    private void choosePlace(boolean isOrigin) {
        String typed = text(isOrigin ? origin : destination);
        PlacesLocationPicker.show(this, typed, selected -> {
            if (isOrigin) { originCenter = selected; originCenterFor = typed; }
            else { destinationCenter = selected; destinationCenterFor = typed; }
            render();
        });
    }

    private void renderWith(PlaceInfo originPoint, PlaceInfo destinationPoint, Double miles) {
        HistoryFilter f = new HistoryFilter();
        f.origin = text(origin);
        f.destination = text(destination);
        f.originCenter = originPoint;
        f.destinationCenter = destinationPoint;
        f.radiusMiles = miles;
        f.status = selectedStatus();
        f.oldestFirst = sortToggle.getCheckedButtonId() == R.id.historySortOldest;
        int index = participant.getSelectedItemPosition();
        f.participantId = index > 0 && index < participantIds.size() ? participantIds.get(index) : "";

        List<Ride> all = HistoryService.filter(rides, uid, new HistoryFilter(), System.currentTimeMillis());
        List<Ride> displayed;
        try {
            displayed = HistoryService.filter(rides, uid, f, System.currentTimeMillis());
        } catch (IllegalArgumentException error) {
            showResultsError("Enter a radius greater than 0 miles.");
            return;
        }

        List<String> active = activeFilters(f);
        resultsHeader.setText(active.isEmpty()
                ? "Your rides (" + all.size() + ")"
                : "Showing " + displayed.size() + " of " + all.size() + " rides");
        resultsNote.setText(active.isEmpty()
                ? (f.oldestFirst ? "Oldest first" : "Newest first")
                : "Filtered by " + android.text.TextUtils.join(" · ", active)
                        + " · " + (f.oldestFirst ? "oldest first" : "newest first"));

        results.removeAllViews();
        if (displayed.isEmpty()) {
            showMessage(all.isEmpty()
                    ? "No completed or cancelled rides yet."
                    : "No rides match these filters. Tap Clear to see all your rides.");
            return;
        }
        message.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Ride ride : displayed) {
            View card = inflater.inflate(R.layout.item_history_ride, results, false);
            bindRide(card, ride);
            results.addView(card);
        }
    }

    private List<String> activeFilters(HistoryFilter f) {
        List<String> active = new ArrayList<>();
        boolean distance = f.radiusMiles != null;
        String miles = distance ? trimNumber(f.radiusMiles) + " mi of " : "";
        if (!f.origin.isEmpty()) active.add((distance ? "origin within " + miles : "origin \"") + f.origin + (distance ? "" : "\""));
        if (!f.destination.isEmpty()) active.add((distance ? "destination within " + miles : "destination \"") + f.destination + (distance ? "" : "\""));
        if (Ride.STATUS_COMPLETED.equals(f.status)) active.add("Completed");
        if (Ride.STATUS_CANCELLED.equals(f.status)) active.add("Cancelled");
        if (!f.participantId.isEmpty()) active.add("with " + nameOf(f.participantId));
        return active;
    }

    private void bindRide(View card, Ride ride) {
        ((TextView) card.findViewById(R.id.historyRideTitle)).setText(fallback(ride.title, "Ride"));
        TextView status = card.findViewById(R.id.historyRideStatus);
        boolean completed = Ride.STATUS_COMPLETED.equals(ride.status);
        status.setText(completed ? "Completed" : Ride.STATUS_CANCELLED.equals(ride.status) ? "Cancelled" : statusLabel(ride.status));
        tintBadge(status, completed ? com.google.android.material.R.attr.colorSecondaryContainer : com.google.android.material.R.attr.colorErrorContainer,
                completed ? com.google.android.material.R.attr.colorOnSecondaryContainer : com.google.android.material.R.attr.colorOnErrorContainer);

        ((TextView) card.findViewById(R.id.historyRideRoute)).setText(
                placeName(ride.origin) + "  →  " + placeName(ride.destination));
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(ride.departureTime.toDate());
        if (ride.completedAt != null) {
            when += " · marked completed " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(ride.completedAt.toDate());
        }
        ((TextView) card.findViewById(R.id.historyRideWhen)).setText(when);
        int count = ride.getAllParticipantIds().size();
        ((TextView) card.findViewById(R.id.historyRideMeta)).setText(
                count + (count == 1 ? " participant" : " participants")
                        + (uid.equals(ride.organizerId) ? " · You organized" : " · You joined"));

        View shareRow = card.findViewById(R.id.historyShareRow);
        if (completed) {
            shareRow.setVisibility(View.VISIBLE);
            Payment share = payments.get(ride.id);
            TextView amount = card.findViewById(R.id.historyShareAmount);
            TextView paid = card.findViewById(R.id.historySharePaid);
            Button payNow = card.findViewById(R.id.historyPayNow);
            if (share == null || !uid.equals(share.userId)) {
                amount.setText("Your share: not assigned yet");
                paid.setVisibility(View.GONE);
                payNow.setVisibility(View.GONE);
            } else {
                amount.setText("Your share " + Money.format(Money.toCents(share.amount)));
                paid.setVisibility(View.VISIBLE);
                paid.setText(share.paid ? "✓ Paid" : "Unpaid");
                tintBadge(paid, share.paid ? com.google.android.material.R.attr.colorPrimaryContainer : com.google.android.material.R.attr.colorErrorContainer,
                        share.paid ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnErrorContainer);
                payNow.setVisibility(share.paid ? View.GONE : View.VISIBLE);
                payNow.setOnClickListener(v -> confirmPay(ride, share, payNow));
            }
        } else {
            shareRow.setVisibility(View.GONE);
        }
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, RideDetailActivity.class);
            intent.putExtra(RideDetailActivity.EXTRA_RIDE_ID, ride.id);
            intent.putExtra(RideDetailActivity.EXTRA_CURRENT_USER_ID, uid);
            startActivity(intent);
        });
    }

    /** Marks the rider's own share paid after they've paid the organizer outside the app. */
    private void confirmPay(Ride ride, Payment share, Button payNow) {
        String amount = Money.format(Money.toCents(share.amount));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Mark " + amount + " as paid?")
                .setMessage("Do this after you've paid the organizer for \"" + fallback(ride.title, "this ride")
                        + "\" (cash, Venmo, etc.). You can't undo it, and the organizer will see it as Paid.")
                .setNegativeButton("Not yet", null)
                .setPositiveButton("I've paid", (dialog, which) -> {
                    payNow.setEnabled(false);
                    paymentService.markPaid(ride.id, uid, new PaymentService.UpdateCallback() {
                        public void onSuccess() {
                            if (isFinishing() || isDestroyed()) return;
                            Toast.makeText(HistoryActivity.this, "Marked as paid.", Toast.LENGTH_SHORT).show();
                            load();
                        }

                        public void onError(String error) {
                            if (isFinishing() || isDestroyed()) return;
                            payNow.setEnabled(true);
                            Toast.makeText(HistoryActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }

    private void showMessage(String text) {
        results.removeAllViews();
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    private void showResultsError(String text) {
        resultsHeader.setText("Your rides");
        resultsNote.setText("");
        showMessage(text);
    }

    // ---------- Monthly summary ----------

    private void shiftMonth(int delta) {
        summaryMonth.add(Calendar.MONTH, delta);
        updateMonthLabel();
        renderSummary();
    }

    private void pickMonth() {
        String[] months = new String[24];
        Calendar[] values = new Calendar[24];
        Calendar cursor = Calendar.getInstance();
        cursor.set(Calendar.DAY_OF_MONTH, 1);
        cursor.add(Calendar.MONTH, 1);
        SimpleDateFormat format = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        for (int i = 0; i < months.length; i++) {
            values[i] = (Calendar) cursor.clone();
            months[i] = format.format(cursor.getTime());
            cursor.add(Calendar.MONTH, -1);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose a month")
                .setItems(months, (dialog, which) -> {
                    summaryMonth.setTime(values[which].getTime());
                    updateMonthLabel();
                    renderSummary();
                })
                .show();
    }

    private void updateMonthLabel() {
        summaryMonthLabel.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(summaryMonth.getTime()));
    }

    private void renderSummary() {
        if (uid == null) return;
        int year = summaryMonth.get(Calendar.YEAR);
        int month = summaryMonth.get(Calendar.MONTH) + 1;
        MonthlySummary s = HistoryService.summarize(rides, payments, uid, year, month);
        if (!s.hasActivity()) {
            summaryEmpty.setText(NO_ACTIVITY_MESSAGE);
            summaryEmpty.setVisibility(View.VISIBLE);
            summaryContent.setVisibility(View.GONE);
            return;
        }
        summaryEmpty.setVisibility(View.GONE);
        summaryContent.setVisibility(View.VISIBLE);
        setStatValue(R.id.statCompleted, String.valueOf(s.completedRides));
        setStatValue(R.id.statOrganized, String.valueOf(s.ownedRides));
        setStatValue(R.id.statJoined, String.valueOf(s.joinedRides));
        setStatValue(R.id.statTotal, Money.format(s.totalCostCents));
        String note = "Paid " + Money.format(s.paidCostCents) + " · Unpaid " + Money.format(s.unpaidCostCents);
        if (s.unassignedShares > 0) {
            note += "\n" + s.unassignedShares + (s.unassignedShares == 1 ? " ride is" : " rides are")
                    + " still waiting for a cost split, so the total may grow.";
        }
        summaryNote.setText(note);

        summaryRides.removeAllViews();
        for (Ride ride : HistoryService.ridesInMonth(rides, uid, year, month)) {
            TextView row = new TextView(this);
            row.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            int pad = dp(6);
            row.setPadding(0, pad, 0, pad);
            Payment share = payments.get(ride.id);
            String shareText = share == null || !uid.equals(share.userId) ? "share pending"
                    : Money.format(Money.toCents(share.amount)) + (share.paid ? " paid" : " unpaid");
            row.setText("• " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(ride.departureTime.toDate())
                    + "  " + fallback(ride.title, "Ride") + "  ·  "
                    + (uid.equals(ride.organizerId) ? "organized" : "joined") + "  ·  " + shareText);
            summaryRides.addView(row);
        }
    }

    private void setStatLabel(int tileId, String label) {
        ((TextView) findViewById(tileId).findViewById(R.id.statLabel)).setText(label);
    }

    private void setStatValue(int tileId, String value) {
        ((TextView) findViewById(tileId).findViewById(R.id.statValue)).setText(value);
    }

    // ---------- Helpers ----------

    private void tintBadge(TextView badge, int backgroundAttr, int textAttr) {
        badge.setBackgroundTintList(ColorStateList.valueOf(themeColor(backgroundAttr)));
        badge.setTextColor(themeColor(textAttr));
    }

    private int themeColor(int attr) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static String text(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static String trimNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    /** Place name when we have one ("USC Village"), otherwise the address. */
    private static String placeName(PlaceInfo p) {
        if (p == null) return "Unknown location";
        if (p.name != null && !p.name.trim().isEmpty()) return p.name;
        return fallback(p.address, "Unknown location");
    }

    private static String statusLabel(String value) {
        if (Ride.STATUS_DELAYED.equals(value)) return "Delayed";
        if (Ride.STATUS_ON_TIME.equals(value)) return "On time";
        return fallback(value, "Unknown");
    }
}
