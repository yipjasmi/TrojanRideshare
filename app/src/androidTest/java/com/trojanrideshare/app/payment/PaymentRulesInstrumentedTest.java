package com.trojanrideshare.app.payment;

import static com.trojanrideshare.app.payment.PaymentEmulator.assertDenied;
import static com.trojanrideshare.app.payment.PaymentEmulator.await;
import static com.trojanrideshare.app.payment.PaymentEmulator.db;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.trojanrideshare.app.data.joinrequest.JoinRequestDataAccess;
import com.trojanrideshare.app.data.joinrequest.JoinRequestRepository;
import com.trojanrideshare.app.data.payment.PaymentRepository;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.Ride;
import com.trojanrideshare.app.service.payment.PaymentService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Talks to the Firestore emulator with firestore.rules loaded, as the user each attack would use.
// Direct writes on purpose: these prove what the database refuses, not what the screens hide.
@RunWith(AndroidJUnit4.class)
public class PaymentRulesInstrumentedTest {
    private PaymentEmulator emulator;
    private final PaymentService service = new PaymentService();

    @Before
    public void setUp() throws Exception {
        emulator = PaymentEmulator.start();
    }

    private String splitRide() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);
        await(service.saveSplit(rideId, emulator.organizer, participants(), new long[] {334, 333, 333}, 1000));
        return rideId;
    }

    private List<String> participants() {
        return Arrays.asList(emulator.organizer, emulator.riderA, emulator.riderB);
    }

    private DocumentReference share(String rideId, String userId) {
        return db().collection("payments").document(PaymentRepository.paymentId(rideId, userId));
    }

    private Map<String, Object> forgedShare(String rideId, String userId, String organizerId) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("rideId", rideId);
        fields.put("userId", userId);
        fields.put("organizerId", organizerId);
        fields.put("rideTitle", "forged");
        fields.put("amount", 1.0);
        fields.put("paid", false);
        fields.put("paidAt", null);
        fields.put("assignedAt", FieldValue.serverTimestamp());
        fields.put("flagged", false);
        fields.put("flaggedAt", null);
        return fields;
    }

    @Test
    public void organizerSplitIsWrittenAtomicallyAndSumsToTheFinalCost() throws Exception {
        String rideId = splitRide();

        assertEquals(10.0, await(db().collection("rides").document(rideId).get()).getDouble("finalCost"), 0);
        DocumentSnapshot organizerShare = await(share(rideId, emulator.organizer).get());
        DocumentSnapshot riderShare = await(share(rideId, emulator.riderA).get());
        assertEquals(3.34, organizerShare.getDouble("amount"), 0);
        assertEquals(3.33, riderShare.getDouble("amount"), 0);
        assertTrue(organizerShare.getBoolean("paid"));
        assertFalse(riderShare.getBoolean("paid"));
        assertNull(organizerShare.get("paidAt"));
        assertEquals(emulator.organizer, riderShare.getString("organizerId"));
        assertEquals("LAX run before finals", riderShare.getString("rideTitle"));
    }

    @Test
    public void savingTheSplitAgainOverwritesInsteadOfDuplicating() throws Exception {
        String rideId = splitRide();
        await(service.saveSplit(rideId, emulator.organizer, participants(), new long[] {400, 400, 400}, 1200));

        List<DocumentSnapshot> shares = await(db().collection("payments")
                .whereEqualTo("rideId", rideId)
                .whereEqualTo("organizerId", emulator.organizer).get()).getDocuments();
        assertEquals(3, shares.size());
        assertEquals(12.0, await(db().collection("rides").document(rideId).get()).getDouble("finalCost"), 0);
    }

    @Test
    public void aZeroShareIsSettledAndDoesNotLockTheSplit() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);
        await(service.saveSplit(rideId, emulator.organizer, participants(), new long[] {1000, 0, 0}, 1000));

        DocumentSnapshot zero = await(share(rideId, emulator.riderA).get());
        assertTrue(zero.getBoolean("paid"));
        assertNull(zero.get("paidAt"));

        await(service.saveSplit(rideId, emulator.organizer, participants(), new long[] {400, 300, 300}, 1000));
        assertFalse(await(share(rideId, emulator.riderA).get()).getBoolean("paid"));
    }

    @Test
    public void aRiderCannotWriteASplit() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.riderA);

        // Billing another rider, so the only clauses left to refuse this are the organizer checks.
        assertDenied(share(rideId, emulator.riderB).set(forgedShare(rideId, emulator.riderB, emulator.organizer)));
        assertDenied(share(rideId, emulator.riderB).set(forgedShare(rideId, emulator.riderB, emulator.riderA)));
        assertDenied(db().collection("rides").document(rideId).update("finalCost", 1.0));
    }

    @Test
    public void theOrganizerCannotNameSomeoneElseAsOrganizerOnAShare() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);

        assertDenied(share(rideId, emulator.riderA).set(forgedShare(rideId, emulator.riderA, emulator.stranger)));
    }

    @Test
    public void aStrangerCannotTakeOverARideToSplitIt() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.stranger);

        assertDenied(db().collection("rides").document(rideId).update("organizerId", emulator.stranger));
    }

    @Test
    public void theOrganizerCannotSplitARideThatIsNotCompleted() throws Exception {
        String rideId = emulator.seedRide(Ride.STATUS_ON_TIME);
        emulator.signIn(emulator.organizer);

        assertDenied(share(rideId, emulator.riderA).set(forgedShare(rideId, emulator.riderA, emulator.organizer)));
    }

    @Test
    public void theOrganizerCannotBillSomeoneWhoWasNotOnTheRide() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);

        assertDenied(share(rideId, emulator.stranger).set(forgedShare(rideId, emulator.stranger, emulator.organizer)));
    }

    @Test
    public void shareIdMustMatchItsContents() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);

        assertDenied(share(rideId, emulator.riderB).set(forgedShare(rideId, emulator.riderA, emulator.organizer)));
    }

    @Test
    public void aRiderCannotSeeAnotherRidersShare() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderB);

        assertDenied(share(rideId, emulator.riderA).get());
        assertDenied(db().collection("payments").whereEqualTo("rideId", rideId).get());
        assertDenied(db().collection("payments").get());
    }

    @Test
    public void aRiderCanStillReadTheirOwnShare() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);

        assertEquals(3.33, await(share(rideId, emulator.riderA).get()).getDouble("amount"), 0);
    }

    @Test
    public void signedOutUsersGetNothing() throws Exception {
        String rideId = splitRide();
        FirebaseAuth.getInstance().signOut();

        assertDenied(share(rideId, emulator.riderA).get());
        assertDenied(share(rideId, emulator.riderA).update("paid", true));
    }

    @Test
    public void onlyTheRiderCanMarkTheirOwnSharePaid() throws Exception {
        String rideId = splitRide();

        emulator.signIn(emulator.riderB);
        assertDenied(service.markPaid(rideId, emulator.riderA));
        emulator.signIn(emulator.organizer);
        assertDenied(service.markPaid(rideId, emulator.riderA));
        emulator.signIn(emulator.stranger);
        assertDenied(service.markPaid(rideId, emulator.riderA));

        emulator.signIn(emulator.riderA);
        await(service.markPaid(rideId, emulator.riderA));
        DocumentSnapshot marked = await(share(rideId, emulator.riderA).get());
        assertTrue(marked.getBoolean("paid"));
        assertTrue(marked.getTimestamp("paidAt") != null);
    }

    @Test
    public void markingPaidCannotAlsoChangeTheAmount() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);

        Map<String, Object> fields = new HashMap<>();
        fields.put("paid", true);
        fields.put("paidAt", FieldValue.serverTimestamp());
        fields.put("flagged", false);
        fields.put("flaggedAt", null);
        fields.put("amount", 0.01);
        assertDenied(share(rideId, emulator.riderA).update(fields));
        assertDenied(share(rideId, emulator.riderA).update("amount", 0.01));
    }

    @Test
    public void paidAtCannotBeBackdatedByTheRider() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);

        assertDenied(share(rideId, emulator.riderA).update("paid", true,
                "paidAt", new com.google.firebase.Timestamp(1, 0)));
    }

    @Test
    public void aPaidShareCannotBeUnpaidOrResplit() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);
        await(service.markPaid(rideId, emulator.riderA));

        assertDenied(share(rideId, emulator.riderA).update("paid", false));

        emulator.signIn(emulator.organizer);
        assertDenied(share(rideId, emulator.riderA).set(forgedShare(rideId, emulator.riderA, emulator.organizer)));
    }

    @Test
    public void aRiderCannotFlagOrClearAFlag() throws Exception {
        String rideId = splitRide();
        emulator.backdateShare(PaymentRepository.paymentId(rideId, emulator.riderA), 8);

        emulator.signIn(emulator.riderB);
        assertDenied(service.flag(rideId, emulator.riderA));

        emulator.signIn(emulator.organizer);
        await(service.flag(rideId, emulator.riderA));

        emulator.signIn(emulator.riderA);
        assertDenied(share(rideId, emulator.riderA).update("flagged", false));
    }

    @Test
    public void theOrganizerCannotFlagBeforeTheApprovalPeriodEnds() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.organizer);

        // Past the service's own check, so this is the rule alone saying no.
        assertDenied(share(rideId, emulator.riderA).update("flagged", true,
                "flaggedAt", FieldValue.serverTimestamp()));
    }

    @Test
    public void theOrganizerCanFlagAfterTheApprovalPeriodEnds() throws Exception {
        String rideId = splitRide();
        emulator.backdateShare(PaymentRepository.paymentId(rideId, emulator.riderA), 8);
        emulator.signIn(emulator.organizer);

        await(service.flag(rideId, emulator.riderA));

        assertTrue(await(share(rideId, emulator.riderA).get()).getBoolean("flagged"));
    }

    @Test
    public void markingPaidClearsTheFlag() throws Exception {
        String rideId = splitRide();
        emulator.backdateShare(PaymentRepository.paymentId(rideId, emulator.riderA), 8);
        emulator.signIn(emulator.organizer);
        await(service.flag(rideId, emulator.riderA));

        emulator.signIn(emulator.riderA);
        await(service.markPaid(rideId, emulator.riderA));

        DocumentSnapshot marked = await(share(rideId, emulator.riderA).get());
        assertTrue(marked.getBoolean("paid"));
        assertFalse(marked.getBoolean("flagged"));
    }

    @Test
    public void nobodyCanDeleteAShare() throws Exception {
        String rideId = splitRide();

        emulator.signIn(emulator.organizer);
        assertDenied(share(rideId, emulator.riderA).delete());
        emulator.signIn(emulator.riderA);
        assertDenied(share(rideId, emulator.riderA).delete());
    }

    // Who gets billed is whoever the join flow put in participantIds, so that flow has to get
    // through these rules too.
    @Test
    public void aRiderWhoJoinsByRequestCanBeBilledAndPay() throws Exception {
        String rideId = emulator.seedRide(Ride.STATUS_ON_TIME, new ArrayList<>());
        JoinRequestDataAccess joinRequests = new JoinRequestDataAccess();

        emulator.signIn(emulator.riderA);
        submitJoinRequest(joinRequests, rideId, emulator.riderA);

        emulator.signIn(emulator.organizer);
        acceptJoinRequest(joinRequests, rideId, emulator.riderA);
        await(db().collection("rides").document(rideId).update("status", Ride.STATUS_COMPLETED));
        await(service.saveSplit(rideId, emulator.organizer,
                Arrays.asList(emulator.organizer, emulator.riderA), new long[] {500, 500}, 1000));

        emulator.signIn(emulator.riderA);
        await(service.markPaid(rideId, emulator.riderA));
        assertTrue(await(share(rideId, emulator.riderA).get()).getBoolean("paid"));
    }

    private void submitJoinRequest(JoinRequestDataAccess joinRequests, String rideId, String riderId)
            throws Exception {
        JoinRequest request = new JoinRequest();
        request.rideId = rideId;
        request.requesterId = riderId;
        request.status = JoinRequest.PENDING;
        request.createdAt = Timestamp.now();

        TaskCompletionSource<Void> done = new TaskCompletionSource<>();
        joinRequests.submit(request, new JoinRequestRepository.Callback() {
            @Override
            public void onSuccess(JoinRequest submitted) {
                done.setResult(null);
            }

            @Override
            public void onFailure(JoinRequestRepository.Failure failure) {
                done.setException(new IllegalStateException(failure.name()));
            }

            @Override
            public void onError(Exception exception) {
                done.setException(exception);
            }
        });
        await(done.getTask());
    }

    private void acceptJoinRequest(JoinRequestDataAccess joinRequests, String rideId, String riderId)
            throws Exception {
        TaskCompletionSource<Void> done = new TaskCompletionSource<>();
        joinRequests.accept(rideId, riderId, emulator.organizer,
                new JoinRequestRepository.DecisionCallback() {
                    @Override
                    public void onSuccess() {
                        done.setResult(null);
                    }

                    @Override
                    public void onFailure(JoinRequestRepository.Failure failure) {
                        done.setException(new IllegalStateException(failure.name()));
                    }

                    @Override
                    public void onError(Exception exception) {
                        done.setException(exception);
                    }
                });
        await(done.getTask());
    }
}
