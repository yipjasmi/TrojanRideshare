package com.trojanrideshare.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.User;
import com.trojanrideshare.app.ui.auth.LoginActivity;
import com.trojanrideshare.app.util.BottomNavHelper;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {
    private TextView nameTextView;
    private TextView emailTextView;
    private TextView uscIdTextView;
    private TextView gradYearTextView;
    private ListView upcomingRidesListView;
    private ListView completedRidesListView;
    private Button signOutButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        nameTextView = findViewById(R.id.profileNameTextView);
        emailTextView = findViewById(R.id.profileEmailTextView);
        uscIdTextView = findViewById(R.id.profileUscIdTextView);
        gradYearTextView = findViewById(R.id.profileGradYearTextView);
        upcomingRidesListView = findViewById(R.id.upcomingRidesListView);
        completedRidesListView = findViewById(R.id.completedRidesListView);
        signOutButton = findViewById(R.id.profileSignOutButton);

        signOutButton.setOnClickListener(v -> signOut());

        loadUserProfile();
        loadUserRides();

        BottomNavHelper.setupBottomNav(this, R.id.nav_profile);
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        nameTextView.setText("Name: " + (user.fullName != null ? user.fullName : "N/A"));
                        emailTextView.setText("Email: " + (user.email != null ? user.email : "N/A"));
                        uscIdTextView.setText("USC ID: " + (user.uscId != null ? user.uscId : "N/A"));
                        gradYearTextView.setText("Graduation Year: " + user.gradYear);
                    } else {
                        nameTextView.setText("Name: " + currentUser.getDisplayName());
                        emailTextView.setText("Email: " + currentUser.getEmail());
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUserRides() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();

        db.collection("rides")
                .whereArrayContains("participantIds", userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> upcomingTitles = new ArrayList<>();
                    List<String> completedTitles = new ArrayList<>();

                    snapshot.getDocuments().forEach(doc -> {
                        Ride ride = doc.toObject(Ride.class);
                        if (ride != null) {
                            String titleInfo = ride.title + " (" + ride.status + ")";
                            if (Ride.STATUS_COMPLETED.equals(ride.status)) {
                                completedTitles.add(titleInfo);
                            } else {
                                upcomingTitles.add(titleInfo);
                            }
                        }
                    });

                    upcomingRidesListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, upcomingTitles));
                    completedRidesListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, completedTitles));
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load rides", Toast.LENGTH_SHORT).show();
                });
    }

    private void signOut() {
        auth.signOut();
        CredentialManager.create(this).clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        goToLogin();
                    }

                    @Override
                    public void onError(ClearCredentialException e) {
                        goToLogin();
                    }
                });
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
