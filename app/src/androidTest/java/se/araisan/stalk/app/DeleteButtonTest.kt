package se.araisan.stalk.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeleteButtonTest {
    @Test
    fun deleteButton_isDisabledByDefault() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.delete_button)).check(matches(not(isEnabled())))
    }

    @Test
    fun deleteButton_isEnabled_whenDataExistsAndNotRunning() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        // Seed prefs: name saved and data exists for same name; service not running
        prefs
            .edit()
            .putString(APP_PREF_USER_NAME, "Alice")
            .putString(APP_PREF_LAST_CHECKED_NAME, "Alice")
            .putBoolean(APP_PREF_DATA_EXISTS, true)
            .putBoolean(APP_PREF_SERVICE_RUNNING, false)
            .apply()

        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.delete_button)).check(matches(isEnabled()))
    }

    @Test
    fun deleteButton_isDisabled_whenServiceRunning() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        // Seed prefs: name saved and data exists, but service running -> should be disabled
        prefs
            .edit()
            .putString(APP_PREF_USER_NAME, "Alice")
            .putString(APP_PREF_LAST_CHECKED_NAME, "Alice")
            .putBoolean(APP_PREF_DATA_EXISTS, true)
            .putBoolean(APP_PREF_SERVICE_RUNNING, true)
            .apply()

        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.delete_button)).check(matches(not(isEnabled())))
    }
}
