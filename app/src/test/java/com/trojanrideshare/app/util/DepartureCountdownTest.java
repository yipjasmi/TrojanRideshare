package com.trojanrideshare.app.util;

import static org.junit.Assert.assertEquals;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class DepartureCountdownTest {
    private static final long NOW_MILLIS = 1_700_000_000_000L;
    private static final Timestamp NOW = new Timestamp(new Date(NOW_MILLIS));

    @Test
    public void until_underAMinuteIsNow() {
        DepartureCountdown countdown = DepartureCountdown.until(later(0, 0, 59), NOW);

        assertEquals(DepartureCountdown.Unit.NOW, countdown.unit());
    }

    @Test
    public void until_aDepartureThatPassedIsNow() {
        DepartureCountdown countdown = DepartureCountdown.until(later(0, -5, 0), NOW);

        assertEquals(DepartureCountdown.Unit.NOW, countdown.unit());
    }

    @Test
    public void until_underAnHourCountsWholeMinutesRoundedDown() {
        DepartureCountdown countdown = DepartureCountdown.until(later(0, 45, 59), NOW);

        assertEquals(DepartureCountdown.Unit.MINUTES, countdown.unit());
        assertEquals(45, countdown.minutes());
    }

    @Test
    public void until_oneMinuteIsNotNow() {
        DepartureCountdown countdown = DepartureCountdown.until(later(0, 1, 0), NOW);

        assertEquals(DepartureCountdown.Unit.MINUTES, countdown.unit());
        assertEquals(1, countdown.minutes());
    }

    @Test
    public void until_underADayCountsHoursAndMinutes() {
        DepartureCountdown countdown = DepartureCountdown.until(later(2, 15, 30), NOW);

        assertEquals(DepartureCountdown.Unit.HOURS, countdown.unit());
        assertEquals(2, countdown.hours());
        assertEquals(15, countdown.minutes());
    }

    @Test
    public void until_exactlyAnHourSwitchesToHours() {
        DepartureCountdown countdown = DepartureCountdown.until(later(1, 0, 0), NOW);

        assertEquals(DepartureCountdown.Unit.HOURS, countdown.unit());
        assertEquals(1, countdown.hours());
        assertEquals(0, countdown.minutes());
    }

    @Test
    public void until_aDayOrMoreCountsDaysAndHours() {
        DepartureCountdown countdown = DepartureCountdown.until(later(47, 59, 0), NOW);

        assertEquals(DepartureCountdown.Unit.DAYS, countdown.unit());
        assertEquals(1, countdown.days());
        assertEquals(23, countdown.hours());
    }

    private static Timestamp later(int hours, int minutes, int seconds) {
        long millis = TimeUnit.HOURS.toMillis(hours)
                + TimeUnit.MINUTES.toMillis(minutes)
                + TimeUnit.SECONDS.toMillis(seconds);
        return new Timestamp(new Date(NOW_MILLIS + millis));
    }
}
