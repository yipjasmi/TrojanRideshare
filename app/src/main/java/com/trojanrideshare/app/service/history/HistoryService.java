package com.trojanrideshare.app.service.history;

import com.trojanrideshare.app.data.history.HistoryDataAccess;
import com.trojanrideshare.app.model.*;
import com.trojanrideshare.app.util.Money;
import java.util.*;

public class HistoryService {
    public interface Callback {
        void onSuccess(String uid, List<Ride> rides, Map<String, Payment> payments);
        void onError(String message);
    }
    public void load(Callback callback) {
        new HistoryDataAccess().load(new HistoryDataAccess.Callback() {
            public void onSuccess(String uid, List<Ride> rides, Map<String, Payment> payments) {
                callback.onSuccess(uid, rides, payments);
            }
            public void onError(String message) { callback.onError(message); }
        });
    }

    public static List<Ride> filter(List<Ride> rides, String uid, HistoryFilter f, long now) {
        if (f.radiusMiles != null && (!Double.isFinite(f.radiusMiles) || f.radiusMiles <= 0
                || (f.originCenter == null && f.destinationCenter == null))) {
            throw new IllegalArgumentException("Select at least one location and enter a positive radius.");
        }
        List<Ride> result = new ArrayList<>();
        for (Ride r : rides) {
            if (uid == null || !r.getAllParticipantIds().contains(uid) || r.departureTime == null) continue;
            // History is Completed and Cancelled rides only. Upcoming rides, and departed rides
            // still waiting for the organizer to complete them, stay on the profile.
            if (!Ride.STATUS_COMPLETED.equals(r.status) && !Ride.STATUS_CANCELLED.equals(r.status)) continue;
            if (!matches(r.origin, f.origin, f.originCenter, f.radiusMiles)
                    || !matches(r.destination, f.destination, f.destinationCenter, f.radiusMiles)) continue;
            if (!f.status.isEmpty() && !f.status.equals(r.status)) continue;
            if (!f.participantId.isEmpty() && !r.getAllParticipantIds().contains(f.participantId)) continue;
            result.add(r);
        }
        Comparator<Ride> order = Comparator.comparingLong(r -> r.departureTime.toDate().getTime());
        if (!f.oldestFirst) order = order.reversed();
        result.sort(order.thenComparing(r -> r.id == null ? "" : r.id));
        return result;
    }

    private static boolean matches(PlaceInfo location, String text, PlaceInfo center, Double radius) {
        if (radius != null && center != null) return location != null && distanceMiles(location, center) <= radius;
        return com.trojanrideshare.app.util.KeywordMatcher.matches(location, text);
    }

    public static double distanceMiles(PlaceInfo a, PlaceInfo b) {
        double lat = Math.toRadians(b.lat - a.lat), lng = Math.toRadians(b.lng - a.lng);
        double h = Math.sin(lat / 2) * Math.sin(lat / 2)
            + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat))
            * Math.sin(lng / 2) * Math.sin(lng / 2);
        return 3958.7613 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, h))));
    }

    /** The completed rides counted in a month's summary, newest first (same rules as summarize). */
    public static List<Ride> ridesInMonth(List<Ride> rides, String uid, int year, int month) {
        List<Ride> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Ride r : rides) {
            if (uid == null || !r.getAllParticipantIds().contains(uid) || r.departureTime == null
                    || !Ride.STATUS_COMPLETED.equals(r.status)) continue;
            if (r.id != null && !seen.add(r.id)) continue;
            Calendar date = Calendar.getInstance(); date.setTime(r.departureTime.toDate());
            if (date.get(Calendar.YEAR) != year || date.get(Calendar.MONTH) + 1 != month) continue;
            result.add(r);
        }
        result.sort((a, b) -> b.departureTime.compareTo(a.departureTime));
        return result;
    }

    /**
     * Month is 1..12. The month comes from the ride's scheduled departure date (the updated time
     * for a delayed ride), not from when it was marked completed. Cancelled rides are excluded.
     * Dates use the device timezone, matching displayed history dates.
     */
    public static MonthlySummary summarize(List<Ride> rides, Map<String, Payment> payments,
                                           String uid, int year, int month) {
        if (month < 1 || month > 12) throw new IllegalArgumentException("Invalid month");
        MonthlySummary summary = new MonthlySummary();
        Set<String> seen = new HashSet<>();
        for (Ride r : rides) {
            if (uid == null || !r.getAllParticipantIds().contains(uid) || r.departureTime == null
                    || !Ride.STATUS_COMPLETED.equals(r.status)) continue;
            if (r.id != null && !seen.add(r.id)) continue;
            Calendar date = Calendar.getInstance(); date.setTime(r.departureTime.toDate());
            if (date.get(Calendar.YEAR) != year || date.get(Calendar.MONTH) + 1 != month) continue;
            summary.completedRides++;
            if (uid.equals(r.organizerId)) summary.ownedRides++; else summary.joinedRides++;
            Payment p = payments.get(r.id);
            if (p == null || !uid.equals(p.userId)) {
                summary.unassignedShares++;
            } else {
                long cents = Money.toCents(p.amount);
                summary.totalCostCents += cents;
                if (p.paid) summary.paidCostCents += cents; else summary.unpaidCostCents += cents;
            }
        }
        return summary;
    }
}
