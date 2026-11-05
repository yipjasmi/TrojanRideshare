package com.trojanrideshare.app.util;

import static org.junit.Assert.assertEquals;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class RideStatusChipTest {
    private static final Timestamp NOW = new Timestamp(new Date(1_700_000_000_000L));
    private static final Timestamp FUTURE =
            new Timestamp(new Date(1_700_000_000_000L + 86_400_000L));
    private static final Timestamp PAST =
            new Timestamp(new Date(1_700_000_000_000L - 86_400_000L));

    @Test
    public void of_upcomingOnTimeRideIsOnTime() {
        assertEquals(RideStatusChip.ON_TIME, RideStatusChip.of(ride(Ride.STATUS_ON_TIME, FUTURE), NOW));
    }

    @Test
    public void of_upcomingDelayedRideIsDelayed() {
        assertEquals(RideStatusChip.DELAYED, RideStatusChip.of(ride(Ride.STATUS_DELAYED, FUTURE), NOW));
    }

    @Test
    public void of_activeRidePastDepartureIsDeparted() {
        assertEquals(RideStatusChip.DEPARTED, RideStatusChip.of(ride(Ride.STATUS_ON_TIME, PAST), NOW));
        assertEquals(RideStatusChip.DEPARTED, RideStatusChip.of(ride(Ride.STATUS_DELAYED, PAST), NOW));
    }

    @Test
    public void of_completedRideIsCompleted() {
        assertEquals(RideStatusChip.COMPLETED, RideStatusChip.of(ride(Ride.STATUS_COMPLETED, PAST), NOW));
    }

    @Test
    public void of_cancelledRideStaysCancelledAfterItsDepartureTime() {
        assertEquals(RideStatusChip.CANCELLED, RideStatusChip.of(ride(Ride.STATUS_CANCELLED, PAST), NOW));
        assertEquals(RideStatusChip.CANCELLED, RideStatusChip.of(ride(Ride.STATUS_CANCELLED, FUTURE), NOW));
    }

    @Test
    public void of_unrecognizedStatusIsUnknown() {
        assertEquals(RideStatusChip.UNKNOWN, RideStatusChip.of(ride(null, FUTURE), NOW));
        assertEquals(RideStatusChip.UNKNOWN, RideStatusChip.of(ride("boarding", FUTURE), NOW));
        assertEquals(RideStatusChip.UNKNOWN, RideStatusChip.of(null, NOW));
    }

    @Test
    public void chips_eachNameTheirOwnState() {
        Set<Integer> labels = new HashSet<>();
        for (RideStatusChip chip : RideStatusChip.values()) {
            labels.add(chip.labelRes);
        }
        assertEquals(RideStatusChip.values().length, labels.size());
    }

    @Test
    public void chips_giveEveryKnownStateItsOwnColors() {
        RideStatusChip[] known = {
                RideStatusChip.ON_TIME, RideStatusChip.DELAYED, RideStatusChip.DEPARTED,
                RideStatusChip.COMPLETED, RideStatusChip.CANCELLED
        };
        Set<Integer> containers = new HashSet<>();
        Set<Integer> texts = new HashSet<>();
        for (RideStatusChip chip : known) {
            containers.add(chip.containerColorRes);
            texts.add(chip.textColorRes);
        }
        assertEquals(known.length, containers.size());
        assertEquals(known.length, texts.size());
    }

    private static Ride ride(String status, Timestamp departureTime) {
        Ride ride = new Ride();
        ride.status = status;
        ride.departureTime = departureTime;
        return ride;
    }
}
