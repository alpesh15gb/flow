package `in`.cartunez.flow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class PartyCollectedSum(val partyId: String, val totalCollected: Double)

data class MonthlyCollectionStat(val month: String, val totalCollected: Double)

@Dao
interface SlipCollectionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: SlipCollection)

    @Query("SELECT * FROM slip_collections WHERE partyId = :partyId ORDER BY date DESC")
    fun observeByParty(partyId: String): Flow<List<SlipCollection>>

    @Query("SELECT * FROM slip_collections WHERE synced = 0")
    suspend fun getUnsynced(): List<SlipCollection>

    @Query("UPDATE slip_collections SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT partyId, SUM(amountPaid) AS totalCollected FROM slip_collections GROUP BY partyId")
    fun observeAllCollectionTotals(): Flow<List<PartyCollectedSum>>

    @Query("""
        SELECT substr(date, 1, 7) AS month, SUM(amountPaid) AS totalCollected
        FROM slip_collections WHERE partyId = :partyId
        GROUP BY substr(date, 1, 7) ORDER BY month DESC
    """)
    suspend fun getMonthlyCollections(partyId: String): List<MonthlyCollectionStat>
}
