package se.araisan.stalk.app.car

import android.Manifest
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestAppManager
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import se.araisan.stalk.app.APP_PREF_DATA_EXISTS
import se.araisan.stalk.app.APP_PREF_LAST_CHECKED_NAME
import se.araisan.stalk.app.APP_PREF_SERVICE_RUNNING
import se.araisan.stalk.app.APP_PREF_STALK_FREQ
import se.araisan.stalk.app.APP_PREF_START_FAILED_AT
import se.araisan.stalk.app.APP_PREF_USER_NAME
import se.araisan.stalk.app.LocationService
import se.araisan.stalk.app.appPrefs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StalkScreenTest {
    private val app = RuntimeEnvironment.getApplication()
    private lateinit var carContext: TestCarContext

    @Before
    fun setUp() {
        app
            .appPrefs()
            .edit()
            .clear()
            .apply()
        LocationService.isRunning = false
        carContext = TestCarContext.createCarContext(app)
    }

    private fun grantPermissions(location: String = Manifest.permission.ACCESS_FINE_LOCATION) {
        shadowOf(app).grantPermissions(location, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun setPrefs(
        name: String,
        running: Boolean = false,
    ) {
        app
            .appPrefs()
            .edit()
            .putString(APP_PREF_USER_NAME, name)
            .putString(APP_PREF_STALK_FREQ, "5s")
            .putBoolean(APP_PREF_SERVICE_RUNNING, running)
            .commit()
        LocationService.isRunning = running
    }

    private fun renderedPane(): PaneTemplate {
        val template = StalkScreen(carContext).onGetTemplate()
        return template.shouldBeInstanceOf<PaneTemplate>()
    }

    @Test
    fun `shows setup message when no name is saved`() {
        grantPermissions()

        StalkScreen(carContext).onGetTemplate().shouldBeInstanceOf<MessageTemplate>()
    }

    @Test
    fun `shows setup message when location permission is missing`() {
        setPrefs("alice")

        StalkScreen(carContext).onGetTemplate().shouldBeInstanceOf<MessageTemplate>()
    }

    @Test
    fun `approximate location is enough to use the car screen`() {
        grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        setPrefs("alice")

        renderedPane()
    }

    @Test
    fun `shows status rows and start action when idle`() {
        grantPermissions()
        setPrefs("alice")

        val pane = renderedPane().pane
        pane.rows.map { it.texts.single().toString() } shouldBe listOf("alice", "5s", "Stopped")
        pane.actions
            .single()
            .title
            .toString() shouldBe "Start stalking"
    }

    @Test
    fun `start action starts the location service`() {
        grantPermissions()
        setPrefs("alice")

        renderedPane().pane.actions.single().onClickDelegate!!.sendClick(
            object : androidx.car.app.OnDoneCallback {},
        )

        shadowOf(app).nextStartedService.component?.className shouldBe LocationService::class.java.name
    }

    @Test
    fun `stop action stops the location service when running`() {
        grantPermissions()
        setPrefs("alice", running = true)

        val pane = renderedPane().pane
        pane.rows
            .last()
            .texts
            .single()
            .toString() shouldBe "Stalking"
        pane.actions
            .single()
            .title
            .toString() shouldBe "Stop stalking"

        pane.actions
            .single()
            .onClickDelegate!!
            .sendClick(object : androidx.car.app.OnDoneCallback {})

        shadowOf(app).nextStoppedService.component?.className shouldBe LocationService::class.java.name
    }

    @Test
    fun `screen re-renders when the running flag changes`() {
        grantPermissions()
        setPrefs("alice")
        val controller = ScreenController(StalkScreen(carContext)).moveToState(Lifecycle.State.RESUMED)
        controller.reset()

        LocationService.isRunning = true
        app
            .appPrefs()
            .edit()
            .putBoolean(APP_PREF_SERVICE_RUNNING, true)
            .commit()

        val latest = controller.templatesReturned.last().shouldBeInstanceOf<PaneTemplate>()
        latest.pane.actions
            .single()
            .title
            .toString() shouldBe "Stop stalking"
    }

    @Test
    fun `stale running flag after process death shows start and is cleared`() {
        grantPermissions()
        setPrefs("alice", running = true)
        LocationService.isRunning = false // process was killed; onDestroy never ran

        val controller = ScreenController(StalkScreen(carContext)).moveToState(Lifecycle.State.RESUMED)

        app.appPrefs().getBoolean(APP_PREF_SERVICE_RUNNING, true) shouldBe false
        val latest = controller.templatesReturned.last().shouldBeInstanceOf<PaneTemplate>()
        latest.pane.actions
            .single()
            .title
            .toString() shouldBe "Start stalking"
    }

    @Test
    fun `screen re-renders when it becomes visible again`() {
        setPrefs("alice")
        val controller = ScreenController(StalkScreen(carContext)).moveToState(Lifecycle.State.RESUMED)
        controller.templatesReturned.last().shouldBeInstanceOf<MessageTemplate>()

        // Permissions granted on the phone while the car screen was in the background.
        controller.moveToState(Lifecycle.State.CREATED)
        grantPermissions()
        controller.reset()
        controller.moveToState(Lifecycle.State.RESUMED)

        controller.templatesReturned.last().shouldBeInstanceOf<PaneTemplate>()
    }

    @Test
    fun `refused start inside the service shows a toast on the car`() {
        grantPermissions()
        setPrefs("alice")
        val screen = StalkScreen(carContext)
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
        (screen.onGetTemplate() as PaneTemplate).pane.actions.single().onClickDelegate!!.sendClick(
            object : androidx.car.app.OnDoneCallback {},
        )

        app
            .appPrefs()
            .edit()
            .putLong(APP_PREF_START_FAILED_AT, 1234L)
            .commit()

        carContext.getCarService(TestAppManager::class.java).toastsShown.map { it.toString() } shouldBe
            listOf("Could not start from the car. Start stalking on your phone.")
    }

    @Test
    fun `refused system restart does not show a toast on the car`() {
        grantPermissions()
        setPrefs("alice")
        ScreenController(StalkScreen(carContext)).moveToState(Lifecycle.State.RESUMED)

        // The driver never tapped Start; the system restarted the service and it was refused.
        app
            .appPrefs()
            .edit()
            .putLong(APP_PREF_START_FAILED_AT, 1234L)
            .commit()

        carContext.getCarService(TestAppManager::class.java).toastsShown shouldBe emptyList()
    }

    @Test
    fun `pending start is forgotten when the screen stops`() {
        grantPermissions()
        setPrefs("alice")
        val screen = StalkScreen(carContext)
        val controller = ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
        (screen.onGetTemplate() as PaneTemplate).pane.actions.single().onClickDelegate!!.sendClick(
            object : androidx.car.app.OnDoneCallback {},
        )

        // Screen goes to the background before the outcome arrives; later a system restart is refused.
        controller.moveToState(Lifecycle.State.CREATED)
        controller.moveToState(Lifecycle.State.RESUMED)
        app
            .appPrefs()
            .edit()
            .putLong(APP_PREF_START_FAILED_AT, 1234L)
            .commit()

        carContext.getCarService(TestAppManager::class.java).toastsShown shouldBe emptyList()
    }

    @Test
    fun `unrelated preference writes do not refresh the car screen`() {
        grantPermissions()
        setPrefs("alice", running = true)
        val controller = ScreenController(StalkScreen(carContext)).moveToState(Lifecycle.State.RESUMED)
        controller.reset()

        // Written by LocationService after every successful location report.
        app
            .appPrefs()
            .edit()
            .putString(APP_PREF_LAST_CHECKED_NAME, "alice")
            .putBoolean(APP_PREF_DATA_EXISTS, true)
            .commit()

        controller.templatesReturned shouldBe emptyList()
    }
}
