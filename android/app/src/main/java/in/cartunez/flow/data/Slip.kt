package `in`.cartunez.flow.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class SlipStatus { REVIEW, APPROVED, PARTIAL, COLLECTED }

@Entity(
    tableName = "slips",
    foreignKeys = [ForeignKey(
        entity = Party::class,
        parentColumns = ["id"],
        childColumns = ["partyId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("partyId")]
)
data class Slip(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val partyId: String,
    val amount: Double,
    val amountPaid: Double = 0.0,
    val date: String,                           // YYYY-MM-DD
    val imageUri: String? = null,               // absolute path in filesDir
    val status: String = SlipStatus.REVIEW.name,
    val linkedTxId: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
