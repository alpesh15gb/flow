package `in`.cartunez.flow.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import `in`.cartunez.flow.data.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PartyWithBalance(
    val party: Party,
    val outstanding: Double,
    val slipCount: Int,
    val totalSlipAmount: Double,
    val totalCollected: Double
)

class SlipsViewModel(
    private val repo: SlipsRepository,
    private val txRepo: TransactionRepository
) : ViewModel() {

    // Party list with live balances
    private val _parties = MutableLiveData<List<PartyWithBalance>>()
    val parties: LiveData<List<PartyWithBalance>> = _parties

    // Currently selected party
    private val _selectedPartyId = MutableLiveData<String?>()
    val selectedPartySlips: LiveData<List<Slip>> = _selectedPartyId.switchMap { id ->
        if (id == null) MutableLiveData(emptyList())
        else repo.observeSlipsByParty(id).asLiveData()
    }
    val selectedPartyId: LiveData<String?> = _selectedPartyId

    // Monthly report for a selected party
    private val _monthlyReport = MutableLiveData<List<SlipsRepository.MonthlyPartyReport>>()
    val monthlyReport: LiveData<List<SlipsRepository.MonthlyPartyReport>> = _monthlyReport

    init {
        viewModelScope.launch {
            combine(
                repo.observeParties(),
                repo.observePartyStats(),
                repo.observeAllCollectionTotals()
            ) { parties, stats, collected ->
                val statsMap = stats.associateBy { it.partyId }
                val collMap = collected.associateBy { it.partyId }
                parties.map { party ->
                    val s = statsMap[party.id]
                    PartyWithBalance(
                        party = party,
                        outstanding = s?.outstanding ?: 0.0,
                        slipCount = s?.pendingCount ?: 0,
                        totalSlipAmount = s?.totalAmount ?: 0.0,
                        totalCollected = collMap[party.id]?.totalCollected ?: 0.0
                    )
                }
            }.collect { _parties.value = it }
        }
    }

    fun refreshParties() = viewModelScope.launch {
        // Balances are now derived reactively from combined flows in init
    }

    fun setParty(partyId: String?) { _selectedPartyId.value = partyId }

    // ── Monthly report ────────────────────────────────────────────────────────

    fun loadMonthlyReport(partyId: String) = viewModelScope.launch {
        _monthlyReport.value = repo.getMonthlyReport(partyId)
    }

    // ── Party management ─────────────────────────────────────────────────────

    fun addParty(name: String) = viewModelScope.launch { repo.addParty(name) }

    fun updateParty(party: Party) = viewModelScope.launch { repo.updateParty(party) }

    fun deleteParty(party: Party) = viewModelScope.launch { repo.deleteParty(party) }

    // ── Slip management ───────────────────────────────────────────────────────

    fun deleteSlip(context: Context, slip: Slip) = viewModelScope.launch {
        repo.deleteSlip(context, slip)
        // If it had a linked purchase tx, delete it too
        slip.linkedTxId?.let { txRepo.delete(it) }
    }

    /**
     * Called from SlipReviewSheet on Approve.
     * Creates a Purchase transaction + saves the slip as APPROVED.
     */
    fun approveSlip(
        context: Context,
        partyName: String,
        imageUri: Uri?,
        slip: Slip
    ) = viewModelScope.launch {
        val savedPath = imageUri?.let { repo.saveImage(context, it, slip.partyId) }
        val slipWithImage = slip.copy(imageUri = savedPath)
        val tx = Transaction(
            amount = slip.amount,
            type   = "purchase",
            note   = "Slip · $partyName",
            date   = slip.date
        )
        txRepo.add(tx)
        repo.saveAndApproveSlip(slipWithImage, tx.id)
    }

    // ── Collection ────────────────────────────────────────────────────────────

    suspend fun getOutstanding(partyId: String): Double = repo.getOutstanding(partyId)

    suspend fun getPendingSlips(partyId: String): List<Slip> =
        repo.observeSlipsByParty(partyId).asLiveData().value
            ?.filter { it.status != SlipStatus.COLLECTED.name }
            ?: emptyList()

    fun recordCollection(partyId: String, amount: Double, date: String, note: String?) =
        viewModelScope.launch {
            repo.recordCollection(partyId, amount, date, note)
        }
}
