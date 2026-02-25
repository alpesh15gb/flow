package `in`.cartunez.flow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.databinding.FragmentHealthBinding
import `in`.cartunez.flow.network.MonthlyCloseResponse
import `in`.cartunez.flow.network.DailyHealthResponse
import `in`.cartunez.flow.network.PartyAging
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class HealthFragment : Fragment() {

    private var _binding: FragmentHealthBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: HealthViewModel by viewModels {
        HealthViewModelFactory(app.apiService, app.prefsStore)
    }

    private var activeTab = "daily"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupTabs()
        setupObservers()
        viewModel.refreshAll()
    }

    private fun setupTabs() {
        binding.tabDaily.setOnClickListener { if (activeTab != "daily") switchTab("daily") }
        binding.tabMonthly.setOnClickListener { if (activeTab != "monthly") switchTab("monthly") }
        binding.tabAging.setOnClickListener { if (activeTab != "aging") switchTab("aging") }
    }

    private fun switchTab(tab: String) {
        activeTab = tab
        updateTabStyles()
        binding.contentContainer.removeAllViews()

        when (tab) {
            "daily" -> {
                viewModel.loadDailyHealth()
                showDailyView()
            }
            "monthly" -> {
                viewModel.loadMonthlyClose()
                showMonthlyView()
            }
            "aging" -> {
                viewModel.loadPartyAging()
                showAgingView()
            }
        }
    }

    private fun updateTabStyles() {
        val activeColor = ContextCompat.getDrawable(requireContext(), R.drawable.bg_tab_active)
        val inactiveColor = ContextCompat.getDrawable(requireContext(), R.drawable.bg_tab_inactive)
        val activeTx = ContextCompat.getColor(requireContext(), R.color.textPrimary)
        val inactiveTx = ContextCompat.getColor(requireContext(), R.color.textSecondary)

        binding.apply {
            tabDaily.background = if (activeTab == "daily") activeColor else inactiveColor
            tabDaily.setTextColor(if (activeTab == "daily") activeTx else inactiveTx)

            tabMonthly.background = if (activeTab == "monthly") activeColor else inactiveColor
            tabMonthly.setTextColor(if (activeTab == "monthly") activeTx else inactiveTx)

            tabAging.background = if (activeTab == "aging") activeColor else inactiveColor
            tabAging.setTextColor(if (activeTab == "aging") activeTx else inactiveTx)
        }
    }

    private fun showDailyView() {
        viewModel.dailyHealth.observe(viewLifecycleOwner) { health ->
            if (health != null) {
                binding.contentContainer.removeAllViews()
                binding.contentContainer.addView(buildDailyCard(health))
            }
        }
    }

    private fun showMonthlyView() {
        viewModel.monthlyClose.observe(viewLifecycleOwner) { close ->
            if (close != null) {
                binding.contentContainer.removeAllViews()
                binding.contentContainer.addView(buildMonthlyCard(close))
            }
        }
    }

    private fun showAgingView() {
        viewModel.partyAging.observe(viewLifecycleOwner) { aging ->
            if (aging != null) {
                binding.contentContainer.removeAllViews()
                binding.contentContainer.addView(buildAgingTable(aging.parties))
            }
        }
    }

    private fun buildDailyCard(health: DailyHealthResponse): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            // P&L Summary
            addView(TextView(requireContext()).apply {
                text = "TODAY'S PROFIT"
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                setPadding(0, 0, 0, 8)
            })

            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface))
                setPadding(16, 16, 16, 16)
                addView(TextView(requireContext()).apply {
                    text = "₹${String.format("%,.0f", health.profit)}"
                    textSize = 28f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(if (health.profit >= 0) ContextCompat.getColor(requireContext(), R.color.green) else ContextCompat.getColor(requireContext(), R.color.red))
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            // Collections
            addView(TextView(requireContext()).apply {
                text = "COLLECTIONS & OUTSTANDING"
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                setPadding(0, 16, 0, 8)
            })

            addView(buildMetricRow("Collected Today", "₹${String.format("%,.0f", health.collections_today)}"))
            addView(buildMetricRow("Outstanding", "₹${String.format("%,.0f", health.outstanding)}"))
            addView(buildMetricRow("Overdue", "₹${String.format("%,.0f", health.overdue)}", R.color.red))
        }
    }

    private fun buildMonthlyCard(close: MonthlyCloseResponse): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(requireContext()).apply {
                text = "MONTHLY P&L"
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                setPadding(0, 0, 0, 8)
            })

            addView(buildMetricRow("Sales", "₹${String.format("%,.0f", close.sales)}", R.color.green))
            addView(buildMetricRow("Expenses", "-₹${String.format("%,.0f", close.expenses)}", R.color.red))
            addView(buildMetricRow("Purchases", "-₹${String.format("%,.0f", close.purchases)}", R.color.blue))
            addView(buildMetricRow("Profit", "₹${String.format("%,.0f", close.profit)}", if (close.profit >= 0) R.color.green else R.color.red))

            addView(TextView(requireContext()).apply {
                text = "COLLECTIONS & BILLING"
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                setPadding(0, 16, 0, 8)
            })

            addView(buildMetricRow("Billed", "₹${String.format("%,.0f", close.slips_billed)}"))
            addView(buildMetricRow("Collected", "₹${String.format("%,.0f", close.collections)}"))
            addView(buildMetricRow("Collection Rate", "${String.format("%.0f", close.collection_rate * 100)}%"))
            addView(buildMetricRow("Days Outstanding", "${close.dso} days"))
        }
    }

    private fun buildAgingTable(parties: List<PartyAging>): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(requireContext()).apply {
                text = "PAYMENT AGING"
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                setPadding(0, 0, 0, 8)
            })

            if (parties.isEmpty()) {
                addView(TextView(requireContext()).apply {
                    text = "No outstanding payments"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                    gravity = android.view.Gravity.CENTER
                    setPadding(16, 32, 16, 32)
                })
            } else {
                for (party in parties) {
                    if (party.total_outstanding > 0) {
                        addView(buildPartyAgingCard(party))
                    }
                }
            }
        }
    }

    private fun buildPartyAgingCard(party: PartyAging): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface))
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8
            }

            addView(TextView(requireContext()).apply {
                text = party.partyName
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
            })

            addView(buildMetricRow("Total Outstanding", "₹${String.format("%,.0f", party.total_outstanding)}"))
            addView(buildMetricRow("Current", "₹${String.format("%,.0f", party.buckets.current)}", R.color.green))
            if (party.buckets.overdue_1_30 > 0) {
                addView(buildMetricRow("Overdue 1-30 days", "₹${String.format("%,.0f", party.buckets.overdue_1_30)}", R.color.red))
            }
            if (party.buckets.overdue_30_60 > 0) {
                addView(buildMetricRow("Overdue 30-60 days", "₹${String.format("%,.0f", party.buckets.overdue_30_60)}", R.color.red))
            }
            if (party.buckets.overdue_60_plus > 0) {
                addView(buildMetricRow("Overdue 60+ days", "₹${String.format("%,.0f", party.buckets.overdue_60_plus)}", R.color.red))
            }
        }
    }

    private fun buildMetricRow(label: String, value: String, colorRes: Int = R.color.textPrimary): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 4, 0, 4)

            addView(TextView(requireContext()).apply {
                text = label
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(TextView(requireContext()).apply {
                text = value
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            })
        }
    }

    private fun setupObservers() {
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
