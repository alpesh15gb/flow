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

// Slip sync DTOs
data class RemoteParty(val id: String, val name: String, val created_at: Long?)
data class RemoteSlip(
    val id: String, val party_id: String,
    val amount: Double, val amount_paid: Double,
    val date: String, val status: String,
    val linked_tx_id: String?, val note: String?,
    val created_at: Long?
)
data class RemoteCollection(
    val id: String, val party_id: String,
    val amount_paid: Double, val date: String,
    val note: String?, val created_at: Long?
)
data class SlipsPushRequest(
    val parties: List<RemoteParty>,
    val slips: List<RemoteSlip>,
    val collections: List<RemoteCollection>
)
data class SlipsPushResponse(val ok: Boolean)
data class SlipsPullResponse(
    val parties: List<RemoteParty>,
    val slips: List<RemoteSlip>,
    val collections: List<RemoteCollection>,
    val server_time: String
)

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

    @POST("slips/push")
    suspend fun pushSlips(
        @Header("Authorization") bearer: String,
        @Body body: SlipsPushRequest
    ): Response<SlipsPushResponse>

    @GET("slips/pull")
    suspend fun pullSlips(
        @Header("Authorization") bearer: String,
        @Query("since") since: String?
    ): Response<SlipsPullResponse>
}
