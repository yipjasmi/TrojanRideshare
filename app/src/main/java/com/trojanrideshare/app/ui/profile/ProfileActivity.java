package com.trojanrideshare.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.User;
import com.trojanrideshare.app.service.ride.RideScheduleService;
import com.trojanrideshare.app.ui.auth.LoginActivity;
import com.trojanrideshare.app.ui.ride.RideDetailActivity;
import com.trojanrideshare.app.util.BottomNavHelper;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Own profile: full details, nickname editing, private upcoming/completed ride lists with a
 * per-ride "Public" toggle, and log out.
 *
 * Another user's profile (opened with {@link #EXTRA_USER_ID}): name, nickname and graduation
 * year only, plus just the rides that user shared publicly. Email and USC ID are not shown.
 */
public class ProfileActivity extends AppCompatActivity {
    public static final String EXTRA_USER_ID = "userId";
    private static final int MAX_NICKNAME_LENGTH = 30;

    private TextView titleTextView;
    private TextView nameTextView;
    private TextView nicknameTextView;
    private TextView emailTextView;
    private TextView uscIdTextView;
    private TextView gradYearTextView;
    private TextView sharingHintTextView;
    private TextView flagTextView;
    private TextView upcomingHeaderTextView;
    private TextView completedHeaderTextView;
    private TextView upcomingEmptyTextView;
    private TextView completedEmptyTextView;
    private View editNicknameButton;
    private LinearLayout upcomingRidesListView;
    private LinearLayout completedRidesListView;
    private Button signOutButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String currentUserId;
    private String targetUserId;
    private boolean isOwnProfile;
    private User profileUser;
    private final List<Ride> loadedRides = new ArrayList<>();
    private int selectedTab = 0; // 0 = created by this user, 1 = joined

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            goToLogin();
            return;
        }
        currentUserId = currentUser.getUid();
        String extraUserId = getIntent().getStringExtra(EXTRA_USER_ID);
        isOwnProfile = extraUserId == null || extraUserId.equals(currentUserId);
        targetUserId = isOwnProfile ? currentUserId : extraUserId;

        titleTextView = findViewById(R.id.profileTitleTextView);
        nameTextView = findViewById(R.id.profileNameTextView);
        nicknameTextView = findViewById(R.id.profileNicknameTextView);
        emailTextView = findViewById(R.id.profileEmailTextView);
        uscIdTextView = findViewById(R.id.profileUscIdTextView);
        gradYearTextView = findViewById(R.id.profileGradYearTextView);
        sharingHintTextView = findViewById(R.id.profileSharingHintTextView);
        flagTextView = findViewById(R.id.profileFlagTextView);
        upcomingHeaderTextView = findViewById(R.id.profileUpcomingHeader);
        completedHeaderTextView = findViewById(R.id.profileCompletedHeader);
        upcomingEmptyTextView = findViewById(R.id.upcomingRidesEmptyTextView);
        completedEmptyTextView = findViewById(R.id.completedRidesEmptyTextView);
        editNicknameButton = findViewById(R.id.profileEditNicknameButton);
        upcomingRidesListView = findViewById(R.id.upcomingRidesListView);
        completedRidesListView = findViewById(R.id.completedRidesListView);
        signOutButton = findViewById(R.id.profileSignOutButton);

        com.google.android.material.tabs.TabLayout tabs = findViewById(R.id.profileRideTabs);
        tabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                renderRides();
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });

        if (isOwnProfile) {
            editNicknameButton.setOnClickListener(v -> showEditNicknameDialog());
            signOutButton.setOnClickListener(v -> signOut());
            BottomNavHelper.setupBottomNav(this, R.id.nav_profile);
        } else {
            titleTextView.setText("User Profile");
            com.google.android.material.tabs.TabLayout otherTabs = findViewById(R.id.profileRideTabs);
            com.google.android.material.tabs.TabLayout.Tab organizedTab = otherTabs.getTabAt(0);
            if (organizedTab != null) organizedTab.setText("Organized");
            editNicknameButton.setVisibility(View.GONE);
            signOutButton.setVisibility(View.GONE);
            emailTextView.setVisibility(View.GONE);
            uscIdTextView.setVisibility(View.GONE);
            sharingHintTextView.setVisibility(View.GONE);
            upcomingHeaderTextView.setText("Shared Upcoming Rides");
            completedHeaderTextView.setText("Shared Completed & Cancelled Rides");
            upcomingEmptyTextView.setText("No publicly shared upcoming rides");
            completedEmptyTextView.setText("No publicly shared completed or cancelled rides");
            View bottomNav = findViewById(R.id.bottomNavigationView);
            if (bottomNav != null) bottomNav.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (targetUserId != null) {
            loadProfileThenRides();
        }
    }

    private void loadProfileThenRides() {
        db.collection("users").document(targetUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    profileUser = doc.toObject(User.class);
                    if (profileUser == null) {
                        nameTextView.setText("Profile not found");
                        nicknameTextView.setText("Nickname: N/A");
                        return;
                    }
                    if (profileUser.sharedRideIds == null) {
                        profileUser.sharedRideIds = new ArrayList<>();
                    }
                    nameTextView.setText(profileUser.fullName != null ? profileUser.fullName : "Trojan User");
                    nicknameTextView.setText("Nickname: " + profileUser.getDisplayName());
                    emailTextView.setText(profileUser.email != null ? profileUser.email : "");
                    uscIdTextView.setText("USC ID " + (profileUser.uscId != null ? profileUser.uscId : "N/A"));
                    gradYearTextView.setText("Class of " + profileUser.gradYear);
                    loadUserRides();
                    loadFlags();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show());
    }

    /** Shows a warning when an organizer has flagged one of this user's unpaid shares. */
    private void loadFlags() {
        db.collection("payments")
                .whereEqualTo("userId", targetUserId)
                .whereEqualTo("flagged", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> rides = new ArrayList<>();
                    snapshot.getDocuments().forEach(doc -> {
                        String title = doc.getString("rideTitle");
                        rides.add(title == null || title.trim().isEmpty() ? "a ride" : title.trim());
                    });
                    if (rides.isEmpty()) {
                        flagTextView.setVisibility(View.GONE);
                        return;
                    }
                    String who = isOwnProfile ? "You have " : "Has ";
                    flagTextView.setText("⚠ Flagged: " + who + rides.size()
                            + (rides.size() == 1 ? " unpaid share" : " unpaid shares")
                            + " (" + android.text.TextUtils.join(", ", rides) + ")");
                    flagTextView.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> flagTextView.setVisibility(View.GONE));
    }

    private void showEditNicknameDialog() {
        if (!isOwnProfile || profileUser == null) return;
        EditText input = new EditText(this);
        input.setHint("Leave blank to use your full name");
        input.setText(profileUser.getDisplayName());
        input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(MAX_NICKNAME_LENGTH)});
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Nickname")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String entered = input.getText().toString().trim();
                    // Blank resets to the full name, same default as signup.
                    String newNickname = entered.isEmpty() ? profileUser.fullName : entered;
                    db.collection("users").document(targetUserId)
                            .update("nickname", newNickname)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Nickname updated", Toast.LENGTH_SHORT).show();
                                profileUser.nickname = newNickname;
                                nicknameTextView.setText("Nickname: " + profileUser.getDisplayName());
                            })
                            .addOnFailureListener(e -> Toast.makeText(this,
                                    "Failed to update nickname: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                })
                .show();
    }

    private void loadUserRides() {
        db.collection("rides")
                .whereArrayContains("participantIds", targetUserId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Ride> rides = new ArrayList<>();
                    snapshot.getDocuments().forEach(doc -> {
                        Ride ride = doc.toObject(Ride.class);
                        if (ride == null) return;
                        // Other users only ever see rides this user shared.
                        if (!isOwnProfile && !profileUser.isRideShared(ride.id)) return;
                        rides.add(ride);
                    });

                    loadedRides.clear();
                    loadedRides.addAll(rides);
                    renderRides();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load rides", Toast.LENGTH_SHORT).show());
    }

    /** Shows the selected tab: rides this user created, or rides they joined. */
    private void renderRides() {
        List<Ride> tabRides = new ArrayList<>();
        for (Ride ride : loadedRides) {
            boolean created = targetUserId != null && targetUserId.equals(ride.organizerId);
            if ((selectedTab == 0) == created) {
                tabRides.add(ride);
            }
        }
        RideScheduleService.Schedule schedule =
                RideScheduleService.partition(tabRides, Timestamp.now());
        fillList(upcomingRidesListView, upcomingEmptyTextView, schedule.upcoming, false);
        fillList(completedRidesListView, completedEmptyTextView, schedule.completed, true);
    }

    /** Rows sit in the page itself (no inner scrolling box), separated by thin dividers. */
    private void fillList(LinearLayout container, TextView emptyView, List<Ride> rides,
                          boolean completedList) {
        container.removeAllViews();
        emptyView.setVisibility(rides.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < rides.size(); i++) {
            if (i > 0) {
                container.addView(new com.google.android.material.divider.MaterialDivider(this));
            }
            View row = inflater.inflate(R.layout.item_profile_ride, container, false);
            bindRideRow(row, rides.get(i), completedList);
            container.addView(row);
        }
    }

    private void setRideShared(String rideId, boolean shared, MaterialSwitch toggle) {
        db.collection("users").document(currentUserId)
                .update("sharedRideIds", shared
                        ? FieldValue.arrayUnion(rideId)
                        : FieldValue.arrayRemove(rideId))
                .addOnSuccessListener(unused -> {
                    if (shared) {
                        if (!profileUser.sharedRideIds.contains(rideId)) profileUser.sharedRideIds.add(rideId);
                    } else {
                        profileUser.sharedRideIds.remove(rideId);
                    }
                    Toast.makeText(this, shared ? "Ride shared on your public profile"
                            : "Ride is now private", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    toggle.setOnCheckedChangeListener(null);
                    toggle.setChecked(!shared);
                    Toast.makeText(this, "Could not update sharing: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    loadUserRides();
                });
    }

    private void openRide(Ride ride) {
        if (ride.id == null) return;
        Intent intent = new Intent(this, RideDetailActivity.class);
        intent.putExtra(RideDetailActivity.EXTRA_RIDE_ID, ride.id);
        intent.putExtra(RideDetailActivity.EXTRA_CURRENT_USER_ID, currentUserId);
        startActivity(intent);
    }

    private void bindRideRow(View row, Ride ride, boolean completedList) {
        ((TextView) row.findViewById(R.id.profileRideTitle)).setText(
                ride.title == null || ride.title.trim().isEmpty() ? "Untitled ride" : ride.title);
        ((TextView) row.findViewById(R.id.profileRideRoute)).setText(
                addressOf(ride.origin) + " → " + addressOf(ride.destination));
        ((TextView) row.findViewById(R.id.profileRideMeta)).setText(metaLine(ride, completedList));

        MaterialSwitch toggle = row.findViewById(R.id.profileRideShareSwitch);
        if (isOwnProfile) {
            toggle.setVisibility(View.VISIBLE);
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(profileUser != null && profileUser.isRideShared(ride.id));
            toggle.setOnCheckedChangeListener((button, checked) ->
                    setRideShared(ride.id, checked, toggle));
        } else {
            toggle.setVisibility(View.GONE);
        }
        row.setBackgroundResource(outValueSelectable());
        row.setOnClickListener(v -> openRide(ride));
    }

    private int outValueSelectable() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private String metaLine(Ride ride, boolean completedList) {
        String when = ride.departureTime == null ? "Time TBD"
                : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(ride.departureTime.toDate());
        String status;
        if (Ride.STATUS_CANCELLED.equals(ride.status)) {
            status = "Cancelled";
        } else if (completedList) {
            status = "Completed";
        } else if (RideScheduleService.awaitingCompletion(ride, Timestamp.now())) {
            status = "Departed, awaiting completion";
        } else if (Ride.STATUS_DELAYED.equals(ride.status)) {
            status = "Delayed";
        } else {
            status = "On Time";
        }
        int riders = ride.participantIds == null ? 0 : ride.participantIds.size();
        String role = targetUserId != null && targetUserId.equals(ride.organizerId)
                ? (isOwnProfile ? " · You organize" : " · Organizer") : "";
        return when + " · " + status + " · " + riders + "/" + ride.maxRiders + " riders" + role;
    }

    private static String addressOf(com.trojanrideshare.app.model.PlaceInfo place) {
        return place == null || place.address == null ? "?" : place.address;
    }

    private void signOut() {
        auth.signOut();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
