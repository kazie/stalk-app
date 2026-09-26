package se.araisan.stalk.app.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestCarContext
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StalkCarAppServiceTest {
    private fun service(debuggable: Boolean): StalkCarAppService {
        val service = Robolectric.buildService(StalkCarAppService::class.java).create().get()
        service.applicationInfo.flags =
            if (debuggable) {
                service.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
            } else {
                service.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            }
        return service
    }

    @Test
    fun `debug builds accept any car host`() {
        service(debuggable = true).createHostValidator() shouldBe HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    @Test
    fun `release builds only accept allowlisted car hosts`() {
        val validator = service(debuggable = false).createHostValidator()

        validator shouldNotBe HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        validator.allowedHosts.keys.shouldNotBeEmpty()
    }

    @Test
    fun `session opens the stalk screen`() {
        val session = service(debuggable = true).onCreateSession()
        val carContext = TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
        SessionController(session, carContext, Intent()).moveToState(Lifecycle.State.CREATED)

        session.onCreateScreen(Intent()).shouldBeInstanceOf<StalkScreen>()
    }
}
