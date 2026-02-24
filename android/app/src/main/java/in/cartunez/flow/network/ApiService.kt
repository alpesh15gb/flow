package `in`.cartunez.flow.network

import retrofit2.Response
import retrofit2.http.*

data class AuthRequest(val device_id: String, val name: String?)
data class AuthResponse(val token: String, val user_id: String)

data class RemoteTransaction(
    val id: String,
    val amount: Double,
    val type: String,
    val note: String?,
    val date: String,
    val device_id: String?,
    val created_at: String?,
    val updated_at: String?
)

data class PushRequest(val transactions: List<RemoteTransaction>)
data class PushResponse(val synced: Int)
data class PullResponse(val transactions: List<RemoteTransaction>, val server_time: String)

interface ApiService {

    @POST("auth/simple")
    suspend fun auth(@Body body: AuthRequest): Response<AuthResponse>

    @POST("sync/push")
    suspend fun push(
        @Header("Authorization") bearer: String,
        @Body body: PushRequest
    ): Response<PushResponse>

    @GET("sync/pull")
    suspend fun pull(
        @Header("Authorization") bearer: String,
        @Query("since") since: String?
    ): Response<PullResponse>
}
