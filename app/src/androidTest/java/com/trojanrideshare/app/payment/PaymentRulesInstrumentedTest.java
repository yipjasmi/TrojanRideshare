package com.trojanrideshare.app.payment;

import static com.trojanrideshare.app.payment.PaymentEmulator.assertDenied;
import static com.trojanrideshare.app.payment.PaymentEmulator.assertFailure;
import static com.trojanrideshare.app.payment.PaymentEmulator.await;
import static com.trojanrideshare.app.payment.PaymentEmulator.firestore;
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
import com.trojanrideshare.app.data.payment.PaymentDataAccess;
import com.trojanrideshare.app.data.payment.PaymentRepository;
import com.trojanrideshare.app.model.JoinRequest;
import com.trojanrideshare.app.model.Ride;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class PaymentRulesInstrumentedTest {
    private PaymentEmulator emulator;

    @Before
    public void setUp() throws Exception {
        emulator = PaymentEmulator.start();
    }

    private String splitRide() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);
        saveSplit(rideId, new long[] {334, 333, 333}, 1000);
        return rideId;
    }

    private void saveSplit(String rideId, long[] shares, long finalCents) throws Exception {
        await(PaymentEmulator.saveSplit(
                rideId,
                emulator.organizer,
                participants(),
                shares,
                finalCents
        ));
    }

    private double finalCost(String rideId) throws Exception {
        return await(firestore().collection("rides").document(rideId).get())
                .getDouble("finalCost");
    }

    private List<String> participants() {
        return Arrays.asList(emulator.organizer, emulator.riderA, emulator.riderB);
    }

    private DocumentReference share(String rideId, String userId) {
        return firestore()
                .collection("payments")
                .document(PaymentDataAccess.paymentDocumentId(rideId, userId));
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
    public void saveSplit_writesEveryShareAndTheFinalCostTogether() throws Exception {
        String rideId = splitRide();

        assertEquals(10.0, finalCost(rideId), 0);
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
    public void saveSplit_overwritesInsteadOfDuplicating() throws Exception {
        String rideId = splitRide();
        saveSplit(rideId, new long[] {400, 400, 400}, 1200);

        List<DocumentSnapshot> shares = await(firestore().collection("payments")
                .whereEqualTo("rideId", rideId)
                .whereEqualTo("organizerId", emulator.organizer).get()).getDocuments();
        assertEquals(3, shares.size());
        assertEquals(12.0, finalCost(rideId), 0);
    }

    @Test
    public void saveSplit_settlesZeroSharesWithoutLockingTheSplit() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);
        saveSplit(rideId, new long[] {1000, 0, 0}, 1000);

        DocumentSnapshot zero = await(share(rideId, emulator.riderA).get());
        assertTrue(zero.getBoolean("paid"));
        assertNull(zero.get("paidAt"));

        saveSplit(rideId, new long[] {400, 300, 300}, 1000);
        assertFalse(await(share(rideId, emulator.riderA).get()).getBoolean("paid"));
    }

    @Test
    public void rules_refuseASplitFromARider() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.riderA);

        assertDenied(share(rideId, emulator.riderB)
                .set(forgedShare(rideId, emulator.riderB, emulator.organizer)));
        assertDenied(share(rideId, emulator.riderB)
                .set(forgedShare(rideId, emulator.riderB, emulator.riderA)));
        assertDenied(firestore().collection("rides").document(rideId).update("finalCost", 1.0));
    }

    @Test
    public void rules_refuseNamingAnotherOrganizerOnAShare() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);

        assertDenied(share(rideId, emulator.riderA)
                .set(forgedShare(rideId, emulator.riderA, emulator.stranger)));
    }

    @Test
    public void rules_refuseAStrangerTakingOverARide() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.stranger);

        assertDenied(firestore()
                .collection("rides")
                .document(rideId)
                .update("organizerId", emulator.stranger));
    }

    @Test
    public void rules_refuseSplittingAnUncompletedRide() throws Exception {
        String rideId = emulator.seedRide(Ride.STATUS_ON_TIME);
        emulator.signIn(emulator.organizer);

        assertDenied(share(rideId, emulator.riderA)
                .set(forgedShare(rideId, emulator.riderA, emulator.organizer)));
    }

    @Test
    public void rules_refuseBillingSomeoneNotOnTheRide() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);

        assertDenied(share(rideId, emulator.stranger)
                .set(forgedShare(rideId, emulator.stranger, emulator.organizer)));
    }

    @Test
    public void rules_refuseAShareIdThatDoesNotMatchItsContents() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);

        assertDenied(share(rideId, emulator.riderB)
                .set(forgedShare(rideId, emulator.riderA, emulator.organizer)));
    }

    @Test
    public void rules_hideOtherRidersShares() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderB);

        assertDenied(share(rideId, emulator.riderA).get());
        assertDenied(firestore().collection("payments").whereEqualTo("rideId", rideId).get());
        assertDenied(firestore().collection("payments").get());
    }

    @Test
    public void rules_letARiderReadTheirOwnShare() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);

        assertEquals(3.33, await(share(rideId, emulator.riderA).get()).getDouble("amount"), 0);
    }

    @Test
    public void rules_refuseSignedOutUsers() throws Exception {
        String rideId = splitRide();
        FirebaseAuth.getInstance().signOut();

        assertDenied(share(rideId, emulator.riderA).get());
        assertDenied(share(rideId, emulator.riderA).update("paid", true));
    }

    @Test
    public void rules_refuseAccountsOutsideUsc() throws Exception {
        String rideId = splitRide();

        for (String domain : Arrays.asList("gmail.com", "usc.edu.evil.com", "notusc.edu")) {
            PaymentEmulator.signInWithGoogle(UUID.randomUUID() + "@" + domain);
            assertLockedOut(rideId);
        }
    }

    // Email verification is out of scope since the team switched to password signup, so an
    // unverified @usc.edu account counts as a student. It still only sees its own shares.
    @Test
    public void rules_letAPasswordAccountWithAUscEmailIn() throws Exception {
        String rideId = splitRide();

        PaymentEmulator.signUpWithPassword(UUID.randomUUID() + "@usc.edu");

        assertTrue(await(firestore().collection("rides").document(rideId).get()).exists());
        assertDenied(share(rideId, emulator.riderA).get());
    }

    private void assertLockedOut(String rideId) throws Exception {
        assertDenied(firestore().collection("rides").document(rideId).get());
        assertDenied(firestore().collection("joinRequests").get());
        assertDenied(share(rideId, emulator.riderA).get());
        assertDenied(firestore().collection("users").document(emulator.stranger)
                .update("fullName", "Not a student"));
    }

    @Test
    public void saveHandles_letsTheOwnerSaveAndClearTheirPaymentApps() throws Exception {
        emulator.signIn(emulator.riderA);
        DocumentReference profile = firestore().collection("users").document(emulator.riderA);

        await(PaymentEmulator.saveHandles(emulator.riderA, "kyle-t", "kyle"));
        DocumentSnapshot saved = await(profile.get());
        assertEquals("kyle-t", saved.getString("venmoUsername"));
        assertEquals("kyle", saved.getString("cashtag"));

        await(PaymentEmulator.saveHandles(emulator.riderA, null, null));
        DocumentSnapshot cleared = await(profile.get());
        assertFalse(cleared.contains("venmoUsername"));
        assertFalse(cleared.contains("cashtag"));
        assertEquals("Kyle Macasilli-Tan", cleared.getString("fullName"));
    }

    @Test
    public void rules_refuseSettingSomeoneElsesPaymentApps() throws Exception {
        emulator.signIn(emulator.stranger);

        assertDenied(PaymentEmulator.saveHandles(emulator.organizer, "stranger-venmo", null));
    }

    @Test
    public void markPaid_onlyWorksForTheRidersOwnShare() throws Exception {
        String rideId = splitRide();

        emulator.signIn(emulator.riderB);
        assertDenied(PaymentEmulator.markPaid(rideId, emulator.riderA));
        emulator.signIn(emulator.organizer);
        assertDenied(PaymentEmulator.markPaid(rideId, emulator.riderA));
        emulator.signIn(emulator.stranger);
        assertDenied(PaymentEmulator.markPaid(rideId, emulator.riderA));

        emulator.signIn(emulator.riderA);
        await(PaymentEmulator.markPaid(rideId, emulator.riderA));
        DocumentSnapshot marked = await(share(rideId, emulator.riderA).get());
        assertTrue(marked.getBoolean("paid"));
        assertTrue(marked.getTimestamp("paidAt") != null);
    }

    @Test
    public void rules_refuseChangingTheAmountWhileMarkingPaid() throws Exception {
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
    public void rules_refuseABackdatedPaidAt() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);

        assertDenied(share(rideId, emulator.riderA).update(
                "paid", true,
                "paidAt", new Timestamp(1, 0)
        ));
    }

    @Test
    public void rules_refuseUnpayingOrResplittingAPaidShare() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);
        await(PaymentEmulator.markPaid(rideId, emulator.riderA));

        assertDenied(share(rideId, emulator.riderA).update("paid", false));

        emulator.signIn(emulator.organizer);
        assertDenied(share(rideId, emulator.riderA)
                .set(forgedShare(rideId, emulator.riderA, emulator.organizer)));
    }

    @Test
    public void rules_refuseARiderFlaggingOrClearingAFlag() throws Exception {
        String rideId = splitRide();
        emulator.backdateShare(
                PaymentDataAccess.paymentDocumentId(rideId, emulator.riderA),
                8
        );

        emulator.signIn(emulator.riderB);
        assertDenied(PaymentEmulator.flag(rideId, emulator.riderA));

        emulator.signIn(emulator.organizer);
        await(PaymentEmulator.flag(rideId, emulator.riderA));

        emulator.signIn(emulator.riderA);
        assertDenied(share(rideId, emulator.riderA).update("flagged", false));
    }

    @Test
    public void rules_refuseFlaggingBeforeTheApprovalPeriodEnds() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.organizer);

        assertDenied(share(rideId, emulator.riderA).update(
                "flagged", true,
                "flaggedAt", FieldValue.serverTimestamp()
        ));
    }

    @Test
    public void flag_worksAfterTheApprovalPeriodEnds() throws Exception {
        String rideId = splitRide();
        emulator.backdateShare(
                PaymentDataAccess.paymentDocumentId(rideId, emulator.riderA),
                8
        );
        emulator.signIn(emulator.organizer);

        await(PaymentEmulator.flag(rideId, emulator.riderA));

        assertTrue(await(share(rideId, emulator.riderA).get()).getBoolean("flagged"));
    }

    @Test
    public void markPaid_clearsTheFlag() throws Exception {
        String rideId = splitRide();
        emulator.backdateShare(
                PaymentDataAccess.paymentDocumentId(rideId, emulator.riderA),
                8
        );
        emulator.signIn(emulator.organizer);
        await(PaymentEmulator.flag(rideId, emulator.riderA));

        emulator.signIn(emulator.riderA);
        await(PaymentEmulator.markPaid(rideId, emulator.riderA));

        DocumentSnapshot marked = await(share(rideId, emulator.riderA).get());
        assertTrue(marked.getBoolean("paid"));
        assertFalse(marked.getBoolean("flagged"));
    }

    @Test
    public void rules_refuseDeletingAShare() throws Exception {
        String rideId = splitRide();

        emulator.signIn(emulator.organizer);
        assertDenied(share(rideId, emulator.riderA).delete());
        emulator.signIn(emulator.riderA);
        assertDenied(share(rideId, emulator.riderA).delete());
    }

    @Test
    public void rules_refuseANonStringRideTitle() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);

        Map<String, Object> fields = forgedShare(rideId, emulator.riderA, emulator.organizer);
        fields.put("rideTitle", 123);
        assertDenied(share(rideId, emulator.riderA).set(fields));
    }

    @Test
    public void rules_refuseABackdatedAssignedAt() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);

        Map<String, Object> fields = forgedShare(rideId, emulator.riderA, emulator.organizer);
        fields.put("assignedAt", new Timestamp(1, 0));
        assertDenied(share(rideId, emulator.riderA).set(fields));
    }

    @Test
    public void saveSplit_worksForAnUntitledRide() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.clearTitle(rideId);
        emulator.signIn(emulator.organizer);

        saveSplit(rideId, new long[] {334, 333, 333}, 1000);

        assertNull(await(share(rideId, emulator.riderA).get()).get("rideTitle"));
    }

    @Test
    public void saveSplit_reportsALockedSplitAsAFailure() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);
        await(PaymentEmulator.markPaid(rideId, emulator.riderA));

        emulator.signIn(emulator.organizer);
        assertFailure(
                PaymentRepository.Failure.SPLIT_LOCKED,
                PaymentEmulator.saveSplit(
                        rideId,
                        emulator.organizer,
                        participants(),
                        new long[] {400, 300, 300},
                        1000
                )
        );
    }

    @Test
    public void saveSplit_reportsChangedRidersAsAFailure() throws Exception {
        String rideId = emulator.seedCompletedRide();
        emulator.signIn(emulator.organizer);

        assertFailure(
                PaymentRepository.Failure.RIDERS_CHANGED,
                PaymentEmulator.saveSplit(
                        rideId,
                        emulator.organizer,
                        Arrays.asList(emulator.organizer, emulator.riderA),
                        new long[] {500, 500},
                        1000
                )
        );
    }

    @Test
    public void flag_reportsAShareThatIsNotOverdueAsAFailure() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.organizer);

        assertFailure(
                PaymentRepository.Failure.NOT_OVERDUE,
                PaymentEmulator.flag(rideId, emulator.riderA)
        );
    }

    @Test
    public void flag_reportsAPaidShareAsAFailure() throws Exception {
        String rideId = splitRide();
        emulator.signIn(emulator.riderA);
        await(PaymentEmulator.markPaid(rideId, emulator.riderA));

        emulator.signIn(emulator.organizer);
        assertFailure(
                PaymentRepository.Failure.ALREADY_PAID,
                PaymentEmulator.flag(rideId, emulator.riderA)
        );
    }

    @Test
    public void joinRequest_letsARiderBeBilledAndPay() throws Exception {
        String rideId = emulator.seedRide(Ride.STATUS_ON_TIME, new ArrayList<>());
        // Requests and acceptances stop at departure, and completing waits for it, so the ride
        // leaves in an hour until the rider is in, then departs.
        emulator.moveDeparture(rideId, 60 * 60 * 1000L);
        JoinRequestDataAccess joinRequests = new JoinRequestDataAccess();

        emulator.signIn(emulator.riderA);
        submitJoinRequest(joinRequests, rideId, emulator.riderA);

        emulator.signIn(emulator.organizer);
        acceptJoinRequest(joinRequests, rideId, emulator.riderA);
        emulator.moveDeparture(rideId, -60 * 1000L);
        await(firestore()
                .collection("rides")
                .document(rideId)
                .update("status", Ride.STATUS_COMPLETED));
        await(PaymentEmulator.saveSplit(
                rideId,
                emulator.organizer,
                Arrays.asList(emulator.organizer, emulator.riderA),
                new long[] {500, 500},
                1000
        ));

        emulator.signIn(emulator.riderA);
        await(PaymentEmulator.markPaid(rideId, emulator.riderA));
        assertTrue(await(share(rideId, emulator.riderA).get()).getBoolean("paid"));
    }

    private void submitJoinRequest(
            JoinRequestDataAccess joinRequests,
            String rideId,
            String riderId
    ) throws Exception {
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

    private void acceptJoinRequest(
            JoinRequestDataAccess joinRequests,
            String rideId,
            String riderId
    ) throws Exception {
        TaskCompletionSource<Void> done = new TaskCompletionSource<>();
        joinRequests.accept(
                rideId,
                riderId,
                emulator.organizer,
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
                }
        );
        await(done.getTask());
    }
}
