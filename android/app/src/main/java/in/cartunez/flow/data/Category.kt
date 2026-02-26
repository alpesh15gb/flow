package `in`.cartunez.flow.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String,           // expense, sale, purchase
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

object DefaultCategories {
    val expense = listOf("Tea", "Petrol", "Salary", "Rent", "Office Supplies", "Travel", "Food", "Utilities", "Misc")
    val sale = listOf("Retail", "Wholesale", "Service", "Online", "Misc")
    val purchase = listOf("Raw Material", "Stock", "Equipment", "Misc")

    fun forType(type: String): List<String> = when (type) {
        "expense" -> expense
        "sale" -> sale
        "purchase" -> purchase
        else -> emptyList()
    }
}
