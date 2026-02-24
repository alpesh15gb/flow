package `in`.cartunez.flow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
}
