package se.araisan.stalk.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

private fun problemResponse(
    code: Int,
    body: String,
) = MockResponse
    .Builder()
    .code(code)
    .addHeader("Content-Type", "application/problem+json")
    .body(body)
    .build()

class ApiClientTest :
    FunSpec({
        lateinit var server: MockWebServer
        val productionApi = ApiClient.api

        beforeTest {
            server = MockWebServer()
            server.start()
            ApiClient.api = ApiClient.buildApi(server.url("/").toString())
        }

        afterTest {
            runCatching { server.close() }
            ApiClient.api = productionApi
        }

        test("checkUserHasData returns Success on 200 and hits GET /{name} with auth header") {
            server.enqueue(MockResponse(code = 200))

            ApiClient.checkUserHasData("Alice") shouldBe ApiResult.Success(true)

            val recorded = server.takeRequest()
            recorded.method shouldBe "GET"
            recorded.url.encodedPath shouldBe "/api/coords/Alice"
            recorded.headers["Authorization"] shouldBe "Bearer ${BuildConfig.API_KEY}"
        }

        test("checkUserHasData returns Success on any other 2xx, e.g. 204") {
            server.enqueue(MockResponse(code = 204))

            ApiClient.checkUserHasData("Alice") shouldBe ApiResult.Success(true)
        }

        test("checkUserHasData returns Success(false) on 404") {
            server.enqueue(MockResponse(code = 404))

            ApiClient.checkUserHasData("Alice") shouldBe ApiResult.Success(false)
        }

        test("checkUserHasData returns NetworkError when the server is unreachable") {
            server.close()

            val result = ApiClient.checkUserHasData("Alice")
            result.shouldBeInstanceOf<ApiResult.NetworkError>()
        }

        test("deleteUserData returns Success on 200") {
            server.enqueue(MockResponse(code = 200))

            ApiClient.deleteUserData("Bob") shouldBe ApiResult.Success(Unit)
            server.takeRequest().method shouldBe "DELETE"
        }

        test("deleteUserData returns Success on 204") {
            server.enqueue(MockResponse(code = 204))

            ApiClient.deleteUserData("Bob") shouldBe ApiResult.Success(Unit)
        }

        test("deleteUserData returns Success on 404, treated as already deleted") {
            server.enqueue(MockResponse(code = 404))

            ApiClient.deleteUserData("Bob") shouldBe ApiResult.Success(Unit)
        }

        test("deleteUserData returns HttpError on 500") {
            server.enqueue(MockResponse(code = 500))

            ApiClient.deleteUserData("Bob") shouldBe ApiResult.HttpError(500, null)
        }

        test("postLocation sends the JSON payload and returns Success on 200") {
            server.enqueue(MockResponse(code = 200))

            ApiClient.postLocation("Carol", 12.34, 56.78) shouldBe ApiResult.Success(Unit)

            val recorded = server.takeRequest()
            recorded.method shouldBe "POST"
            // Collection endpoint, no trailing slash (a trailing slash 404s on the server).
            recorded.url.encodedPath shouldBe "/api/coords"
            val body = recorded.body?.utf8().orEmpty()
            body shouldContain "\"name\":\"Carol\""
            body shouldContain "\"latitude\":12.34"
            body shouldContain "\"longitude\":56.78"
        }

        test("postLocation includes speed and heading when given") {
            server.enqueue(MockResponse(code = 200))

            ApiClient.postLocation("Carol", 12.34, 56.78, speed = 8.3, heading = 45.0) shouldBe ApiResult.Success(Unit)

            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            body shouldContain "\"speed\":8.3"
            body shouldContain "\"heading\":45.0"
        }

        test("postLocation omits speed and heading keys entirely when null") {
            server.enqueue(MockResponse(code = 200))

            ApiClient.postLocation("Carol", 12.34, 56.78, speed = null, heading = null) shouldBe ApiResult.Success(Unit)

            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            body shouldBe """{"name":"Carol","latitude":12.34,"longitude":56.78}"""
        }

        test("postLocation returns Success on 201") {
            server.enqueue(MockResponse(code = 201))

            ApiClient.postLocation("Carol", 0.0, 0.0) shouldBe ApiResult.Success(Unit)
        }

        test("postLocation returns HttpError on 500") {
            server.enqueue(MockResponse(code = 500))

            ApiClient.postLocation("Carol", 0.0, 0.0) shouldBe ApiResult.HttpError(500, null)
        }

        test("sends an Accept header that includes problem+json") {
            server.enqueue(MockResponse(code = 200))

            ApiClient.checkUserHasData("Alice")

            server.takeRequest().headers["Accept"] shouldBe "application/json, application/problem+json"
        }

        test("postLocation parses field errors from a 400 problem body") {
            server.enqueue(
                problemResponse(
                    400,
                    """{"type":"about:blank","title":"Bad Request","status":400,"detail":"2 fields are invalid",""" +
                        """"instance":"/api/coords","errors":[{"pointer":"#/latitude","detail":"must be between -90 and 90"},""" +
                        """{"pointer":"#/heading","detail":"must be between 0 and 360"}]}""",
                ),
            )

            val result = ApiClient.postLocation("Carol", 100.0, 0.0, heading = 400.0)

            result.shouldBeInstanceOf<ApiResult.HttpError>()
            result.code shouldBe 400
            result.problem?.errors shouldBe
                listOf(
                    FieldError("#/latitude", "must be between -90 and 90"),
                    FieldError("#/heading", "must be between 0 and 360"),
                )
        }

        test("a 401 with an invalid token gives HttpError with the problem detail") {
            server.enqueue(
                problemResponse(
                    401,
                    """{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Invalid bearer token","instance":"/api/coords"}""",
                ).newBuilder()
                    .addHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"")
                    .build(),
            )

            val result = ApiClient.postLocation("Carol", 0.0, 0.0)

            result.shouldBeInstanceOf<ApiResult.HttpError>()
            result.code shouldBe 401
            result.problem?.detail shouldBe "Invalid bearer token"
        }

        test("a 401 with a missing token gives HttpError with the problem detail") {
            server.enqueue(
                problemResponse(
                    401,
                    """{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Missing bearer token","instance":"/api/coords"}""",
                ),
            )

            val result = ApiClient.deleteUserData("Bob")

            result.shouldBeInstanceOf<ApiResult.HttpError>()
            result.code shouldBe 401
            result.problem?.detail shouldBe "Missing bearer token"
        }

        test("checkUserHasData returns HttpError, not false, on a 500 problem") {
            server.enqueue(
                problemResponse(
                    500,
                    """{"type":"about:blank","title":"Internal Server Error","status":500,""" +
                        """"detail":"The server failed to handle the request; it has been logged"}""",
                ),
            )

            val result = ApiClient.checkUserHasData("Alice")

            result.shouldBeInstanceOf<ApiResult.HttpError>()
            result.problem?.detail shouldBe "The server failed to handle the request; it has been logged"
        }

        test("a 404 problem on GET still means no data") {
            server.enqueue(
                problemResponse(
                    404,
                    """{"type":"about:blank","title":"Not Found","status":404,"detail":"No user named 'kazie'"}""",
                ),
            )

            ApiClient.checkUserHasData("kazie") shouldBe ApiResult.Success(false)
        }

        test("a malformed error body gives HttpError with no problem") {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(502)
                    .addHeader("Content-Type", "text/html")
                    .body("<html>Bad Gateway</html>")
                    .build(),
            )

            val result = ApiClient.deleteUserData("Bob")

            result shouldBe ApiResult.HttpError(502, null)
        }
    })
