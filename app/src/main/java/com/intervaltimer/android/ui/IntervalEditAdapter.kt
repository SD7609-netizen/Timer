package com.intervaltimer.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.intervaltimer.android.data.Interval
import com.intervaltimer.android.databinding.ItemIntervalEditBinding

class IntervalEditAdapter(
    private val items: MutableList<Interval>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<IntervalEditAdapter.VH>() {

    inner class VH(val b: ItemIntervalEditBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemIntervalEditBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.tvName.text = item.name
        val m = item.durationSeconds / 60
        val s = item.durationSeconds % 60
        holder.b.tvDuration.text = if (m > 0 && s > 0) "${m} мин ${s} сек"
            else if (m > 0) "${m} мин"
            else "${s} сек"
        holder.b.tvSound.text = item.soundType.label
        holder.b.btnEdit.setOnClickListener { onEdit(holder.adapterPosition) }
        holder.b.btnDelete.setOnClickListener { onDelete(holder.adapterPosition) }
    }
}
