package com.vonnegut.app.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vonnegut.app.data.db.entities.Role
import com.vonnegut.app.databinding.ItemMessageAssistantBinding
import com.vonnegut.app.databinding.ItemMessageUserBinding

class MessageAdapter : ListAdapter<MessageUi, RecyclerView.ViewHolder>(DiffCallback) {

    private companion object {
        const val VIEW_TYPE_USER = 0
        const val VIEW_TYPE_ASSISTANT = 1

        val DiffCallback = object : DiffUtil.ItemCallback<MessageUi>() {
            override fun areItemsTheSame(a: MessageUi, b: MessageUi) = a.id == b.id
            override fun areContentsTheSame(a: MessageUi, b: MessageUi) = a == b
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).role == Role.USER) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> UserViewHolder(
                ItemMessageUserBinding.inflate(inflater, parent, false)
            )
            else -> AssistantViewHolder(
                ItemMessageAssistantBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is UserViewHolder -> holder.bind(item)
            is AssistantViewHolder -> holder.bind(item)
        }
    }

    class UserViewHolder(private val binding: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageUi) {
            binding.textContent.text = message.content
        }
    }

    class AssistantViewHolder(private val binding: ItemMessageAssistantBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageUi) {
            if (message.isTypingIndicator && message.content.isEmpty()) {
                binding.textContent.text = "…"
            } else {
                binding.textContent.text = message.content
            }
        }
    }
}
