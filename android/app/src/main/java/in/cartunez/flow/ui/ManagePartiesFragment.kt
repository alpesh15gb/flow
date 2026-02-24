package `in`.cartunez.flow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.data.Party
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.FragmentManagePartiesBinding
import `in`.cartunez.flow.databinding.ItemPartyBinding

class ManagePartiesFragment : Fragment() {

    private var _binding: FragmentManagePartiesBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: SlipsViewModel by activityViewModels {
        SlipsViewModelFactory(
            app.slipsRepository,
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore)
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentManagePartiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = ManagePartyAdapter(
            onDelete = { party ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete ${party.name}?")
                    .setMessage("All slips for this party will also be deleted.")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteParty(party) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onRename = { party ->
                val et = android.widget.EditText(requireContext()).apply {
                    setText(party.name)
                    selectAll()
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Rename party")
                    .setView(et)
                    .setPositiveButton("Save") { _, _ ->
                        val name = et.text.toString().trim()
                        if (name.isNotEmpty()) viewModel.updateParty(party.copy(name = name))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvParties.layoutManager = LinearLayoutManager(requireContext())
        binding.rvParties.adapter = adapter

        viewModel.parties.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list.map { it.party })
        }

        binding.btnAdd.setOnClickListener {
            val name = binding.etPartyName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(context, "Enter a party name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addParty(name)
            binding.etPartyName.text?.clear()
        }

        binding.btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class ManagePartyAdapter(
    private val onDelete: (Party) -> Unit,
    private val onRename: (Party) -> Unit
) : ListAdapter<Party, ManagePartyAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemPartyBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(party: Party) {
            b.tvPartyName.text = party.name
            b.tvSlipCount.text = "Long-press to rename"
            b.tvOutstanding.text = "✕"
            b.tvOutstanding.setTextColor(0xFFFF3B30.toInt())
            b.tvOutstanding.setOnClickListener { onDelete(party) }
            b.root.setOnLongClickListener { onRename(party); true }
            b.root.setOnClickListener { onRename(party) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPartyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Party>() {
            override fun areItemsTheSame(a: Party, b: Party) = a.id == b.id
            override fun areContentsTheSame(a: Party, b: Party) = a == b
        }
    }
}
