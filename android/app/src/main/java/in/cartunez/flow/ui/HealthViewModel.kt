package `in`.cartunez.flow.ui

import androidx.lifecycle.*
import `in`.cartunez.flow.network.ApiService
import `in`.cartunez.flow.network.DailyHealthResponse
import `in`.cartunez.flow.network.MonthlyCloseResponse
import `in`.cartunez.flow.network.PartyAgingResponse
import `in`.cartunez.flow.data.PrefsStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class HealthViewModel(
    private val api: ApiService,
    private val prefs: PrefsStore
) : ViewModel() {

    private val _dailyHealth = MutableLiveData<DailyHealthResponse?>()
    val dailyHealth: LiveData<DailyHealthResponse?> = _dailyHealth

    private val _monthlyClose = MutableLiveData<MonthlyCloseResponse?>()
    val monthlyClose: LiveData<MonthlyCloseResponse?> = _monthlyClose

    private val _partyAging = MutableLiveData<PartyAgingResponse?>()
    val partyAging: LiveData<PartyAgingResponse?> = _partyAging

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _selectedDate = MutableLiveData(LocalDate.now())
    val selectedDate: LiveData<LocalDate> = _selectedDate

    private val _selectedMonth = MutableLiveData(YearMonth.now())
    val selectedMonth: LiveData<YearMonth> = _selectedMonth

    fun setDate(date: LocalDate) {
        _selectedDate.value = date
        loadDailyHealth(date)
    }

    fun setMonth(month: YearMonth) {
        _selectedMonth.value = month
        loadMonthlyClose(month)
    }

    fun loadDailyHealth(date: LocalDate = _selectedDate.value ?: LocalDate.now()) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val token = prefs.getToken() ?: return@launch
                val dateStr = date.format(DateTimeFormatter.ISO_DATE)
                val response = api.dailyHealth("Bearer $token", dateStr)
                if (response.isSuccessful) {
                    _dailyHealth.value = response.body()
                    _error.value = null
                } else {
                    _error.value = "Failed to load daily health"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadMonthlyClose(month: YearMonth = _selectedMonth.value ?: YearMonth.now()) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val token = prefs.getToken() ?: return@launch
                val monthStr = month.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                val response = api.monthlyClose("Bearer $token", monthStr)
                if (response.isSuccessful) {
                    _monthlyClose.value = response.body()
                    _error.value = null
                } else {
                    _error.value = "Failed to load monthly close"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadPartyAging() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val token = prefs.getToken() ?: return@launch
                val response = api.partyAging("Bearer $token")
                if (response.isSuccessful) {
                    _partyAging.value = response.body()
                    _error.value = null
                } else {
                    _error.value = "Failed to load party aging"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun refreshAll() {
        loadDailyHealth()
        loadMonthlyClose()
        loadPartyAging()
    }
}

class HealthViewModelFactory(
    private val api: ApiService,
    private val prefs: PrefsStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HealthViewModel(api, prefs) as T
    }
}
