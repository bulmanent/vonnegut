package com.vonnegut.app.ui.chat

sealed interface ChatAttachment {
    val label: String

    data class Image(
        override val label: String,
        val absolutePath: String
    ) : ChatAttachment

    data class TextDocument(
        override val label: String,
        val textContent: String
    ) : ChatAttachment
}
