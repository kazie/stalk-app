package se.araisan.stalk.app

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
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
                        .addHeader("Accept", "application/json, application/problem+json")
                        .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
                        .build()
                chain.proceed(request)
            }.connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    // Relied upon (also the library default): unknown speed/heading are omitted, not sent as null.
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    internal fun buildApi(baseUrl: String): StalkApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl.let { if (it.endsWith("/")) it else "$it/" })
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(StalkApi::class.java)

    internal var api: StalkApi = buildApi(BuildConfig.SERVER_URL)

    // 2xx means data exists and 404 means none; anything else is an error, not "no data".
    suspend fun checkUserHasData(name: String): ApiResult<Boolean> =
        try {
            val response = api.checkUserHasData(name)
            Log.d(TAG, "GET exists? code=${response.code()}")
            when {
                response.isSuccessful -> ApiResult.Success(true)
                response.code() == HTTP_NOT_FOUND -> ApiResult.Success(false)
                else -> httpError(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkUserHasData error", e)
            ApiResult.NetworkError(e)
        }

    suspend fun deleteUserData(name: String): ApiResult<Unit> =
        try {
            val response = api.deleteUserData(name)
            Log.d(TAG, "DELETE code=${response.code()}")
            // 404 means already deleted
            if (response.isSuccessful || response.code() == HTTP_NOT_FOUND) {
                ApiResult.Success(Unit)
            } else {
                httpError(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteUserData error", e)
            ApiResult.NetworkError(e)
        }

    suspend fun postLocation(
        name: String,
        latitude: Double,
        longitude: Double,
        speed: Double? = null,
        heading: Double? = null,
    ): ApiResult<Unit> =
        try {
            val response = api.postLocation(LocationPayload(name, latitude, longitude, speed, heading))
            Log.d(TAG, "POST location code=${response.code()}")
            if (response.isSuccessful) ApiResult.Success(Unit) else httpError(response)
        } catch (e: Exception) {
            Log.e(TAG, "postLocation error", e)
            ApiResult.NetworkError(e)
        }

    private fun httpError(response: Response<*>): ApiResult.HttpError {
        val problem = parseProblem(response)
        Log.w(TAG, "HTTP ${response.code()}: ${problem?.detail} ${problem?.errors.orEmpty()}")
        return ApiResult.HttpError(response.code(), problem)
    }

    // Null when the body is missing or not a problem document (e.g. an HTML page from a proxy).
    internal fun parseProblem(response: Response<*>): ProblemDetails? =
        runCatching {
            response.errorBody()?.string()?.let { json.decodeFromString(ProblemDetails.serializer(), it) }
        }.getOrNull()
}
