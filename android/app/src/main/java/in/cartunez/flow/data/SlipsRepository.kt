package `in`.cartunez.flow.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate

class SlipsRepository(
    private val partyDao: PartyDao,
    private val slipDao: SlipDao,
    private val collectionDao: SlipCollectionDao
) {

    // ── Parties ──────────────────────────────────────────────────────────────

    fun observeParties(): Flow<List<Party>> = partyDao.observeAll()

    suspend fun addParty(name: String) = partyDao.insert(Party(name = name.trim()))

    suspend fun updateParty(party: Party) = partyDao.update(party)

    suspend fun deleteParty(party: Party) = partyDao.delete(party)

    // ── Slips ─────────────────────────────────────────────────────────────────

    fun observeSlipsByParty(partyId: String): Flow<List<Slip>> =
        slipDao.observeByParty(partyId)

    suspend fun addSlip(slip: Slip) = slipDao.insert(slip)

    suspend fun deleteSlip(context: Context, slip: Slip) {
        slip.imageUri?.let { path -> File(path).takeIf { it.exists() }?.delete() }
        slipDao.delete(slip)
    }

    /** Copy shared image URI into app-internal storage and return the path */
    fun saveImage(context: Context, uri: Uri, partyId: String): String {
        val dir = File(context.filesDir, "slips/$partyId").also { it.mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    }

    /**
     * Approve a slip in REVIEW state:
     * - updates status to APPROVED
     * - saves the linked purchase transaction ID
     */
    suspend fun approveSlip(slip: Slip, txId: String) {
        slipDao.update(slip.copy(status = SlipStatus.APPROVED.name, linkedTxId = txId))
    }

    /**
     * Outstanding = sum of (amount - amountPaid) for non-COLLECTED slips of a party.
     */
    suspend fun getOutstanding(partyId: String): Double =
        slipDao.getPendingByParty(partyId).sumOf { it.amount - it.amountPaid }

    // ── Collections ───────────────────────────────────────────────────────────

    fun observeCollectionsByParty(partyId: String): Flow<List<SlipCollection>> =
        collectionDao.observeByParty(partyId)

    /**
     * Record a payment from a party.
     * Allocates FIFO (oldest slip first), updates amountPaid and status on each slip.
     */
    suspend fun recordCollection(partyId: String, amount: Double, date: String, note: String?) {
        collectionDao.insert(SlipCollection(partyId = partyId, amountPaid = amount, date = date, note = note))

        var remaining = amount
        val pending = slipDao.getPendingByParty(partyId) // already sorted by date ASC
        for (slip in pending) {
            if (remaining <= 0.0) break
            val owed = slip.amount - slip.amountPaid
            val pay  = minOf(owed, remaining)
            remaining -= pay
            val newPaid   = slip.amountPaid + pay
            val newStatus = if (newPaid >= slip.amount) SlipStatus.COLLECTED.name else SlipStatus.PARTIAL.name
            slipDao.update(slip.copy(amountPaid = newPaid, status = newStatus))
        }
    }
}
