package `in`.cartunez.flow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import `in`.cartunez.flow.R
import `in`.cartunez.flow.databinding.DialogAddTransactionBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AddTransactionDialog : BottomSheetDialogFragment() {

    private var _binding: DialogAddTransactionBinding? = null
    private val binding get() = _binding!!

    var onSave: ((amount: Double, note: String, date: String) -> Unit)? = null

    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedType: String = "sale"

    companion object {
        private const val ARG_TYPE = "type"
        fun newInstance(type: String) = AddTransactionDialog().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val initType = arguments?.getString(ARG_TYPE) ?: "sale"
        selectedType = initType

        // Set initial type in toggle
        val btnId = when (initType) {
            "expense"  -> R.id.btnTypeExpense
            "purchase" -> R.id.btnTypePurchase
            else       -> R.id.btnTypeSale
        }
        binding.toggleType.check(btnId)
        updateTitle(initType)

        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedType = when (checkedId) {
                R.id.btnTypeExpense  -> "expense"
                R.id.btnTypePurchase -> "purchase"
                else                 -> "sale"
            }
            updateTitle(selectedType)
        }

        // Date chip
        updateDateChip()
        binding.chipDate.setOnClickListener { showDatePicker() }

        // Save
        binding.btnSave.setOnClickListener {
            val amountText = binding.etAmount.text.toString().trim()
            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSave?.invoke(amount, binding.etNote.text.toString().trim(), selectedDate.toString())
            dismiss()
        }
    }

    private fun updateTitle(type: String) {
        binding.tvTitle.text = "Add ${type.replaceFirstChar { it.uppercase() }}"
    }

    private fun updateDateChip() {
        val today = LocalDate.now()
        binding.chipDate.text = when (selectedDate) {
            today             -> "Today"
            today.minusDays(1) -> "Yesterday"
            else              -> selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            updateDateChip()
        }
        picker.show(parentFragmentManager, "date_picker")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
