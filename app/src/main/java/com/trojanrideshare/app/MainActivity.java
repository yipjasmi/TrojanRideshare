package com.trojanrideshare.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.User;
import com.trojanrideshare.app.ui.notification.NotificationsActivity;
import com.trojanrideshare.app.ui.payment.PaymentsActivity;
import com.trojanrideshare.app.ui.ride.CreateRideActivity;
import com.trojanrideshare.app.ui.search.OrganizerRequestsActivity;
import com.trojanrideshare.app.ui.search.SearchActivity;
import com.trojanrideshare.app.util.BottomNavHelper;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        findViewById(R.id.searchRidesButton).setOnClickListener(v -> {
            Intent searchIntent = new Intent(MainActivity.this, SearchActivity.class);
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                searchIntent.putExtra(
                        SearchActivity.EXTRA_CURRENT_USER_ID,
                        currentUser.getUid()
                );
            }
            startActivity(searchIntent);
        });
        findViewById(R.id.manageRequestsButton).setOnClickListener(v -> {
            Intent requestsIntent = new Intent(
                    MainActivity.this,
                    OrganizerRequestsActivity.class
            );
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                requestsIntent.putExtra(
                        OrganizerRequestsActivity.EXTRA_ORGANIZER_ID,
                        currentUser.getUid()
                );
            }
            startActivity(requestsIntent);
        });
        View headerNotifBtn = findViewById(R.id.headerNotificationButton);
        if (headerNotifBtn != null) {
            headerNotifBtn.setOnClickListener(v -> {
                Intent notificationsIntent = new Intent(MainActivity.this, NotificationsActivity.class);
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    notificationsIntent.putExtra(
                            NotificationsActivity.EXTRA_CURRENT_USER_ID,
                            currentUser.getUid()
                    );
                }
                startActivity(notificationsIntent);
            });
        }

        View createFab = findViewById(R.id.createRideFab);
        if (createFab != null) {
            createFab.setOnClickListener(v -> {
                Intent createIntent = new Intent(MainActivity.this, CreateRideActivity.class);
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    createIntent.putExtra(
                            CreateRideActivity.EXTRA_CURRENT_USER_ID,
                            currentUser.getUid()
                    );
                }
                startActivity(createIntent);
            });
        }

        findViewById(R.id.paymentsButton).setOnClickListener(
                v -> startActivity(new Intent(MainActivity.this, PaymentsActivity.class))
        );

        BottomNavHelper.setupBottomNav(this, R.id.nav_home);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGreeting();
    }

    /** Greets the user by their displayed nickname; refreshed on resume so edits show up. */
    private void loadGreeting() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        FirebaseFirestore.getInstance().collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    String name = user != null ? user.getDisplayName() : "Trojan";
                    android.widget.TextView helloText = findViewById(R.id.helloText);
                    if (helloText != null) {
                        helloText.setText("Hello, " + name + "!");
                    }
                })
                .addOnFailureListener(e -> {
                    android.widget.TextView helloText = findViewById(R.id.helloText);
                    if (helloText != null) {
                        helloText.setText("Hello, Trojan!");
                    }
                });
    }
}
