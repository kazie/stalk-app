package se.araisan.stalk.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class ReportLocationTest :
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

        test("returns true and sends the location on 200") {
            server.enqueue(MockResponse(code = 200))

            reportLocation("Alice", 12.34, 56.78) shouldBe true

            val recorded = server.takeRequest()
            recorded.method shouldBe "POST"
            val body = recorded.body?.utf8().orEmpty()
            body shouldContain "\"name\":\"Alice\""
            body shouldContain "\"latitude\":12.34"
            body shouldContain "\"longitude\":56.78"
        }

        test("returns false on a server error") {
            server.enqueue(MockResponse(code = 500))

            reportLocation("Alice", 0.0, 0.0) shouldBe false
        }

        test("returns false when the server is unreachable") {
            server.close()

            reportLocation("Alice", 0.0, 0.0) shouldBe false
        }
    })
