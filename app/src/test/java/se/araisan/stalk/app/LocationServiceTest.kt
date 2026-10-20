package se.araisan.stalk.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowService

private const val NOTIFICATION_CHANNEL_ID = "LocationServiceChannel"

/** Simulates Android 14+ refusing a location foreground service started from the background. */
@Implements(Service::class)
class RefusingForegroundService : ShadowService() {
    @Implementation
    override fun startForeground(
        id: Int,
        notification: Notification,
    ): Unit = throw SecurityException("Not allowed to start foreground service")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationServiceTest {
    private val app = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        app
            .appPrefs()
            .edit()
            .clear()
            .apply()
        // Static in-process flag; reset so a previous test's service doesn't leak into this one.
        LocationService.isRunning = false
    }

    @Test
    fun `onCreate marks the service as running`() {
        Robolectric.buildService(LocationService::class.java).create()

        app.appPrefs().getBoolean(APP_PREF_SERVICE_RUNNING, false) shouldBe true
        LocationService.isRunning shouldBe true
    }

    @Test
    fun `onCreate posts a foreground notification on the expected channel`() {
        val controller = Robolectric.buildService(LocationService::class.java).create()
        val service = controller.get()

        val notificationManager = service.getSystemService(NotificationManager::class.java)
        notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID).shouldNotBeNull()

