# Stalk App

<p align="center">
  <img src="detective.svg" width="128" height="128" alt="Stalk App Logo">
</p>

Stalk App is an Android application that tracks your location and sends it to a central API. It is designed to work as a background service, ensuring continuous location reporting even when the app is not in the foreground.

## Features

- **Background Location Tracking:** Uses an Android Foreground Service to maintain location updates.
- **Configurable Intervals:** Choose how often your location is sent (1s, 5s, 10s, or 30s).
- **User Identification:** Enter a name to associate with your location data.
- **Data Privacy:** Includes a built-in feature to delete your data from the server.
- **Modern Android:** Built with Kotlin, Jetpack components, and targets Android 15 (API 35+) and 16 (API 36).

## Permissions

The app requires the following permissions to function:
- `INTERNET`: To send data to the API.
- `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`: To track your device's position.
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_LOCATION`: To allow the app to track location while in the background.

## Prerequisites

- **Android Studio Ladybug** or newer.
- **JDK 17**.
- **Android device or emulator** running Android 14 (API 34) or higher.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/araisan/stalk-app.git
cd stalk-app
```

### 2. Configure Environment Variables

The app requires a server URL and an API key to communicate with the backend. These are configured via environment variables during the build process.

- `SERVER_URL`: The base URL of the Stalk API (default: `http://localhost:8080/api/coords`).
- `API_KEY`: The authorization token for the API.

You can set these in your shell before building:

```bash
export SERVER_URL="https://api.your-stalk-server.com/api/coords"
export API_KEY="your-secret-api-key"
./gradlew assembleDebug
```

### 3. Build and Run

Open the project in Android Studio and run it on your device. Alternatively, use Gradle:

```bash
./gradlew installDebug
```

## Technical Details

### Foreground Service
The app uses `LocationService`, which runs as a [Foreground Service](https://developer.android.com/guide/components/foreground-services). This ensures that the Android system maintains the process while it's tracking location. A persistent notification is displayed to the user while tracking is active.

### API Integration
The app communicates with the backend using the following endpoints (relative to `SERVER_URL`):

- **POST** `/`: Sends the current location.
  - Payload: `{ "name": "...", "latitude": ..., "longitude": ... }`
- **GET** `/{name}`: Checks if data exists for a specific user name.
- **DELETE** `/{name}`: Deletes all data associated with the user name.

## Development

### Code Style
The project uses [Spotless](https://github.com/diffplug/spotless) with [Ktlint](https://pinterest.github.io/ktlint/) for code formatting.

To check formatting:
```bash
./gradlew spotlessCheck
```

To apply formatting:
```bash
./gradlew spotlessApply
```

### Testing
- **Unit Tests:** `./gradlew test`
- **Instrumentation Tests:** `./gradlew connectedCheck`