package com.vonnegut.app.ui.model

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    private val importModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importModel(uri, null)
        }
    }

    private val importFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importModelsFromTree(uri)
        }
    }

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

        binding.textModelsDir.text = viewModel.modelsDir.absolutePath
        binding.textSourceDir.text = viewModel.sourceTreeUri.value ?: "No external folder selected"
        binding.buttonImportModel.setOnClickListener {
            importModelLauncher.launch(arrayOf("*/*"))
        }
        binding.buttonScanFolder.setOnClickListener {
            importFolderLauncher.launch(null)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.installedModels.collect { models ->
                        adapter.submitList(models)
                        binding.emptyState.isVisible = models.isEmpty()
                    }
                }
                launch {
                    viewModel.sourceTreeUri.collect { uri ->
                        binding.textSourceDir.text = uri ?: "No external folder selected"
                    }
                }
                launch {
                    viewModel.statusMessage.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
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
