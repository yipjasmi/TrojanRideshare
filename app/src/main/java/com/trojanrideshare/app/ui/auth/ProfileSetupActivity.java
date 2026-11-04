package com.trojanrideshare.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.MainActivity;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.User;

import java.util.Calendar;

public class ProfileSetupActivity extends AppCompatActivity {
    private EditText nicknameEditText;
    private EditText uscIdEditText;
    private EditText gradYearEditText;
    private Button saveProfileButton;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        nicknameEditText = findViewById(R.id.nicknameEditText);
        uscIdEditText = findViewById(R.id.uscIdEditText);
        gradYearEditText = findViewById(R.id.gradYearEditText);
        saveProfileButton = findViewById(R.id.saveProfileButton);

        saveProfileButton.setOnClickListener(v -> validateAndSave());
    }

    private void validateAndSave() {
        String nickname = nicknameEditText.getText().toString().trim();
        String uscId = uscIdEditText.getText().toString().trim();
        String gradYearStr = gradYearEditText.getText().toString().trim();

        if (!uscId.matches("\\d{10}")) {
            uscIdEditText.setError("USC ID must be exactly 10 digits");
            return;
        }

        int gradYear;
        try {
            gradYear = Integer.parseInt(gradYearStr);
        } catch (NumberFormatException e) {
            gradYearEditText.setError("Enter a valid graduation year");
            return;
        }

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int maxYear = currentYear + 6;
        if (gradYear < currentYear || gradYear > maxYear) {
            gradYearEditText.setError("Graduation year must be between " + currentYear + " and " + maxYear);
            return;
        }

        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        saveProfileButton.setEnabled(false);

        // Check uniqueness of email and USC ID
        db.collection("users")
                .whereEqualTo("email", firebaseUser.getEmail())
                .get()
                .addOnSuccessListener(emailSnap -> {
                    boolean duplicateEmail = false;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : emailSnap.getDocuments()) {
                        if (!doc.getId().equals(firebaseUser.getUid())) {
                            duplicateEmail = true;
                            break;
                        }
                    }
                    if (duplicateEmail) {
                        saveProfileButton.setEnabled(true);
                        Toast.makeText(this, "This email address is already registered.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    db.collection("users")
                            .whereEqualTo("uscId", uscId)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                boolean duplicateUscId = false;
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                    if (!doc.getId().equals(firebaseUser.getUid())) {
                                        duplicateUscId = true;
                                        break;
                                    }
                                }
                                if (duplicateUscId) {
                                    saveProfileButton.setEnabled(true);
                                    uscIdEditText.setError("This USC ID is already registered");
                                    Toast.makeText(this, "USC ID already registered", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Save user
                                User user = new User();
                                user.id = firebaseUser.getUid();
                                user.fullName = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "Trojan User";
                                user.nickname = nickname.isEmpty() ? user.fullName : nickname;
                                user.email = firebaseUser.getEmail();
                                user.uscId = uscId;
                                user.gradYear = gradYear;
                                user.createdAt = Timestamp.now();

                                db.collection("users").document(firebaseUser.getUid())
                                        .set(user)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Profile saved successfully", Toast.LENGTH_SHORT).show();
                                            startActivity(new Intent(this, MainActivity.class));
                                            finish();
                                        })
                                        .addOnFailureListener(e -> {
                                            saveProfileButton.setEnabled(true);
                                            Toast.makeText(this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                saveProfileButton.setEnabled(true);
                                Toast.makeText(this, "Error checking USC ID: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    saveProfileButton.setEnabled(true);
                    Toast.makeText(this, "Error checking email: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
