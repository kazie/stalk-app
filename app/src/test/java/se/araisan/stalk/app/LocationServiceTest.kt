package se.araisan.stalk.app

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
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

private const val NOTIFICATION_CHANNEL_ID = "LocationServiceChannel"

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
        // Location.speed/bearing are Floats; use values exactly representable as Float
        // so the Double in the JSON body is predictable.
        val location =
            Location("fused").apply {
                latitude = 12.34
                longitude = 56.78
                speed = 8.5f
                bearing = 45f
            }

        deliverAndCaptureBody(location) shouldBe
            """{"name":"Alice","latitude":12.34,"longitude":56.78,"speed":8.5,"heading":45.0}"""
    }

    /** Feeds [location] to the service's location callback as user "Alice" and returns the POSTed body. */
    private fun deliverAndCaptureBody(location: Location): String {
        val server = MockWebServer()
        server.start()
        val productionApi = ApiClient.api
        ApiClient.api = ApiClient.buildApi(server.url("/").toString())

        try {
            app
                .appPrefs()
                .edit()
                .putString(APP_PREF_USER_NAME, "Alice")
                .apply()
            server.enqueue(MockResponse(code = 200))

            val controller = Robolectric.buildService(LocationService::class.java).create()
            val service = controller.get()
            val callbackField =
                LocationService::class.java.getDeclaredField("locationCallback").apply { isAccessible = true }
            val callback = callbackField.get(service) as LocationCallback

            callback.onLocationResult(LocationResult.create(listOf(location)))

            // sendLocationToServer() launches on Dispatchers.IO; give it a moment to finish.
            Thread.sleep(500)

            val recorded = server.takeRequest()
            recorded.method shouldBe "POST"
            return recorded.body?.utf8().orEmpty()
        } finally {
            ApiClient.api = productionApi
            runCatching { server.close() }
        }
    }
}
