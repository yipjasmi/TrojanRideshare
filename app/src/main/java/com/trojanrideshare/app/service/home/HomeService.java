package com.trojanrideshare.app.service.home;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.notification.NotificationDataAccess;
import com.trojanrideshare.app.data.ride.RideDataAccess;
import com.trojanrideshare.app.model.Notification;
import com.trojanrideshare.app.model.OrganizerRideSummary;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.payment.PaymentService;
import com.trojanrideshare.app.util.Money;

import java.util.List;

/** Home's dashboard: the next ride, what the user owes and is owed, and unread notifications. */
public class HomeService {
    public interface NextRideCallback {
        /** The ride is null when the user has nothing upcoming. */
        void onSuccess(Ride ride);
        void onError(String message);
    }

    public interface BalanceCallback {
        void onSuccess(Balance balance);
        void onError(String message);
    }

    public interface CountCallback {
        void onSuccess(int count);
        void onError(String message);
    }

    public static final class Balance {
        /** Unpaid shares on rides someone else organized. */
        public final long youOweCents;
        /** Other riders' unpaid shares on rides this user organized. */
        public final long owedToYouCents;

        Balance(long youOweCents, long owedToYouCents) {
            this.youOweCents = youOweCents;
            this.owedToYouCents = owedToYouCents;
        }

        public boolean isSettled() {
            return youOweCents == 0 && owedToYouCents == 0;
        }
    }

    private final RideDataAccess rideDataAccess;
    private final PaymentService paymentService;
    private final NotificationDataAccess notificationDataAccess;

    public HomeService() {
        rideDataAccess = new RideDataAccess();
        paymentService = new PaymentService();
        notificationDataAccess = new NotificationDataAccess();
    }

    public void loadNextRide(String userId, NextRideCallback callback) {
        if (isBlank(userId)) {
            callback.onError("You must be signed in to see your rides.");
            return;
        }
        rideDataAccess.getRidesForUser(userId, new RideDataAccess.Callback() {
            @Override
            public void onSuccess(List<Ride> rides) {
                callback.onSuccess(nextRide(rides, Timestamp.now()));
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load your rides. Please try again.");
            }
        });
    }

    public void loadBalance(String userId, BalanceCallback callback) {
        if (isBlank(userId)) {
            callback.onError("You must be signed in to see your payments.");
            return;
        }
        paymentService.loadMyShares(userId, new PaymentService.SharesCallback() {
            @Override
            public void onSuccess(List<Payment> shares) {
                loadOwedToYou(userId, unpaidCents(shares), callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void loadOwedToYou(String userId, long youOweCents, BalanceCallback callback) {
        paymentService.loadMyRides(userId, new PaymentService.RideSummariesCallback() {
            @Override
            public void onSuccess(List<OrganizerRideSummary> summaries) {
                callback.onSuccess(new Balance(youOweCents, outstandingCents(summaries)));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void loadUnreadNotificationCount(String userId, CountCallback callback) {
        if (isBlank(userId)) {
            callback.onError("You must be signed in to see your notifications.");
            return;
        }
        notificationDataAccess.getForUser(userId, new NotificationDataAccess.ListCallback() {
            @Override
            public void onSuccess(List<Notification> notifications) {
                callback.onSuccess(unreadCount(notifications));
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load your notifications.");
            }
        });
    }

    /**
     * The soonest active ride that hasn't left yet. A delay replaces departureTime, so a delayed
     * ride sorts by its new time.
     */
    static Ride nextRide(List<Ride> rides, Timestamp now) {
        Ride next = null;
        for (Ride ride : rides) {
            if (ride == null
                    || !ride.isActive()
                    || ride.departureTime == null
                    || ride.hasDeparted(now)) {
                continue;
            }
            if (next == null || ride.departureTime.compareTo(next.departureTime) < 0) {
                next = ride;
            }
        }
        return next;
    }

    static long unpaidCents(List<Payment> shares) {
        long cents = 0;
        for (Payment share : shares) {
            if (!share.paid) {
                cents += Money.toCents(share.amount);
            }
        }
        return cents;
    }

    static long outstandingCents(List<OrganizerRideSummary> summaries) {
        long cents = 0;
        for (OrganizerRideSummary summary : summaries) {
            cents += summary.outstandingCents;
        }
        return cents;
    }

    static int unreadCount(List<Notification> notifications) {
        int unread = 0;
        for (Notification notification : notifications) {
            if (!notification.read) {
                unread++;
            }
        }
        return unread;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
