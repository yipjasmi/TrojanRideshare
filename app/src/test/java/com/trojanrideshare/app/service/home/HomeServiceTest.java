package com.trojanrideshare.app.service.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.Notification;
import com.trojanrideshare.app.model.OrganizerRideSummary;
import com.trojanrideshare.app.model.Payment;
import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HomeServiceTest {
    private static final long NOW_MILLIS = 1_700_000_000_000L;
    private static final long HOUR_MILLIS = 3_600_000L;
    private static final Timestamp NOW = hoursFromNow(0);

    @Test
    public void nextRide_picksTheSoonestUpcomingRide() {
        Ride later = ride(Ride.STATUS_ON_TIME, hoursFromNow(5));
        Ride sooner = ride(Ride.STATUS_ON_TIME, hoursFromNow(2));

        assertSame(sooner, HomeService.nextRide(Arrays.asList(later, sooner), NOW));
    }

    @Test
    public void nextRide_sortsADelayedRideByItsNewDepartureTime() {
        Ride delayed = ride(Ride.STATUS_DELAYED, hoursFromNow(6));
        delayed.originalDepartureTime = hoursFromNow(1);
        Ride onTime = ride(Ride.STATUS_ON_TIME, hoursFromNow(3));

        assertSame(onTime, HomeService.nextRide(Arrays.asList(delayed, onTime), NOW));
    }

    @Test
    public void nextRide_skipsCompletedAndCancelledRides() {
        Ride completed = ride(Ride.STATUS_COMPLETED, hoursFromNow(1));
        Ride cancelled = ride(Ride.STATUS_CANCELLED, hoursFromNow(2));
        Ride onTime = ride(Ride.STATUS_ON_TIME, hoursFromNow(3));

        assertSame(onTime, HomeService.nextRide(Arrays.asList(completed, cancelled, onTime), NOW));
    }

    @Test
    public void nextRide_skipsARideThatAlreadyLeftButIsNotCompleted() {
        Ride departed = ride(Ride.STATUS_ON_TIME, hoursFromNow(-1));
        Ride upcoming = ride(Ride.STATUS_DELAYED, hoursFromNow(4));

        assertSame(upcoming, HomeService.nextRide(Arrays.asList(departed, upcoming), NOW));
    }

    @Test
    public void nextRide_skipsARideWithNoDepartureTime() {
        Ride unscheduled = ride(Ride.STATUS_ON_TIME, null);
        Ride upcoming = ride(Ride.STATUS_ON_TIME, hoursFromNow(4));

        assertSame(upcoming, HomeService.nextRide(Arrays.asList(unscheduled, upcoming), NOW));
    }

    @Test
    public void nextRide_isNullWhenNothingIsUpcoming() {
        Ride departed = ride(Ride.STATUS_ON_TIME, hoursFromNow(-1));
        Ride completed = ride(Ride.STATUS_COMPLETED, hoursFromNow(1));

        assertNull(HomeService.nextRide(Arrays.asList(departed, completed), NOW));
        assertNull(HomeService.nextRide(Collections.emptyList(), NOW));
    }

    @Test
    public void unpaidCents_skipsPaidShares() {
        List<Payment> shares = Arrays.asList(share(3.33, false), share(5.00, true));

        assertEquals(333, HomeService.unpaidCents(shares));
    }

    @Test
    public void unpaidCents_addsEachShareInWholeCents() {
        // Adding the dollars first and truncating gives 869 here.
        List<Payment> shares = Arrays.asList(share(4.35, false), share(4.35, false));

        assertEquals(870, HomeService.unpaidCents(shares));
    }

    @Test
    public void outstandingCents_addsEveryOrganizedRide() {
        List<OrganizerRideSummary> summaries = Arrays.asList(
                new OrganizerRideSummary(new Ride(), 2, 0, 666),
                new OrganizerRideSummary(new Ride(), 2, 2, 0),
                new OrganizerRideSummary(new Ride(), 3, 2, 1250)
        );

        assertEquals(1916, HomeService.outstandingCents(summaries));
    }

    @Test
    public void unreadCount_countsOnlyUnreadNotifications() {
        assertEquals(2, HomeService.unreadCount(Arrays.asList(
                notification(false), notification(true), notification(false))));
    }

    private static Timestamp hoursFromNow(int hours) {
        return new Timestamp(new Date(NOW_MILLIS + hours * HOUR_MILLIS));
    }

    private static Ride ride(String status, Timestamp departureTime) {
        Ride ride = new Ride();
        ride.status = status;
        ride.departureTime = departureTime;
        return ride;
    }

    private static Payment share(double amount, boolean paid) {
        Payment share = new Payment();
        share.amount = amount;
        share.paid = paid;
        return share;
    }

    private static Notification notification(boolean read) {
        Notification notification = new Notification();
        notification.read = read;
        return notification;
    }
}
