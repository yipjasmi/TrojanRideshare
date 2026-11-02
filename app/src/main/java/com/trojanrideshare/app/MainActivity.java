package com.trojanrideshare.app;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.Intent;
import android.os.CancellationSignal;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.trojanrideshare.app.ui.auth.LoginActivity;
import com.trojanrideshare.app.ui.search.SearchActivity;
import com.trojanrideshare.app.ui.search.OrganizerRequestsActivity;
import com.trojanrideshare.app.util.BottomNavHelper;

public class MainActivity extends AppCompatActivity {

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        CredentialManager.create(this).clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    }

                    @Override
                    public void onError(ClearCredentialException e) {
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    }
                });
    }

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
        findViewById(R.id.signOutButton).setOnClickListener(v -> signOut());
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
    }
}
