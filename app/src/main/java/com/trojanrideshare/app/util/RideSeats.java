package com.trojanrideshare.app.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
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

    /**
     * {@link #dots} drawn as a primary disc per taken seat and an outline-colored ring per open
     * one, both the same size. Phones take ● and ○ from different fonts, so the glyphs alone
     * showed ○ visibly smaller.
     */
    public static SpannableStringBuilder dotsText(Context context, int taken, int total) {
        int takenColor = MaterialColors.getColor(
                context, androidx.appcompat.R.attr.colorPrimary, RideSeats.class.getSimpleName());
        int openColor = MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorOutline, RideSeats.class.getSimpleName());
        SpannableStringBuilder text = new SpannableStringBuilder(dots(taken, total));
        for (int index = 0; index < total; index++) {
            boolean seatTaken = index < taken;
            text.setSpan(new SeatSpan(seatTaken, seatTaken ? takenColor : openColor),
                    index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
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

        SpannableStringBuilder text = dotsText(context, taken, total);
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

    /** One seat: a filled disc when taken, a ring when open, with the same outer size. */
    private static final class SeatSpan extends ReplacementSpan {
        private static final float DIAMETER_EM = 0.62f;
        private static final float GAP_EM = 0.18f;
        private static final float RING_EM = 0.09f;

        private final boolean taken;
        private final int color;

        SeatSpan(boolean taken, int color) {
            this.taken = taken;
            this.color = color;
        }

        @Override
        public int getSize(
                Paint paint,
                CharSequence text,
                int start,
                int end,
                Paint.FontMetricsInt metrics
        ) {
            if (metrics != null) {
                paint.getFontMetricsInt(metrics);
            }
            return Math.round(paint.getTextSize() * (DIAMETER_EM + GAP_EM));
        }

        @Override
        public void draw(
                Canvas canvas,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int y,
                int bottom,
                Paint paint
        ) {
            float textSize = paint.getTextSize();
            float radius = textSize * DIAMETER_EM / 2f;
            float centerX = x + radius;
            float centerY = y + (paint.ascent() + paint.descent()) / 2f;
            int oldColor = paint.getColor();
            Paint.Style oldStyle = paint.getStyle();
            float oldStrokeWidth = paint.getStrokeWidth();
            paint.setColor(color);
            if (taken) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(centerX, centerY, radius, paint);
            } else {
                // A stroke straddles its path, so pull the ring in by half to match the disc.
                float ring = textSize * RING_EM;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(ring);
                canvas.drawCircle(centerX, centerY, radius - ring / 2f, paint);
            }
            paint.setColor(oldColor);
            paint.setStyle(oldStyle);
            paint.setStrokeWidth(oldStrokeWidth);
        }
    }
}
