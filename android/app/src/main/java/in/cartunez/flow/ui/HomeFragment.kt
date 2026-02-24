package `in`.cartunez.flow.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Summary
import `in`.cartunez.flow.data.Transaction
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.FragmentHomeBinding
import `in`.cartunez.flow.notification.FlowNotificationListener
import `in`.cartunez.flow.sms.SmsReceiver
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
    private var activeTab = "daily"

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
        setupTabs()
        setupButtons()
        observeViewModel()
        // Fade in
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setDuration(250).start()
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
            itemAnimator = null
        }
    }

    private fun setupTabs() {
        binding.tabToday.setOnClickListener { if (activeTab != "daily") setTab("daily") }
        binding.tabMonth.setOnClickListener { if (activeTab != "monthly") setTab("monthly") }
    }

    private fun setTab(tab: String) {
        activeTab = tab
        binding.tabToday.apply {
            background = ContextCompat.getDrawable(requireContext(),
                if (tab == "daily") R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
            setTextColor(ContextCompat.getColor(requireContext(),
                if (tab == "daily") R.color.textPrimary else R.color.textSecondary))
            setTypeface(null, if (tab == "daily") android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        binding.tabMonth.apply {
            background = ContextCompat.getDrawable(requireContext(),
                if (tab == "monthly") R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
            setTextColor(ContextCompat.getColor(requireContext(),
                if (tab == "monthly") R.color.textPrimary else R.color.textSecondary))
            setTypeface(null, if (tab == "monthly") android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        binding.tvHeroLabel.text = if (tab == "daily") "TODAY'S PROFIT" else "THIS MONTH'S PROFIT"
        viewModel.setRange(tab)
    }

    private fun setupButtons() {
        binding.btnAddSale.setOnClickListener     { showAddDialog("sale") }
        binding.btnAddExpense.setOnClickListener  { showAddDialog("expense") }
        binding.btnAddPurchase.setOnClickListener { showAddDialog("purchase") }
        binding.btnSync.setOnClickListener {
            binding.btnSync.animate().rotation(binding.btnSync.rotation + 360f).setDuration(500).start()
            viewModel.sync()
        }
        binding.tvSeeAll.setOnClickListener { (requireActivity() as MainActivity).showHistory() }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Authenticated -> {}
                is AuthState.NeedsAuth     -> viewModel.authenticate(app.apiService)
                is AuthState.Error         -> Toast.makeText(requireContext(), state.msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.summary.observe(viewLifecycleOwner) { s -> bindSummary(s) }

        viewModel.recentTransactions.observe(viewLifecycleOwner) { list ->
            recentAdapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.syncing.observe(viewLifecycleOwner) { syncing ->
            binding.progressSync.visibility = if (syncing) View.VISIBLE else View.GONE
        }

        viewModel.pendingTx.observe(viewLifecycleOwner) { pending ->
            pending ?: return@observe
            AlertDialog.Builder(requireContext())
                .setTitle("${pending.source} detected")
                .setMessage(
                    "${pending.type.replaceFirstChar { it.uppercase() }}: ₹${String.format("%,.0f", pending.amount)}\n${pending.note}\n\nSave this transaction?"
                )
                .setPositiveButton("Save")    { _, _ -> viewModel.acceptPending() }
                .setNegativeButton("Dismiss") { _, _ -> viewModel.dismissPending() }
                .setCancelable(false)
                .show()
        }
    }

    private fun bindSummary(s: Summary) {
        // Update hero gradient
        val heroDrawable = if (s.profit >= 0) R.drawable.gradient_hero_profit else R.drawable.gradient_hero_loss
        binding.heroCard.background = ContextCompat.getDrawable(requireContext(), heroDrawable)

        // Animate big profit number
        animateAmount(binding.tvHeroAmount, s.profit)
        animateAmount(binding.tvHeroSales, s.sales)
        animateAmount(binding.tvHeroExpenses, s.expenses)
        animateAmount(binding.tvHeroPurchases, s.purchases)

        // Profit color
        binding.tvHeroAmount.setTextColor(
            ContextCompat.getColor(requireContext(), if (s.profit >= 0) R.color.green else R.color.red)
        )
    }

    private fun animateAmount(tv: TextView, target: Double) {
        ValueAnimator.ofFloat(0f, target.toFloat()).apply {
            duration = 700
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                val v = (it.animatedValue as Float).toDouble()
                tv.text = (if (v < 0) "-" else "") + "₹" + String.format("%,.0f", Math.abs(v))
            }
            start()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
