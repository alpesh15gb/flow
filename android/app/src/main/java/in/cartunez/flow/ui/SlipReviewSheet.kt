package `in`.cartunez.flow.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.data.Slip
import `in`.cartunez.flow.data.SlipStatus
import `in`.cartunez.flow.data.TransactionRepository
import `in`.cartunez.flow.databinding.SheetSlipReviewBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SlipReviewSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSlipReviewBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as FlowApp }
    private val viewModel: SlipsViewModel by activityViewModels {
        SlipsViewModelFactory(
            app.slipsRepository,
            TransactionRepository(app.database.transactionDao(), app.apiService, app.prefsStore)
        )
    }

    private var selectedDate = LocalDate.now()
    private var imageUri: Uri? = null
    private var preselectedPartyId: String? = null
    private var partyList = listOf<PartyWithBalance>()

    companion object {
        private const val ARG_URI      = "uri"
        private const val ARG_PARTY_ID = "partyId"

        /** From share intent — no party pre-selected */
        fun newInstance(uri: Uri?) = SlipReviewSheet().apply {
            arguments = Bundle().apply {
                uri?.let { putString(ARG_URI, it.toString()) }
            }
        }

        /** From party detail — party pre-selected, image optional */
        fun newInstance(uri: Uri?, partyId: String) = SlipReviewSheet().apply {
            arguments = Bundle().apply {
                uri?.let { putString(ARG_URI, it.toString()) }
                putString(ARG_PARTY_ID, partyId)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetSlipReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uriString = arguments?.getString(ARG_URI)
        imageUri = uriString?.let { Uri.parse(it) }
        preselectedPartyId = arguments?.getString(ARG_PARTY_ID)

        updateDateChip()

        // Show image
        if (imageUri != null) {
            binding.ivSlip.visibility = View.VISIBLE
            runCatching {
                requireContext().contentResolver.openInputStream(imageUri!!)?.use { stream ->
                    binding.ivSlip.setImageBitmap(BitmapFactory.decodeStream(stream))
                }
            }
            runOcr(imageUri!!)
        } else {
            binding.ivSlip.visibility = View.GONE
            binding.progressOcr.visibility = View.GONE
        }

        // Party spinner
        viewModel.parties.observe(viewLifecycleOwner) { parties ->
            partyList = parties
            val names = parties.map { it.party.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerParty.adapter = adapter
            // Pre-select if opened from party detail
            val idx = parties.indexOfFirst { it.party.id == preselectedPartyId }
            if (idx >= 0) binding.spinnerParty.setSelection(idx)
        }

        binding.chipDate.setOnClickListener { showDatePicker() }

        binding.btnDiscard.setOnClickListener { dismiss() }

        binding.btnApprove.setOnClickListener {
            val amount = binding.etAmount.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedPartyIdx = binding.spinnerParty.selectedItemPosition
            if (selectedPartyIdx < 0 || selectedPartyIdx >= partyList.size) {
                Toast.makeText(context, "Select a party", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val party = partyList[selectedPartyIdx]
            val note  = binding.etNote.text.toString().trim().ifBlank { null }

            val slip = Slip(
                partyId  = party.party.id,
                amount   = amount,
                date     = selectedDate.toString(),
                note     = note,
                status   = SlipStatus.REVIEW.name
            )
            viewModel.approveSlip(requireContext(), party.party.name, imageUri, slip)
            Toast.makeText(context, "Slip approved & saved as Purchase", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun runOcr(uri: Uri) {
        binding.progressOcr.visibility = View.VISIBLE
        binding.llOcrChips.removeAllViews()

        try {
            val image = InputImage.fromFilePath(requireContext(), uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    binding.progressOcr.visibility = View.GONE
                    val numbers = extractNumbers(visionText.text)
                    if (numbers.isEmpty()) return@addOnSuccessListener

                    // Pre-fill with largest number
                    binding.etAmount.setText(numbers.first().toBigDecimal().stripTrailingZeros().toPlainString())

                    // Show all candidates as chips
                    numbers.take(5).forEach { num ->
                        val chip = TextView(requireContext()).apply {
                            text = "₹${String.format("%,.0f", num)}"
                            textSize = 12f
                            setPadding(20, 8, 20, 8)
                            setBackgroundResource(`in`.cartunez.flow.R.drawable.bg_type_unselected)
                            setTextColor(0xFF9AA0A6.toInt())
                            setOnClickListener {
                                binding.etAmount.setText(num.toBigDecimal().stripTrailingZeros().toPlainString())
                            }
                        }
                        val params = ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = 8 }
                        binding.llOcrChips.addView(chip, params)
                    }
                }
                .addOnFailureListener {
                    binding.progressOcr.visibility = View.GONE
                }
        } catch (e: Exception) {
            binding.progressOcr.visibility = View.GONE
        }
    }

    /** Extract all numeric values from OCR text, sorted descending (largest = likely total) */
    private fun extractNumbers(text: String): List<Double> {
        val regex = Regex("""[\d,]+\.?\d*""")
        return regex.findAll(text)
            .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .filter { it >= 1.0 }
            .sortedDescending()
            .distinct()
            .toList()
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
            .setTitleText("Slip date")
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
