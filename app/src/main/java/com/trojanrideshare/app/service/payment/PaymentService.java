package com.trojanrideshare.app.service.payment;

import android.app.Activity;

import com.google.firebase.firestore.FirebaseFirestoreException;
import com.trojanrideshare.app.data.payment.PaymentDataAccess;
import com.trojanrideshare.app.data.payment.PaymentRepository;
import com.trojanrideshare.app.model.OrganizerRideSummary;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.PaymentHandles;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.model.RidePaymentDetails;
import com.trojanrideshare.app.service.validation.PaymentValidator;
import com.trojanrideshare.app.util.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PaymentService {
    public interface SharesCallback {
        void onSuccess(List<Payment> shares);
        void onError(String message);
    }

    public interface RideSummariesCallback {
        void onSuccess(List<OrganizerRideSummary> summaries);
        void onError(String message);
    }

    public interface RideDetailsCallback {
        void onSuccess(RidePaymentDetails details);
        void onError(String message);
    }

    public interface UpdateCallback {
        void onSuccess();
        void onError(String message);
    }

    private final PaymentRepository repository;

    public PaymentService() {
        this(new PaymentDataAccess());
    }

    PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public void loadMyShares(String userId, SharesCallback callback) {
        repository.loadSharesFor(userId, new PaymentRepository.SharesCallback() {
            @Override
            public void onSuccess(List<Payment> shares) {
                List<Payment> riderShares = new ArrayList<>();
                for (Payment share : shares) {
                    if (!userId.equals(share.organizerId)) {
                        riderShares.add(share);
                    }
                }
                riderShares.sort(Comparator.comparing(share -> share.paid));
                callback.onSuccess(riderShares);
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load your shares. " + errorDetail(exception));
            }
        });
    }

    public void loadMyRides(String userId, RideSummariesCallback callback) {
        repository.loadCompletedRides(userId, new PaymentRepository.RidesCallback() {
            @Override
            public void onSuccess(List<Ride> rides) {
                loadSharesForRides(rides, userId, callback);
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load your rides. " + errorDetail(exception));
            }
        });
    }

    private void loadSharesForRides(
            List<Ride> rides,
            String userId,
            RideSummariesCallback callback
    ) {
        repository.loadSharesOrganizedBy(userId, new PaymentRepository.SharesCallback() {
            @Override
            public void onSuccess(List<Payment> shares) {
                callback.onSuccess(summarize(rides, shares));
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load your rides. " + errorDetail(exception));
            }
        });
    }

    public void loadRideDetails(String rideId, String userId, RideDetailsCallback callback) {
        repository.loadRide(rideId, new PaymentRepository.RideCallback() {
            @Override
            public void onSuccess(Ride ride) {
                if (ride == null) {
                    callback.onError("This ride no longer exists.");
                    return;
                }
                loadNames(ride, userId, callback);
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Unable to load this ride. " + errorDetail(exception));
            }
        });
    }

    private void loadNames(Ride ride, String userId, RideDetailsCallback callback) {
        List<String> participants = ride.getAllParticipantIds();
        repository.loadNames(participants, new PaymentRepository.NamesCallback() {
            @Override
            public void onSuccess(
                    Map<String, String> names,
                    Map<String, PaymentHandles> handles
            ) {
                if (userId.equals(ride.organizerId)) {
                    loadRideShares(ride, participants, names, handles, callback);
                } else {
                    callback.onSuccess(new RidePaymentDetails(
                            ride,
                            participants,
                            names,
                            handles,
                            Collections.emptyList()
                    ));
                }
            }
        });
    }

    private void loadRideShares(
            Ride ride,
            List<String> participants,
            Map<String, String> names,
            Map<String, PaymentHandles> handles,
            RideDetailsCallback callback
    ) {
        repository.loadRideShares(
                ride.id,
                ride.organizerId,
                new PaymentRepository.SharesCallback() {
                    @Override
                    public void onSuccess(List<Payment> shares) {
                        callback.onSuccess(new RidePaymentDetails(
                                ride,
                                participants,
                                names,
                                handles,
                                shares
                        ));
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError("Unable to load this ride. " + errorDetail(exception));
                    }
                }
        );
    }

    public void watchShares(Activity owner, Ride ride, String userId, SharesCallback callback) {
        PaymentRepository.SharesCallback repositoryCallback =
                new PaymentRepository.SharesCallback() {
                    @Override
                    public void onSuccess(List<Payment> shares) {
                        callback.onSuccess(shares);
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError(
                                "Unable to load payment status. " + errorDetail(exception)
                        );
                    }
                };
        if (userId.equals(ride.organizerId)) {
            repository.watchRideShares(owner, ride.id, userId, repositoryCallback);
        } else {
            repository.watchShare(owner, ride.id, userId, repositoryCallback);
        }
    }

    public void saveSplit(
            String rideId,
            String organizerId,
            List<String> participants,
            long[] shares,
            long finalCents,
            UpdateCallback callback
    ) {
        repository.saveSplit(
                rideId,
                organizerId,
                participants,
                shares,
                finalCents,
                updateCallback("Unable to save this split. ", callback)
        );
    }

    public void markPaid(String rideId, String userId, UpdateCallback callback) {
        repository.markPaid(
                rideId,
                userId,
                updateCallback("Unable to mark this share paid. ", callback)
        );
    }

    public void flag(String rideId, String riderId, UpdateCallback callback) {
        repository.flag(
                rideId,
                riderId,
                updateCallback("Unable to flag this rider. ", callback)
        );
    }

    public void saveHandles(
            String userId,
            String venmoInput,
            String cashtagInput,
            UpdateCallback callback
    ) {
        PaymentValidator.HandleResult venmo = PaymentValidator.venmoUsername(venmoInput);
        PaymentValidator.HandleResult cashtag = PaymentValidator.cashtag(cashtagInput);
        if (!venmo.isValid() || !cashtag.isValid()) {
            callback.onError(venmo.isValid() ? cashtag.error : venmo.error);
            return;
        }
        repository.saveHandles(
                userId,
                venmo.handle,
                cashtag.handle,
                updateCallback("Unable to save your payment apps. ", callback)
        );
    }

    public static String splitBlockedReason(Ride ride, String userId, List<Payment> existing) {
        PaymentRepository.Failure failure = PaymentDataAccess.splitFailure(ride, userId, existing);
        return failure == null ? null : failureMessage(failure);
    }

    static List<OrganizerRideSummary> summarize(List<Ride> rides, List<Payment> shares) {
        Map<String, List<Payment>> sharesByRide = new HashMap<>();
        for (Payment share : shares) {
            sharesByRide.computeIfAbsent(share.rideId, rideId -> new ArrayList<>()).add(share);
        }

        List<OrganizerRideSummary> summaries = new ArrayList<>();
        for (Ride ride : rides) {
            int riders = 0;
            int ridersPaid = 0;
            long outstandingCents = 0;
            for (Payment share : sharesByRide.getOrDefault(ride.id, Collections.emptyList())) {
                if (Objects.equals(share.userId, ride.organizerId)) {
                    continue;
                }
                riders++;
                if (share.paid) {
                    ridersPaid++;
                } else {
                    outstandingCents += Money.toCents(share.amount);
                }
            }
            summaries.add(new OrganizerRideSummary(ride, riders, ridersPaid, outstandingCents));
        }
        summaries.sort(Comparator
                .comparing((OrganizerRideSummary summary) -> !summary.needsFinalCost)
                .thenComparing(summary -> summary.outstandingCents == 0));
        return summaries;
    }

    private static PaymentRepository.UpdateCallback updateCallback(
            String errorPrefix,
            UpdateCallback callback
    ) {
        return new PaymentRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onFailure(PaymentRepository.Failure failure) {
                callback.onError(failureMessage(failure));
            }

            @Override
            public void onError(Exception exception) {
                callback.onError(errorPrefix + errorDetail(exception));
            }
        };
    }

    private static String failureMessage(PaymentRepository.Failure failure) {
        if (failure == PaymentRepository.Failure.RIDE_NOT_FOUND) {
            return "This ride no longer exists.";
        }
        if (failure == PaymentRepository.Failure.NOT_AUTHORIZED) {
            return "Only the ride organizer can record the final cost.";
        }
        if (failure == PaymentRepository.Failure.RIDE_NOT_COMPLETED) {
            return "Mark the ride completed before recording the final cost.";
        }
        if (failure == PaymentRepository.Failure.SPLIT_LOCKED) {
            return "A rider already marked their share paid, so this split is locked.";
        }
        if (failure == PaymentRepository.Failure.RIDERS_CHANGED) {
            return "The riders on this ride changed. Reopen it and try again.";
        }
        if (failure == PaymentRepository.Failure.SHARE_NOT_FOUND) {
            return "This share no longer exists.";
        }
        if (failure == PaymentRepository.Failure.ALREADY_PAID) {
            return "This rider has already marked their share paid.";
        }
        return "You can flag a rider once " + Payment.APPROVAL_PERIOD_DAYS + " days have passed.";
    }

    private static String errorDetail(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException.Code code =
                        ((FirebaseFirestoreException) current).getCode();
                if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    return "You are offline. Connect and try again.";
                }
                if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    return "You do not have access to this. "
                            + "Sign in with your USC account and try again.";
                }
            }
            current = current.getCause();
        }
        return "Please try again.";
    }
}
