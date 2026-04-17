package com.vonnegut.app.ui.model

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.vonnegut.app.databinding.FragmentInstalledModelsBinding
import kotlinx.coroutines.launch

class InstalledModelsFragment : Fragment() {

    private var _binding: FragmentInstalledModelsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ModelManagerViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: InstalledModelAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstalledModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = InstalledModelAdapter(
            onSetActive = { model -> viewModel.setActiveModel(model) },
            onDelete = { model -> confirmDelete(model) }
        )

        binding.recyclerInstalled.apply {
            this.adapter = this@InstalledModelsFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.installedModels.collect { models ->
                    adapter.submitList(models)
                    binding.emptyState.isVisible = models.isEmpty()
                }
            }
        }
    }

    private fun confirmDelete(model: InstalledModel) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete model")
            .setMessage("Delete \"${model.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteModel(model) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
