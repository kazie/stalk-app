package se.araisan.stalk.app

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method
import java.time.Duration

class AsDurationTest {

    private lateinit var asDurationMethod: Method

    @Before
    fun setUp() {
        // Kotlin top-level functions are compiled into a class named <FileName>Kt
        val clazz = Class.forName("se.araisan.stalk.app.LocationServiceKt")
        // private extension function String.asDuration() becomes a static method asDuration(String)
        asDurationMethod = clazz.getDeclaredMethod("asDuration", String::class.java)
        asDurationMethod.isAccessible = true
    }

    private fun callAsDuration(input: String): Duration {
        val result = asDurationMethod.invoke(null, input)
        return result as Duration
    }

    @Test
    fun `maps 1s to 1 second`() {
        assertEquals(Duration.ofSeconds(1), callAsDuration("1s"))
    }

    @Test
    fun `maps 5s to 5 seconds`() {
        assertEquals(Duration.ofSeconds(5), callAsDuration("5s"))
    }

    @Test
    fun `maps 10s to 10 seconds`() {
        assertEquals(Duration.ofSeconds(10), callAsDuration("10s"))
    }

    @Test
    fun `maps 30s to 30 seconds`() {
        assertEquals(Duration.ofSeconds(30), callAsDuration("30s"))
    }

    @Test
    fun `defaults to 10 seconds for unknown values`() {
        assertEquals(Duration.ofSeconds(10), callAsDuration("abc"))
        assertEquals(Duration.ofSeconds(10), callAsDuration(""))
        assertEquals(Duration.ofSeconds(10), callAsDuration("60s"))
    }
}
