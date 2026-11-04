package com.trojanrideshare.app.util;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.model.Ride;

/**
 * Seat dots (●●●○ for 3 of 4 riders) and the per-person cost estimate. The organizer is in
 * participantIds, so they count as a rider here the same way they do in getAvailableSeats().
 */
public final class RideSeats {
    private static final char TAKEN = '●';
    private static final char OPEN = '○';

    private RideSeats() {}

    public static int total(Ride ride) {
        return Math.max(0, ride.maxRiders);
    }

    /** Capped at the ride's size, so it agrees with getAvailableSeats() on when a ride is full. */
    public static int taken(Ride ride) {
        return total(ride) - ride.getAvailableSeats();
    }

    public static String dots(int taken, int total) {
        StringBuilder dots = new StringBuilder();
        for (int index = 0; index < total; index++) {
            dots.append(index < taken ? TAKEN : OPEN);
        }
        return dots.toString();
    }

    /** The expected cost split across the riders on the ride now, to the nearest cent. */
    public static long costEachCents(Ride ride) {
        return splitCents(Money.toCents(ride.expectedCost), taken(ride));
    }

    /** The expected cost split as if one more rider joined; on a full ride it matches costEachCents. */
    public static long costEachIfJoinedCents(Ride ride) {
        int riders = Math.min(total(ride), taken(ride) + 1);
        return splitCents(Money.toCents(ride.expectedCost), riders);
    }

    /** Taken dots in the primary color, open ones in the outline color, spoken as "3 of 4 seats taken". */
    public static void bind(TextView view, Ride ride) {
        Context context = view.getContext();
        int taken = taken(ride);
        int total = total(ride);
        int availableSeats = ride.getAvailableSeats();
        String label = "";
        if (availableSeats == 0) {
            label = context.getString(R.string.seats_full);
        } else if (ride.isActive()) {
            label = context.getResources().getQuantityString(
                    R.plurals.search_available_seats, availableSeats, availableSeats);
        }

        SpannableStringBuilder text = new SpannableStringBuilder(dots(taken, total));
        // A zero-length exclusive span throws, so an empty run gets no span.
        if (taken > 0) {
            int takenColor = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary);
            text.setSpan(new ForegroundColorSpan(takenColor), 0, taken,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (total > taken) {
            int openColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOutline);
            text.setSpan(new ForegroundColorSpan(openColor), taken, total,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String description = context.getString(R.string.seats_taken_description, taken, total);
        if (!label.isEmpty()) {
            text.append("  ").append(label);
            description += ", " + label;
        }
        view.setText(text);
        view.setContentDescription(description);
    }

    private static long splitCents(long totalCents, int riders) {
        int people = Math.max(1, riders);
        // Round on the remainder; adding half a share first overflows on a huge cost.
        long share = totalCents / people;
        return totalCents % people * 2 >= people ? share + 1 : share;
    }
}
