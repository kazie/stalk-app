package se.araisan.stalk.app

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {
    @Test
    fun serverUrl_and_apiKey_arePresent_andSane() {
        assertNotNull(BuildConfig.SERVER_URL)
        assertNotNull(BuildConfig.API_KEY)

        // SERVER_URL should look like a URL
        assertTrue("SERVER_URL should start with http/https", BuildConfig.SERVER_URL.startsWith("http"))
        // API_KEY may be default or provided; ensure non-empty string
        assertTrue("API_KEY should not be empty", BuildConfig.API_KEY.isNotEmpty())
    }
}
