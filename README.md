# Fitness Metrics Dashboard

An Android application that reads a user’s fitness and wellness data using Google Fit APIs and Health Connect APIs, rendering the aggregated metrics in a local, responsive WebView-based frontend.

---

## 1. Project Overview

**Fitness Metrics Dashboard** is a standalone, privacy-first Android application designed to aggregate daily fitness data (Steps, Calories, Distance, Heart Points, Move Minutes, and Sleep Duration) across flexible date ranges (Today, Current Week, Current Month). 

The app features a hybrid architecture:
- **Native Android (Kotlin)**: Handles Google Account Sign-In / OAuth 2.0 authorization, Android runtime permissions, Google Fit API History & Recording queries, Health Connect API queries, and date boundaries using `java.time` APIs.
- **JavaScript Bridge (`AndroidFitnessBridge`)**: Asynchronously communicates native results to the web frontend using thread-safe JSON callbacks.
- **Web Frontend (HTML5/CSS3/JavaScript)**: Local dashboard rendering summary cards, day-by-day table logs, and a canvas activity chart.

---

## 2. Supported Metrics

| Metric | Google Fit API Data Type | Health Connect API Record | Unit |
| :--- | :--- | :--- | :--- |
| **Steps** | `TYPE_STEP_COUNT_DELTA` / `AGGREGATE_STEP_COUNT_DELTA` | `StepsRecord` | count |
| **Calories / Energy Expended** | `TYPE_CALORIES_EXPENDED` / `AGGREGATE_CALORIES_EXPENDED` | `TotalCaloriesBurnedRecord` | kilocalories (kcal) |
| **Distance** | `TYPE_DISTANCE_DELTA` / `AGGREGATE_DISTANCE_DELTA` | `DistanceRecord` | meters (m) / km |
| **Heart Points** | `TYPE_HEART_POINTS` / `AGGREGATE_HEART_POINTS` | *Unsupported on Health Connect* | points |
| **Move Minutes** | `TYPE_MOVE_MINUTES` / `AGGREGATE_MOVE_MINUTES` | `ExerciseSessionRecord` duration | minutes |
| **Sleep Duration** | `TYPE_SLEEP_SEGMENT` | `SleepSessionRecord` duration | minutes / hours |

*Note: If a metric is unsupported or has no recorded data for a specific date, the API returns `null` with a structured `NO_DATA` or `UNSUPPORTED` status code rather than converting missing values to zero.*

---

## 3. Google Fit vs. Health Connect & Current API Support Status

### Deprecation & Transition Context
Google officially deprecated the Google Fit Android SDK and REST APIs, initiating a transition towards **Health Connect**. Google Fit APIs were turned down in mid-2025.

### Provider Abstraction Solution
To preserve the requested architecture while guaranteeing long-term functionality, this project implements a provider abstraction interface (`FitnessDataProvider`):

```kotlin
interface FitnessDataProvider {
    val name: String
    suspend fun checkAvailability(): ProviderAvailability
    suspend fun checkPermissions(): PermissionStatus
    suspend fun requestPermissions(activity: Activity): PermissionResult
    suspend fun readFitnessData(range: FitnessDateRange): FitnessDashboardData
    suspend fun subscribeToRecording(): SubscriptionResult
    suspend fun unsubscribeFromRecording(): SubscriptionResult
}
```

The application ships with three concrete provider implementations:
1. **`GoogleFitDataProvider`**: Implements Google Play Services Fitness (`com.google.android.gms:play-services-fitness`) and Google Sign-In (`play-services-auth`).
2. **`HealthConnectDataProvider`**: Implements Jetpack Health Connect (`androidx.health.connect:connect-client`).
3. **`FakeFitnessDataProvider` (Demo Mode)**: Generates deterministic, realistic daily fitness data for offline testing, emulator execution, and unit tests without requiring active Google Play Services accounts.

