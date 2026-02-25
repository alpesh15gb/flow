package `in`.cartunez.flow.data

import android.content.Context
import android.net.Uri
import `in`.cartunez.flow.network.ApiService
import `in`.cartunez.flow.network.RemoteCollection
import `in`.cartunez.flow.network.RemoteParty
import `in`.cartunez.flow.network.RemoteSlip
import `in`.cartunez.flow.network.SlipsPushRequest
import kotlinx.coroutines.flow.Flow
import java.io.File

class SlipsRepository(
    private val partyDao: PartyDao,
    private val slipDao: SlipDao,
    private val collectionDao: SlipCollectionDao,
    private val api: ApiService,
    private val prefs: PrefsStore
) {

    // ── Parties ──────────────────────────────────────────────────────────────

    fun observeParties(): Flow<List<Party>> = partyDao.observeAll()

    suspend fun addParty(name: String) = partyDao.insert(Party(name = name.trim(), synced = false))

    suspend fun updateParty(party: Party) = partyDao.update(party.copy(synced = false))

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
     * Approve a slip: update status to APPROVED and link the purchase tx.
     * Marks synced = false so the updated record is pushed on next sync.
     */
    suspend fun approveSlip(slip: Slip, txId: String) {
        slipDao.update(slip.copy(status = SlipStatus.APPROVED.name, linkedTxId = txId, synced = false))
    }

    /**
     * Insert the slip (if new) then mark it APPROVED with the linked tx.
     * Uses IGNORE conflict strategy so an existing slip is updated instead.
     */
    suspend fun saveAndApproveSlip(slip: Slip, txId: String) {
        val approved = slip.copy(status = SlipStatus.APPROVED.name, linkedTxId = txId, synced = false)
        val inserted = slipDao.insert(approved)
        if (inserted == -1L) slipDao.update(approved)
    }

    /**
     * Outstanding = sum of (amount - amountPaid) for non-COLLECTED slips of a party.
     */
    suspend fun getOutstanding(partyId: String): Double =
        slipDao.getPendingByParty(partyId).sumOf { it.amount - it.amountPaid }

    fun observePartyStats(): Flow<List<PartyStats>> = slipDao.observePartyStats()

    fun observeAllCollectionTotals(): Flow<List<PartyCollectedSum>> =
        collectionDao.observeAllCollectionTotals()

    data class MonthlyPartyReport(
        val month: String,
        val displayMonth: String,
        val slipCount: Int,
        val totalBilled: Double,
        val totalCollected: Double,
        val outstanding: Double
    )

    suspend fun getMonthlyReport(partyId: String): List<MonthlyPartyReport> {
        val slipStats = slipDao.getMonthlyStats(partyId)
        val collStats = collectionDao.getMonthlyCollections(partyId)
        val collMap = collStats.associateBy { it.month }
        return slipStats.map { s ->
            val collected = collMap[s.month]?.totalCollected ?: 0.0
            MonthlyPartyReport(
                month = s.month,
                displayMonth = formatYearMonth(s.month),
                slipCount = s.slipCount,
                totalBilled = s.totalAmount,
                totalCollected = collected,
                outstanding = s.totalAmount - s.collectedAmount
            )
        }
    }

    private fun formatYearMonth(ym: String): String = try {
        val parts = ym.split("-")
        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        "${months[parts[1].toInt() - 1]} ${parts[0]}"
    } catch (e: Exception) { ym }

    // ── Collections ───────────────────────────────────────────────────────────

    fun observeCollectionsByParty(partyId: String): Flow<List<SlipCollection>> =
        collectionDao.observeByParty(partyId)

    /**
     * Record a payment from a party.
     * Allocates FIFO (oldest slip first), updates amountPaid and status on each slip.
     */
    suspend fun recordCollection(partyId: String, amount: Double, date: String, note: String?) {
        collectionDao.insert(
            SlipCollection(partyId = partyId, amountPaid = amount, date = date, note = note, synced = false)
        )

        var remaining = amount
        val pending = slipDao.getPendingByParty(partyId)
        for (slip in pending) {
            if (remaining <= 0.0) break
            val owed      = slip.amount - slip.amountPaid
            val pay       = minOf(owed, remaining)
            remaining    -= pay
            val newPaid   = slip.amountPaid + pay
            val newStatus = if (newPaid >= slip.amount) SlipStatus.COLLECTED.name else SlipStatus.PARTIAL.name
            slipDao.update(slip.copy(amountPaid = newPaid, status = newStatus, synced = false))
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * Sync slip tracker data with the server.
     * Call this BEFORE TransactionRepository.sync() so both use the same `lastSync` timestamp.
     * TransactionRepository.sync() will update lastSync at the end.
     */
    suspend fun sync() {
        val token = prefs.getToken() ?: return
        val since = prefs.getLastSync()

        // PUSH unsynced records (parties → slips → collections, FK order)
        val unsyncedParties     = partyDao.getUnsynced()
        val unsyncedSlips       = slipDao.getUnsynced()
        val unsyncedCollections = collectionDao.getUnsynced()

        if (unsyncedParties.isNotEmpty() || unsyncedSlips.isNotEmpty() || unsyncedCollections.isNotEmpty()) {
            val body = SlipsPushRequest(
                parties = unsyncedParties.map { RemoteParty(it.id, it.name, it.createdAt) },
                slips   = unsyncedSlips.map {
                    RemoteSlip(it.id, it.partyId, it.amount, it.amountPaid,
                        it.date, it.status, it.linkedTxId, it.note, it.createdAt)
                },
                collections = unsyncedCollections.map {
                    RemoteCollection(it.id, it.partyId, it.amountPaid, it.date, it.note, null)
                }
            )
            runCatching { api.pushSlips("Bearer $token", body) }
                .onSuccess { resp ->
                    if (resp.isSuccessful) {
                        if (unsyncedParties.isNotEmpty())
                            partyDao.markSynced(unsyncedParties.map { it.id })
                        if (unsyncedSlips.isNotEmpty())
                            slipDao.markSynced(unsyncedSlips.map { it.id })
                        if (unsyncedCollections.isNotEmpty())
                            collectionDao.markSynced(unsyncedCollections.map { it.id })
                    }
                }
        }

        // PULL changes from server since last sync
        runCatching { api.pullSlips("Bearer $token", since) }
            .onSuccess { resp ->
                if (!resp.isSuccessful) return@onSuccess
                val body = resp.body() ?: return@onSuccess

                body.parties.forEach { rp ->
                    partyDao.insert(
                        Party(id = rp.id, name = rp.name, synced = true,
                            createdAt = rp.created_at ?: System.currentTimeMillis())
                    )
                }
                body.slips.forEach { rs ->
                    // Insert if new; if it exists locally (IGNORE), update to reflect server state
                    val inserted = slipDao.insert(
                        Slip(id = rs.id, partyId = rs.party_id,
                            amount = rs.amount, amountPaid = rs.amount_paid,
                            date = rs.date, status = rs.status,
                            linkedTxId = rs.linked_tx_id, note = rs.note,
                            synced = true,
                            createdAt = rs.created_at ?: System.currentTimeMillis())
                    )
                    if (inserted == -1L) {
                        // Already exists — update status/amountPaid from server
                        slipDao.getPendingByParty(rs.party_id)
                            .find { it.id == rs.id }
                            ?.let { existing ->
                                slipDao.update(
                                    existing.copy(amount = rs.amount, amountPaid = rs.amount_paid,
                                        status = rs.status, linkedTxId = rs.linked_tx_id,
                                        note = rs.note, synced = true)
                                )
                            }
                    }
                }
                body.collections.forEach { rc ->
                    collectionDao.insert(
                        SlipCollection(id = rc.id, partyId = rc.party_id,
                            amountPaid = rc.amount_paid, date = rc.date,
                            note = rc.note, synced = true)
                    )
                }
                // Note: lastSync is updated by TransactionRepository.sync() after this call
            }
    }
}
