package `in`.cartunez.flow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.data.SlipsRepository
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.FragmentPartyMonthlyReportBinding
import `in`.cartunez.flow.databinding.ItemMonthlyReportBinding

class PartyMonthlyReportFragment : Fragment() {

    private var _binding: FragmentPartyMonthlyReportBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: SlipsViewModel by activityViewModels {
        SlipsViewModelFactory(
            app.slipsRepository,
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore)
        )
    }

    private var partyId = ""
    private var partyName = ""

    companion object {
        fun newInstance(partyId: String, partyName: String) =
            PartyMonthlyReportFragment().apply {
                arguments = Bundle().apply {
                    putString("partyId", partyId)
                    putString("partyName", partyName)
                }
            }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyMonthlyReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        partyId   = arguments?.getString("partyId") ?: ""
        partyName = arguments?.getString("partyName") ?: ""

        binding.tvPartyName.text = partyName
        binding.btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val adapter = MonthlyReportAdapter()
        binding.rvMonths.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMonths.adapter = adapter

        viewModel.monthlyReport.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            val grandTotal = list.sumOf { it.totalBilled }
            binding.tvGrandTotal.text = "₹${String.format("%,.0f", grandTotal)}"
        }

        viewModel.loadMonthlyReport(partyId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MonthlyReportAdapter : ListAdapter<SlipsRepository.MonthlyPartyReport, MonthlyReportAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemMonthlyReportBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SlipsRepository.MonthlyPartyReport) {
            b.tvMonth.text    = item.displayMonth
            b.tvBilled.text   = "₹${String.format("%,.0f", item.totalBilled)}"
            b.tvSlipCount.text = "${item.slipCount} slip${if (item.slipCount != 1) "s" else ""}"
            b.tvCollected.text = "Collected ₹${String.format("%,.0f", item.totalCollected)}"
            if (item.outstanding > 0) {
                b.tvOutstanding.visibility = View.VISIBLE
                b.tvOutstanding.text = "₹${String.format("%,.0f", item.outstanding)} due"
            } else {
                b.tvOutstanding.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMonthlyReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SlipsRepository.MonthlyPartyReport>() {
            override fun areItemsTheSame(a: SlipsRepository.MonthlyPartyReport, b: SlipsRepository.MonthlyPartyReport) = a.month == b.month
            override fun areContentsTheSame(a: SlipsRepository.MonthlyPartyReport, b: SlipsRepository.MonthlyPartyReport) = a == b
        }
    }
}
