package `in`.cartunez.flow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import `in`.cartunez.flow.data.PrefsStore
import `in`.cartunez.flow.data.TransactionRepository

class MainViewModelFactory(
    private val repo: TransactionRepository,
    private val prefs: PrefsStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(repo, prefs) as T
    }
}
