package `in`.cartunez.flow.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Slip
import `in`.cartunez.flow.data.SlipStatus
import `in`.cartunez.flow.databinding.ItemSlipBinding
import java.io.File

class SlipAdapter(
    private val onLongClick: (Slip) -> Unit
) : ListAdapter<Slip, SlipAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemSlipBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(slip: Slip) {
            b.tvDate.text = slip.date
            b.tvNote.text = slip.note?.ifBlank { null } ?: "No note"
            b.tvAmount.text = "₹${String.format("%,.0f", slip.amount)}"

            // Thumbnail
            if (slip.imageUri != null) {
                val file = File(slip.imageUri)
                if (file.exists()) {
                    b.ivThumbnail.setImageBitmap(BitmapFactory.decodeFile(slip.imageUri))
                } else {
                    b.ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                b.ivThumbnail.setImageResource(android.R.drawable.ic_menu_report_image)
            }

            // Status badge
            val (statusText, statusColor) = when (slip.status) {
                SlipStatus.COLLECTED.name -> "PAID" to R.color.green
                SlipStatus.PARTIAL.name   -> "PARTIAL" to R.color.blue
                SlipStatus.APPROVED.name  -> "PENDING" to R.color.yellow
                SlipStatus.REVIEW.name    -> "REVIEW" to R.color.textSecondary
                else                      -> "PENDING" to R.color.yellow
            }
            b.tvStatus.text = statusText
            b.tvStatus.setTextColor(ContextCompat.getColor(b.root.context, statusColor))

            // Show remaining if partial
            if (slip.status == SlipStatus.PARTIAL.name) {
                val remaining = slip.amount - slip.amountPaid
                b.tvNote.text = "₹${String.format("%,.0f", remaining)} remaining"
            }

            b.root.setOnLongClickListener { onLongClick(slip); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSlipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Slip>() {
            override fun areItemsTheSame(a: Slip, b: Slip) = a.id == b.id
            override fun areContentsTheSame(a: Slip, b: Slip) = a == b
        }
    }
}
