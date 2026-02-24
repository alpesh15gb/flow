package `in`.cartunez.flow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SlipCollectionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: SlipCollection)

    @Query("SELECT * FROM slip_collections WHERE partyId = :partyId ORDER BY date DESC")
    fun observeByParty(partyId: String): Flow<List<SlipCollection>>
}
