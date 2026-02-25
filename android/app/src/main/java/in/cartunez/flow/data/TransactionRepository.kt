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
    fun observeRecent(limit: Int = 5): Flow<List<Transaction>> = dao.observeRecent(limit)
    fun observeByType(type: String): Flow<List<Transaction>> = dao.observeByType(type)

    suspend fun add(tx: Transaction): Boolean = dao.insert(tx) != -1L

    suspend fun update(tx: Transaction) = dao.update(tx)

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun getLastNDays(n: Int): List<Transaction> {
        val from = LocalDate.now().minusDays((n - 1).toLong()).format(fmt)
        return dao.getFrom(from)
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

    data class MonthReport(
        val month: String,
        val displayMonth: String,
        val sales: Double, val salesCount: Int,
        val expenses: Double, val expenseCount: Int,
        val purchases: Double, val purchaseCount: Int,
        val netProfit: Double
    )

    suspend fun getMonthlyReport(): List<MonthReport> {
        val rows = dao.getMonthlyBreakdown()
        return rows.groupBy { it.month }.entries
            .sortedByDescending { it.key }
            .map { (month, stats) ->
                val s = stats.associateBy { it.type }
                val sales = s["sale"]?.total ?: 0.0; val sc = s["sale"]?.count ?: 0
                val expenses = s["expense"]?.total ?: 0.0; val ec = s["expense"]?.count ?: 0
                val purchases = s["purchase"]?.total ?: 0.0; val pc = s["purchase"]?.count ?: 0
                MonthReport(month, formatYearMonth(month), sales, sc, expenses, ec, purchases, pc,
                    sales - expenses - purchases)
            }
    }

    suspend fun getAllTimeSummary(): List<TypeCountSum> = dao.getAllTimeSummary()

    private fun formatYearMonth(ym: String): String = try {
        val parts = ym.split("-")
        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        "${months[parts[1].toInt() - 1]} ${parts[0]}"
    } catch (e: Exception) { ym }

    suspend fun sync(): SyncResult {
        val token   = prefs.getToken() ?: return SyncResult.NoAuth
        val bearer  = "Bearer $token"
        val deviceId = prefs.getDeviceId()

        val unsynced = dao.getUnsynced()
        if (unsynced.isNotEmpty()) {
            val remote = unsynced.map {
                RemoteTransaction(it.id, it.amount, it.type, it.note, it.date, deviceId, null, null)
            }
            val resp = runCatching { api.push(bearer, PushRequest(remote)) }.getOrNull()
            if (resp?.isSuccessful == true) dao.markSynced(unsynced.map { it.id })
        }

        val since = prefs.getLastSync()
        val pull  = runCatching { api.pull(bearer, since) }.getOrNull()
        if (pull?.isSuccessful == true) {
            val body = pull.body()!!
            val incoming = body.transactions.map {
                Transaction(id = it.id, amount = it.amount, type = it.type,
                    note = it.note, date = it.date, synced = true)
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
