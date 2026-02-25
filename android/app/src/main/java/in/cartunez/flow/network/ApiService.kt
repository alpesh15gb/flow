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

// Business health DTOs
data class DailyHealthResponse(
    val sales: Double,
    val expenses: Double,
    val purchases: Double,
    val profit: Double,
    val collections_today: Double,
    val outstanding: Double,
    val overdue: Double,
    val pending_today: Int,
    val sync_status: SyncStatus
)

data class SyncStatus(
    val last_synced: String,
    val pending_unsynced: Int
)

data class MonthlyCloseResponse(
    val sales: Double,
    val expenses: Double,
    val purchases: Double,
    val profit: Double,
    val slips_billed: Double,
    val collections: Double,
    val outstanding_total: Double,
    val collection_rate: Double,
    val dso: Int
)

data class PartyAging(
    val partyId: String,
    val partyName: String,
    val total_outstanding: Double,
    val buckets: AgingBuckets
)

data class AgingBuckets(
    val current: Double,
    val overdue_1_30: Double,
    val overdue_30_60: Double,
    val overdue_60_plus: Double
)

data class PartyAgingResponse(
    val parties: List<PartyAging>
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

    @GET("dashboard/daily-health")
    suspend fun dailyHealth(
        @Header("Authorization") bearer: String,
        @Query("date") date: String?
    ): Response<DailyHealthResponse>

    @GET("dashboard/monthly-close")
    suspend fun monthlyClose(
        @Header("Authorization") bearer: String,
        @Query("month") month: String
    ): Response<MonthlyCloseResponse>

    @GET("dashboard/party-aging")
    suspend fun partyAging(
        @Header("Authorization") bearer: String
    ): Response<PartyAgingResponse>
}
