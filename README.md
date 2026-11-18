# TrojanRideshare

Android app for USC students to organize shared Uber/Lyft rides: sign up with a USC email, post or find rides, request to join, and split the final cost. CSCI 310, Team 17.

## Requirements

- Android Studio (latest stable) with JDK 17 or newer
- An Android emulator or device on API 24+ (we test on a Pixel 4, API 33)
- Node.js and the Firebase CLI (`npm install -g firebase-tools`), only for the instrumented tests

## Run the app

1. Open the project root in Android Studio and let Gradle sync.
2. Add a Google Places key to `local.properties` in the project root (needed for the origin and destination pickers):
   ```
   PLACES_API_KEY=your_key_here
   ```
3. `app/google-services.json` is already in the repo, so the app connects to our Firebase project.
4. Pick an emulator and press Run, or from a terminal:
   ```sh
   ./gradlew installDebug
   ```
5. Create an account with any `@usc.edu` email, a 10-digit USC ID and a graduation year within the next six years.

## Run the tests

### White-box (JUnit, no emulator needed)

```sh
./gradlew testDebugUnitTest
```
Report: `app/build/reports/tests/testDebugUnitTest/index.html`

Coverage (JaCoCo):
```sh
./gradlew createDebugUnitTestCoverageReport
```
Report: `app/build/reports/coverage/test/debug/index.html`

### Black-box (Espresso)

The ride detail and payment tests need the local Firebase emulators. Start them in one terminal and leave them running:
```sh
firebase emulators:start --only auth,firestore --project demo-trojanrideshare
```
Then start an Android emulator and, in a second terminal:
```sh
./gradlew connectedDebugAndroidTest
```
Report: `app/build/reports/androidTests/connected/debug/index.html`

To run one class or one test:
```sh
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.trojanrideshare.app.ui.auth.SignUpActivityTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.trojanrideshare.app.ui.auth.SignUpActivityTest#signUp_rejectsNonUscEmails
```

The sign-up, login, create-ride and search tests stop at form validation, so they pass without the Firebase emulators. The ride detail and payment tests are skipped if the emulators are not reachable.
