package se.araisan.stalk.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

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

        test("checkUserHasData returns true on 200 and hits GET /{name} with auth header") {
            server.enqueue(MockResponse(code = 200))

            ApiClient.checkUserHasData("Alice") shouldBe true

            val recorded = server.takeRequest()
            recorded.method shouldBe "GET"
            recorded.url.encodedPath shouldBe "/Alice"
            recorded.headers["Authorization"] shouldBe "Bearer ${BuildConfig.API_KEY}"
        }

        test("checkUserHasData returns true on any other 2xx, e.g. 204") {
            server.enqueue(MockResponse(code = 204))

            ApiClient.checkUserHasData("Alice") shouldBe true
        }

        test("checkUserHasData returns false on 404") {
            server.enqueue(MockResponse(code = 404))

            ApiClient.checkUserHasData("Alice") shouldBe false
        }

        test("checkUserHasData returns false when the server is unreachable") {
            server.close()

            ApiClient.checkUserHasData("Alice") shouldBe false
        }

        test("deleteUserData returns true on 200") {
            server.enqueue(MockResponse(code = 200))

            ApiClient.deleteUserData("Bob") shouldBe true
            server.takeRequest().method shouldBe "DELETE"
        }

        test("deleteUserData returns true on 204") {
            server.enqueue(MockResponse(code = 204))

            ApiClient.deleteUserData("Bob") shouldBe true
        }

        test("deleteUserData returns true on 404, treated as already deleted") {
            server.enqueue(MockResponse(code = 404))

            ApiClient.deleteUserData("Bob") shouldBe true
        }

        test("deleteUserData returns false on 500") {
            server.enqueue(MockResponse(code = 500))

            ApiClient.deleteUserData("Bob") shouldBe false
        }

        test("postLocation sends the JSON payload and returns true on 200") {
            server.enqueue(MockResponse(code = 200))

            ApiClient.postLocation("Carol", 12.34, 56.78) shouldBe true

            val recorded = server.takeRequest()
            recorded.method shouldBe "POST"
            recorded.url.encodedPath shouldBe "/"
            val body = recorded.body?.utf8().orEmpty()
            body shouldContain "\"name\":\"Carol\""
            body shouldContain "\"latitude\":12.34"
            body shouldContain "\"longitude\":56.78"
        }

        test("postLocation returns true on 201") {
            server.enqueue(MockResponse(code = 201))

            ApiClient.postLocation("Carol", 0.0, 0.0) shouldBe true
        }

        test("postLocation returns false on 500") {
            server.enqueue(MockResponse(code = 500))

            ApiClient.postLocation("Carol", 0.0, 0.0) shouldBe false
        }
    })
