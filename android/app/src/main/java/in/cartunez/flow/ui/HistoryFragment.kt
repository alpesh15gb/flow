package `in`.cartunez.flow.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.data.Transaction
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore),
            app.prefsStore
        )
    }

    private lateinit var historyAdapter: HistoryAdapter
    private var currentFilter = "all"
    private var allTransactions = listOf<Transaction>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        setupFilters()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter()
        historyAdapter.onLongClick = { tx -> showEditDialog(tx) }
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
        attachSwipeToDelete()
    }

    private fun showEditDialog(tx: Transaction) {
        val dialog = AddTransactionDialog.newInstanceEdit(tx)
        dialog.onSave = { amount, note, date ->
            viewModel.updateTransaction(tx.copy(amount = amount, note = note.ifBlank { null }, date = date))
        }
        dialog.show(parentFragmentManager, "edit_tx")
    }

    private fun attachSwipeToDelete() {
        val paint = Paint().apply { color = Color.parseColor("#FFDC2626") }

        val swipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                // Only allow swipe on transaction items, not headers
                return if (historyAdapter.getTransactionAt(vh.adapterPosition) != null)
                    ItemTouchHelper.LEFT else 0
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val tx = historyAdapter.getTransactionAt(vh.adapterPosition) ?: return
                viewModel.deleteTransaction(tx.id)
                Snackbar.make(binding.root, "Deleted ${tx.type}: ₹${String.format("%,.0f", tx.amount)}", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        viewModel.addTransaction(tx)
                    }.show()
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = vh.itemView
                    c.drawRect(
                        itemView.right.toFloat() + dX, itemView.top.toFloat(),
                        itemView.right.toFloat(), itemView.bottom.toFloat(), paint
                    )
                    // Delete label
                    val textPaint = Paint().apply {
                        color = Color.WHITE; textSize = 36f; textAlign = Paint.Align.RIGHT
                    }
                    c.drawText(
                        "Delete",
                        itemView.right.toFloat() - 32f,
                        itemView.top + (itemView.height / 2f) + 12f,
                        textPaint
                    )
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        }
        ItemTouchHelper(swipe).attachToRecyclerView(binding.rvHistory)
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener      { applyFilter("all") }
        binding.chipSale.setOnClickListener     { applyFilter("sale") }
        binding.chipExpense.setOnClickListener  { applyFilter("expense") }
        binding.chipPurchase.setOnClickListener { applyFilter("purchase") }
    }

    private fun applyFilter(type: String) {
        currentFilter = type
        binding.chipAll.isChecked      = (type == "all")
        binding.chipSale.isChecked     = (type == "sale")
        binding.chipExpense.isChecked  = (type == "expense")
        binding.chipPurchase.isChecked = (type == "purchase")
        submitFiltered()
    }

    private fun submitFiltered() {
        val filtered = if (currentFilter == "all") allTransactions
                       else allTransactions.filter { it.type == currentFilter }
        historyAdapter.submitList(HistoryAdapter.buildList(filtered))
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.tvCount.text = "${filtered.size} entries"
    }

    private fun observeViewModel() {
        viewModel.transactions.observe(viewLifecycleOwner) { list ->
            allTransactions = list
            submitFiltered()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
