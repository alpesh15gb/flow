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
            b.tvAmount.text = "₹${String.format("%,.0f", tx.amount)}"
            b.tvNote.text   = tx.note ?: tx.type.replaceFirstChar { it.uppercase() }
            b.tvDate.text   = tx.date
            b.tvType.text   = tx.type.replaceFirstChar { it.uppercase() }

            val (bgColor, textColor) = when (tx.type) {
                "sale"     -> Pair(R.color.type_sale_bg,     R.color.green)
                "expense"  -> Pair(R.color.type_expense_bg,  R.color.red)
                "purchase" -> Pair(R.color.type_purchase_bg, R.color.blue)
                else       -> Pair(R.color.surface,          R.color.textPrimary)
            }
            b.tvType.backgroundTintList = ContextCompat.getColorStateList(b.root.context, bgColor)
            b.tvType.setTextColor(ContextCompat.getColor(b.root.context, textColor))
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
