package se.araisan.stalk.app

import android.util.Log
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ApiClient {
    private const val TAG = "ApiClient"

    fun checkUserHasData(name: String): Boolean {
        return try {
            val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
            val base = BuildConfig.SERVER_URL.trimEnd('/')
            val url = java.net.URL("$base/$encoded")
            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${BuildConfig.API_KEY}")
                connectTimeout = 10_000
                readTimeout = 10_000
                val code = responseCode
                Log.d(TAG, "GET exists? code=$code")
                when (code) {
                    HttpURLConnection.HTTP_OK -> true
                    HttpURLConnection.HTTP_NOT_FOUND -> false
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkUserHasData error", e)
            false
        }
    }

    fun deleteUserData(name: String): Boolean {
        return try {
            val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
            val base = BuildConfig.SERVER_URL.trimEnd('/')
            val url = java.net.URL("$base/$encoded")
            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "DELETE"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${BuildConfig.API_KEY}")
                connectTimeout = 10_000
                readTimeout = 10_000
                val code = responseCode
                Log.d(TAG, "DELETE code=$code")
                when (code) {
                    HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_NOT_FOUND -> true
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteUserData error", e)
            false
        }
    }

    fun postLocation(name: String, latitude: Double, longitude: Double): Boolean {
        return try {
            val payload = """
            {
                "name": "$name",
                "latitude": $latitude,
                "longitude": $longitude
            }
            """.trimIndent()
            val base = BuildConfig.SERVER_URL.trimEnd('/')
            // For POST we hit the base endpoint (no /{name}) as per existing implementation
            val url = java.net.URL(base)
            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${BuildConfig.API_KEY}")
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true

                outputStream.use {
                    it.write(payload.toByteArray())
                    it.flush()
                }

                val code = responseCode
                Log.d(TAG, "POST location code=$code")
                code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED
            }
        } catch (e: Exception) {
            Log.e(TAG, "postLocation error", e)
            false
        }
    }
}
