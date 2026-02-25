package `in`.cartunez.flow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class PartyStats(
    val partyId: String,
    val outstanding: Double,
    val pendingCount: Int,
    val totalAmount: Double,
    val totalSlips: Int
)

data class MonthlySlipStat(
    val month: String,
    val slipCount: Int,
    val totalAmount: Double,
    val collectedAmount: Double
)

@Dao
interface SlipDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(slip: Slip): Long

    @Update
    suspend fun update(slip: Slip)

    @Delete
    suspend fun delete(slip: Slip)

    @Query("SELECT * FROM slips WHERE partyId = :partyId ORDER BY date ASC, createdAt ASC")
    fun observeByParty(partyId: String): Flow<List<Slip>>

    @Query("SELECT * FROM slips WHERE partyId = :partyId ORDER BY date ASC, createdAt ASC")
    suspend fun getByParty(partyId: String): List<Slip>

    @Query("SELECT * FROM slips WHERE partyId = :partyId AND status != 'COLLECTED' ORDER BY date ASC, createdAt ASC")
    suspend fun getPendingByParty(partyId: String): List<Slip>

    @Query("SELECT * FROM slips ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Slip>>

    @Query("SELECT * FROM slips WHERE synced = 0")
    suspend fun getUnsynced(): List<Slip>

    @Query("UPDATE slips SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("""
        SELECT partyId,
               SUM(CASE WHEN status != 'COLLECTED' THEN amount - amountPaid ELSE 0.0 END) AS outstanding,
               SUM(CASE WHEN status != 'COLLECTED' THEN 1 ELSE 0 END) AS pendingCount,
               SUM(amount) AS totalAmount,
               COUNT(*) AS totalSlips
        FROM slips GROUP BY partyId
    """)
    fun observePartyStats(): Flow<List<PartyStats>>

    @Query("""
        SELECT substr(date, 1, 7) AS month,
               COUNT(*) AS slipCount,
               SUM(amount) AS totalAmount,
               SUM(amountPaid) AS collectedAmount
        FROM slips WHERE partyId = :partyId
        GROUP BY substr(date, 1, 7) ORDER BY month DESC
    """)
    suspend fun getMonthlyStats(partyId: String): List<MonthlySlipStat>
}
