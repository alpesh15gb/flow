package `in`.cartunez.flow.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Transaction
import `in`.cartunez.flow.databinding.ItemTransactionBinding

class TransactionAdapter : ListAdapter<Transaction, TransactionAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemTransactionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(tx: Transaction) {
            val (icon, stripeColor, amountColor, iconBg) = when (tx.type) {
                "sale"     -> Quad("↑", R.color.green,  R.color.green,  R.color.type_sale_bg)
                "expense"  -> Quad("↓", R.color.red,    R.color.red,    R.color.type_expense_bg)
                "purchase" -> Quad("⬤", R.color.blue,   R.color.blue,   R.color.type_purchase_bg)
                else       -> Quad("•", R.color.border, R.color.textPrimary, R.color.surface2)
            }

            b.tvTypeIcon.text = icon
            b.tvTypeIcon.backgroundTintList = ContextCompat.getColorStateList(b.root.context, iconBg)
            b.tvTypeIcon.setTextColor(ContextCompat.getColor(b.root.context, amountColor))
            b.vStripe.setBackgroundColor(ContextCompat.getColor(b.root.context, stripeColor))
            b.tvAmount.text = "₹${String.format("%,.0f", tx.amount)}"
            b.tvAmount.setTextColor(ContextCompat.getColor(b.root.context, amountColor))
            b.tvNote.text = tx.note?.ifBlank { null } ?: tx.type.replaceFirstChar { it.uppercase() }
            b.tvType.text = tx.type.replaceFirstChar { it.uppercase() }
            b.tvDate.text = tx.date
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Transaction>() {
            override fun areItemsTheSame(a: Transaction, b: Transaction) = a.id == b.id
            override fun areContentsTheSame(a: Transaction, b: Transaction) = a == b
        }
    }
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
