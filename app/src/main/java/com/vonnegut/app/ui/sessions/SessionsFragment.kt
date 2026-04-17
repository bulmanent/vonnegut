package com.vonnegut.app.ui.sessions

import android.os.Bundle
import android.view.*
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.vonnegut.app.R
import com.vonnegut.app.VonnegutApplication
import com.vonnegut.app.data.db.entities.Session
import com.vonnegut.app.databinding.FragmentSessionsBinding
import kotlinx.coroutines.launch

class SessionsFragment : Fragment() {

    private var _binding: FragmentSessionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SessionsViewModel by viewModels()
    private lateinit var sessionAdapter: SessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionAdapter = SessionAdapter(
            onSessionClick = { session -> openSession(session) },
            onSessionLongClick = { session -> showSessionOptions(session) },
            onSessionDelete = { session -> confirmDeleteSession(session) }
        )

        binding.recyclerSessions.apply {
            adapter = sessionAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        binding.buttonNewSession.setOnClickListener {
            viewModel.createNewSession { session -> openSession(session) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessions.collect { sessions ->
                    sessionAdapter.submitList(sessions)
                    binding.emptyState.isVisible = sessions.isEmpty()
                }
            }
        }
    }

    private fun openSession(session: Session) {
        val app = requireActivity().application as VonnegutApplication
        app.preferences.currentSessionId = session.id
        findNavController().navigate(R.id.action_sessions_to_chat)
    }

    private fun showSessionOptions(session: Session) {
        val options = arrayOf("Prune messages…", "Clear all messages", "Delete session")
        AlertDialog.Builder(requireContext())
            .setTitle(session.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPruneDialog(session)
                    1 -> confirmClearSession(session)
                    2 -> confirmDeleteSession(session)
                }
            }
            .show()
    }

    private fun showPruneDialog(session: Session) {
        val input = EditText(requireContext()).apply {
            setText(session.pruneLimit.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Keep last N messages")
            .setView(input)
            .setPositiveButton("Prune") { _, _ ->
                val n = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                if (n > 0) viewModel.pruneSession(session, n)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClearSession(session: Session) {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear all messages")
            .setMessage("All messages in \"${session.name}\" will be deleted.")
            .setPositiveButton("Clear") { _, _ -> viewModel.clearSession(session) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteSession(session: Session) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete session")
            .setMessage("\"${session.name}\" will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteSession(session) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
