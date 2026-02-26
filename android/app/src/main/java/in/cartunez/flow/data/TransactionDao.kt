package `in`.cartunez.flow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txs: List<Transaction>)

    @Update
    suspend fun update(tx: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC LIMIT 100")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC, createdAt DESC LIMIT 100")
    fun observeByType(type: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date = :date ORDER BY createdAt DESC")
    fun observeByDate(date: String): Flow<List<Transaction>>

    @Query("SELECT type, SUM(amount) as total FROM transactions WHERE date = :date GROUP BY type")
    suspend fun dailySummary(date: String): List<TypeSum>

    @Query("SELECT type, SUM(amount) as total FROM transactions WHERE date LIKE :monthPrefix || '%' GROUP BY type")
    suspend fun monthlySummary(monthPrefix: String): List<TypeSum>

    @Query("SELECT * FROM transactions WHERE date >= :from ORDER BY date ASC, createdAt ASC")
    suspend fun getFrom(from: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE synced = 0")
    suspend fun getUnsynced(): List<Transaction>

    @Query("UPDATE transactions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("""
        SELECT substr(date, 1, 7) AS month, type,
               SUM(amount) AS total, COUNT(*) AS count
        FROM transactions
        GROUP BY substr(date, 1, 7), type ORDER BY month DESC
    """)
    suspend fun getMonthlyBreakdown(): List<MonthlyTypeStat>

    @Query("SELECT type, SUM(amount) AS total, COUNT(*) AS count FROM transactions GROUP BY type")
    suspend fun getAllTimeSummary(): List<TypeCountSum>

    @Query("""
        SELECT type, COALESCE(category, 'Uncategorized') AS category, SUM(amount) AS total, COUNT(*) AS count
        FROM transactions WHERE date = :date
        GROUP BY type, category ORDER BY type, total DESC
    """)
    suspend fun dailyCategoryBreakdown(date: String): List<TypeCategorySum>

    @Query("""
        SELECT type, COALESCE(category, 'Uncategorized') AS category, SUM(amount) AS total, COUNT(*) AS count
        FROM transactions WHERE date LIKE :monthPrefix || '%'
        GROUP BY type, category ORDER BY type, total DESC
    """)
    suspend fun monthlyCategoryBreakdown(monthPrefix: String): List<TypeCategorySum>
}

data class TypeSum(val type: String, val total: Double)

data class MonthlyTypeStat(val month: String, val type: String, val total: Double, val count: Int)

data class TypeCountSum(val type: String, val total: Double, val count: Int)

data class TypeCategorySum(val type: String, val category: String, val total: Double, val count: Int)
