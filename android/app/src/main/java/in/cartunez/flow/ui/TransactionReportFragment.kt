package `in`.cartunez.flow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.data.TypeCategorySum
import `in`.cartunez.flow.databinding.FragmentTransactionReportBinding
import `in`.cartunez.flow.databinding.ItemMonthlyTxReportBinding

class TransactionReportFragment : Fragment() {

    private var _binding: FragmentTransactionReportBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore),
            app.slipsRepository,
            app.prefsStore
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransactionReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val adapter = MonthlyTxReportAdapter()
        binding.rvMonthly.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMonthly.adapter = adapter

        viewModel.txAllTime.observe(viewLifecycleOwner) { list ->
            val m = list.associateBy { it.type }
            val sales = m["sale"]; val exp = m["expense"]; val pur = m["purchase"]
            binding.tvAllTimeSales.text = "₹${String.format("%,.0f", sales?.total ?: 0.0)}"
            binding.tvAllTimeSalesCount.text = "${sales?.count ?: 0} txns"
            binding.tvAllTimeExpenses.text = "₹${String.format("%,.0f", exp?.total ?: 0.0)}"
            binding.tvAllTimeExpensesCount.text = "${exp?.count ?: 0} txns"
            binding.tvAllTimePurchases.text = "₹${String.format("%,.0f", pur?.total ?: 0.0)}"
            binding.tvAllTimePurchasesCount.text = "${pur?.count ?: 0} txns"
        }

        viewModel.txReport.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        val catAdapter = CategoryBreakdownAdapter()
        binding.rvCategory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategory.adapter = catAdapter

        viewModel.categoryBreakdown.observe(viewLifecycleOwner) { list ->
            catAdapter.submitList(list)
        }

        viewModel.loadTransactionReport()
        viewModel.loadCategoryBreakdown("monthly")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MonthlyTxReportAdapter : ListAdapter<TransactionRepository.MonthReport, MonthlyTxReportAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemMonthlyTxReportBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: TransactionRepository.MonthReport) {
            b.tvMonth.text = item.displayMonth
            b.tvNetProfit.text = (if (item.netProfit < 0) "-" else "") +
                "₹${String.format("%,.0f", Math.abs(item.netProfit))}"
            b.tvNetProfit.setTextColor(
                ContextCompat.getColor(b.root.context,
                    if (item.netProfit >= 0) R.color.green else R.color.red)
            )
            b.tvSales.text = "₹${String.format("%,.0f", item.sales)}"
            b.tvSalesCount.text = "${item.salesCount} txns"
            b.tvExpenses.text = "₹${String.format("%,.0f", item.expenses)}"
            b.tvExpensesCount.text = "${item.expenseCount} txns"
            b.tvPurchases.text = "₹${String.format("%,.0f", item.purchases)}"
            b.tvPurchasesCount.text = "${item.purchaseCount} txns"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMonthlyTxReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TransactionRepository.MonthReport>() {
            override fun areItemsTheSame(a: TransactionRepository.MonthReport, b: TransactionRepository.MonthReport) = a.month == b.month
            override fun areContentsTheSame(a: TransactionRepository.MonthReport, b: TransactionRepository.MonthReport) = a == b
        }
    }
}

class CategoryBreakdownAdapter : ListAdapter<TypeCategorySum, CategoryBreakdownAdapter.VH>(DIFF) {

    inner class VH(private val layout: LinearLayout) : RecyclerView.ViewHolder(layout) {
        fun bind(item: TypeCategorySum) {
            val tvType = layout.getChildAt(0) as TextView
            val tvCat  = layout.getChildAt(1) as TextView
            val tvAmt  = layout.getChildAt(2) as TextView

            val color = when (item.type) {
                "sale"     -> R.color.green
                "expense"  -> R.color.red
                "purchase" -> R.color.blue
                else       -> R.color.textSecondary
            }
            tvType.text = item.type.replaceFirstChar { it.uppercase() }
            tvType.setTextColor(ContextCompat.getColor(layout.context, color))
            tvCat.text = item.category
            tvAmt.text = "₹${String.format("%,.0f", item.total)} (${item.count})"
            tvAmt.setTextColor(ContextCompat.getColor(layout.context, color))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
            setPadding(12, 10, 12, 10)
            background = ContextCompat.getDrawable(parent.context, R.drawable.bg_tx_item)
        }
        val tvType = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 12f
        }
        val tvCat = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
            textSize = 13f
            setTextColor(ContextCompat.getColor(parent.context, R.color.textPrimary))
        }
        val tvAmt = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 13f
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }
        layout.addView(tvType)
        layout.addView(tvCat)
        layout.addView(tvAmt)
        return VH(layout)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TypeCategorySum>() {
            override fun areItemsTheSame(a: TypeCategorySum, b: TypeCategorySum) = a.type == b.type && a.category == b.category
            override fun areContentsTheSame(a: TypeCategorySum, b: TypeCategorySum) = a == b
        }
    }
}
