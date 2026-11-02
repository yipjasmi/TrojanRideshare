package com.trojanrideshare.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.User;
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

        BottomNavHelper.setupBottomNav(this, R.id.nav_home);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        String name = (user != null && user.fullName != null) ? user.fullName : currentUser.getDisplayName();
                        if (name == null || name.isEmpty()) {
                            name = "Trojan";
                        }
                        android.widget.TextView helloText = findViewById(R.id.helloText);
                        if (helloText != null) {
                            helloText.setText("Hello, " + name + "!");
                        }
                    })
                    .addOnFailureListener(e -> {
                        android.widget.TextView helloText = findViewById(R.id.helloText);
                        if (helloText != null) {
                            String name = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Trojan";
                            helloText.setText("Hello, " + name + "!");
                        }
                    });
        }
    }
}
