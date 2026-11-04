package com.trojanrideshare.app.service.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class RideScheduleServiceTest {
    private static final Timestamp NOW = new Timestamp(new Date(1_700_000_000_000L));
    private static final Timestamp FUTURE =
            new Timestamp(new Date(1_700_000_000_000L + 86_400_000L));
    private static final Timestamp PAST =
            new Timestamp(new Date(1_700_000_000_000L - 86_400_000L));

    @Test
    public void partition_futureActiveRideIsUpcoming() {
        Ride ride = ride("r", Ride.STATUS_ON_TIME, FUTURE);

        RideScheduleService.Schedule schedule =
                RideScheduleService.partition(Collections.singletonList(ride), NOW);

        assertEquals(Collections.singletonList(ride), schedule.upcoming);
        assertTrue(schedule.completed.isEmpty());
    }

    @Test
    public void partition_departedRideStaysUpcomingUntilOrganizerCompletes() {
        Ride ride = ride("r", Ride.STATUS_DELAYED, PAST);

        RideScheduleService.Schedule schedule =
                RideScheduleService.partition(Collections.singletonList(ride), NOW);

        assertEquals(Collections.singletonList(ride), schedule.upcoming);
        assertTrue(schedule.completed.isEmpty());
    }

    @Test
    public void partition_completedStatusIsCompleted() {
        Ride ride = ride("r", Ride.STATUS_COMPLETED, FUTURE);

        RideScheduleService.Schedule schedule =
                RideScheduleService.partition(Collections.singletonList(ride), NOW);

        assertEquals(Collections.singletonList(ride), schedule.completed);
    }

    @Test
    public void partition_cancelledRideIsExcludedFromBoth() {
        Ride future = ride("future", Ride.STATUS_CANCELLED, FUTURE);
        Ride past = ride("past", Ride.STATUS_CANCELLED, PAST);

        RideScheduleService.Schedule schedule =
                RideScheduleService.partition(Arrays.asList(future, past), NOW);

        assertTrue(schedule.upcoming.isEmpty());
        assertTrue(schedule.completed.isEmpty());
    }

    @Test
    public void partition_mixedSetSplitsCorrectly() {
        Ride upcoming = ride("up", Ride.STATUS_ON_TIME, FUTURE);
        Ride done = ride("done", Ride.STATUS_COMPLETED, PAST);
        Ride cancelled = ride("x", Ride.STATUS_CANCELLED, FUTURE);

        RideScheduleService.Schedule schedule =
                RideScheduleService.partition(Arrays.asList(upcoming, done, cancelled), NOW);

        assertEquals(Collections.singletonList(upcoming), schedule.upcoming);
        assertEquals(Collections.singletonList(done), schedule.completed);
    }

    @Test
    public void awaitingCompletion_trueOnlyForDepartedActiveRides() {
        assertTrue(RideScheduleService.awaitingCompletion(
                ride("a", Ride.STATUS_ON_TIME, PAST), NOW));
        assertTrue(RideScheduleService.awaitingCompletion(
                ride("b", Ride.STATUS_DELAYED, PAST), NOW));
        assertFalse(RideScheduleService.awaitingCompletion(
                ride("c", Ride.STATUS_ON_TIME, FUTURE), NOW));
        assertFalse(RideScheduleService.awaitingCompletion(
                ride("d", Ride.STATUS_CANCELLED, PAST), NOW));
        assertFalse(RideScheduleService.awaitingCompletion(
                ride("e", Ride.STATUS_COMPLETED, PAST), NOW));
    }

    private static Ride ride(String id, String status, Timestamp departureTime) {
        Ride ride = new Ride();
        ride.id = id;
        ride.status = status;
        ride.departureTime = departureTime;
        return ride;
    }
}
