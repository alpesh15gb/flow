package `in`.cartunez.flow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.FragmentSlipsBinding

class SlipsFragment : Fragment() {

    private var _binding: FragmentSlipsBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: SlipsViewModel by activityViewModels {
        SlipsViewModelFactory(
            app.slipsRepository,
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore)
        )
    }

    /** Called by MainActivity when a share intent arrives */
    var pendingShareUri: android.net.Uri? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSlipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = PartyAdapter { partyWithBalance ->
            viewModel.setParty(partyWithBalance.party.id)
            requireActivity().supportFragmentManager
                .beginTransaction()
                .replace(requireActivity().findViewById<View>(android.R.id.content).id.let {
                    `in`.cartunez.flow.R.id.fragmentContainer
                }, PartyDetailFragment.newInstance(partyWithBalance.party.id, partyWithBalance.party.name))
                .addToBackStack("party_detail")
                .commit()
        }

        binding.rvParties.layoutManager = LinearLayoutManager(requireContext())
        binding.rvParties.adapter = adapter

        viewModel.parties.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnManageParties.setOnClickListener {
            requireActivity().supportFragmentManager
                .beginTransaction()
                .replace(`in`.cartunez.flow.R.id.fragmentContainer, ManagePartiesFragment())
                .addToBackStack("manage_parties")
                .commit()
        }

        // Open slip review if we got a share intent
        pendingShareUri?.let { uri ->
            pendingShareUri = null
            SlipReviewSheet.newInstance(uri).show(parentFragmentManager, "slip_review")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
