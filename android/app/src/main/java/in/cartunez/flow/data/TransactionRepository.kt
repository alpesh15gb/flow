package `in`.cartunez.flow.data

import `in`.cartunez.flow.network.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TransactionRepository(
    private val dao: TransactionDao,
    private val api: ApiService,
    private val prefs: PrefsStore
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun observeAll(): Flow<List<Transaction>> = dao.observeAll()

    fun observeByDate(date: LocalDate): Flow<List<Transaction>> =
        dao.observeByDate(date.format(fmt))

    suspend fun add(tx: Transaction): Boolean {
        return dao.insert(tx) != -1L
    }

    suspend fun dailySummary(date: LocalDate): Summary {
        val sums = dao.dailySummary(date.format(fmt))
        return buildSummary(sums)
    }

    suspend fun monthlySummary(date: LocalDate): Summary {
        val prefix = date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val sums = dao.monthlySummary(prefix)
        return buildSummary(sums)
    }

    private fun buildSummary(sums: List<TypeSum>): Summary {
        var sales = 0.0; var expenses = 0.0; var purchases = 0.0
        for (s in sums) when (s.type) {
            "sale"     -> sales     = s.total
            "expense"  -> expenses  = s.total
            "purchase" -> purchases = s.total
        }
        return Summary(sales, expenses, purchases, sales - expenses - purchases)
    }

    suspend fun sync(): SyncResult {
        val token = prefs.getToken() ?: return SyncResult.NoAuth
        val bearer = "Bearer $token"
        val deviceId = prefs.getDeviceId()

        // Push unsynced
        val unsynced = dao.getUnsynced()
        if (unsynced.isNotEmpty()) {
            val remote = unsynced.map {
                RemoteTransaction(it.id, it.amount, it.type, it.note, it.date, deviceId, null, null)
            }
            val resp = runCatching { api.push(bearer, PushRequest(remote)) }.getOrNull()
            if (resp?.isSuccessful == true) {
                dao.markSynced(unsynced.map { it.id })
            }
        }

        // Pull new from server
        val since = prefs.getLastSync()
        val pull = runCatching { api.pull(bearer, since) }.getOrNull()
        if (pull?.isSuccessful == true) {
            val body = pull.body()!!
            val incoming = body.transactions.map {
                Transaction(
                    id = it.id,
                    amount = it.amount,
                    type = it.type,
                    note = it.note,
                    date = it.date,
                    synced = true
                )
            }
            if (incoming.isNotEmpty()) dao.insertAll(incoming)
            prefs.saveLastSync(body.server_time)
        }

        return SyncResult.Ok
    }
}

data class Summary(val sales: Double, val expenses: Double, val purchases: Double, val profit: Double)

sealed class SyncResult {
    object Ok     : SyncResult()
    object NoAuth : SyncResult()
}
