package `in`.cartunez.flow.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
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

    private var selectedDate = LocalDate.now()
    private var selectedType = "sale"

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
        selectType(initType)
        updateDateChip()

        // Type selector
        binding.btnTypeSale.setOnClickListener     { selectType("sale") }
        binding.btnTypeExpense.setOnClickListener  { selectType("expense") }
        binding.btnTypePurchase.setOnClickListener { selectType("purchase") }

        // Show amount display linked to hidden edittext
        binding.tvAmountDisplay.setOnClickListener { binding.etAmount.requestFocus() }
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val v = s.toString().toDoubleOrNull() ?: 0.0
                binding.tvAmountDisplay.text = if (v == 0.0) "₹0" else "₹${String.format("%,.0f", v)}"
            }
        })

        // Date
        binding.chipDate.setOnClickListener { showDatePicker() }

        // Save
        binding.btnSave.setOnClickListener {
            val amount = binding.etAmount.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSave?.invoke(amount, binding.etNote.text.toString().trim(), selectedDate.toString())
            dismiss()
        }

        // Auto-open keyboard on amount
        binding.etAmount.postDelayed({ binding.etAmount.requestFocus() }, 200)
    }

    private fun selectType(type: String) {
        selectedType = type
        val sale     = if (type == "sale")     R.drawable.bg_type_sale_selected     else R.drawable.bg_type_unselected
        val expense  = if (type == "expense")  R.drawable.bg_type_expense_selected  else R.drawable.bg_type_unselected
        val purchase = if (type == "purchase") R.drawable.bg_type_purchase_selected else R.drawable.bg_type_unselected

        binding.btnTypeSale.background     = ContextCompat.getDrawable(requireContext(), sale)
        binding.btnTypeExpense.background  = ContextCompat.getDrawable(requireContext(), expense)
        binding.btnTypePurchase.background = ContextCompat.getDrawable(requireContext(), purchase)

        // Update text colors
        val greenOrMuted = if (type == "sale")     R.color.green     else R.color.textSecondary
        val redOrMuted   = if (type == "expense")  R.color.red       else R.color.textSecondary
        val blueOrMuted  = if (type == "purchase") R.color.blue      else R.color.textSecondary

        for (child in listOf(binding.btnTypeSale.getChildAt(0), binding.btnTypeSale.getChildAt(1))) {
            (child as? android.widget.TextView)?.setTextColor(ContextCompat.getColor(requireContext(), greenOrMuted))
        }
        for (child in listOf(binding.btnTypeExpense.getChildAt(0), binding.btnTypeExpense.getChildAt(1))) {
            (child as? android.widget.TextView)?.setTextColor(ContextCompat.getColor(requireContext(), redOrMuted))
        }
        for (child in listOf(binding.btnTypePurchase.getChildAt(0), binding.btnTypePurchase.getChildAt(1))) {
            (child as? android.widget.TextView)?.setTextColor(ContextCompat.getColor(requireContext(), blueOrMuted))
        }

        // Update save button color
        val btnColor = when (type) {
            "expense"  -> ContextCompat.getColor(requireContext(), R.color.red)
            "purchase" -> ContextCompat.getColor(requireContext(), R.color.blue)
            else       -> ContextCompat.getColor(requireContext(), R.color.green)
        }
        binding.btnSave.setBackgroundColor(btnColor)
    }

    private fun updateDateChip() {
        val today = LocalDate.now()
        binding.chipDate.text = when (selectedDate) {
            today              -> "Today"
            today.minusDays(1) -> "Yesterday"
            else               -> selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
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
