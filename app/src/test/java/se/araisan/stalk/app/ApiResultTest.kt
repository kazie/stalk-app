package se.araisan.stalk.app

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

private fun unauthorized(detail: String) = ApiResult.HttpError(401, ProblemDetails(status = 401, detail = detail))

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiResultTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun `success gives OK`() {
        ApiResult.Success(true).userMessage(app) shouldBe "OK"
    }

    @Test
    fun `network error gives the network error message`() {
        ApiResult.NetworkError(IOException("boom")).userMessage(app) shouldBe "Network error"
    }

    @Test
    fun `401 with a missing token gives the API key missing message`() {
        unauthorized("Missing bearer token").userMessage(app) shouldBe "API key missing"
    }

    @Test
    fun `401 with an invalid token gives the Invalid API key message`() {
        unauthorized("Invalid bearer token").userMessage(app) shouldBe "Invalid API key"
    }

    @Test
    fun `401 without a problem body gives the Invalid API key message`() {
        ApiResult.HttpError(401, null).userMessage(app) shouldBe "Invalid API key"
    }

    @Test
    fun `401 without a detail gives the Invalid API key message`() {
        ApiResult.HttpError(401, ProblemDetails(status = 401)).userMessage(app) shouldBe "Invalid API key"
    }

    @Test
    fun `400 without field errors shows the server detail`() {
        ApiResult.HttpError(400, ProblemDetails(status = 400, detail = "Malformed JSON")).userMessage(app) shouldBe
            "Malformed JSON"
    }

    @Test
    fun `400 with an empty errors list and no detail falls back to the status code`() {
        ApiResult.HttpError(400, ProblemDetails(status = 400, errors = emptyList())).userMessage(app) shouldBe
            "Request failed (400)"
    }

    @Test
    fun `400 with field errors lists each invalid field`() {
        val result =
            ApiResult.HttpError(
                400,
                ProblemDetails(
                    status = 400,
                    detail = "2 fields are invalid",
                    errors =
                        listOf(
                            FieldError("#/latitude", "must be between -90 and 90"),
                            FieldError("#/heading", "must be between 0 and 360"),
                        ),
                ),
            )

        result.userMessage(app) shouldBe
            "Invalid location: latitude must be between -90 and 90; heading must be between 0 and 360"
    }

    @Test
    fun `other errors show the server detail`() {
        val result =
            ApiResult.HttpError(
                500,
                ProblemDetails(status = 500, detail = "The server failed to handle the request; it has been logged"),
            )

        result.userMessage(app) shouldBe "The server failed to handle the request; it has been logged"
    }

    @Test
    fun `errors without a problem body fall back to the status code`() {
        ApiResult.HttpError(502, null).userMessage(app) shouldBe "Request failed (502)"
    }
}
