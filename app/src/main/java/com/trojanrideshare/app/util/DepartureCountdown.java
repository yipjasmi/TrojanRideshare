package com.trojanrideshare.app.util;

import android.content.res.Resources;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.R;

import java.util.concurrent.TimeUnit;

/**
 * "Leaving in 2h 15m" on Home's next ride card. Counts whole minutes rounded down, so it never
 * shows more time than is left.
 */
public final class DepartureCountdown {
    public enum Unit { NOW, MINUTES, HOURS, DAYS }

    private static final long MINUTES_PER_HOUR = 60;
    private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    private final long totalMinutes;

    private DepartureCountdown(long totalMinutes) {
        this.totalMinutes = totalMinutes;
    }

    public static DepartureCountdown until(Timestamp departureTime, Timestamp now) {
        long millis = departureTime.toDate().getTime() - now.toDate().getTime();
        return new DepartureCountdown(Math.max(0, TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

    public Unit unit() {
        if (totalMinutes < 1) {
            return Unit.NOW;
        }
        if (totalMinutes < MINUTES_PER_HOUR) {
            return Unit.MINUTES;
        }
        if (totalMinutes < MINUTES_PER_DAY) {
            return Unit.HOURS;
        }
        return Unit.DAYS;
    }

    public long days() {
        return totalMinutes / MINUTES_PER_DAY;
    }

    public long hours() {
        return totalMinutes % MINUTES_PER_DAY / MINUTES_PER_HOUR;
    }

    public long minutes() {
        return totalMinutes % MINUTES_PER_HOUR;
    }

    public String text(Resources resources) {
        switch (unit()) {
            case NOW:
                return resources.getString(R.string.main_leaving_now);
            case MINUTES:
                return resources.getString(R.string.main_leaving_in_minutes, minutes());
            case HOURS:
                return resources.getString(R.string.main_leaving_in_hours, hours(), minutes());
            default:
                return resources.getString(R.string.main_leaving_in_days, days(), hours());
        }
    }
}
