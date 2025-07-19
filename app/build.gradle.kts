plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.diffplug.spotless") version "7.1.0"
}

android {
    namespace = "se.araisan.stalk.app"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "se.araisan.stalk.app"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

spotless {
    kotlin {
        // Apply the Ktlint formatting rules — you can specify the version
        ktlint("1.6.0") // Ktlint version
        target("**/*.kt") // Target all Kotlin files
        // You can exclude certain files if needed:
        // targetExclude("build/**/*.kt", "src/main/generated/**/*.kt")
    }

    kotlinGradle {
        // For formatting Kotlin code in Gradle build files
        ktlint("1.6.0")
        target("**/*.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
