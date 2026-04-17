package com.vonnegut.app.ui.sessions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vonnegut.app.data.db.entities.Session
import com.vonnegut.app.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionUi(
    val session: Session,
    val messageCount: Int,
    val firstLine: String
)

class SessionAdapter(
    private val onSessionClick: (Session) -> Unit,
    private val onSessionLongClick: (Session) -> Unit,
    private val onSessionDelete: (Session) -> Unit
) : ListAdapter<SessionUi, SessionAdapter.ViewHolder>(DiffCallback) {

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<SessionUi>() {
            override fun areItemsTheSame(a: SessionUi, b: SessionUi) =
                a.session.id == b.session.id
            override fun areContentsTheSame(a: SessionUi, b: SessionUi) = a == b
        }
        val DATE_FORMAT = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())
    }

    inner class ViewHolder(private val binding: ItemSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SessionUi) {
            binding.textSessionName.text = item.session.name
            binding.textSessionDate.text = DATE_FORMAT.format(Date(item.session.updatedAt))
            binding.textSessionPreview.text = item.firstLine.ifBlank { "Empty session" }
            binding.textMessageCount.text = "${item.messageCount} messages"

            binding.root.setOnClickListener { onSessionClick(item.session) }
            binding.root.setOnLongClickListener {
                onSessionLongClick(item.session)
                true
            }
            binding.buttonDelete.setOnClickListener { onSessionDelete(item.session) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}
