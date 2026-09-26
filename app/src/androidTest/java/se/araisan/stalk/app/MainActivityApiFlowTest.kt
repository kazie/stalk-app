package se.araisan.stalk.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the parts of MainActivity that actually call ApiClient, by pointing
 * ApiClient at a local MockWebServer instead of the real backend.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityApiFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var productionApi: StalkApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        productionApi = ApiClient.api
        ApiClient.api = ApiClient.buildApi(server.url("/").toString())

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    @After
    fun tearDown() {
        ApiClient.api = productionApi
        runCatching { server.close() }
    }

    @Test
    fun typingAnExistingName_enablesDeleteButtonAfterDebounce() {
        server.enqueue(MockResponse(code = 200))

        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.nameEditText)).perform(typeText("Alice"), closeSoftKeyboard())

        // The existence check is debounced by 500ms in MainActivity.
        Thread.sleep(800)

        onView(withId(R.id.delete_button)).check(matches(isEnabled()))
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/coords/Alice", recorded.url.encodedPath)
    }

    @Test
    fun typingANewName_leavesDeleteButtonDisabledAfterDebounce() {
        server.enqueue(MockResponse(code = 404))

        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.nameEditText)).perform(typeText("Nobody"), closeSoftKeyboard())

        Thread.sleep(800)

        onView(withId(R.id.delete_button)).check(matches(not(isEnabled())))
    }

    @Test
    fun clickingDelete_callsApiAndDisablesTheButtonOnSuccess() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(APP_PREF_USER_NAME, "Alice")
            .putString(APP_PREF_LAST_CHECKED_NAME, "Alice")
            .putBoolean(APP_PREF_DATA_EXISTS, true)
            .apply()
        server.enqueue(MockResponse(code = 200))

        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.delete_button)).perform(click())

        Thread.sleep(500)

        onView(withId(R.id.delete_button)).check(matches(not(isEnabled())))
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun clickingDelete_keepsButtonStateOnFailure() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(APP_PREF_USER_NAME, "Alice")
            .putString(APP_PREF_LAST_CHECKED_NAME, "Alice")
            .putBoolean(APP_PREF_DATA_EXISTS, true)
            .apply()
        server.enqueue(MockResponse(code = 500))

        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.delete_button)).perform(click())

        Thread.sleep(500)

        // Data still marked as existing since the delete failed, so the button re-enables.
        onView(withId(R.id.delete_button)).check(matches(isEnabled()))
    }
}
