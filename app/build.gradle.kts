import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    id("com.diffplug.spotless") version "8.10.3"
    alias(libs.plugins.kover)
}

android {
    namespace = "se.araisan.stalk.app"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "se.araisan.stalk.app"
        minSdk = 34
        targetSdk = 37
        versionCode = 5
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SERVER_URL",
            // Need to make it have it escaped for code, so making "$result"
            "\"${System.getenv("SERVER_URL") ?: "http://localhost:8080/api/coords"}\"",
        )
        buildConfigField(
            "String",
            "API_KEY",
            // Need to make it have it escaped for code, so making "$result"
            "\"${System.getenv("API_KEY") ?: "INVALID_API_KEY"}\"",
        )
    }

    signingConfigs {
        create("release") {
            keyAlias = "stalk-app"
            // Fix on release
            keyPassword = ""
            // Fix on release
            storeFile = File("stalk-app-release-key.jks")
            // Fix on release
            storePassword = ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.location)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.car.app)
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.car.app.testing)
    testRuntimeOnly(libs.junit.vintage.engine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockwebserver3)
}

spotless {
    kotlin {
        // Apply the Ktlint formatting rules — you can specify the version
        ktlint("1.8.0") // Ktlint version
        target("**/*.kt") // Target all Kotlin files
        // You can exclude certain files if needed:
        // targetExclude("build/**/*.kt", "src/main/generated/**/*.kt")
    }

    kotlinGradle {
        // For formatting Kotlin code in Gradle build files
        ktlint("1.8.0")
        target("**/*.gradle.kts")
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck", "koverHtmlReportDebug", "koverLogDebug")
}

kover {
    reports {
        filters {
            excludes {
                // kotlinx.serialization @Serializable DTO: only compiler-generated
                // equals/hashCode/toString/copy/component/serializer, no real logic.
                classes("se.araisan.stalk.app.LocationPayload*")
                // Android/view-binding generated code: no logic of ours to test.
                classes("se.araisan.stalk.app.BuildConfig")
                packages("se.araisan.stalk.app.databinding")
            }
        }
    }
}
