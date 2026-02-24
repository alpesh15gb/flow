package `in`.cartunez.flow.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "slip_collections",
    foreignKeys = [ForeignKey(
        entity = Party::class,
        parentColumns = ["id"],
        childColumns = ["partyId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("partyId")]
)
data class SlipCollection(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val partyId: String,
    val amountPaid: Double,
    val date: String,
    val note: String? = null
)
