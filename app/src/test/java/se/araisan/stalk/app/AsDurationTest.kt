package se.araisan.stalk.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Method
import java.time.Duration

class AsDurationTest :
    FunSpec({
        // Kotlin top-level functions are compiled into a class named <FileName>Kt;
        // the private extension function String.asDuration() becomes a static method asDuration(String)
        val asDurationMethod: Method =
            Class
                .forName("se.araisan.stalk.app.LocationServiceKt")
                .getDeclaredMethod("asDuration", String::class.java)
                .apply { isAccessible = true }

        fun callAsDuration(input: String): Duration = asDurationMethod.invoke(null, input) as Duration

        test("maps 1s to 1 second") {
            callAsDuration("1s") shouldBe Duration.ofSeconds(1)
        }

        test("maps 5s to 5 seconds") {
            callAsDuration("5s") shouldBe Duration.ofSeconds(5)
        }

        test("maps 10s to 10 seconds") {
            callAsDuration("10s") shouldBe Duration.ofSeconds(10)
        }

        test("maps 30s to 30 seconds") {
            callAsDuration("30s") shouldBe Duration.ofSeconds(30)
        }

        test("defaults to 10 seconds for unknown values") {
            callAsDuration("abc") shouldBe Duration.ofSeconds(10)
            callAsDuration("") shouldBe Duration.ofSeconds(10)
            callAsDuration("60s") shouldBe Duration.ofSeconds(10)
        }
    })
