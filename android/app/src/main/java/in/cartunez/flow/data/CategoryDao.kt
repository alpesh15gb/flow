package `in`.cartunez.flow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY name ASC")
    suspend fun getByType(type: String): List<Category>

    @Query("SELECT * FROM categories ORDER BY type, name ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)
}
