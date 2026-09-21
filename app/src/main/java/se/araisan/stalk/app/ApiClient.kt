package se.araisan.stalk.app

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ApiClient {
    private const val TAG = "ApiClient"
    private const val TIMEOUT_MS = 10_000

    private fun openConnection(
        urlString: String,
        method: String,
    ): HttpURLConnection =
        (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${BuildConfig.API_KEY}")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

    private fun nameUrl(name: String): String {
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
        val base = BuildConfig.SERVER_URL.trimEnd('/')
        return "$base/$encoded"
    }

    fun checkUserHasData(name: String): Boolean =
        try {
            val connection = openConnection(nameUrl(name), "GET")
            try {
                val code = connection.responseCode
                Log.d(TAG, "GET exists? code=$code")
                code == HttpURLConnection.HTTP_OK
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkUserHasData error", e)
            false
        }

    fun deleteUserData(name: String): Boolean =
        try {
            val connection = openConnection(nameUrl(name), "DELETE")
            try {
                val code = connection.responseCode
                Log.d(TAG, "DELETE code=$code")
                code == HttpURLConnection.HTTP_OK ||
                    code == HttpURLConnection.HTTP_NO_CONTENT ||
                    code == HttpURLConnection.HTTP_NOT_FOUND
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteUserData error", e)
            false
        }

    fun postLocation(
        name: String,
        latitude: Double,
        longitude: Double,
    ): Boolean =
        try {
            val payload =
                JSONObject()
                    .put("name", name)
                    .put("latitude", latitude)
                    .put("longitude", longitude)
                    .toString()
            // For POST we hit the base endpoint (no /{name}) as per existing implementation
            val connection = openConnection(BuildConfig.SERVER_URL, "POST")
            try {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(payload.toByteArray())
                    it.flush()
                }

                val code = connection.responseCode
                Log.d(TAG, "POST location code=$code")
                code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "postLocation error", e)
            false
        }
}
