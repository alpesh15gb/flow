package `in`.cartunez.flow.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import `in`.cartunez.flow.R
import `in`.cartunez.flow.databinding.ItemPartyBinding

class PartyAdapter(
    private val onClick: (PartyWithBalance) -> Unit
) : ListAdapter<PartyWithBalance, PartyAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemPartyBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: PartyWithBalance) {
            b.tvPartyName.text = item.party.name
            b.tvSlipCount.text = when {
                item.slipCount == 0 -> "All settled ✓"
                item.slipCount == 1 -> "1 pending slip"
                else -> "${item.slipCount} pending slips"
            }
            if (item.outstanding > 0) {
                b.tvOutstanding.text = "₹${String.format("%,.0f", item.outstanding)} due"
                b.tvOutstanding.setTextColor(ContextCompat.getColor(b.root.context, R.color.red))
            } else {
                b.tvOutstanding.text = "Settled ✓"
                b.tvOutstanding.setTextColor(ContextCompat.getColor(b.root.context, R.color.green))
            }
            b.tvTotalBilled.text = "₹${String.format("%,.0f", item.totalSlipAmount)}"
            b.tvTotalCollected.text = "₹${String.format("%,.0f", item.totalCollected)}"
            b.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPartyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PartyWithBalance>() {
            override fun areItemsTheSame(a: PartyWithBalance, b: PartyWithBalance) =
                a.party.id == b.party.id
            override fun areContentsTheSame(a: PartyWithBalance, b: PartyWithBalance) =
                a == b
        }
    }
}
