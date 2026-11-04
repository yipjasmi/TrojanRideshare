package com.trojanrideshare.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.MainActivity;
import com.trojanrideshare.app.model.User;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.util.SystemBarsHelper;

import java.util.Calendar;

/**
 * Account creation: full name, USC email, USC ID, password, graduation year and an optional
 * nickname (defaults to the full name).
 *
 * Duplicate checks: Firebase Auth rejects an existing email (FirebaseAuthUserCollisionException).
 * The USC ID check needs a signed-in user to query Firestore, so it runs right after the auth
 * account is created; if the ID is taken, the new auth account is deleted again.
 */
public class SignUpActivity extends AppCompatActivity {
    public static final int MIN_PASSWORD_LENGTH = 6;

    private EditText fullNameEditText;
    private EditText nicknameEditText;
    private EditText emailEditText;
    private EditText uscIdEditText;
    private EditText passwordEditText;
    private EditText confirmPasswordEditText;
    private EditText gradYearEditText;
    private Button signUpButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public static boolean isUscEmail(String email) {
        return email != null && email.trim().toLowerCase().matches("^[^@\\s]+@usc\\.edu$");
    }

    public static boolean isValidUscId(String uscId) {
        return uscId != null && uscId.matches("\\d{10}");
    }

    /** Active-student check: current year through current year + 6, inclusive. */
    public static boolean isValidGradYear(int gradYear, int currentYear) {
        return gradYear >= currentYear && gradYear <= currentYear + 6;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        SystemBarsHelper.applyPadding(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        fullNameEditText = findViewById(R.id.signUpFullNameEditText);
        nicknameEditText = findViewById(R.id.signUpNicknameEditText);
        emailEditText = findViewById(R.id.signUpEmailEditText);
        uscIdEditText = findViewById(R.id.signUpUscIdEditText);
        passwordEditText = findViewById(R.id.signUpPasswordEditText);
        confirmPasswordEditText = findViewById(R.id.signUpConfirmPasswordEditText);
        gradYearEditText = findViewById(R.id.signUpGradYearEditText);
        signUpButton = findViewById(R.id.signUpButton);

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        gradYearEditText.setHint("Expected graduation year (" + currentYear + "–" + (currentYear + 6) + ")");

        signUpButton.setOnClickListener(v -> validateAndSignUp());
        findViewById(R.id.backToLoginButton).setOnClickListener(v -> finish());
    }

    private void validateAndSignUp() {
        String fullName = fullNameEditText.getText().toString().trim();
        String nickname = nicknameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim().toLowerCase();
        String uscId = uscIdEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String confirmPassword = confirmPasswordEditText.getText().toString();
        String gradYearStr = gradYearEditText.getText().toString().trim();

        if (fullName.isEmpty()) {
            fullNameEditText.setError("Full name is required");
            fullNameEditText.requestFocus();
            return;
        }
        if (!isUscEmail(email)) {
            emailEditText.setError("Must be a valid @usc.edu email");
            emailEditText.requestFocus();
            return;
        }
        if (!isValidUscId(uscId)) {
            uscIdEditText.setError("USC ID must be exactly 10 digits");
            uscIdEditText.requestFocus();
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordEditText.setError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            passwordEditText.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            confirmPasswordEditText.requestFocus();
            return;
        }

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int gradYear;
        try {
            gradYear = Integer.parseInt(gradYearStr);
        } catch (NumberFormatException e) {
            gradYearEditText.setError("Enter a valid graduation year");
            gradYearEditText.requestFocus();
            return;
        }
        if (!isValidGradYear(gradYear, currentYear)) {
            gradYearEditText.setError("Graduation year must be between "
                    + currentYear + " and " + (currentYear + 6));
            gradYearEditText.requestFocus();
            return;
        }

        signUpButton.setEnabled(false);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> checkUscIdThenSave(
                        result.getUser(), fullName, nickname, email, uscId, gradYear))
                .addOnFailureListener(e -> {
                    signUpButton.setEnabled(true);
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        emailEditText.setError("This USC email is already registered");
                        emailEditText.requestFocus();
                        Toast.makeText(this, "An account with this USC email already exists",
                                Toast.LENGTH_LONG).show();
                    } else if (e instanceof FirebaseAuthWeakPasswordException) {
                        passwordEditText.setError("Password is too weak");
                        passwordEditText.requestFocus();
                    } else {
                        Toast.makeText(this, "Sign-up failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkUscIdThenSave(FirebaseUser firebaseUser, String fullName, String nickname,
                                    String email, String uscId, int gradYear) {
        if (firebaseUser == null) {
            signUpButton.setEnabled(true);
            return;
        }
        db.collection("users")
                .whereEqualTo("uscId", uscId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (!doc.getId().equals(firebaseUser.getUid())) {
                            rollBack(firebaseUser, () -> {
                                uscIdEditText.setError("This USC ID is already registered");
                                uscIdEditText.requestFocus();
                                Toast.makeText(this, "An account with this USC ID already exists",
                                        Toast.LENGTH_LONG).show();
                            });
                            return;
                        }
                    }

                    User user = new User();
                    user.fullName = fullName;
                    user.nickname = nickname.isEmpty() ? fullName : nickname;
                    user.email = email;
                    user.uscId = uscId;
                    user.gradYear = gradYear;
                    user.createdAt = Timestamp.now();

                    db.collection("users").document(firebaseUser.getUid())
                            .set(user)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Account created", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(this, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> rollBack(firebaseUser, () ->
                                    Toast.makeText(this, "Failed to save profile: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show()));
                })
                .addOnFailureListener(e -> rollBack(firebaseUser, () ->
                        Toast.makeText(this, "Error checking USC ID: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()));
    }

    /** Removes the just-created auth account so the email can be used again, then reports. */
    private void rollBack(FirebaseUser firebaseUser, Runnable thenShowError) {
        firebaseUser.delete().addOnCompleteListener(task -> {
            auth.signOut();
            signUpButton.setEnabled(true);
            thenShowError.run();
        });
    }
}
