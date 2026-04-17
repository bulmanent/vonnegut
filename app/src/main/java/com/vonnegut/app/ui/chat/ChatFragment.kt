package com.vonnegut.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.vonnegut.app.R
import com.vonnegut.app.databinding.FragmentChatBinding
import com.vonnegut.app.speech.SpeechInputManager
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var speechManager: SpeechInputManager

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceInput() else
            Toast.makeText(requireContext(), "Microphone permission required for voice input.", Toast.LENGTH_SHORT).show()
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
        observeViewModel()

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

        binding.editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        binding.buttonMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startVoiceInput()
            } else {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setupSpeech() {
        speechManager = SpeechInputManager(requireContext())
        speechManager.setListener(object : SpeechInputManager.Listener {
            override fun onListeningStarted() {
                binding.buttonMic.setImageResource(R.drawable.ic_mic_active)
            }

            override fun onPartialResult(text: String) {
                binding.editInput.setText(text)
                binding.editInput.setSelection(text.length)
            }

            override fun onResult(text: String) {
                binding.editInput.setText(text)
                binding.editInput.setSelection(text.length)
                binding.buttonMic.setImageResource(R.drawable.ic_mic)
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

    private fun sendMessage() {
        val text = binding.editInput.text?.toString() ?: return
        if (text.isBlank()) return
        viewModel.sendMessage(text)
        binding.editInput.setText("")
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }
                launch {
                    viewModel.messages.collect { messages ->
                        messageAdapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                binding.recyclerMessages.smoothScrollToPosition(messages.size - 1)
                            }
                        }
                    }
                }
                launch {
                    viewModel.currentSession.collect { session ->
                        requireActivity().title = session?.name ?: getString(R.string.app_name)
                    }
                }
            }
        }
    }

    private fun handleUiState(state: ChatUiState) {
        when (state) {
            is ChatUiState.NoModel -> {
                binding.inputArea.isVisible = false
                binding.statusBar.isVisible = true
                binding.statusText.text = "No model loaded. Go to Settings to select a model."
                binding.statusActionButton.isVisible = true
                binding.statusActionButton.text = "Open Settings"
                binding.statusActionButton.setOnClickListener {
                    findNavController().navigate(R.id.action_chat_to_settings)
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
                binding.editInput.isEnabled = true
            }
            is ChatUiState.Generating -> {
                binding.inputArea.isVisible = true
                binding.statusBar.isVisible = false
                binding.buttonSend.isEnabled = false
                binding.buttonMic.isEnabled = false
            }
            is ChatUiState.LoadError -> {
                binding.inputArea.isVisible = false
                binding.statusBar.isVisible = true
                binding.statusText.text = "Error: ${state.message}"
                binding.statusActionButton.isVisible = true
                binding.statusActionButton.text = "Open Settings"
                binding.statusActionButton.setOnClickListener {
                    findNavController().navigate(R.id.action_chat_to_settings)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_chat, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_sessions -> {
            findNavController().navigate(R.id.action_chat_to_sessions)
            true
        }
        R.id.action_rename -> {
            showRenameDialog()
            true
        }
        R.id.action_new_session -> {
            viewModel.startNewSession()
            true
        }
        else -> super.onOptionsItemSelected(item)
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
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) viewModel.renameCurrentSession(newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechManager.destroy()
        _binding = null
    }
}
