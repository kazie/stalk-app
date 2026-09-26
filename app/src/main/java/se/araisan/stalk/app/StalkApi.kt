package se.araisan.stalk.app

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class LocationPayload(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

// The base URL is the server origin; the resource paths live here. POST targets the
// collection endpoint (no trailing slash), GET/DELETE address a single record by name.
interface StalkApi {
    @GET("api/coords/{name}")
    suspend fun checkUserHasData(
        @Path("name") name: String,
    ): Response<Unit>

    @DELETE("api/coords/{name}")
    suspend fun deleteUserData(
        @Path("name") name: String,
    ): Response<Unit>

    @POST("api/coords")
    suspend fun postLocation(
        @Body location: LocationPayload,
    ): Response<Unit>
}
