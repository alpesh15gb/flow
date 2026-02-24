package `in`.cartunez.flow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import `in`.cartunez.flow.data.SlipsRepository
import `in`.cartunez.flow.data.TransactionRepository

class SlipsViewModelFactory(
    private val slipsRepo: SlipsRepository,
    private val txRepo: TransactionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SlipsViewModel(slipsRepo, txRepo) as T
    }
}
