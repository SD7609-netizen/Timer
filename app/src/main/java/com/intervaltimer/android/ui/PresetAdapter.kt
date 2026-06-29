package com.intervaltimer.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.intervaltimer.android.data.Preset
import com.intervaltimer.android.databinding.ItemPresetBinding

class PresetAdapter(
    private val onStart: (Preset) -> Unit,
    private val onEdit: (Preset) -> Unit,
    private val onDelete: (Preset) -> Unit
) : ListAdapter<Preset, PresetAdapter.VH>(DIFF) {

    inner class VH(val b: ItemPresetBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPresetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val preset = getItem(position)
        holder.b.tvPresetName.text = preset.name
        holder.b.btnStart.setOnClickListener { onStart(preset) }
        holder.b.btnEdit.setOnClickListener { onEdit(preset) }
        holder.b.btnDelete.setOnClickListener { onDelete(preset) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Preset>() {
            override fun areItemsTheSame(a: Preset, b: Preset) = a.id == b.id
            override fun areContentsTheSame(a: Preset, b: Preset) = a == b
        }
    }
}
