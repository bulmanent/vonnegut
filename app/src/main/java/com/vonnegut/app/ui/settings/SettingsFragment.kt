package com.vonnegut.app.ui.settings

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.vonnegut.app.R
import com.vonnegut.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.refresh()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Only populate fields once to avoid overwriting user edits
                    if (binding.editSystemPrompt.tag == null) {
                        binding.editSystemPrompt.setText(state.systemPrompt)
                        binding.editCustomInstructions.setText(state.customInstructions)
                        binding.editUserName.setText(state.userName)
                        binding.editUserOccupation.setText(state.userOccupation)
                        binding.editUserLocation.setText(state.userLocation)
                        binding.editUserAge.setText(state.userAge)
                        binding.editUserFamily.setText(state.userFamily)
                        binding.editUserInterests.setText(state.userInterests)
                        binding.editUserTone.setText(state.userTone)
                        binding.editContextWindow.setText(state.contextWindowLimit.toString())
                        binding.editMaxTokens.setText(state.maxResponseTokens.toString())
                        binding.sliderTemperature.value = state.temperature
                        binding.switchDarkTheme.isChecked = state.darkTheme
                        binding.editSystemPrompt.tag = "populated"
                    }
                    binding.textActiveModelPath.text =
                        state.activeModelPath ?: "No active model selected"
                    binding.textModelsDirectoryPath.text = state.modelsDirectoryPath
                    binding.textSourceDirectoryUri.text =
                        state.sourceDirectoryUri ?: "No external model folder selected"
                }
            }
        }

        binding.sliderTemperature.addOnChangeListener { _, value, _ ->
            binding.textTemperatureValue.text = "%.1f".format(value)
        }

        binding.buttonResetSystemPrompt.setOnClickListener {
            viewModel.resetSystemPrompt()
            binding.editSystemPrompt.setText(viewModel.state.value.systemPrompt)
        }

        binding.buttonOpenModelManager.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                findNavController().navigate(R.id.action_settings_to_model_manager)
            }
        }

        binding.buttonSave.setOnClickListener {
            saveSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun saveSettings() {
        val contextWindow = binding.editContextWindow.text.toString().toIntOrNull()
        val maxTokens = binding.editMaxTokens.text.toString().toIntOrNull()

        if (contextWindow == null || contextWindow < 512) {
            binding.editContextWindow.error = "Enter a value ≥ 512"
            return
        }
        if (maxTokens == null || maxTokens < 64) {
            binding.editMaxTokens.error = "Enter a value ≥ 64"
            return
        }

        val darkTheme = binding.switchDarkTheme.isChecked
        viewModel.saveAll(
            systemPrompt = binding.editSystemPrompt.text.toString(),
            customInstructions = binding.editCustomInstructions.text.toString(),
            userName = binding.editUserName.text.toString(),
            userOccupation = binding.editUserOccupation.text.toString(),
            userLocation = binding.editUserLocation.text.toString(),
            userAge = binding.editUserAge.text.toString(),
            userFamily = binding.editUserFamily.text.toString(),
            userInterests = binding.editUserInterests.text.toString(),
            userTone = binding.editUserTone.text.toString(),
            contextWindowLimit = contextWindow,
            maxResponseTokens = maxTokens,
            temperature = binding.sliderTemperature.value,
            darkTheme = darkTheme
        )

        AppCompatDelegate.setDefaultNightMode(
            if (darkTheme) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        Toast.makeText(requireContext(), "Settings saved.", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
