package `in`.cartunez.flow.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Transaction
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.FragmentHomeBinding
import `in`.cartunez.flow.sms.SmsReceiver
import `in`.cartunez.flow.notification.FlowNotificationListener
import java.time.LocalDate
import java.time.LocalTime

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore),
            app.prefsStore
        )
    }

    private lateinit var recentAdapter: TransactionAdapter

    // BroadcastReceiver for SMS + WhatsApp suggestions
    private val suggestionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val amount = intent.getDoubleExtra("amount", -1.0)
            val type   = intent.getStringExtra("type") ?: return
            val note   = intent.getStringExtra("note") ?: ""
            val source = if (intent.action == SmsReceiver.ACTION_SMS_PARSED) "SMS" else "WhatsApp"
            if (amount > 0) viewModel.suggestTransaction(PendingTransaction(amount, type, note, source))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupGreeting()
        setupRecyclerView()
        setupToggle()
        setupButtons()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(SmsReceiver.ACTION_SMS_PARSED)
            addAction(FlowNotificationListener.ACTION_WA_PARSED)
        }
        requireContext().registerReceiver(suggestionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        runCatching { requireContext().unregisterReceiver(suggestionReceiver) }
    }

    private fun setupGreeting() {
        val hour = LocalTime.now().hour
        binding.tvGreeting.text = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else      -> "Good evening"
        }
    }

    private fun setupRecyclerView() {
        recentAdapter = TransactionAdapter()
        binding.rvRecent.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
        }
    }

    private fun setupToggle() {
        binding.toggleRange.check(R.id.btnToday)
        binding.toggleRange.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setRange(if (checkedId == R.id.btnToday) "daily" else "monthly")
        }
    }

    private fun setupButtons() {
        binding.btnAddSale.setOnClickListener     { showAddDialog("sale") }
        binding.btnAddExpense.setOnClickListener  { showAddDialog("expense") }
        binding.btnAddPurchase.setOnClickListener { showAddDialog("purchase") }
        binding.btnSync.setOnClickListener        { viewModel.sync() }
        binding.tvSeeAll.setOnClickListener {
            (requireActivity() as MainActivity).showHistory()
        }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Authenticated -> {}
                is AuthState.NeedsAuth     -> viewModel.authenticate(app.apiService)
                is AuthState.Error         -> Toast.makeText(requireContext(), state.msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.summary.observe(viewLifecycleOwner) { s ->
            binding.cardSales.tvLabel.text     = "SALES"
            binding.cardSales.tvAmount.text    = fmt(s.sales)
            binding.cardSales.tvIcon.text      = "↑"
            binding.cardSales.tvIcon.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.type_sale_bg)
            binding.cardSales.tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))

            binding.cardExpenses.tvLabel.text  = "EXPENSES"
            binding.cardExpenses.tvAmount.text = fmt(s.expenses)
            binding.cardExpenses.tvIcon.text   = "↓"
            binding.cardExpenses.tvIcon.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.type_expense_bg)
            binding.cardExpenses.tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))

            binding.cardPurchases.tvLabel.text  = "PURCHASES"
            binding.cardPurchases.tvAmount.text = fmt(s.purchases)
            binding.cardPurchases.tvIcon.text   = "⬤"
            binding.cardPurchases.tvIcon.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.type_purchase_bg)
            binding.cardPurchases.tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue))

            binding.cardProfit.tvLabel.text  = "PROFIT"
            binding.cardProfit.tvAmount.text = fmt(s.profit)
            binding.cardProfit.tvIcon.text   = "₹"
            val profitColor = if (s.profit >= 0) R.color.green else R.color.red
            binding.cardProfit.tvAmount.setTextColor(ContextCompat.getColor(requireContext(), profitColor))
        }

        viewModel.recentTransactions.observe(viewLifecycleOwner) { list ->
            recentAdapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.syncing.observe(viewLifecycleOwner) { syncing ->
            binding.progressSync.visibility = if (syncing) View.VISIBLE else View.GONE
            binding.btnSync.isEnabled = !syncing
        }

        viewModel.pendingTx.observe(viewLifecycleOwner) { pending ->
            pending ?: return@observe
            AlertDialog.Builder(requireContext())
                .setTitle("${pending.source} — Auto Detected")
                .setMessage(
                    "${pending.type.replaceFirstChar { it.uppercase() }}: ₹${String.format("%,.0f", pending.amount)}" +
                    "\n${pending.note}\n\nSave this transaction?"
                )
                .setPositiveButton("Save")    { _, _ -> viewModel.acceptPending() }
                .setNegativeButton("Dismiss") { _, _ -> viewModel.dismissPending() }
                .setCancelable(false)
                .show()
        }
    }

    private fun showAddDialog(type: String) {
        val dialog = AddTransactionDialog.newInstance(type)
        dialog.onSave = { amount, note, date ->
            viewModel.addTransaction(
                Transaction(amount = amount, type = type, note = note.ifBlank { null }, date = date)
            )
        }
        dialog.show(parentFragmentManager, "add_tx")
    }

    private fun fmt(v: Double): String {
        val abs = Math.abs(v)
        return (if (v < 0) "-" else "") + "₹" + String.format("%,.0f", abs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
