package `in`.cartunez.flow.ui

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Summary
import `in`.cartunez.flow.data.Transaction
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.FragmentHomeBinding
import java.time.LocalTime

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore),
            app.slipsRepository,
            app.prefsStore
        )
    }
    private lateinit var recentAdapter: TransactionAdapter
    private var activeTab = "daily"
    private var showSyncToast = false


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupGreeting()
        setupRecyclerView()
        setupTabs()
        setupButtons()
        setupChart()
        observeViewModel()
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setDuration(250).start()
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
        recentAdapter.onClick = { tx -> showEditDialog(tx) }
        binding.rvRecent.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
            itemAnimator = null
            layoutAnimation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_slide_in)
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
        binding.btnAddSale.addPressAnim()
        binding.btnAddSale.setOnClickListener { showAddDialog("sale") }

        binding.btnAddExpense.addPressAnim()
        binding.btnAddExpense.setOnClickListener { showAddDialog("expense") }

        binding.btnAddPurchase.addPressAnim()
        binding.btnAddPurchase.setOnClickListener { showAddDialog("purchase") }

        binding.btnSync.setOnClickListener {
            binding.btnSync.animate().rotation(binding.btnSync.rotation + 360f).setDuration(500).start()
            showSyncToast = true
            viewModel.sync()
        }
        binding.btnSync.setOnLongClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val deviceId = app.prefsStore.getDeviceId()
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Your Device ID")
                    .setMessage("Use this ID to log in on the web dashboard:\n\n$deviceId")
                    .setPositiveButton("Copy") { _, _ ->
                        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Device ID", deviceId))
                        Toast.makeText(requireContext(), "Copied!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
            true
        }
        binding.tvSeeAll.setOnClickListener { (requireActivity() as MainActivity).showHistory() }
    }

    private fun View.addPressAnim() {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(OvershootInterpolator(3f)).start()
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }
            }
            false
        }
    }

    private fun setupChart() {
        binding.chartWeekly.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setScaleEnabled(false)
            setDrawValueAboveBar(false)
            setDrawBarShadow(false)
            setDrawGridBackground(false)
            axisLeft.isEnabled = false
            axisRight.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.textSecondary)
                textSize = 10f
                granularity = 1f
            }
            setExtraOffsets(0f, 0f, 0f, 4f)
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

        viewModel.summary.observe(viewLifecycleOwner) { s -> bindSummary(s) }

        viewModel.recentTransactions.observe(viewLifecycleOwner) { list ->
            recentAdapter.submitList(list)
            binding.rvRecent.scheduleLayoutAnimation()
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.syncing.observe(viewLifecycleOwner) { syncing ->
            binding.progressSync.visibility = if (syncing) View.VISIBLE else View.GONE
        }

        viewModel.syncStatus.observe(viewLifecycleOwner) { status ->
            if (status.isNullOrEmpty()) {
                binding.tvSyncStatus.visibility = View.GONE
            } else {
                binding.tvSyncStatus.text = status
                binding.tvSyncStatus.visibility = View.VISIBLE
                if (showSyncToast) {
                    Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
                    showSyncToast = false
                }
            }
        }

viewModel.weeklyData.observe(viewLifecycleOwner) { data -> bindChart(data) }
    }

    private fun bindSummary(s: Summary) {
        val heroDrawable = when {
            s.profit > 0  -> R.drawable.gradient_hero_profit
            s.profit < 0  -> R.drawable.gradient_hero_loss
            else          -> R.drawable.gradient_hero_neutral
        }
        binding.heroCard.background = ContextCompat.getDrawable(requireContext(), heroDrawable)

        animateAmount(binding.tvHeroAmount, s.profit)
        animateAmount(binding.tvHeroSales, s.sales)
        animateAmount(binding.tvHeroExpenses, s.expenses)
        animateAmount(binding.tvHeroPurchases, s.purchases)

        binding.tvHeroAmount.setTextColor(
            ContextCompat.getColor(requireContext(), if (s.profit >= 0) R.color.green else R.color.red)
        )
    }

    private fun bindChart(data: List<Pair<String, Double>>) {
        if (data.isEmpty()) return
        val green = ContextCompat.getColor(requireContext(), R.color.green)
        val red   = ContextCompat.getColor(requireContext(), R.color.red)

        val entries = data.mapIndexed { i, (_, net) -> BarEntry(i.toFloat(), net.toFloat()) }
        val colors  = data.map { (_, net) -> if (net >= 0) green else red }

        val dataSet = BarDataSet(entries, "").apply {
            setColors(colors)
            setDrawValues(false)
        }
        val barData = BarData(dataSet).apply { barWidth = 0.6f }

        binding.chartWeekly.apply {
            xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.first })
            this.data = barData
            animateY(400)
            invalidate()
        }
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

    private fun showEditDialog(tx: Transaction) {
        val dialog = AddTransactionDialog.newInstanceEdit(tx)
        dialog.onSave = { amount, note, date ->
            viewModel.updateTransaction(tx.copy(amount = amount, note = note.ifBlank { null }, date = date))
        }
        dialog.show(parentFragmentManager, "edit_tx")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
