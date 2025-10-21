package se.araisan.stalk.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun typingName_enablesStartButton_andPersistsName() {
        ActivityScenario.launch(MainActivity::class.java)

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        onView(withId(R.id.nameEditText)).perform(typeText("Alice"), closeSoftKeyboard())

        onView(withId(R.id.start_button)).check(matches(isEnabled()))

        val saved = prefs.getString(APP_PREF_USER_NAME, null)
        assertEquals("Alice", saved)
    }

    @Test
    fun selectingSpinnerValue_persistsFrequency() {
        ActivityScenario.launch(MainActivity::class.java)

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Open spinner
        onView(withId(R.id.intervalSpinner)).perform(click())
        // Select item "5s"
        onData(allOf(`is`("5s"))).perform(click())

        val saved = prefs.getString(APP_PREF_STALK_FREQ, null)
        assertEquals("5s", saved)
    }
}
