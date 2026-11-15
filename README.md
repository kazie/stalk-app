# Stalk App

<p align="center">
  <img src="detective.svg" width="128" height="128" alt="Stalk App Logo">
</p>

Stalk App is an Android application that tracks your location and sends it to a central API. It is designed to work as a background service, ensuring continuous location reporting even when the app is not in the foreground.

## Features

- **Background Location Tracking:** Uses an Android Foreground Service to maintain location updates. The ongoing notification has a **Stop** action.
- **Configurable Intervals:** Choose how often your location is sent (1s, 5s, 10s, or 30s). The interval is locked while tracking; stop to change it.
- **User Identification:** Enter a name to associate with your location data.
- **Speed & Heading Toggle:** Choose whether speed and heading are sent with your location. Can be changed while tracking.
- **Android Auto:** Start/stop tracking and see its status from the car screen.
- **Data Privacy:** Includes a built-in feature to delete your data from the server.
- **API Error Feedback:** Server errors (bad API key, rejected location, …) are shown in the app instead of failing silently.
- **Modern Android:** Built with Kotlin and Jetpack components. Requires Android 14 (API 34) or newer and targets API 37.

## Permissions

The app requires the following permissions to function:
- `INTERNET`: To send data to the API.
- `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`: To track your device's position.
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_LOCATION`: To allow the app to track location while in the background.
- `POST_NOTIFICATIONS`: To show the foreground service notification while tracking.

The location and notification permissions are requested at runtime when you first start tracking.

## Prerequisites

- **Android Studio**, a version recent enough for Android Gradle Plugin 9.5.
- **Android SDK** with platform 37.
- **JDK 25** to run Gradle (Temurin is what CI and the Nix shell use). The code itself compiles with a JDK 17 toolchain, which Gradle downloads automatically if you don't have one.
- **Android device or emulator** running Android 14 (API 34) or higher.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/kazie/stalk-app.git
cd stalk-app
```

Optional: with [Nix](https://nixos.org/) and [direnv](https://direnv.net/), run `direnv allow` (or `nix develop`) to get a shell with Temurin 25. It also sets `ANDROID_HOME` to `~/Android/Sdk` if that exists and `ANDROID_HOME` isn't already set. The Android SDK itself is still managed by Android Studio.

### 2. Configure Environment Variables

The app requires a server URL and an API key to communicate with the backend. These are read from environment variables at build time and baked into `BuildConfig`.

- `SERVER_URL`: The origin of the Stalk server, without a path (default: `http://localhost:8080`). The app adds `/api/coords` itself.
- `API_KEY`: The token sent as `Authorization: Bearer <API_KEY>` (default: `INVALID_API_KEY`).

You can set these in your shell before building:

```bash
export SERVER_URL="https://api.your-stalk-server.com"
export API_KEY="your-secret-api-key"
./gradlew assembleDebug
```

> [!NOTE]
> Android blocks plain `http://` traffic by default. [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml) only allows it to `192.168.1.174`. For any other server, use HTTPS or add your host to that file. On the emulator, your development machine is `10.0.2.2`, not `localhost`.

### 3. Build and Run

Open the project in Android Studio and run it on your device. Alternatively, use Gradle:

```bash
./gradlew installDebug
```

Only debug builds work out of the box. The `release` signing config in `app/build.gradle.kts` is a placeholder (keystore `stalk-app-release-key.jks`, empty passwords) that you need to fill in before running `assembleRelease`.

## Android Auto

When your phone is connected to Android Auto, Stalk App shows up on the car screen. The screen shows your name, the interval and whether tracking is running, and has a single **Start/Stop stalking** button. You still do the setup on the phone: pick a name and interval, and grant permissions.

Android 14+ may refuse to start a location foreground service while the phone app is in the background. If that happens, the car screen says so and you need to start tracking on the phone instead.

The car UI is built with the [Jetpack Car App Library](https://developer.android.com/training/cars/apps) and uses the IoT category. Since the app isn't published on Play for Auto, Android Auto only shows it if **Unknown sources** is enabled:

1. On the phone, open **Settings → Connected devices → Android Auto** (or the Android Auto app). Tap **Version** 10 times to unlock developer settings.
2. In the ⋮ menu, open **Developer settings** and enable **Unknown sources**.
3. Connect to the car, or to the [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu) for testing:
   ```bash
   # In Android Auto developer settings: ⋮ → Start head unit server
   adb forward tcp:5277 tcp:5277
   $ANDROID_HOME/extras/google/auto/desktop-head-unit
   ```

## Technical Details

### Foreground Service
The app uses `LocationService`, which runs as a [Foreground Service](https://developer.android.com/guide/components/foreground-services). This ensures that the Android system maintains the process while it's tracking location. A persistent notification is displayed to the user while tracking is active.

Intervals under 30s request `PRIORITY_HIGH_ACCURACY` from the fused location provider; the 30s interval uses `PRIORITY_BALANCED_POWER_ACCURACY` to save battery.

### API Integration
The app communicates with the backend using the following endpoints, relative to `SERVER_URL`. Every request carries `Authorization: Bearer <API_KEY>`.

- **POST** `/api/coords`: Sends the current location.
  - Payload: `{ "name": "...", "latitude": ..., "longitude": ..., "speed": ..., "heading": ... }`
  - `speed` (m/s, from `Location.speed`) and `heading` (degrees clockwise from true north, from `Location.bearing`) are optional: they are only sent when the location fix has them (`hasSpeed()` / `hasBearing()`) and the **Send speed and heading** switch is on (the default), otherwise the keys are omitted entirely.
- **GET** `/api/coords/{name}`: Checks if data exists for a user name. A 2xx response means yes, 404 means no, anything else is an error.
- **DELETE** `/api/coords/{name}`: Deletes all data for the user name. A 404 also counts as success (nothing left to delete).

Error responses are parsed as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (`application/problem+json`), and the result is shown in the app. A body that isn't a problem document (for example an HTML page from a proxy) falls back to the HTTP status code.

## Development

### Code Style & Static Analysis
The project uses [Spotless](https://github.com/diffplug/spotless) with [Ktlint](https://pinterest.github.io/ktlint/) for code formatting.

To check formatting:
```bash
./gradlew spotlessCheck
```

To apply formatting:
```bash
./gradlew spotlessApply
```

Static analysis:
- **Android Lint:** `./gradlew lintDebug`. Any warning fails the build; suppress deliberately in [`app/lint.xml`](app/lint.xml).
- **Detekt:** `./gradlew detekt`, configured in [`config/detekt/detekt.yml`](config/detekt/detekt.yml) with a baseline in [`app/detekt-baseline.xml`](app/detekt-baseline.xml).

`./gradlew check` runs the unit tests together with Spotless, Lint, Detekt and the Kover coverage report.

### Testing
- **Unit Tests:** `./gradlew test`. Written with [Kotest](https://kotest.io/) on JUnit 5, with Robolectric for Android classes and MockWebServer for the API client.
- **Coverage:** `./gradlew koverHtmlReportDebug`, report in `app/build/reports/kover/`.
- **Instrumentation Tests:** `./gradlew connectedCheck`. Needs a connected device or emulator; not run in CI.

### CI
[`android-ci.yml`](.github/workflows/android-ci.yml) builds the app, runs static analysis and the unit tests, and posts a coverage report on pull requests. [`nix.yml`](.github/workflows/nix.yml) checks that the Nix dev shell still builds when the flake changes.
