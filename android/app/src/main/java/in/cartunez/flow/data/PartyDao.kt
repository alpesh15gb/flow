package `in`.cartunez.flow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(party: Party)

    @Update
    suspend fun update(party: Party)

    @Delete
    suspend fun delete(party: Party)

    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun observeAll(): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun getById(id: String): Party?

    @Query("SELECT * FROM parties WHERE synced = 0")
    suspend fun getUnsynced(): List<Party>

    @Query("UPDATE parties SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
