package com.trojanrideshare.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.MainActivity;
import com.trojanrideshare.app.R;

/** Email/password login. New users go to {@link SignUpActivity}. */
public class LoginActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        auth = FirebaseAuth.getInstance();

        emailEditText = findViewById(R.id.loginEmailEditText);
        passwordEditText = findViewById(R.id.loginPasswordEditText);
        loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.goToSignUpButton).setOnClickListener(
                v -> startActivity(new Intent(this, SignUpActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (auth.getCurrentUser() != null) {
            checkProfileAndNavigate();
        }
    }

    private void attemptLogin() {
        String email = emailEditText.getText().toString().trim().toLowerCase();
        String password = passwordEditText.getText().toString();

        if (!SignUpActivity.isUscEmail(email)) {
            emailEditText.setError("Enter your @usc.edu email");
            return;
        }
        if (password.isEmpty()) {
            passwordEditText.setError("Enter your password");
            return;
        }

        loginButton.setEnabled(false);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> checkProfileAndNavigate())
                .addOnFailureListener(e -> {
                    loginButton.setEnabled(true);
                    String message;
                    if (e instanceof FirebaseAuthInvalidUserException
                            || e instanceof FirebaseAuthInvalidCredentialsException) {
                        message = "Incorrect email or password";
                    } else {
                        message = "Login failed: " + e.getMessage();
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
    }

    private void checkProfileAndNavigate() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        // Auth account without a profile (e.g. an old Google sign-in account).
                        auth.signOut();
                        loginButton.setEnabled(true);
                        Toast.makeText(this,
                                "No TrojanRideshare profile for this account. Please sign up.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    loginButton.setEnabled(true);
                    Toast.makeText(this, "Could not load your profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