**Default Behavior**: `FitnessRepository` defaults to `GoogleFitDataProvider` when available. If Google Fit is unavailable or fails due to SDK deprecation, `FitnessRepository` automatically falls back to `HealthConnectDataProvider` or allows user selection via the frontend provider dropdown.

---

## 4. Setup Requirements & Technical Stack

- **Android Studio**: Android Studio Jellyfish / Ladybug / Meerkat (AGP 8.x - 9.x compatible)
- **JDK Version**: Java 17 (JVM target 17)
- **Minimum SDK**: API Level 26 (Android 8.0 Oreo)
- **Compile / Target SDK**: API Level 35 / 37
- **Kotlin**: 2.2.10
- **UI Architecture**: XML Layouts + Native ViewBinding + Local WebView Assets

---

## 5. Google Cloud Console & OAuth Setup Guide

To run Google Fit with live Google Account authorization:

1. **Create Google Cloud Project**:
   - Visit the [Google Cloud Console](https://console.cloud.google.com/).
   - Create a new project named `Fitness Dashboard`.

2. **Enable Required APIs**:
   - Go to **APIs & Services** > **Library**.
   - Search for **Fitness API** and click **Enable**.

3. **Configure OAuth Consent Screen**:
   - Select **External** user type.
   - Fill in app details and add authorized test email addresses under **Test users**.
   - Add the following Fitness scopes:
     - `https://www.googleapis.com/auth/fitness.activity.read`
     - `https://www.googleapis.com/auth/fitness.body.read`
     - `https://www.googleapis.com/auth/fitness.location.read`
     - `https://www.googleapis.com/auth/fitness.sleep.read`

4. **Create Android OAuth Client Credentials**:
   - Go to **APIs & Services** > **Credentials**.
   - Click **Create Credentials** > **OAuth client ID**.
   - Select application type: **Android**.
   - Package Name: `com.example.fitnessdashboard`
   - Generate and add your SHA-1 fingerprint:
     ```bash
     ./gradlew signingReport
     ```
   - Copy the `SHA1` fingerprint output for `debugAndroidTest` or `debug` variant and paste it in Cloud Console.

5. **`google-services.json`**:
   - Download `google-services.json` if using Firebase/Google services plugin, or ensure package name matches Cloud Console.

---

## 6. Android Permissions

The app declares minimum necessary permissions in `AndroidManifest.xml`:
- `android.permission.INTERNET`: Required for OAuth token exchange with Google Sign-In servers.
- `android.permission.ACTIVITY_RECOGNITION`: Required for on-device step sensor access (Android 10+).
- Health Connect Read Permissions:
  - `android.permission.health.READ_STEPS`
  - `android.permission.health.READ_TOTAL_CALORIES_BURNED`
  - `android.permission.health.READ_DISTANCE`
  - `android.permission.health.READ_SLEEP`
  - `android.permission.health.READ_HEART_RATE`
  - `android.permission.health.READ_EXERCISE`

Runtime permission requests are requested using AndroidX Activity Result APIs (`ActivityResultContracts.RequestPermission()`).

---

## 7. How to Run & Test the App

### Running on Physical Device
1. Connect physical Android device via USB with USB Debugging enabled.
2. Select your device in Android Studio.
3. Click **Run 'app'** (`Shift + F10`).
4. Select **Google Fit** or **Health Connect** from the provider dropdown and tap **Connect Access**.

### Testing on Android Emulator
Emulators often lack physical step sensors and active Google account health data.
To test data rendering on an emulator:
1. Select **Demo Mode** in the dashboard provider dropdown.
2. Toggle between **Today**, **This Week**, and **This Month**.
3. Demo mode generates full metric sets, daily breakdown logs, and bar chart visualizations.

---

## 8. JavaScript Bridge API Reference

The native bridge is exposed to JavaScript as `window.AndroidFitnessBridge`.

### Exposed Native Methods:
- `checkProviderAvailability()`: Checks if current provider (Google Fit / Health Connect) is available.
- `checkFitnessPermissions()`: Checks runtime & OAuth permission status.
- `requestFitnessPermissions()`: Launches native permission dialog or Google Sign-In flow.
- `getFitnessData(requestJson)`: Reads fitness metrics for range (`{"range": "TODAY" | "WEEK" | "MONTH"}`).
- `subscribeToRecording()`: Subscribes to background step count recording.
- `unsubscribeFromRecording()`: Unsubscribes from step count recording.
- `selectProvider(providerName)`: Switches active provider (`"GoogleFit"`, `"HealthConnect"`, or `"Demo"`).
- `openApplicationSettings()`: Opens system app settings if permissions are permanently denied.

### Received JavaScript Callbacks:
- `window.onFitnessDataReceived(dataJson)`
- `window.onFitnessError(errorJson)`
- `window.onPermissionStatus(permissionJson)`
- `window.onAvailabilityStatus(availabilityJson)`
- `window.onSubscriptionStatus(subscriptionJson)`

### Example Request JSON:
```json
{
  "range": "WEEK"
}
```

### Example Success Callback Payload (`window.onFitnessDataReceived`):
```json
{
  "success": true,
  "range": "WEEK",
  "provider": "GoogleFit",
  "generatedAt": "2025-01-15T10:30:00Z",
  "data": {
    "summary": {
      "steps": 42150,
      "caloriesKcal": 2840.25,
      "distanceMeters": 29800.4,
      "heartPoints": 142.0,
      "moveMinutes": 315,
      "sleepDurationMinutes": 2520
    },
    "days": [
      {
        "date": "2025-01-13",
        "startTime": "2025-01-13T00:00:00+05:30",
        "endTime": "2025-01-13T23:59:59.999+05:30",
        "steps": { "value": 8500, "status": "AVAILABLE" },
        "calories": { "value": 570.2, "status": "AVAILABLE" },
        "distanceMeters": { "value": 6200.1, "status": "AVAILABLE" },
        "heartPoints": { "value": 29.0, "status": "AVAILABLE" },
        "moveMinutes": { "value": 62, "status": "AVAILABLE" },
        "sleepDurationMinutes": { "value": 420, "status": "AVAILABLE" },
        "availability": {
          "steps": "AVAILABLE",
          "calories": "AVAILABLE",
          "distanceMeters": "AVAILABLE",
          "heartPoints": "AVAILABLE",
          "moveMinutes": "AVAILABLE",
          "sleepDurationMinutes": "AVAILABLE"
        }
      }
    ]
  }
}
```

---

## 9. Error Codes

- `INVALID_REQUEST`: Malformed range or parameter.
- `MALFORMED_JSON`: Unable to parse incoming request JSON.
- `PROVIDER_UNAVAILABLE`: Google Play Services or Health Connect SDK not available.
- `GOOGLE_ACCOUNT_REQUIRED`: Google account authorization missing.
- `PERMISSION_DENIED`: User denied runtime or OAuth permissions.
- `DATA_READ_FAILED`: API exception while querying history records.
- `UNSUPPORTED_METRIC`: Metric not supported by selected provider (e.g. Heart Points on Health Connect).
- `NO_DATA`: No records recorded for specified date range.

---

## 10. Privacy Statement

- **100% On-Device Data Handling**: Fitness data is retrieved directly from local Google Fit / Health Connect on-device APIs and passed directly to local WebView memory.
- **No Backend**: This application contains zero remote server endpoints, tracking SDKs, analytics, or third-party backends.
- **Controlled Logging**: Payload values and health tokens are stripped from release logs.

---

## 11. Build and Test Commands

```bash
# Assemble Debug APK
./gradlew app:assembleDebug

# Run All Unit Tests
./gradlew testDebugUnitTest

# Assemble Instrumentation Tests
./gradlew assembleDebugAndroidTest
```

---

## 12. Verification & Test Summary

- Unit Tests: **16/16 Passed** (`DateRangeUtilsTest`, `JsonUtilsTest`, `FakeFitnessDataProviderTest`, `MainViewModelTest`).
- Build Status: **SUCCESS**
