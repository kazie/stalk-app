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

interface StalkApi {
    @GET("{name}")
    suspend fun checkUserHasData(
        @Path("name") name: String,
    ): Response<Unit>

    @DELETE("{name}")
    suspend fun deleteUserData(
        @Path("name") name: String,
    ): Response<Unit>

    @POST(".")
    suspend fun postLocation(
        @Body location: LocationPayload,
    ): Response<Unit>
}
