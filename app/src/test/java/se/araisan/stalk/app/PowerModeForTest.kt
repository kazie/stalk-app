package se.araisan.stalk.app

import com.google.android.gms.location.Priority
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class PowerModeForTest :
    FunSpec({
        test("uses high accuracy for frequencies under 30 seconds") {
            powerModeFor(Duration.ofSeconds(1)) shouldBe Priority.PRIORITY_HIGH_ACCURACY
            powerModeFor(Duration.ofSeconds(10)) shouldBe Priority.PRIORITY_HIGH_ACCURACY
            powerModeFor(Duration.ofSeconds(29)) shouldBe Priority.PRIORITY_HIGH_ACCURACY
        }

        test("uses balanced power accuracy for 30 seconds and above") {
            powerModeFor(Duration.ofSeconds(30)) shouldBe Priority.PRIORITY_BALANCED_POWER_ACCURACY
            powerModeFor(Duration.ofMinutes(5)) shouldBe Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
    })
