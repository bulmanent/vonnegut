package com.vonnegut.app.ui.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vonnegut.app.databinding.ItemAvailableModelBinding

class AvailableModelAdapter(
    private val onDownload: (AvailableModel) -> Unit
) : ListAdapter<AvailableModel, AvailableModelAdapter.ViewHolder>(DiffCallback) {

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<AvailableModel>() {
            override fun areItemsTheSame(a: AvailableModel, b: AvailableModel) =
                a.filename == b.filename
            override fun areContentsTheSame(a: AvailableModel, b: AvailableModel) = a == b
        }
    }

    var downloadingFilename: String? = null
    var downloadProgress: Int = 0

    inner class ViewHolder(private val binding: ItemAvailableModelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(model: AvailableModel) {
            binding.textModelName.text = model.name
            binding.textModelSize.text = "${model.sizeMb} MB  •  ${model.quantization}"
            binding.textModelNotes.text = model.notes

            val isDownloading = downloadingFilename == model.filename
            binding.progressDownload.visibility = if (isDownloading) View.VISIBLE else View.GONE
            binding.progressDownload.progress = if (isDownloading) downloadProgress else 0
            binding.buttonDownload.isEnabled = !isDownloading && downloadingFilename == null

            binding.buttonDownload.setOnClickListener { onDownload(model) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemAvailableModelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}
