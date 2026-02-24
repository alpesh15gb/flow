package `in`.cartunez.flow.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import `in`.cartunez.flow.databinding.DialogAddTransactionBinding

class AddTransactionDialog : BottomSheetDialogFragment() {

    private var _binding: DialogAddTransactionBinding? = null
    private val binding get() = _binding!!
    var onSave: ((amount: Double, note: String) -> Unit)? = null

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
        val type = arguments?.getString(ARG_TYPE) ?: "sale"
        binding.tvTitle.text = "Add ${type.replaceFirstChar { it.uppercase() }}"

        binding.btnSave.setOnClickListener {
            val amountText = binding.etAmount.text.toString().trim()
            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSave?.invoke(amount, binding.etNote.text.toString().trim())
            dismiss()
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
