import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.diffplug.spotless") version "8.0.0"
}

android {
    namespace = "se.araisan.stalk.app"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "se.araisan.stalk.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

spotless {
    kotlin {
        // Apply the Ktlint formatting rules — you can specify the version
        ktlint("1.7.1") // Ktlint version
        target("**/*.kt") // Target all Kotlin files
        // You can exclude certain files if needed:
        // targetExclude("build/**/*.kt", "src/main/generated/**/*.kt")
    }

    kotlinGradle {
        // For formatting Kotlin code in Gradle build files
        ktlint("1.7.1")
        target("**/*.gradle.kts")
    }
}

kotlin {
    jvmToolchain(11)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
