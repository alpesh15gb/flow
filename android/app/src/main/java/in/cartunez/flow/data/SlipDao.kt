package `in`.cartunez.flow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
}
