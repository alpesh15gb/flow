package `in`.cartunez.flow.ui

import androidx.lifecycle.*
import `in`.cartunez.flow.data.*
import `in`.cartunez.flow.network.ApiService
import `in`.cartunez.flow.network.AuthRequest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainViewModel(
    private val repo: TransactionRepository,
    private val slipsRepo: SlipsRepository,
    private val prefs: PrefsStore
) : ViewModel() {

    // "daily" or "monthly"
    private val _range = MutableLiveData("daily")

    val transactions = repo.observeAll().asLiveData()
    val recentTransactions = repo.observeRecent(5).asLiveData()

    private val _summary = MutableLiveData<Summary>()
    val summary: LiveData<Summary> = _summary

    private val _syncing = MutableLiveData(false)
    val syncing: LiveData<Boolean> = _syncing

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

private val _weeklyData = MutableLiveData<List<Pair<String, Double>>>()
    val weeklyData: LiveData<List<Pair<String, Double>>> = _weeklyData

    private val _txReport = MutableLiveData<List<TransactionRepository.MonthReport>>()
    val txReport: LiveData<List<TransactionRepository.MonthReport>> = _txReport

    private val _txAllTime = MutableLiveData<List<TypeCountSum>>()
    val txAllTime: LiveData<List<TypeCountSum>> = _txAllTime

    init {
        checkAuth()
        refreshSummary()
        refreshWeekly()
    }

    private fun checkAuth() = viewModelScope.launch {
        if (prefs.getToken() != null) {
            _authState.value = AuthState.Authenticated
            sync() // Auto-sync on every app start
        } else {
            _authState.value = AuthState.NeedsAuth
        }
    }

    fun authenticate(api: ApiService) = viewModelScope.launch {
        val deviceId = prefs.getDeviceId()
        val resp = runCatching { api.auth(AuthRequest(deviceId, null)) }.getOrNull()
        if (resp?.isSuccessful == true) {
            val body = resp.body()!!
            prefs.saveAuth(body.token, body.user_id)
            _authState.value = AuthState.Authenticated
            sync() // Auto-sync immediately after first auth
        } else {
            _authState.value = AuthState.Error("Running offline — could not reach server.")
        }
    }

    fun setRange(range: String) {
        _range.value = range
        refreshSummary()
    }

    fun refreshSummary() = viewModelScope.launch {
        val today = LocalDate.now()
        _summary.value = if (_range.value == "monthly") {
            repo.monthlySummary(today)
        } else {
            repo.dailySummary(today)
        }
    }

    fun refreshWeekly() = viewModelScope.launch {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dayFmt = DateTimeFormatter.ofPattern("EEE")
        val today = LocalDate.now()

        // Build a map date→net for the last 7 days
        val txs = repo.getLastNDays(7)
        val netByDate = mutableMapOf<String, Double>()
        for (tx in txs) {
            val net = when (tx.type) {
                "sale"     ->  tx.amount
                "expense"  -> -tx.amount
                "purchase" -> -tx.amount
                else       ->  0.0
            }
            netByDate[tx.date] = (netByDate[tx.date] ?: 0.0) + net
        }

        val result = (6 downTo 0).map { daysAgo ->
            val d = today.minusDays(daysAgo.toLong())
            val label = d.format(dayFmt)
            val net = netByDate[d.format(fmt)] ?: 0.0
            label to net
        }
        _weeklyData.value = result
    }

    fun addTransaction(tx: `in`.cartunez.flow.data.Transaction) = viewModelScope.launch {
        repo.add(tx)
        refreshSummary()
        refreshWeekly()
        sync()
    }

    fun updateTransaction(tx: `in`.cartunez.flow.data.Transaction) = viewModelScope.launch {
        repo.update(tx)
        refreshSummary()
        refreshWeekly()
        sync()
    }

    fun deleteTransaction(id: String) = viewModelScope.launch {
        repo.delete(id)
        refreshSummary()
        refreshWeekly()
    }

    fun loadTransactionReport() = viewModelScope.launch {
        _txReport.value = repo.getMonthlyReport()
        _txAllTime.value = repo.getAllTimeSummary()
    }

    fun sync() = viewModelScope.launch {
        _syncing.value = true
        slipsRepo.sync()
        repo.sync()
        refreshSummary()
        refreshWeekly()
        _syncing.value = false
    }
}

sealed class AuthState {
    object Authenticated : AuthState()
    object NeedsAuth     : AuthState()
    data class Error(val msg: String) : AuthState()
}