        val notification = shadowOf(service).lastForegroundNotification
        notification.shouldNotBeNull()
        notification.channelId shouldBe NOTIFICATION_CHANNEL_ID
    }

    @Test
    fun `onCreate stops the service when location permissions are missing`() {
        // Robolectric grants no permissions by default, so startLocationUpdates()
        // should bail out via stopSelf() without ever touching FusedLocationProviderClient.
        val controller = Robolectric.buildService(LocationService::class.java).create()

        shadowOf(controller.get()).isStoppedBySelf shouldBe true
    }

    @Test
    fun `onCreate requests location updates when permissions are granted`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        val controller = Robolectric.buildService(LocationService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()

        // Passes the permission gate and requests updates instead of stopping itself.
        shadowOf(controller.get()).isStoppedBySelf shouldBe false
        LocationService.isRunning shouldBe true
    }

    @Test
    @Config(shadows = [RefusingForegroundService::class])
    fun `onCreate stops and records the failure when foreground start is refused`() {
        val controller = Robolectric.buildService(LocationService::class.java).create()

        shadowOf(controller.get()).isStoppedBySelf shouldBe true
        LocationService.isRunning shouldBe false
        app.appPrefs().getBoolean(APP_PREF_SERVICE_RUNNING, false) shouldBe false
        app.appPrefs().getLong(APP_PREF_START_FAILED_AT, 0L) shouldBeGreaterThan 0L
    }

    @Test
    fun `onDestroy clears the running flag and stops the foreground notification`() {
        val controller = Robolectric.buildService(LocationService::class.java).create()

        controller.destroy()

        app.appPrefs().getBoolean(APP_PREF_SERVICE_RUNNING, true) shouldBe false
        LocationService.isRunning shouldBe false
        shadowOf(controller.get()).isForegroundStopped shouldBe true
    }

    @Test
    fun `onStartCommand with ACTION_STOP stops the service`() {
        val controller = Robolectric.buildService(LocationService::class.java).create()
        val service = controller.get()
        val stopIntent =
            Intent(app, LocationService::class.java).apply {
                action = LocationService.ACTION_STOP
            }

        val result = service.onStartCommand(stopIntent, 0, 1)

        result shouldBe Service.START_NOT_STICKY
        shadowOf(service).isStoppedBySelf shouldBe true
    }

    @Test
    fun `onStartCommand without ACTION_STOP keeps the service running`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val service = Robolectric.buildService(LocationService::class.java).create().get()

        service.onStartCommand(Intent(app, LocationService::class.java), 0, 1) shouldBe Service.START_STICKY
        shadowOf(service).isStoppedBySelf shouldBe false
    }

    @Test
    fun `onBind returns null since the service is not bindable`() {
        val service = Robolectric.buildService(LocationService::class.java).create().get()

        service.onBind(Intent(app, LocationService::class.java)) shouldBe null
    }

    @Test
    fun `a delivered location is posted to the API and marks data as existing`() {
        val location =
            Location("fused").apply {
                latitude = 12.34
                longitude = 56.78
            }

        deliverAndCaptureBody(location) shouldBe """{"name":"Alice","latitude":12.34,"longitude":56.78}"""

        app.appPrefs().getBoolean(APP_PREF_DATA_EXISTS, false) shouldBe true
        app.appPrefs().getString(APP_PREF_LAST_CHECKED_NAME, null) shouldBe "Alice"
    }

    @Test
    fun `a delivered location with speed and bearing includes them in the body`() {
        // Setting left unset: sending speed and heading is on by default.
        deliverAndCaptureBody(movingLocation()) shouldBe
            """{"name":"Alice","latitude":12.34,"longitude":56.78,"speed":8.5,"heading":45.0}"""
    }

    @Test
    fun `speed and bearing are left out when sending speed is turned off`() {
        deliverAndCaptureBody(movingLocation(), sendSpeed = false) shouldBe
            """{"name":"Alice","latitude":12.34,"longitude":56.78}"""
    }

    @Test
    fun `speed and bearing are sent when sending speed is turned on`() {
        deliverAndCaptureBody(movingLocation(), sendSpeed = true) shouldBe
            """{"name":"Alice","latitude":12.34,"longitude":56.78,"speed":8.5,"heading":45.0}"""
    }

    // Location.speed/bearing are Floats; use values exactly representable as Float
    // so the Double in the JSON body is predictable.
    private fun movingLocation() =
        Location("fused").apply {
            latitude = 12.34
            longitude = 56.78
            speed = 8.5f
            bearing = 45f
        }

    @Test
    fun `a failed post does not mark data as existing`() {
        val location =
            Location("fused").apply {
                latitude = 12.34
                longitude = 56.78
            }

        deliverAndCaptureBody(location, response = MockResponse(code = 500))

        app.appPrefs().getBoolean(APP_PREF_DATA_EXISTS, false) shouldBe false
    }

    @Test
    fun `an empty location result sends nothing`() {
        deliver(LocationResult.create(emptyList())) shouldBe 0
    }

    @Test
    fun `a delivered location without a user name sends nothing`() {
        deliver(LocationResult.create(listOf(Location("fused"))), userName = null) shouldBe 0
    }

    /** Feeds [location] to the service's location callback as user "Alice" and returns the POSTed body. */
    private fun deliverAndCaptureBody(
        location: Location,
        response: MockResponse = MockResponse(code = 200),
        sendSpeed: Boolean? = null,
    ): String {
        var body = ""
        deliver(LocationResult.create(listOf(location)), response = response, sendSpeed = sendSpeed) { server ->
            val recorded = server.takeRequest()
            recorded.method shouldBe "POST"
            body = recorded.body?.utf8().orEmpty()
        }
        return body
    }

    /**
     * Feeds [result] to the service's location callback, with [userName] (and [sendSpeed], if set)
     * stored in prefs, and returns how many requests reached the server.
     */
    private fun deliver(
        result: LocationResult,
        userName: String? = "Alice",
        response: MockResponse = MockResponse(code = 200),
        sendSpeed: Boolean? = null,
        inspect: (MockWebServer) -> Unit = {},
    ): Int {
        val server = MockWebServer()
        server.start()
        val productionApi = ApiClient.api
        ApiClient.api = ApiClient.buildApi(server.url("/").toString())

        try {
            val editor = app.appPrefs().edit().putString(APP_PREF_USER_NAME, userName)
            if (sendSpeed != null) editor.putBoolean(APP_PREF_SEND_SPEED_HEADING, sendSpeed)
            editor.apply()
            server.enqueue(response)

            val controller = Robolectric.buildService(LocationService::class.java).create()
            val service = controller.get()
            val callbackField =
                LocationService::class.java.getDeclaredField("locationCallback").apply { isAccessible = true }
            val callback = callbackField.get(service) as LocationCallback

            callback.onLocationResult(result)

            // sendLocationToServer() launches on Dispatchers.IO; give it a moment to finish.
            Thread.sleep(500)

            inspect(server)
            return server.requestCount
        } finally {
            ApiClient.api = productionApi
            runCatching { server.close() }
        }
    }
}
