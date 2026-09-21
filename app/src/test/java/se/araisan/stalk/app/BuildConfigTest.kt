package se.araisan.stalk.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.string.shouldStartWith

class BuildConfigTest :
    FunSpec({
        test("SERVER_URL and API_KEY are present and sane") {
            BuildConfig.SERVER_URL.shouldStartWith("http")
            BuildConfig.API_KEY.shouldNotBeEmpty()
        }
    })
