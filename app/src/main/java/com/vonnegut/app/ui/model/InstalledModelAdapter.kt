package com.vonnegut.app.ui.model

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vonnegut.app.databinding.ItemInstalledModelBinding

class InstalledModelAdapter(
    private val onSetActive: (InstalledModel) -> Unit,
    private val onDelete: (InstalledModel) -> Unit
) : ListAdapter<InstalledModel, InstalledModelAdapter.ViewHolder>(DiffCallback) {

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<InstalledModel>() {
            override fun areItemsTheSame(a: InstalledModel, b: InstalledModel) =
                a.file.absolutePath == b.file.absolutePath
            override fun areContentsTheSame(a: InstalledModel, b: InstalledModel) = a == b
        }
    }

    inner class ViewHolder(private val binding: ItemInstalledModelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(model: InstalledModel) {
            binding.textModelName.text = model.name
            binding.textModelSize.text = "${model.sizeMb} MB"
            binding.activeIndicator.text = if (model.isActive) "Active" else ""
            binding.activeIndicator.alpha = if (model.isActive) 1f else 0f

            binding.root.setOnClickListener { onSetActive(model) }
            binding.buttonDelete.setOnLongClickListener {
                onDelete(model)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemInstalledModelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}
