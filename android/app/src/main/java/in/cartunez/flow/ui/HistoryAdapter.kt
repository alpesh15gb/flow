package `in`.cartunez.flow.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Transaction
import `in`.cartunez.flow.databinding.ItemDateHeaderBinding
import `in`.cartunez.flow.databinding.ItemTransactionBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

sealed class HistoryItem {
    data class Header(val date: String) : HistoryItem()
    data class TxItem(val tx: Transaction) : HistoryItem()
}

class HistoryAdapter : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(DIFF) {

    var onLongClick: ((Transaction) -> Unit)? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TX     = 1

        val DIFF = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(a: HistoryItem, b: HistoryItem): Boolean = when {
                a is HistoryItem.Header && b is HistoryItem.Header -> a.date == b.date
                a is HistoryItem.TxItem && b is HistoryItem.TxItem -> a.tx.id == b.tx.id
                else -> false
            }
            override fun areContentsTheSame(a: HistoryItem, b: HistoryItem) = a == b
        }

        fun buildList(transactions: List<Transaction>): List<HistoryItem> {
            val result = mutableListOf<HistoryItem>()
            var lastDate = ""
            for (tx in transactions) {
                if (tx.date != lastDate) {
                    result.add(HistoryItem.Header(formatDate(tx.date)))
                    lastDate = tx.date
                }
                result.add(HistoryItem.TxItem(tx))
            }
            return result
        }

        private fun formatDate(raw: String): String = try {
            val d = LocalDate.parse(raw)
            val today = LocalDate.now()
            when {
                d == today           -> "Today"
                d == today.minusDays(1) -> "Yesterday"
                else -> d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            }
        } catch (e: Exception) { raw }
    }

    inner class HeaderVH(private val b: ItemDateHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(h: HistoryItem.Header) { b.tvDateHeader.text = h.date }
    }

    inner class TxVH(private val b: ItemTransactionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(tx: Transaction) {
            b.tvAmount.text = "₹${String.format("%,.0f", tx.amount)}"
            b.tvNote.text   = tx.note?.ifBlank { null } ?: tx.type.replaceFirstChar { it.uppercase() }
            b.tvDate.text   = tx.type.replaceFirstChar { it.uppercase() }
            b.tvType.text   = if (tx.synced) "✓ synced" else "pending sync"

            val (icon, iconBg, amountColor) = when (tx.type) {
                "sale"     -> Triple("↑", R.color.type_sale_bg,     R.color.green)
                "expense"  -> Triple("↓", R.color.type_expense_bg,  R.color.red)
                "purchase" -> Triple("⬤", R.color.type_purchase_bg, R.color.blue)
                else       -> Triple("•", R.color.border,            R.color.textPrimary)
            }
            b.tvTypeIcon.text = icon
            b.tvTypeIcon.backgroundTintList = ContextCompat.getColorStateList(b.root.context, iconBg)
            b.tvAmount.setTextColor(ContextCompat.getColor(b.root.context, amountColor))

            b.root.setOnLongClickListener {
                onLongClick?.invoke(tx)
                true
            }
        }
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position) is HistoryItem.Header) TYPE_HEADER else TYPE_TX

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER)
            HeaderVH(ItemDateHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else
            TxVH(ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryItem.Header -> (holder as HeaderVH).bind(item)
            is HistoryItem.TxItem -> (holder as TxVH).bind(item.tx)
        }
    }

    /** Returns the Transaction at position, or null if it's a header */
    fun getTransactionAt(position: Int): Transaction? =
        (getItem(position) as? HistoryItem.TxItem)?.tx
}
