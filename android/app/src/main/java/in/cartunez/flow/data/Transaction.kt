package `in`.cartunez.flow.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TxType { sale, expense, purchase }

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val type: String,           // TxType.name
    val note: String? = null,
    val date: String,           // YYYY-MM-DD
    val category: String? = null,
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
