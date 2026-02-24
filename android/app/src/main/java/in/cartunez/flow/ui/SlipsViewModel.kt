package `in`.cartunez.flow.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import `in`.cartunez.flow.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PartyWithBalance(val party: Party, val outstanding: Double, val slipCount: Int)

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

    init {
        repo.observeParties().asLiveData().observeForever { parties ->
            viewModelScope.launch {
                _parties.value = parties.map { party ->
                    val slips = repo.observeSlipsByParty(party.id).asLiveData().value
                        ?: emptyList()
                    val pending = slips.filter { it.status != SlipStatus.COLLECTED.name }
                    PartyWithBalance(
                        party = party,
                        outstanding = pending.sumOf { it.amount - it.amountPaid },
                        slipCount = pending.size
                    )
                }
            }
        }
        refreshParties()
    }

    fun refreshParties() = viewModelScope.launch {
        // Force parties LiveData to re-emit with up-to-date balances
        val allSlips = mutableMapOf<String, MutableList<Slip>>()
        // Balances are derived from observeSlipsByParty — handled by observeForever above
    }

    fun setParty(partyId: String?) { _selectedPartyId.value = partyId }

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
        // Save image to internal storage
        val savedPath = imageUri?.let { repo.saveImage(context, it, slip.partyId) }

        // Create a Purchase transaction
        val tx = Transaction(
            amount = slip.amount,
            type   = "purchase",
            note   = "Slip · $partyName",
            date   = slip.date
        )
        txRepo.add(tx)

        // Save slip as APPROVED
        repo.approveSlip(slip.copy(imageUri = savedPath), tx.id)
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
