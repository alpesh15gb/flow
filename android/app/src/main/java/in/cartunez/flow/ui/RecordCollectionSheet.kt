package `in`.cartunez.flow.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Slip
import `in`.cartunez.flow.data.SlipStatus
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.SheetRecordCollectionBinding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RecordCollectionSheet : BottomSheetDialogFragment() {

    private var _binding: SheetRecordCollectionBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: SlipsViewModel by activityViewModels {
        SlipsViewModelFactory(
            app.slipsRepository,
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore)
        )
    }

    private var selectedDate = LocalDate.now()
    private var pendingSlips = listOf<Slip>()

    companion object {
        private const val ARG_PARTY_ID   = "partyId"
        private const val ARG_PARTY_NAME = "partyName"

        fun newInstance(partyId: String, partyName: String) = RecordCollectionSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_PARTY_ID, partyId)
                putString(ARG_PARTY_NAME, partyName)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetRecordCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val partyId   = arguments?.getString(ARG_PARTY_ID)   ?: return
        val partyName = arguments?.getString(ARG_PARTY_NAME) ?: "Party"

        binding.tvPartyName.text = partyName
        updateDateChip()

        // Load pending slips and outstanding
        lifecycleScope.launch {
            val outstanding = viewModel.getOutstanding(partyId)
            binding.tvOutstanding.text = "₹${String.format("%,.0f", outstanding)}"
        }

        viewModel.selectedPartySlips.observe(viewLifecycleOwner) { slips ->
            pendingSlips = slips.filter { it.status != SlipStatus.COLLECTED.name }
                               .sortedBy { it.date }
        }

        // Live allocation preview
        val allocationAdapter = AllocationPreviewAdapter()
        binding.rvAllocation.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAllocation.adapter = allocationAdapter

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val amount = s.toString().toDoubleOrNull() ?: 0.0
                allocationAdapter.submitPreview(pendingSlips, amount)
            }
        })

        binding.chipDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Collection date")
                .setSelection(selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                updateDateChip()
            }
            picker.show(parentFragmentManager, "date_picker")
        }

        binding.btnConfirm.setOnClickListener {
            val amount = binding.etAmount.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val note = binding.etNote.text.toString().trim().ifBlank { null }
            viewModel.recordCollection(partyId, amount, selectedDate.toString(), note)
            dismiss()
        }
    }

    private fun updateDateChip() {
        val today = LocalDate.now()
        binding.chipDate.text = when (selectedDate) {
            today              -> "Today"
            today.minusDays(1) -> "Yesterday"
            else               -> selectedDate.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Simple inline adapter for the allocation preview list */
private class AllocationPreviewAdapter : RecyclerView.Adapter<AllocationPreviewAdapter.VH>() {

    private data class Row(val date: String, val slipAmount: Double, val applying: Double, val remaining: Double)
    private val rows = mutableListOf<Row>()

    fun submitPreview(slips: List<Slip>, payment: Double) {
        rows.clear()
        var rem = payment
        for (slip in slips) {
            if (rem <= 0) break
            val owed  = slip.amount - slip.amountPaid
            val apply = minOf(owed, rem)
            rem -= apply
            rows.add(Row(slip.date, slip.amount, apply, owed - apply))
        }
        notifyDataSetChanged()
    }

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(TextView(parent.context).apply {
            setPadding(0, 6, 0, 6)
            textSize = 12f
            setTextColor(0xFF9AA0A6.toInt())
        })

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]
        holder.tv.text = if (r.remaining <= 0)
            "${r.date}  ₹${String.format("%,.0f", r.slipAmount)}  →  fully settled ✓"
        else
            "${r.date}  ₹${String.format("%,.0f", r.slipAmount)}  →  ₹${String.format("%,.0f", r.remaining)} remaining"
    }

    override fun getItemCount() = rows.size
}
