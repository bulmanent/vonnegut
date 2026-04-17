package com.vonnegut.app.ui.model

import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.vonnegut.app.databinding.FragmentAvailableModelsBinding
import kotlinx.coroutines.launch

class AvailableModelsFragment : Fragment() {

    private var _binding: FragmentAvailableModelsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ModelManagerViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: AvailableModelAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAvailableModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AvailableModelAdapter(onDownload = { model -> viewModel.downloadModel(model) })

        binding.recyclerAvailable.apply {
            this.adapter = this@AvailableModelsFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.fetchManifest()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.availableModels.collect { models ->
                        adapter.submitList(models)
                        binding.swipeRefresh.isRefreshing = false
                        binding.emptyState.isVisible = models.isEmpty()
                    }
                }
                launch {
                    viewModel.downloadState.collect { state ->
                        when (state) {
                            is DownloadState.Idle -> {
                                adapter.downloadingFilename = null
                                adapter.notifyDataSetChanged()
                            }
                            is DownloadState.Downloading -> {
                                adapter.downloadingFilename = state.filename
                                adapter.downloadProgress = state.progress
                                adapter.notifyDataSetChanged()
                            }
                            is DownloadState.Error -> {
                                adapter.downloadingFilename = null
                                adapter.notifyDataSetChanged()
                                Snackbar.make(
                                    binding.root,
                                    "Download failed: ${state.message}",
                                    Snackbar.LENGTH_LONG
                                ).setAction("Dismiss") {
                                    viewModel.clearDownloadError()
                                }.show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.manifestError.collect { error ->
                        if (error != null) {
                            binding.textManifestError.text = error
                            binding.textManifestError.isVisible = true
                        } else {
                            binding.textManifestError.isVisible = false
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
