package com.trojanrideshare.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.trojanrideshare.app.model.OrganizerJoinRequest;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.User;
import com.trojanrideshare.app.service.home.HomeService;
import com.trojanrideshare.app.service.joinrequest.JoinRequestService;
import com.trojanrideshare.app.ui.notification.NotificationsActivity;
import com.trojanrideshare.app.ui.payment.PaymentsActivity;
import com.trojanrideshare.app.ui.ride.CreateRideActivity;
import com.trojanrideshare.app.ui.ride.RideDetailActivity;
import com.trojanrideshare.app.ui.search.OrganizerRequestsActivity;
import com.trojanrideshare.app.ui.search.SearchActivity;
import com.trojanrideshare.app.util.BottomNavHelper;
import com.trojanrideshare.app.util.DepartureCountdown;
import com.trojanrideshare.app.util.Money;
import com.trojanrideshare.app.util.Motion;
import com.trojanrideshare.app.util.PullToRefresh;
import com.trojanrideshare.app.util.RideSeats;
import com.trojanrideshare.app.util.RideStatusChip;
import com.trojanrideshare.app.util.SystemBarsHelper;

import java.text.DateFormat;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final long COUNTDOWN_REFRESH_MILLIS = 30_000;
    private static final int MAX_BADGE_COUNT = 99;

    /** A Home card that loads on its own, so one failed query only blanks that card. */
    private final class Section {
        final View content;
        final View progress;
        final View error;
        boolean loaded;
        boolean loading;

        Section(int stateId, int contentId, Runnable load) {
            View state = findViewById(stateId);
            content = findViewById(contentId);
            progress = state.findViewById(R.id.sectionProgress);
            error = state.findViewById(R.id.sectionError);
            state.findViewById(R.id.sectionRetry).setOnClickListener(view -> load.run());
        }

        /** Only the first load shows the skeleton; a refresh keeps the last result up until it lands. */
        void showLoading() {
            loading = true;
            if (loaded) {
                return;
            }
            progress.setVisibility(View.VISIBLE);
            error.setVisibility(View.GONE);
            content.setVisibility(View.GONE);
        }

        void showContent() {
            loaded = true;
            loading = false;
            progress.setVisibility(View.GONE);
            error.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);
            stopRefreshingWhenSettled();
        }

        void showError() {
            loaded = false;
            loading = false;
            progress.setVisibility(View.GONE);
            error.setVisibility(View.VISIBLE);
            content.setVisibility(View.GONE);
            stopRefreshingWhenSettled();
        }
    }

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            bindCountdown();
            countdownHandler.postDelayed(this, COUNTDOWN_REFRESH_MILLIS);
        }
    };

    private HomeService homeService;
    private JoinRequestService joinRequestService;
    private Section nextRideSection;
    private Section moneySection;
    private Section requestsSection;
    private int nextRideGeneration;
    private int balanceGeneration;
    private int requestsGeneration;
    private int notificationsGeneration;
    private boolean notificationsLoading;
    private Ride nextRide;

    private View nextRideCard;
    private View nextRideDetails;
    private View nextRideEmpty;
    private TextView nextRideTitle;
    private TextView nextRideStatus;
    private TextView nextRideCountdown;
    private TextView nextRideRoute;
    private TextView nextRideDeparture;
    private TextView nextRideSeats;
    private TextView youOweText;
    private TextView owedToYouText;
    private View settledText;
    private TextView requestsWaitingText;
    private View notificationButton;
    private TextView notificationBadge;
    private SwipeRefreshLayout homeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        SystemBarsHelper.applyPadding(this);
        homeService = new HomeService();
        joinRequestService = new JoinRequestService();

        nextRideCard = findViewById(R.id.nextRideCard);
        nextRideDetails = findViewById(R.id.nextRideDetails);
        nextRideEmpty = findViewById(R.id.nextRideEmpty);
        nextRideTitle = findViewById(R.id.nextRideTitle);
        nextRideStatus = findViewById(R.id.nextRideStatus);
        nextRideCountdown = findViewById(R.id.nextRideCountdown);
        nextRideRoute = findViewById(R.id.nextRideRoute);
        nextRideDeparture = findViewById(R.id.nextRideDeparture);
        nextRideSeats = findViewById(R.id.nextRideSeats);
        youOweText = findViewById(R.id.moneyYouOwe);
        owedToYouText = findViewById(R.id.moneyOwedToYou);
        settledText = findViewById(R.id.moneySettled);
        requestsWaitingText = findViewById(R.id.requestsWaiting);
        notificationButton = findViewById(R.id.headerNotificationButton);
        notificationBadge = findViewById(R.id.notificationBadge);
        homeRefresh = findViewById(R.id.homeRefresh);
        PullToRefresh.setUp(homeRefresh, this::loadHome);

        nextRideSection = new Section(R.id.nextRideState, R.id.nextRideContent, this::loadNextRide);
        moneySection = new Section(R.id.moneyState, R.id.moneyContent, this::loadBalance);
        requestsSection = new Section(R.id.requestsState, R.id.requestsContent, this::loadRequests);

        findViewById(R.id.nextRideFindButton).setOnClickListener(v -> openSearch());
        findViewById(R.id.nextRideCreateButton).setOnClickListener(v -> openCreateRide());
        findViewById(R.id.requestsCard).setOnClickListener(v -> openRequests());
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

        ExtendedFloatingActionButton createFab = findViewById(R.id.createRideFab);
        if (createFab != null) {
            createFab.setOnClickListener(v -> openCreateRide());
            Motion.shrinkOnScroll(findViewById(R.id.homeScroll), createFab);
        }

        findViewById(R.id.moneyCard).setOnClickListener(
                v -> startActivity(new Intent(MainActivity.this, PaymentsActivity.class))
        );

        BottomNavHelper.setupBottomNav(this, R.id.nav_home);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHome();
        countdownHandler.postDelayed(countdownTick, COUNTDOWN_REFRESH_MILLIS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        countdownHandler.removeCallbacks(countdownTick);
    }

    private void loadHome() {
        loadGreeting();
        loadNextRide();
        loadBalance();
        loadRequests();
        loadUnreadNotifications();
    }

    /** The pull to refresh spinner waits for all three sections and the bell. */
    private void stopRefreshingWhenSettled() {
        if (!nextRideSection.loading && !moneySection.loading && !requestsSection.loading
                && !notificationsLoading) {
            homeRefresh.setRefreshing(false);
        }
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

    /**
     * Each section counts its loads, and a response only lands if no newer load of that section
     * started since (a resume or a retry) and the screen is still open. Otherwise a slow older
     * response could overwrite a newer one.
     */
    private boolean isStale(int generation, int currentGeneration) {
        return generation != currentGeneration || isFinishing() || isDestroyed();
    }

    private void loadNextRide() {
        int generation = ++nextRideGeneration;
        nextRideSection.showLoading();
        homeService.loadNextRide(currentUserId(), new HomeService.NextRideCallback() {
            @Override
            public void onSuccess(Ride ride) {
                if (isStale(generation, nextRideGeneration)) return;
                bindNextRide(ride);
                nextRideSection.showContent();
            }

            @Override
            public void onError(String message) {
                if (isStale(generation, nextRideGeneration)) return;
                bindNextRide(null);
                nextRideSection.showError();
            }
        });
    }

    private void bindNextRide(Ride ride) {
        nextRide = ride;
        nextRideDetails.setVisibility(ride == null ? View.GONE : View.VISIBLE);
        nextRideEmpty.setVisibility(ride == null ? View.VISIBLE : View.GONE);
        if (ride == null) {
            nextRideCard.setOnClickListener(null);
            nextRideCard.setClickable(false);
            return;
        }

        nextRideCard.setOnClickListener(view -> openRide(ride));
        nextRideTitle.setText(emptyFallback(ride.title, getString(R.string.search_untitled_ride)));
        nextRideRoute.setText(getString(
                R.string.search_route_format,
                addressOf(ride.origin),
                addressOf(ride.destination)
        ));
        nextRideDeparture.setText(getString(
                R.string.search_departure_format,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(ride.departureTime.toDate())
        ));
        RideSeats.bind(nextRideSeats, ride);
        bindCountdown();
    }

    /** The chip moves with the clock too (On Time turns Departed), so each tick redraws it. */
    private void bindCountdown() {
        if (nextRide == null) {
            return;
        }
        Timestamp now = Timestamp.now();
        RideStatusChip.of(nextRide, now).applyTo(nextRideStatus);
        nextRideCountdown.setText(
                DepartureCountdown.until(nextRide.departureTime, now).text(getResources()));
    }

    private void loadBalance() {
        int generation = ++balanceGeneration;
        moneySection.showLoading();
        homeService.loadBalance(currentUserId(), new HomeService.BalanceCallback() {
            @Override
            public void onSuccess(HomeService.Balance balance) {
                if (isStale(generation, balanceGeneration)) return;
                youOweText.setText(Money.format(balance.youOweCents));
                owedToYouText.setText(Money.format(balance.owedToYouCents));
                settledText.setVisibility(balance.isSettled() ? View.VISIBLE : View.GONE);
                moneySection.showContent();
            }

            @Override
            public void onError(String message) {
                if (isStale(generation, balanceGeneration)) return;
                moneySection.showError();
            }
        });
    }

    private void loadRequests() {
        int generation = ++requestsGeneration;
        requestsSection.showLoading();
        joinRequestService.loadOrganizerRequests(
                currentUserId(),
                new JoinRequestService.OrganizerRequestsCallback() {
                    @Override
                    public void onSuccess(List<OrganizerJoinRequest> requests) {
                        if (isStale(generation, requestsGeneration)) return;
                        int waiting = requests.size();
                        requestsWaitingText.setText(waiting == 0
                                ? getString(R.string.main_no_requests_waiting)
                                : getResources().getQuantityString(
                                        R.plurals.main_requests_waiting, waiting, waiting));
                        BottomNavHelper.showRequestsBadge(MainActivity.this, waiting);
                        requestsSection.showContent();
                    }

                    // A failed count keeps the badge as it was rather than claiming zero.
                    @Override
                    public void onError(String message) {
                        if (isStale(generation, requestsGeneration)) return;
                        requestsSection.showError();
                    }
                }
        );
    }

    private void loadUnreadNotifications() {
        int generation = ++notificationsGeneration;
        notificationsLoading = true;
        homeService.loadUnreadNotificationCount(currentUserId(), new HomeService.CountCallback() {
            @Override
            public void onSuccess(int unread) {
                if (isStale(generation, notificationsGeneration)) return;
                bindNotificationBadge(unread);
                notificationsLoading = false;
                stopRefreshingWhenSettled();
            }

            @Override
            public void onError(String message) {
                if (isStale(generation, notificationsGeneration)) return;
                // Keep the badge as it was rather than claiming zero.
                notificationsLoading = false;
                stopRefreshingWhenSettled();
            }
        });
    }

    private void bindNotificationBadge(int unread) {
        if (unread == 0) {
            notificationBadge.setVisibility(View.GONE);
            notificationButton.setContentDescription(getString(R.string.notifications_title));
            return;
        }
        notificationBadge.setText(unread > MAX_BADGE_COUNT
                ? getString(R.string.main_badge_overflow, MAX_BADGE_COUNT)
                : String.valueOf(unread));
        notificationBadge.setVisibility(View.VISIBLE);
        notificationButton.setContentDescription(getResources().getQuantityString(
                R.plurals.main_notifications_unread, unread, unread));
    }

    private void openSearch() {
        Intent searchIntent = new Intent(MainActivity.this, SearchActivity.class);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            searchIntent.putExtra(
                    SearchActivity.EXTRA_CURRENT_USER_ID,
                    currentUser.getUid()
            );
        }
        startActivity(searchIntent);
    }

    private void openRequests() {
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
    }

    private void openCreateRide() {
        Intent createIntent = new Intent(MainActivity.this, CreateRideActivity.class);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            createIntent.putExtra(
                    CreateRideActivity.EXTRA_CURRENT_USER_ID,
                    currentUser.getUid()
            );
        }
        startActivity(createIntent);
    }

    private void openRide(Ride ride) {
        Intent rideIntent = new Intent(this, RideDetailActivity.class);
        rideIntent.putExtra(RideDetailActivity.EXTRA_RIDE_ID, ride.id);
        rideIntent.putExtra(RideDetailActivity.EXTRA_RIDE_TITLE, nextRideTitle.getText().toString());
        rideIntent.putExtra(RideDetailActivity.EXTRA_CURRENT_USER_ID, currentUserId());
        startActivity(rideIntent, Motion.sharedElementOptions(
                this, nextRideTitle, RideDetailActivity.titleTransitionName(ride.id)));
    }

    private String currentUserId() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        return currentUser == null ? null : currentUser.getUid();
    }

    private String addressOf(PlaceInfo place) {
        return place == null
                ? getString(R.string.search_unknown_location)
                : emptyFallback(place.address, getString(R.string.search_unknown_location));
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
