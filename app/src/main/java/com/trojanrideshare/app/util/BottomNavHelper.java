package com.trojanrideshare.app.util;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.trojanrideshare.app.MainActivity;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.ui.profile.ProfileActivity;
import com.trojanrideshare.app.ui.search.OrganizerRequestsActivity;
import com.trojanrideshare.app.ui.search.SearchActivity;

public class BottomNavHelper {
    /** For screens reached from a tab (a ride, someone else's profile): no tab is highlighted. */
    public static final int NO_TAB = 0;

    public static void setupBottomNav(Activity activity, int selectedItemId) {
        BottomNavigationView bottomNav = activity.findViewById(R.id.bottomNavigationView);
        if (bottomNav == null) return;

        if (selectedItemId == NO_TAB) {
            Menu menu = bottomNav.getMenu();
            menu.setGroupCheckable(0, true, false);
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setChecked(false);
            }
            menu.setGroupCheckable(0, true, true);
        } else {
            bottomNav.setSelectedItemId(selectedItemId);
        }
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
                intent = new Intent(activity, com.trojanrideshare.app.ui.history.HistoryActivity.class);
            } else if (id == R.id.nav_profile) {
                intent = new Intent(activity, ProfileActivity.class);
            }

            if (intent != null) {
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                // A tab always starts fresh: closing every screen under this one means Back from a
                // tab never walks through an old copy of Home or a ride opened three tabs ago.
                activity.finishAffinity();
            }
            return true;
        });
    }
}
