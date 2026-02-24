package `in`.cartunez.flow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.FragmentPartyDetailBinding

class PartyDetailFragment : Fragment() {

    private var _binding: FragmentPartyDetailBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: SlipsViewModel by activityViewModels {
        SlipsViewModelFactory(
            app.slipsRepository,
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore)
        )
    }

    companion object {
        private const val ARG_PARTY_ID   = "partyId"
        private const val ARG_PARTY_NAME = "partyName"

        fun newInstance(partyId: String, partyName: String) = PartyDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARTY_ID, partyId)
                putString(ARG_PARTY_NAME, partyName)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val partyId   = arguments?.getString(ARG_PARTY_ID)   ?: return
        val partyName = arguments?.getString(ARG_PARTY_NAME) ?: "Party"

        binding.tvPartyName.text = partyName
        viewModel.setParty(partyId)

        val adapter = SlipAdapter(
            onLongClick = { slip ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete slip?")
                    .setMessage("₹${String.format("%,.0f", slip.amount)} on ${slip.date}")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteSlip(requireContext(), slip)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvSlips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSlips.adapter = adapter

        viewModel.selectedPartySlips.observe(viewLifecycleOwner) { slips ->
            adapter.submitList(slips)
            binding.tvEmpty.visibility = if (slips.isEmpty()) View.VISIBLE else View.GONE

            // Update outstanding badge
            val outstanding = slips
                .filter { it.status != `in`.cartunez.flow.data.SlipStatus.COLLECTED.name }
                .sumOf { it.amount - it.amountPaid }
            if (outstanding > 0) {
                binding.tvOutstanding.text = "₹${String.format("%,.0f", outstanding)} due"
                binding.tvOutstanding.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            } else {
                binding.tvOutstanding.text = "Settled ✓"
                binding.tvOutstanding.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            }
        }

        binding.btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        binding.btnAddSlip.setOnClickListener {
            SlipReviewSheet.newInstance(null, partyId).show(parentFragmentManager, "slip_review")
        }

        binding.btnRecordCollection.setOnClickListener {
            RecordCollectionSheet.newInstance(partyId, partyName)
                .show(parentFragmentManager, "record_collection")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
