package com.vonnegut.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.Toolbar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.vonnegut.app.R
import com.vonnegut.app.databinding.FragmentChatBinding
import com.vonnegut.app.speech.SpeechInputManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var speechManager: SpeechInputManager
    private val pendingAttachments = mutableListOf<ChatAttachment>()
    private val installedModelPaths = mutableListOf<String>()
    private var latestUiState: ChatUiState = ChatUiState.NoModel

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceInput()
        else Toast.makeText(requireContext(), "Microphone permission required for voice input.", Toast.LENGTH_SHORT).show()
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            addImageAttachment(uri)
        }
    }

    private val pickDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            addTextAttachment(uri)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            addCameraAttachment(bitmap)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        setupRecyclerView()
        setupInput()
        setupSpeech()
        setupToolbar()
        observeViewModel()
        renderPendingAttachments()
        refreshInstalledModels()

        viewModel.initialise()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar()
        refreshInstalledModels()
        updateToolbarModelLabel()
        viewModel.initialise()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter()
        binding.recyclerMessages.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupInput() {
        binding.buttonSend.setOnClickListener { sendMessage() }
        binding.buttonMic.setOnClickListener { toggleVoiceInput() }
        binding.buttonAddAttachment.setOnClickListener { showAttachmentMenu() }
    }

    private fun setupToolbar() {
        val toolbar = requireActivity().findViewById<Toolbar>(R.id.toolbar)
        val modelSelector = requireActivity().findViewById<View>(R.id.toolbar_model_selector)
        val dropdownIcon = requireActivity().findViewById<ImageView>(R.id.image_toolbar_dropdown)
        requireActivity().title = ""
        toolbar.title = ""
        toolbar.subtitle = null
        toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener { showBurgerMenu(toolbar) }
        modelSelector.isVisible = true
        dropdownIcon.isVisible = installedModelPaths.size > 1
        modelSelector.setOnClickListener {
            if (installedModelPaths.isNotEmpty()) {
                showModelMenu(modelSelector)
            }
        }
        updateToolbarModelLabel()
    }

    private fun setupSpeech() {
        speechManager = SpeechInputManager(requireContext())
        speechManager.setListener(object : SpeechInputManager.Listener {
            override fun onListeningStarted() {
                binding.buttonMic.setImageResource(R.drawable.ic_mic_active)
                Toast.makeText(requireContext(), "Listening… tap again to stop.", Toast.LENGTH_SHORT).show()
            }

            override fun onPartialResult(text: String) {
                // Keep partials out of the draft so edits stay stable.
            }

            override fun onResult(text: String) {
                binding.buttonMic.setImageResource(R.drawable.ic_mic)
                insertTranscription(text)
            }

            override fun onError(message: String) {
                binding.buttonMic.setImageResource(R.drawable.ic_mic)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }

            override fun onListeningStopped() {
                binding.buttonMic.setImageResource(R.drawable.ic_mic)
            }
        })
    }

    private fun startVoiceInput() {
        speechManager.startListening()
    }

    private fun toggleVoiceInput() {
        if (speechManager.isListening()) {
            speechManager.stopListening()
            return
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceInput()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun sendMessage() {
        val text = binding.editInput.text?.toString().orEmpty()
        if (text.isBlank() && pendingAttachments.isEmpty()) return
        viewModel.sendMessage(text, pendingAttachments.toList())
        binding.editInput.setText("")
        pendingAttachments.clear()
        renderPendingAttachments()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> handleUiState(state) }
                }
                launch {
                    viewModel.messages.collect { messages ->
                        messageAdapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                binding.recyclerMessages.scrollToPosition(messages.size - 1)
                            }
                        }
                        updateEmptyState(messages.isEmpty())
                    }
                }
                launch {
                    viewModel.currentSession.collect { updateToolbarModelLabel() }
                }
                launch {
                    viewModel.recentSessions.collect { sessions ->
                        if (latestUiState is ChatUiState.Ready) {
                            updateToolbarModelLabel()
                        }
                        if (sessions.isNotEmpty()) {
                            refreshInstalledModels()
                        }
                    }
                }
            }
        }
    }

    private fun handleUiState(state: ChatUiState) {
        latestUiState = state
        when (state) {
            is ChatUiState.NoModel -> {
                binding.inputArea.isVisible = false
                binding.statusBar.isVisible = true
                binding.statusText.text =
                    "No model loaded. Import a .litertlm model, scan a model folder, or download one below."
                binding.statusActionButton.isVisible = true
                binding.statusActionButton.text = "Manage Models"
                binding.statusActionButton.setOnClickListener {
                    if (findNavController().currentDestination?.id == R.id.chatFragment) {
                        findNavController().navigate(R.id.action_chat_to_model_manager)
                    }
                }
            }
            is ChatUiState.ModelLoading -> {
                binding.inputArea.isVisible = false
                binding.statusBar.isVisible = true
                binding.statusText.text = "Loading model…"
                binding.statusActionButton.isVisible = false
            }
            is ChatUiState.Ready -> {
                binding.inputArea.isVisible = true
                binding.statusBar.isVisible = false
                binding.buttonSend.isEnabled = true
                binding.buttonMic.isEnabled = true
                binding.buttonAddAttachment.isEnabled = true
                binding.editInput.isEnabled = true
            }
            is ChatUiState.Generating -> {
                binding.inputArea.isVisible = true
                binding.statusBar.isVisible = false
                binding.buttonSend.isEnabled = false
                binding.buttonMic.isEnabled = false
                binding.buttonAddAttachment.isEnabled = false
            }
            is ChatUiState.LoadError -> {
                binding.inputArea.isVisible = false
                binding.statusBar.isVisible = true
                binding.statusText.text = "Failed to load model: ${state.message}"
                binding.statusActionButton.isVisible = true
                binding.statusActionButton.text = "Manage Models"
                binding.statusActionButton.setOnClickListener {
                    if (findNavController().currentDestination?.id == R.id.chatFragment) {
                        findNavController().navigate(R.id.action_chat_to_model_manager)
                    }
                }
            }
        }
        updateEmptyState(viewModel.messages.value.isEmpty())
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_chat, menu)
    }

    private fun showRenameDialog() {
        val session = viewModel.currentSession.value ?: return
        val input = android.widget.EditText(requireContext()).apply {
            setText(session.name)
            setSelection(session.name.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename session")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.renameCurrentSession(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().findViewById<View>(R.id.toolbar_model_selector).isVisible = false
        speechManager.destroy()
        _binding = null
    }

    private fun insertTranscription(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        binding.editInput.requestFocus()
        val editable = binding.editInput.editableText
        val selectionStart = binding.editInput.selectionStart.coerceAtLeast(0)
        val needsLeadingSpace = selectionStart > 0 &&
            editable.getOrNull(selectionStart - 1)?.isWhitespace() == false
        val insertion = buildString {
            if (needsLeadingSpace) append(' ')
            append(cleaned)
        }

        editable.insert(selectionStart, insertion)
        binding.editInput.setSelection((selectionStart + insertion.length).coerceAtMost(editable.length))
    }

    private fun renderPendingAttachments() {
        binding.chipAttachments.removeAllViews()
        pendingAttachments.forEachIndexed { index, attachment ->
            val chip = Chip(requireContext()).apply {
                text = attachment.label
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    pendingAttachments.removeAt(index)
                    renderPendingAttachments()
                }
            }
            binding.chipAttachments.addView(chip)
        }
        binding.chipAttachments.isVisible = pendingAttachments.isNotEmpty()
    }

    private fun updateEmptyState(messagesEmpty: Boolean) {
        binding.emptyState.isVisible =
            messagesEmpty && latestUiState is ChatUiState.Ready && binding.statusBar.isVisible.not()
    }

    private fun showAttachmentMenu() {
        PopupMenu(
            ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Vonnegut_PopupMenu),
            binding.buttonAddAttachment
        ).apply {
            menu.add(0, MENU_PICK_IMAGE, 0, "Image")
            menu.add(0, MENU_PICK_TEXT, 1, "Text file")
            menu.add(0, MENU_TAKE_PHOTO, 2, "Take photo")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_PICK_IMAGE -> {
                        pickImageLauncher.launch(arrayOf("image/*"))
                        true
                    }
                    MENU_PICK_TEXT -> {
                        pickDocumentLauncher.launch(arrayOf("text/*", "application/json"))
                        true
                    }
                    MENU_TAKE_PHOTO -> {
                        takePictureLauncher.launch(null)
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun addImageAttachment(uri: android.net.Uri) {
        runCatching {
            val fileName = DocumentFile.fromSingleUri(requireContext(), uri)?.name ?: "image.jpg"
            val destination = File(attachmentsDir(), fileName.substringAfterLast('/'))
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            } ?: error("Could not open image.")
            pendingAttachments += ChatAttachment.Image(fileName, destination.absolutePath)
            renderPendingAttachments()
        }.onFailure {
            Toast.makeText(requireContext(), "Could not attach image: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCameraAttachment(bitmap: Bitmap) {
        runCatching {
            val destination = File(attachmentsDir(), "camera-${System.currentTimeMillis()}.jpg")
            FileOutputStream(destination).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            pendingAttachments += ChatAttachment.Image(destination.name, destination.absolutePath)
            renderPendingAttachments()
        }.onFailure {
            Toast.makeText(requireContext(), "Could not save photo: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addTextAttachment(uri: android.net.Uri) {
        runCatching {
            val resolver = requireContext().contentResolver
            val fileName = DocumentFile.fromSingleUri(requireContext(), uri)?.name ?: "document.txt"
            val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not open document.")
            val trimmed = text.trim()
            require(trimmed.isNotEmpty()) { "Document is empty." }
            val clipped = if (trimmed.length > MAX_TEXT_ATTACHMENT_CHARS) {
                trimmed.take(MAX_TEXT_ATTACHMENT_CHARS) + "\n\n[Document truncated]"
            } else {
                trimmed
            }
            pendingAttachments += ChatAttachment.TextDocument(fileName, clipped)
            renderPendingAttachments()
        }.onFailure {
            Toast.makeText(
                requireContext(),
                "Only plain-text documents are supported right now.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun attachmentsDir(): File =
        File(requireContext().cacheDir, "chat-attachments").also { it.mkdirs() }

    private fun refreshInstalledModels() {
        installedModelPaths.clear()
        val modelsDir = File(requireContext().getExternalFilesDir(null), "models")
        installedModelPaths += modelsDir.listFiles { file -> file.extension == "litertlm" }
            ?.map { it.absolutePath }
            ?.sorted()
            .orEmpty()
        requireActivity().findViewById<ImageView>(R.id.image_toolbar_dropdown)?.isVisible =
            installedModelPaths.size > 1
    }

    private fun updateToolbarModelLabel() {
        val modelLabel = currentModelLabel()
        requireActivity().findViewById<TextView>(R.id.text_toolbar_model).text = modelLabel
    }

    private fun currentModelLabel(): String {
        val activeModelPath =
            (requireActivity().application as com.vonnegut.app.VonnegutApplication).preferences.activeModelPath
        return activeModelPath?.let { File(it).nameWithoutExtension } ?: "No model"
    }

    private fun showModelMenu(anchor: View) {
        val activeModelPath =
            (requireActivity().application as com.vonnegut.app.VonnegutApplication).preferences.activeModelPath
        PopupMenu(
            ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Vonnegut_PopupMenu),
            anchor
        ).apply {
            installedModelPaths.forEachIndexed { index, path ->
                val title = File(path).nameWithoutExtension +
                    if (path == activeModelPath) "  •" else ""
                menu.add(0, index, index, title)
            }
            setOnMenuItemClickListener { item ->
                installedModelPaths.getOrNull(item.itemId)?.let { path ->
                    viewModel.setActiveModelPath(path)
                    updateToolbarModelLabel()
                }
                true
            }
        }.show()
    }

    private fun showBurgerMenu(anchor: View) {
        PopupMenu(
            ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Vonnegut_PopupMenu),
            anchor
        ).apply {
            menu.add(0, MENU_NEW_CHAT, 0, "New chat")
            menu.add(0, MENU_SETTINGS, 1, "Settings")
            if (viewModel.recentSessions.value.isNotEmpty()) {
                menu.add(0, MENU_HISTORY_HEADER, 2, "Recent chats").isEnabled = false
                viewModel.recentSessions.value.take(MAX_RECENT_CHATS).forEachIndexed { index, session ->
                    menu.add(0, MENU_HISTORY_BASE + index, 3 + index, session.name)
                }
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_NEW_CHAT -> {
                        viewModel.startNewSession()
                        pendingAttachments.clear()
                        renderPendingAttachments()
                        true
                    }
                    MENU_SETTINGS -> {
                        findNavController().navigate(R.id.action_chat_to_settings)
                        true
                    }
                    in MENU_HISTORY_BASE until (MENU_HISTORY_BASE + MAX_RECENT_CHATS) -> {
                        viewModel.recentSessions.value
                            .getOrNull(item.itemId - MENU_HISTORY_BASE)
                            ?.let { viewModel.switchToSession(it) }
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private companion object {
        private const val MENU_PICK_IMAGE = 1
        private const val MENU_PICK_TEXT = 2
        private const val MENU_TAKE_PHOTO = 3
        private const val MAX_TEXT_ATTACHMENT_CHARS = 12000
        private const val MENU_NEW_CHAT = 100
        private const val MENU_SETTINGS = 101
        private const val MENU_HISTORY_HEADER = 102
        private const val MENU_HISTORY_BASE = 200
        private const val MAX_RECENT_CHATS = 8
    }
}
