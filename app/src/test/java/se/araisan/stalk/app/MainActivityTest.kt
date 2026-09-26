package se.araisan.stalk.app

import android.Manifest
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import io.kotest.matchers.nulls.shouldBeNull
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
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    private val app = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        app
            .appPrefs()
            .edit()
            .clear()
            .apply()
        LocationService.isRunning = false
        ShadowToast.reset()
    }

    // Robolectric's main looper runs on a virtual clock in PAUSED mode: it only
    // advances via idleFor(), not real wall-clock time. We still sleep for real
    // between steps so background threads (e.g. OkHttp's dispatcher, answering
    // via MockWebServer) get real wall-clock time to actually complete their work.
    private fun idleUntil(
        timeoutMs: Long = 2000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            if (condition()) return
            Thread.sleep(20)
        }
        error("Condition not met within ${timeoutMs}ms")
    }

    @Test
    fun `onCreate restores the saved name and enables the start button`() {
        app
            .appPrefs()
            .edit()
            .putString(APP_PREF_USER_NAME, "Alice")
            .apply()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<EditText>(R.id.nameEditText).text.toString() shouldBe "Alice"
        activity.findViewById<Button>(R.id.start_button).isEnabled shouldBe true
    }

    @Test
    fun `onCreate restores the saved frequency selection`() {
        app
            .appPrefs()
            .edit()
            .putString(APP_PREF_STALK_FREQ, "5s")
            .apply()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Spinner>(R.id.intervalSpinner).selectedItem shouldBe "5s"
    }

    @Test
    fun `typing a name enables the start button and persists it`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<EditText>(R.id.nameEditText).setText("Bob")

        activity.findViewById<Button>(R.id.start_button).isEnabled shouldBe true
        app.appPrefs().getString(APP_PREF_USER_NAME, null) shouldBe "Bob"
    }

    @Test
    fun `selecting a spinner item persists the frequency`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Spinner>(R.id.intervalSpinner).setSelection(1)
        shadowOf(Looper.getMainLooper()).idle()

        app.appPrefs().getString(APP_PREF_STALK_FREQ, null) shouldBe "5s"
    }

    @Test
    fun `delete button is disabled by default`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.delete_button).isEnabled shouldBe false
    }

    @Test
    fun `delete button is enabled when data exists and the service is not running`() {
        app
            .appPrefs()
            .edit()
            .putBoolean(APP_PREF_DATA_EXISTS, true)
            .apply()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.delete_button).isEnabled shouldBe true
    }

    @Test
    fun `clicking start without permissions requests them instead of starting the service`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<EditText>(R.id.nameEditText).setText("Alice")

        activity.findViewById<Button>(R.id.start_button).performClick()

        shadowOf(activity).peekNextStartedService().shouldBeNull()
        app.appPrefs().getBoolean(APP_PREF_SERVICE_RUNNING, false) shouldBe false
    }

    @Test
    fun `clicking start with permissions granted starts the service`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(activity).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        activity.findViewById<EditText>(R.id.nameEditText).setText("Alice")

        activity.findViewById<Button>(R.id.start_button).performClick()

        val startedService = shadowOf(activity).peekNextStartedService()
        startedService.shouldNotBeNull()
        startedService.component?.className shouldBe LocationService::class.java.name
        activity.findViewById<Button>(R.id.start_button).text shouldBe "Stop stalking"
    }

    @Test
    fun `clicking stop while running stops the service`() {
        LocationService.isRunning = true
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.start_button).performClick()

        val stoppedService = shadowOf(activity).nextStoppedService
        stoppedService.shouldNotBeNull()
        stoppedService.component?.className shouldBe LocationService::class.java.name
        activity.findViewById<Button>(R.id.start_button).text shouldBe "Start stalking"
    }

    @Test
    fun `button follows the service when it is started elsewhere`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        // e.g. started from Android Auto while the phone screen is open
        LocationService.isRunning = true
        app
            .appPrefs()
            .edit()
            .putBoolean(APP_PREF_SERVICE_RUNNING, true)
            .commit()

        activity.findViewById<Button>(R.id.start_button).text shouldBe "Stop stalking"
        activity.findViewById<EditText>(R.id.nameEditText).isEnabled shouldBe false
    }

    @Test
    fun `button reverts to start when the service refuses to start`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(activity).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        activity.findViewById<EditText>(R.id.nameEditText).setText("Alice")
        activity.findViewById<Button>(R.id.start_button).performClick()

        app
            .appPrefs()
            .edit()
            .putLong(APP_PREF_START_FAILED_AT, 1234L)
            .commit()

        activity.findViewById<Button>(R.id.start_button).text shouldBe "Start stalking"
    }

    @Test
    fun `stale running flag is cleared so a restarted service still updates the button`() {
        // Process died without onDestroy: the flag was left at true.
        app
            .appPrefs()
            .edit()
            .putBoolean(APP_PREF_SERVICE_RUNNING, true)
            .commit()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        app.appPrefs().getBoolean(APP_PREF_SERVICE_RUNNING, true) shouldBe false

        // The system restarts the service while the screen is open.
        LocationService.isRunning = true
        app
            .appPrefs()
            .edit()
            .putBoolean(APP_PREF_SERVICE_RUNNING, true)
            .commit()

        activity.findViewById<Button>(R.id.start_button).text shouldBe "Stop stalking"
    }

    @Test
    fun `debounced existence check enables delete when the server has data`() {
        val server = MockWebServer()
        server.start()
        val productionApi = ApiClient.api
        ApiClient.api = ApiClient.buildApi(server.url("/").toString())

        try {
            server.enqueue(MockResponse(code = 200))
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.findViewById<EditText>(R.id.nameEditText).setText("Alice")

            idleUntil { app.appPrefs().getBoolean(APP_PREF_DATA_EXISTS, false) }

            activity.findViewById<Button>(R.id.delete_button).isEnabled shouldBe true
            app.appPrefs().getString(APP_PREF_LAST_CHECKED_NAME, null) shouldBe "Alice"
        } finally {
            ApiClient.api = productionApi
            runCatching { server.close() }
        }
    }

    @Test
    fun `clicking delete calls the API and disables the button on success`() {
        val server = MockWebServer()
        server.start()
        val productionApi = ApiClient.api
        ApiClient.api = ApiClient.buildApi(server.url("/").toString())

        try {
            app
                .appPrefs()
                .edit()
                .putString(APP_PREF_USER_NAME, "Alice")
                .putString(APP_PREF_LAST_CHECKED_NAME, "Alice")
                .putBoolean(APP_PREF_DATA_EXISTS, true)
                .apply()
            server.enqueue(MockResponse(code = 200))
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.findViewById<Button>(R.id.delete_button).performClick()

            idleUntil { !app.appPrefs().getBoolean(APP_PREF_DATA_EXISTS, true) }

            activity.findViewById<Button>(R.id.delete_button).isEnabled shouldBe false
            ShadowToast.getTextOfLatestToast() shouldBe "Deleted data"
        } finally {
            ApiClient.api = productionApi
            runCatching { server.close() }
        }
    }

    @Test
    fun `clicking delete keeps data marked as existing on API failure`() {
        val server = MockWebServer()
        server.start()
        val productionApi = ApiClient.api
        ApiClient.api = ApiClient.buildApi(server.url("/").toString())

        try {
            app
                .appPrefs()
                .edit()
                .putString(APP_PREF_USER_NAME, "Alice")
                .putString(APP_PREF_LAST_CHECKED_NAME, "Alice")
                .putBoolean(APP_PREF_DATA_EXISTS, true)
                .apply()
            server.enqueue(MockResponse(code = 500))
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.findViewById<Button>(R.id.delete_button).performClick()

            idleUntil { ShadowToast.getTextOfLatestToast() != null }

            ShadowToast.getTextOfLatestToast() shouldBe "Delete failed"
            app.appPrefs().getBoolean(APP_PREF_DATA_EXISTS, false) shouldBe true
            activity.findViewById<Button>(R.id.delete_button).isEnabled shouldBe true
        } finally {
            ApiClient.api = productionApi
            runCatching { server.close() }
        }
    }
}
