package `in`.cartunez.flow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        viewModel.loadTransactionReport()
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
