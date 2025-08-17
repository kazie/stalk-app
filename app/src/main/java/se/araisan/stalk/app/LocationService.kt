package se.araisan.stalk.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.time.Duration

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate() {
        super.onCreate()

        // Mark service as running
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(APP_PREF_SERVICE_RUNNING, true)
            .apply()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Define the location callback
        locationCallback =
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    // Use the last location if available
                    locationResult.locations.lastOrNull()?.let {
                        sendLocationToServer(location = it)
                    }
                }
            }

        startForegroundService()
        startLocationUpdates()
    }

    private fun startForegroundService() {
        Log.i("LocationService", "Starting foreground service")
        val channelId = "LocationServiceChannel"
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Create a notification channel
        val channel =
            NotificationChannel(
                channelId,
                "Location Service",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        notificationManager?.createNotificationChannel(channel)

        // Create a notification
        val notification: Notification =
            NotificationCompat
                .Builder(this, channelId)
                .setContentTitle("Location Service")
                .setContentText("Collecting your location...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build()
        Log.i("LocationService", "Created notification")
        startForeground(1, notification)
        Log.i("LocationService", "Started foreground service")
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationService", "Location permissions are not granted!")
            stopSelf() // Stop the service if permissions are missing
            return
        }

        Log.i("LocationService", "Starting location updates")
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val stalkFrequency =
            sharedPreferences.getString(APP_PREF_STALK_FREQ, "10s")?.asDuration() ?: return
        val powerMode =
            if (stalkFrequency <
                Duration.ofSeconds(30)
            ) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }
        val locationRequest =
            LocationRequest
                .Builder(powerMode, stalkFrequency.toMillis())
                .build()
        fusedLocationClient
            .requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            .addOnFailureListener { e ->
                Log.e("LocationService", "Failed to request location updates", e)
                stopSelf() // Stop the service if location updates can't be started
            }
    }

    private fun sendLocationToServer(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val stalkVictim = sharedPreferences.getString(APP_PREF_USER_NAME, null) ?: return
        val payload =
            """
            {
                "name": "$stalkVictim",
                "latitude": $latitude,
                "longitude": $longitude
            }
            """.trimIndent()

        // Example using HttpURLConnection (you can replace with Retrofit if needed)
        serviceScope.launch {
            try {
                Log.i("LocationService", "Sending location to server")
                val url = java.net.URL(BuildConfig.SERVER_URL)
                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer ${BuildConfig.API_KEY}")
                    doOutput = true

                    outputStream.use {
                        it.write(payload.toByteArray())
                        it.flush()
                    }

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.i("LocationService", "Sent successfully!")
                    } else {
                        Log.e("LocationService", "Error: $responseCode")
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Error sending location to server", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("LocationService", "Stopping service and removing location updates")
        serviceScope.coroutineContext.cancel() // Cancel pending coroutines
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Mark service as not running
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(APP_PREF_SERVICE_RUNNING, false)
            .apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private fun String.asDuration() =
    when (this) {
        "1s" -> Duration.ofSeconds(1)
        "5s" -> Duration.ofSeconds(5)
        "10s" -> Duration.ofSeconds(10)
        "30s" -> Duration.ofSeconds(30)
        else -> Duration.ofSeconds(10)
    }
