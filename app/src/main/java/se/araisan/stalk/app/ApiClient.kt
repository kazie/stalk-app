package se.araisan.stalk.app

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    private const val TIMEOUT_SECONDS = 10L

    private val okHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val request =
                    chain
                        .request()
                        .newBuilder()
                        .addHeader("Accept", "application/json")
                        .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
                        .build()
                chain.proceed(request)
            }.connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private val json = Json { ignoreUnknownKeys = true }

    internal fun buildApi(baseUrl: String): StalkApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl.let { if (it.endsWith("/")) it else "$it/" })
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(StalkApi::class.java)

    internal var api: StalkApi = buildApi(BuildConfig.SERVER_URL)

    suspend fun checkUserHasData(name: String): Boolean =
        try {
            val response = api.checkUserHasData(name)
            Log.d(TAG, "GET exists? code=${response.code()}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "checkUserHasData error", e)
            false
        }

    suspend fun deleteUserData(name: String): Boolean =
        try {
            val response = api.deleteUserData(name)
            Log.d(TAG, "DELETE code=${response.code()}")
            response.isSuccessful || response.code() == HTTP_NOT_FOUND
        } catch (e: Exception) {
            Log.e(TAG, "deleteUserData error", e)
            false
        }

    suspend fun postLocation(
        name: String,
        latitude: Double,
        longitude: Double,
    ): Boolean =
        try {
            val response = api.postLocation(LocationPayload(name, latitude, longitude))
            Log.d(TAG, "POST location code=${response.code()}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "postLocation error", e)
            false
        }
}
