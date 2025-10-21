package se.araisan.stalk.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
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
import java.time.Duration

class LocationService : Service() {
    companion object {
        const val ACTION_STOP = "se.araisan.stalk.app.action.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "LocationServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate() {
        super.onCreate()

        // Mark service as running
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit {
                putBoolean(APP_PREF_SERVICE_RUNNING, true)
            }

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
        val channelId = NOTIFICATION_CHANNEL_ID
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Create a notification channel
        val channel =
            NotificationChannel(
                channelId,
                "Location Service",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        notificationManager?.createNotificationChannel(channel)

        // Deep link to MainActivity when tapping the notification
        val mainIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val contentPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                mainIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        // Action to stop the service from the notification
        val stopIntent = Intent(this, LocationService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        // Create a notification
        val notification: Notification =
            NotificationCompat
                .Builder(this, channelId)
                .setContentTitle("Location Service")
                .setContentText("Collecting your location in foreground. Tap to manage or stop.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(contentPendingIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
                .setOngoing(true)
                .build()
        Log.i("LocationService", "Created notification")
        startForeground(NOTIFICATION_ID, notification)
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
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val stalkVictim = sharedPreferences.getString(APP_PREF_USER_NAME, null) ?: return
        val latitude = location.latitude
        val longitude = location.longitude

        serviceScope.launch {
            try {
                Log.i("LocationService", "Sending location to server")
                val ok = ApiClient.postLocation(stalkVictim, latitude, longitude)
                if (ok) {
                    Log.i("LocationService", "Sent successfully!")
                    // Mark that data exists for this user
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit {
                            putString(APP_PREF_LAST_CHECKED_NAME, stalkVictim)
                                .putBoolean(APP_PREF_DATA_EXISTS, true)
                        }
                } else {
                    Log.e("LocationService", "Error: post failed")
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
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (t: Throwable) {
            // ignore if not initialized
        }
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Mark service as not running
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit {
                putBoolean(APP_PREF_SERVICE_RUNNING, false)
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i("LocationService", "Received ACTION_STOP; stopping foreground and self")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
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
