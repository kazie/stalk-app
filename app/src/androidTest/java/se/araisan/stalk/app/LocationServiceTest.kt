package se.araisan.stalk.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationServiceTest {

    @Test
    fun service_start_setsRunningFlag_and_stopAction_clearsFlag() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Bring app to foreground to avoid background start restrictions
        ActivityScenario.launch(MainActivity::class.java)

        // Start the service
        appContext.startService(Intent(appContext, LocationService::class.java))

        // Give the service a moment to initialize and set the flag
        Thread.sleep(500)
        assertTrue(prefs.getBoolean(APP_PREF_SERVICE_RUNNING, false))

        // Send stop action via the service intent
        val stopIntent = Intent(appContext, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }
        appContext.startService(stopIntent)

        // Give it time to stop and persist state
        Thread.sleep(500)
        assertFalse(prefs.getBoolean(APP_PREF_SERVICE_RUNNING, true))
    }
}
