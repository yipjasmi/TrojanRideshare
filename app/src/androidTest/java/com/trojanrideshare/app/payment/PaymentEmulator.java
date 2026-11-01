package com.trojanrideshare.app.payment;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.trojanrideshare.app.model.Ride;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// Skips the tests unless these are running on the host:
//   firebase emulators:start --only auth,firestore --project demo-trojanrideshare
final class PaymentEmulator {
    private static final String HOST = "10.0.2.2";
    private static final int AUTH_PORT = 9099;
    private static final int FIRESTORE_PORT = 8080;
    private static boolean connected;

    static final String PASSWORD = "test-password";

    final String organizer;
    final String riderA;
    final String riderB;
    final String stranger;
    private final Map<String, String> emails = new HashMap<>();

    private PaymentEmulator() throws Exception {
        organizer = newUser("Ashley Lan");
        riderA = newUser("Kyle Macasilli-Tan");
        riderB = newUser("Yucheng Zhao");
        stranger = newUser("Someone Else");
    }

    static PaymentEmulator start() throws Exception {
        // Only the first test can skip. Once connected, a network blip has to fail loudly, or a
        // security test quietly drops out of a run that still reports success.
        assumeTrue("Firebase emulators are not running", connected || reachable(FIRESTORE_PORT));
        if (!connected) {
            FirebaseAuth.getInstance().useEmulator(HOST, AUTH_PORT);
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            firestore.useEmulator(HOST, FIRESTORE_PORT);
            firestore.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build());
            connected = true;
        }
        return new PaymentEmulator();
    }

    private static boolean reachable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, port), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // disableNetwork() stops listeners but not transactions, so a real offline test needs this.
    static void setAirplaneMode(boolean on) throws Exception {
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("cmd connectivity airplane-mode " + (on ? "enable" : "disable"));
        try (InputStream in = new FileInputStream(output.getFileDescriptor())) {
            while (in.read() != -1) {
                // Wait for the command to finish.
            }
        }
        long deadline = SystemClock.uptimeMillis() + 30_000;
        while (reachable(FIRESTORE_PORT) == on && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(200);
        }
        assertTrue("network did not change", reachable(FIRESTORE_PORT) != on);
    }

    static <T> T await(Task<T> task) throws Exception {
        return Tasks.await(task, 15, TimeUnit.SECONDS);
    }

    static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private String newUser(String name) throws Exception {
        String email = UUID.randomUUID() + "@usc.edu";
        String uid = await(FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, PASSWORD)).getUser().getUid();
        emails.put(uid, email);
        Map<String, Object> profile = new HashMap<>();
        profile.put("fullName", name);
        await(db().collection("users").document(uid).set(profile));
        return uid;
    }

    void signIn(String uid) throws Exception {
        await(FirebaseAuth.getInstance().signInWithEmailAndPassword(emails.get(uid), PASSWORD));
    }

    String seedRide(String status) throws Exception {
        return seedRide(status, Arrays.asList(riderA, riderB));
    }

    String seedRide(String status, List<String> riders) throws Exception {
        signIn(organizer);
        DocumentReference ride = db().collection("rides").document();
        Map<String, Object> fields = new HashMap<>();
        fields.put("title", "LAX run before finals");
        fields.put("organizerId", organizer);
        fields.put("status", status);
        fields.put("participantIds", riders);
        fields.put("departureTime", Timestamp.now());
        fields.put("expectedCost", 40.0);
        fields.put("maxRiders", 4);
        await(ride.set(fields));
        return ride.getId();
    }

    String seedCompletedRide() throws Exception {
        return seedRide(Ride.STATUS_COMPLETED);
    }

    // Moves a share's clock back with the emulator's admin token, which is the only way past the
    // rule that assignedAt has to be the server's own time.
    void backdateShare(String paymentId, int days) throws Exception {
        long when = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        adminUpdate(paymentId, "\"assignedAt\":" + timestamp(when), "\"assignedAt\"");
    }

    // Stands in for a rider on another phone, so an open screen can be watched updating.
    void markPaidFromAnotherDevice(String paymentId) throws Exception {
        adminUpdate(paymentId,
                "\"paid\":{\"booleanValue\":true},\"paidAt\":" + timestamp(System.currentTimeMillis()),
                "\"paid\",\"paidAt\"");
    }

    private static String timestamp(long millis) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "{\"timestampValue\":\"" + iso.format(new Date(millis)) + "\"}";
    }

    private void adminUpdate(String paymentId, String fields, String fieldPaths) throws Exception {
        String project = FirebaseApp.getInstance().getOptions().getProjectId();
        String name = "projects/" + project + "/databases/(default)/documents/payments/" + paymentId;
        String body = "{\"writes\":[{\"update\":{\"name\":\"" + name + "\",\"fields\":{" + fields + "}},"
                + "\"updateMask\":{\"fieldPaths\":[" + fieldPaths + "]}}]}";

        URL url = new URL("http://" + HOST + ":" + FIRESTORE_PORT + "/v1/projects/" + project
                + "/databases/(default)/documents:commit");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer owner");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        assertTrue("admin write failed: " + connection.getResponseCode(),
                connection.getResponseCode() == 200);
    }

    static void assertDenied(Task<?> task) throws Exception {
        try {
            await(task);
        } catch (ExecutionException e) {
            FirebaseFirestoreException failure = (FirebaseFirestoreException) e.getCause();
            assertTrue(failure.getCode().toString(),
                    failure.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED);
            return;
        }
        throw new AssertionError("The write was allowed");
    }
}
