package com.trojanrideshare.app.service.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.data.ride.FakeRideDataAccess;
import com.trojanrideshare.app.model.PlaceInfo;
import com.trojanrideshare.app.model.Ride;

import org.junit.Test;

import java.util.Collections;
import java.util.Date;

/**
 * White-box tests for {@link CreateRideService#createRide}: each test drives one of its four
 * exits (not signed in, invalid form, write failed, published).
 */
public class CreateRideServiceTest {
    private static final String ORGANIZER = "organizer-1";

    private static Timestamp fromNow(long millis) {
        return new Timestamp(new Date(System.currentTimeMillis() + millis));
    }

    private static PlaceInfo place(String id) {
        PlaceInfo place = new PlaceInfo();
        place.placeId = id;
        place.address = id + " address";
        return place;
    }

    private static RideInput validInput(String title) {
        return new RideInput(title, place("usc"), place("lax"), fromNow(86_400_000L), 4, 40.0);
    }

    @Test
    public void createRide_rejectsAMissingOrBlankOrganizer() {
        FakeRideDataAccess rides = new FakeRideDataAccess();
        CreateRideService service = new CreateRideService(rides);

        Result missing = new Result();
        service.createRide(validInput("USC to LAX"), null, missing);
        Result blank = new Result();
        service.createRide(validInput("USC to LAX"), "   ", blank);

        assertEquals("You must be signed in to create a ride.", missing.error);
        assertEquals("You must be signed in to create a ride.", blank.error);
        assertEquals(0, rides.createCalls);
    }

    @Test
    public void createRide_invalidFormReportsErrorsAndWritesNothing() {
        FakeRideDataAccess rides = new FakeRideDataAccess();
        Result result = new Result();

        new CreateRideService(rides).createRide(validInput("   "), ORGANIZER, result);

        assertNotNull(result.invalid);
        assertEquals(RideValidator.ERROR_TITLE_REQUIRED,
                result.invalid.errorFor(RideValidator.FIELD_TITLE));
        assertNull(result.rideId);
        assertEquals(0, rides.createCalls);
    }

    @Test
    public void createRide_rejectsADepartureInThePast() {
        FakeRideDataAccess rides = new FakeRideDataAccess();
        Result result = new Result();
        RideInput past = new RideInput(
                "USC to LAX", place("usc"), place("lax"), fromNow(-60_000L), 4, 40.0);

        new CreateRideService(rides).createRide(past, ORGANIZER, result);

        assertEquals(RideValidator.ERROR_DEPARTURE_IN_PAST,
                result.invalid.errorFor(RideValidator.FIELD_DEPARTURE_TIME));
        assertEquals(0, rides.createCalls);
    }

    @Test
    public void createRide_publishesAnOnTimeRideWithOnlyTheOrganizer() {
        FakeRideDataAccess rides = new FakeRideDataAccess();
        rides.nextRideId = "ride-42";
        Result result = new Result();
        RideInput input = validInput("  USC to LAX  ");

        new CreateRideService(rides).createRide(input, ORGANIZER, result);

        assertEquals("ride-42", result.rideId);
        Ride published = rides.createdRide;
        assertNull("Firestore rejects a non-null @DocumentId on write", published.id);
        assertEquals("USC to LAX", published.title);
        assertEquals(ORGANIZER, published.organizerId);
        assertEquals(Ride.STATUS_ON_TIME, published.status);
        assertEquals(Collections.singletonList(ORGANIZER), published.participantIds);
        assertSame(input.departureTime, published.departureTime);
        assertEquals(4, published.maxRiders);
        assertEquals(40.0, published.expectedCost, 0);
        assertNotNull(published.createdAt);
    }

    @Test
    public void createRide_turnsAWriteFailureIntoAFriendlyMessage() {
        FakeRideDataAccess rides = new FakeRideDataAccess();
        rides.nextError = new IllegalStateException("PERMISSION_DENIED");
        Result result = new Result();

        new CreateRideService(rides).createRide(validInput("USC to LAX"), ORGANIZER, result);

        assertEquals("Unable to publish your ride. Please try again.", result.error);
        assertNull(result.rideId);
        assertEquals(1, rides.createCalls);
    }

    private static final class Result implements CreateRideService.Callback {
        private ValidationResult invalid;
        private String rideId;
        private String error;

        @Override
        public void onInvalid(ValidationResult result) {
            invalid = result;
        }

        @Override
        public void onSuccess(String id) {
            rideId = id;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }
}
