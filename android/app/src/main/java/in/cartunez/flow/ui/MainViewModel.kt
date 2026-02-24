package `in`.cartunez.flow.ui

import androidx.lifecycle.*
import `in`.cartunez.flow.data.*
import `in`.cartunez.flow.network.AuthRequest
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(
    private val repo: TransactionRepository,
    private val prefs: PrefsStore
) : ViewModel() {

    private val _today = MutableLiveData(LocalDate.now())
    val today: LiveData<LocalDate> = _today

    val transactions = repo.observeAll().asLiveData()

    private val _summary = MutableLiveData<Summary>()
    val summary: LiveData<Summary> = _summary

    private val _syncing = MutableLiveData(false)
    val syncing: LiveData<Boolean> = _syncing

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    // SMS / WA suggestion pending confirmation
    private val _pendingTx = MutableLiveData<PendingTransaction?>()
    val pendingTx: LiveData<PendingTransaction?> = _pendingTx

    init {
        checkAuth()
        refreshSummary()
    }

    private fun checkAuth() = viewModelScope.launch {
        _authState.value = if (prefs.getToken() != null) AuthState.Authenticated else AuthState.NeedsAuth
    }

    fun authenticate(api: `in`.cartunez.flow.network.ApiService) = viewModelScope.launch {
        val deviceId = prefs.getDeviceId()
        val resp = runCatching { api.auth(AuthRequest(deviceId, null)) }.getOrNull()
        if (resp?.isSuccessful == true) {
            val body = resp.body()!!
            prefs.saveAuth(body.token, body.user_id)
            _authState.value = AuthState.Authenticated
        } else {
            _authState.value = AuthState.Error("Could not connect to server. Running offline.")
        }
    }

    fun refreshSummary() = viewModelScope.launch {
        _summary.value = repo.dailySummary(LocalDate.now())
    }

    fun addTransaction(tx: Transaction) = viewModelScope.launch {
        repo.add(tx)
        refreshSummary()
    }

    fun suggestTransaction(pending: PendingTransaction) {
        _pendingTx.value = pending
    }

    fun acceptPending() = viewModelScope.launch {
        val p = _pendingTx.value ?: return@launch
        repo.add(
            Transaction(amount = p.amount, type = p.type, note = p.note, date = LocalDate.now().toString())
        )
        _pendingTx.value = null
        refreshSummary()
    }

    fun dismissPending() { _pendingTx.value = null }

    fun sync() = viewModelScope.launch {
        _syncing.value = true
        repo.sync()
        refreshSummary()
        _syncing.value = false
    }
}

sealed class AuthState {
    object Authenticated : AuthState()
    object NeedsAuth     : AuthState()
    data class Error(val msg: String) : AuthState()
}

data class PendingTransaction(
    val amount: Double,
    val type: String,
    val note: String,
    val source: String   // "sms" or "whatsapp"
)
