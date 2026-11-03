package com.trojanrideshare.app.util;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.trojanrideshare.app.MainActivity;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.ui.history.HistoryActivity;
import com.trojanrideshare.app.ui.payment.PaymentsActivity;
import com.trojanrideshare.app.ui.profile.ProfileActivity;
import com.trojanrideshare.app.ui.ride.CreateRideActivity;
import com.trojanrideshare.app.ui.search.OrganizerRequestsActivity;
import com.trojanrideshare.app.ui.search.SearchActivity;

public class BottomNavHelper {
    public static void setupBottomNav(Activity activity, int selectedItemId) {
        BottomNavigationView bottomNav = activity.findViewById(R.id.bottomNavigationView);
        if (bottomNav == null) return;

        bottomNav.setSelectedItemId(selectedItemId);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) return true;

            Intent intent = null;
            if (id == R.id.nav_home) {
                intent = new Intent(activity, MainActivity.class);
            } else if (id == R.id.nav_find) {
                intent = new Intent(activity, SearchActivity.class);
            } else if (id == R.id.nav_requests) {
                intent = new Intent(activity, OrganizerRequestsActivity.class);
            } else if (id == R.id.nav_history) {
                intent = new Intent(activity, HistoryActivity.class);
            } else if (id == R.id.nav_create_ride) {
                intent = new Intent(activity, CreateRideActivity.class);
            } else if (id == R.id.nav_payments) {
                intent = new Intent(activity, PaymentsActivity.class);
            } else if (id == R.id.nav_profile) {
                intent = new Intent(activity, ProfileActivity.class);
            }

            if (intent != null) {
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                activity.finish();
            }
            return true;
        });
    }
}
