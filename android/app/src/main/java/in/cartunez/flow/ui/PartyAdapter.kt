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
            b.tvSlipCount.text = if (item.slipCount == 0) "No pending slips"
                                 else "${item.slipCount} pending slip${if (item.slipCount > 1) "s" else ""}"

            if (item.outstanding > 0) {
                b.tvOutstanding.text = "₹${String.format("%,.0f", item.outstanding)}"
                b.tvOutstanding.setTextColor(ContextCompat.getColor(b.root.context, R.color.red))
            } else {
                b.tvOutstanding.text = "Settled"
                b.tvOutstanding.setTextColor(ContextCompat.getColor(b.root.context, R.color.green))
            }

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
