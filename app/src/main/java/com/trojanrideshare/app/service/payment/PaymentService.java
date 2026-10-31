package com.trojanrideshare.app.service.payment;

import android.app.Activity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.trojanrideshare.app.data.payment.PaymentRepository;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.util.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class PaymentService {
    // 2.1 says "approval period" without a number. firestore.rules has the same 7 days.
    public static final int APPROVAL_PERIOD_DAYS = 7;
    private static final long APPROVAL_PERIOD_MILLIS = TimeUnit.DAYS.toMillis(APPROVAL_PERIOD_DAYS);

    public static final class RideDetails {
        public Ride ride;
        public List<String> participants;
        public Map<String, String> names;
        public List<Payment> shares;
    }

    public static final class RideCollection {
        public Ride ride;
        public boolean needsFinalCost;
        public int riders;
        public int ridersPaid;
        public long outstandingCents;
    }

    private final PaymentRepository repository = new PaymentRepository();

    // Handles participantIds with or without the organizer in it. Organizer always first.
    public static List<String> participantsOf(Ride ride) {
        Set<String> participants = new LinkedHashSet<>();
        participants.add(ride.organizerId);
        if (ride.participantIds != null) {
            participants.addAll(ride.participantIds);
        }
        return new ArrayList<>(participants);
    }

    public static boolean hasRiderMarkedPaid(List<Payment> shares) {
        for (Payment share : shares) {
            if (share.paidAt != null) {
                return true;
            }
        }
        return false;
    }

    public static String splitBlockedReason(Ride ride, String userId, List<Payment> existing) {
        if (ride == null) {
            return "This ride no longer exists.";
        }
        if (!userId.equals(ride.organizerId)) {
            return "Only the ride organizer can record the final cost.";
        }
        if (!Ride.STATUS_COMPLETED.equals(ride.status)) {
            return "Mark the ride completed before recording the final cost.";
        }
        if (hasRiderMarkedPaid(existing)) {
            return "A rider already marked their share paid, so this split is locked.";
        }
        return null;
    }

    public static boolean isOverdue(boolean paid, long assignedAtMillis, long nowMillis) {
        return !paid && nowMillis - assignedAtMillis >= APPROVAL_PERIOD_MILLIS;
    }

    public static boolean isOverdue(Payment share, long nowMillis) {
        return share.assignedAt != null
                && isOverdue(share.paid, share.assignedAt.toDate().getTime(), nowMillis);
    }

    public static List<RideCollection> summarize(List<Ride> rides, List<Payment> shares) {
        Map<String, List<Payment>> sharesByRide = new HashMap<>();
        for (Payment share : shares) {
            sharesByRide.computeIfAbsent(share.rideId, id -> new ArrayList<>()).add(share);
        }

        List<RideCollection> collections = new ArrayList<>();
        for (Ride ride : rides) {
            RideCollection collection = new RideCollection();
            collection.ride = ride;
            collection.needsFinalCost = ride.finalCost == null;
            for (Payment share : sharesByRide.getOrDefault(ride.id, new ArrayList<>())) {
                if (share.userId.equals(ride.organizerId)) {
                    continue;
                }
                collection.riders++;
                if (share.paid) {
                    collection.ridersPaid++;
                } else {
                    collection.outstandingCents += Money.toCents(share.amount);
                }
            }
            collections.add(collection);
        }
        collections.sort(Comparator
                .comparing((RideCollection c) -> !c.needsFinalCost)
                .thenComparing(c -> c.outstandingCents == 0));
        return collections;
    }

    public Task<List<Payment>> loadMyShares(String userId) {
        return repository.getSharesFor(userId).onSuccessTask(shares -> {
            List<Payment> riderShares = new ArrayList<>();
            for (Payment share : shares) {
                if (!userId.equals(share.organizerId)) {
                    riderShares.add(share);
                }
            }
            riderShares.sort(Comparator.comparing(share -> share.paid));
            return Tasks.forResult(riderShares);
        });
    }

    public Task<List<RideCollection>> loadMyRides(String userId) {
        Task<List<Ride>> rides = repository.getCompletedRidesOrganizedBy(userId);
        Task<List<Payment>> shares = repository.getSharesOrganizedBy(userId);
        return Tasks.whenAll(rides, shares).onSuccessTask(done ->
                Tasks.forResult(summarize(rides.getResult(), shares.getResult())));
    }

    public Task<RideDetails> loadRideDetails(String rideId, String userId) {
        return repository.getRide(rideId).onSuccessTask(ride -> {
            if (ride == null) {
                return Tasks.forException(new PaymentException("This ride no longer exists."));
            }
            List<String> participants = participantsOf(ride);
            Task<Map<String, String>> names = repository.getNames(participants);
            Task<List<Payment>> shares = userId.equals(ride.organizerId)
                    ? repository.getRideShares(rideId, userId)
                    : Tasks.forResult(new ArrayList<>());
            return Tasks.whenAll(names, shares).onSuccessTask(done -> {
                RideDetails details = new RideDetails();
                details.ride = ride;
                details.participants = participants;
                details.names = names.getResult();
                details.shares = shares.getResult();
                return Tasks.forResult(details);
            });
        });
    }

    public void watchShares(Activity owner, Ride ride, String userId,
                            PaymentRepository.SharesListener listener) {
        if (userId.equals(ride.organizerId)) {
            repository.listenToRideShares(owner, ride.id, userId, listener);
        } else {
            repository.listenToShare(owner, ride.id, userId, listener);
        }
    }

    public Task<Void> saveSplit(String rideId, String userId, List<String> participants,
                                long[] shares, long finalCents) {
        return repository.saveSplit(rideId, participants, shares, finalCents, (ride, existing) -> {
            String blocked = splitBlockedReason(ride, userId, existing);
            if (blocked != null) {
                throw new PaymentException(blocked);
            }
            if (!participantsOf(ride).equals(participants)) {
                throw new PaymentException("The riders on this ride changed. Reopen it and try again.");
            }
        });
    }

    public Task<Void> markPaid(String rideId, String userId) {
        return repository.markPaid(PaymentRepository.paymentId(rideId, userId), share -> {
            if (share == null) {
                throw new PaymentException("This share no longer exists.");
            }
            return !share.paid;
        });
    }

    public Task<Void> flag(String rideId, String riderId) {
        long now = System.currentTimeMillis();
        return repository.flag(PaymentRepository.paymentId(rideId, riderId), share -> {
            if (share == null) {
                throw new PaymentException("This share no longer exists.");
            }
            if (share.paid) {
                throw new PaymentException("This rider has already marked their share paid.");
            }
            if (!isOverdue(share, now)) {
                throw new PaymentException(
                        "You can flag a rider once " + APPROVAL_PERIOD_DAYS + " days have passed.");
            }
            return !share.flagged;
        });
    }
}
