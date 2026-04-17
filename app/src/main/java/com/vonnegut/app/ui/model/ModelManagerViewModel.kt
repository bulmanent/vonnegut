package com.vonnegut.app.ui.model

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.vonnegut.app.VonnegutApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class InstalledModel(
    val file: File,
    val name: String = file.name,
    val sizeMb: Long = file.length() / (1024 * 1024),
    val lastModified: Long = file.lastModified(),
    val isActive: Boolean = false
)

data class AvailableModel(
    val name: String,
    val filename: String,
    @SerializedName("size_mb") val sizeMb: Int,
    val url: String,
    val quantization: String,
    val notes: String
)

data class ModelManifest(
    val updated: String,
    val models: List<AvailableModel>
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val filename: String, val progress: Int) : DownloadState()
    data class Error(val filename: String, val message: String) : DownloadState()
}

class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VonnegutApplication
    private val prefs = app.preferences
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    private val _installedModels = MutableStateFlow<List<InstalledModel>>(emptyList())
    val installedModels: StateFlow<List<InstalledModel>> = _installedModels

    private val _availableModels = MutableStateFlow<List<AvailableModel>>(emptyList())
    val availableModels: StateFlow<List<AvailableModel>> = _availableModels

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private val _manifestError = MutableStateFlow<String?>(null)
    val manifestError: StateFlow<String?> = _manifestError

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val statusMessage: SharedFlow<String> = _statusMessage

    private val _sourceTreeUri = MutableStateFlow(prefs.modelSourceTreeUri)
    val sourceTreeUri: StateFlow<String?> = _sourceTreeUri

    val modelsDir: File
        get() = File(app.getExternalFilesDir(null), "models").also { it.mkdirs() }

    init {
        refresh()
    }

    fun refresh() {
        refreshInstalled()
        loadBundledManifest()
        if (MANIFEST_URL.contains("YOUR_USERNAME")) {
            _manifestError.value = "Using bundled Gemma manifest. Configure a real manifest URL to refresh remotely."
        } else {
            fetchManifest()
        }
    }

    fun refreshInstalled() {
        val activeModelPath = prefs.activeModelPath
        val files = modelsDir.listFiles { f -> f.extension == "litertlm" }?.toList() ?: emptyList()
        _installedModels.value = files.map { file ->
            InstalledModel(
                file = file,
                isActive = file.absolutePath == activeModelPath
            )
        }.sortedByDescending { it.lastModified }
    }

    fun setActiveModel(model: InstalledModel) {
        prefs.activeModelPath = model.file.absolutePath
        // Reload the inference engine with the new model
        viewModelScope.launch {
            app.inferenceEngine.release()
            // ChatViewModel will reinitialise on next use
        }
        refreshInstalled()
    }

    fun importModel(uri: Uri, displayName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val importedFile = copyUriToModelsDir(uri, displayName)
                prefs.activeModelPath = importedFile.absolutePath
                app.inferenceEngine.release()
                withContext(Dispatchers.Main) {
                    refreshInstalled()
                }
                _statusMessage.tryEmit("Imported and selected ${importedFile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _statusMessage.tryEmit("Import failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun importModelsFromTree(treeUri: Uri) {
        prefs.modelSourceTreeUri = treeUri.toString()
        _sourceTreeUri.value = treeUri.toString()

        viewModelScope.launch(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(app, treeUri)
            if (tree == null || !tree.isDirectory) {
                _statusMessage.tryEmit("Selected folder could not be opened.")
                return@launch
            }

            try {
                val importedFiles = tree.listFiles()
                    .filter { it.isFile && isSupportedModelName(it.name) }
                    .mapNotNull { doc ->
                        runCatching { copyUriToModelsDir(doc.uri, doc.name) }
                            .onFailure { Log.w(TAG, "Skipping ${doc.name}: ${it.message}") }
                            .getOrNull()
                    }

                withContext(Dispatchers.Main) {
                    refreshInstalled()
                }

                when {
                    importedFiles.isEmpty() ->
                        _statusMessage.tryEmit("No .litertlm models found in the selected folder.")
                    importedFiles.size == 1 -> {
                        prefs.activeModelPath = importedFiles.first().absolutePath
                        app.inferenceEngine.release()
                        refreshInstalled()
                        _statusMessage.tryEmit("Imported 1 model and set it active.")
                    }
                    else ->
                        _statusMessage.tryEmit("Imported ${importedFiles.size} models from the selected folder.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Folder import failed", e)
                _statusMessage.tryEmit("Folder import failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun deleteModel(model: InstalledModel) {
        model.file.delete()
        if (prefs.activeModelPath == model.file.absolutePath) {
            prefs.activeModelPath = null
            app.inferenceEngine.release()
        }
        refreshInstalled()
    }

    fun fetchManifest() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _manifestError.value = null
                val request = Request.Builder()
                    .url(MANIFEST_URL)
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@launch
                    val manifest = gson.fromJson(body, ModelManifest::class.java)
                    _availableModels.value = manifest.models
                    // Cache manifest locally
                    File(app.cacheDir, MANIFEST_CACHE_FILE).writeText(body)
                } else {
                    loadCachedManifest()
                    _manifestError.value = "Could not fetch model list (HTTP ${response.code})."
                }
            } catch (e: Exception) {
                Log.w(TAG, "Manifest fetch failed: ${e.message}")
                if (_availableModels.value.isEmpty()) {
                    loadCachedManifest()
                }
                _manifestError.value = "Network unavailable. Showing bundled or cached model list."
            }
        }
    }

    private fun loadBundledManifest() {
        try {
            app.assets.open(BUNDLED_MANIFEST_ASSET).bufferedReader().use { reader ->
                val manifest = gson.fromJson(reader, ModelManifest::class.java)
                _availableModels.value = manifest.models
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bundled manifest: ${e.message}")
        }
    }

    private fun loadCachedManifest() {
        val cache = File(app.cacheDir, MANIFEST_CACHE_FILE)
        if (cache.exists()) {
            try {
                val manifest = gson.fromJson(cache.readText(), ModelManifest::class.java)
                _availableModels.value = manifest.models
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse cached manifest: ${e.message}")
            }
        }
    }

    fun downloadModel(model: AvailableModel) {
        if (_downloadState.value is DownloadState.Downloading) return

        viewModelScope.launch(Dispatchers.IO) {
            val destFile = File(modelsDir, model.filename)
            try {
                _downloadState.value = DownloadState.Downloading(model.filename, 0)

                val request = Request.Builder().url(model.url).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    _downloadState.value = DownloadState.Error(
                        model.filename,
                        "Download failed: HTTP ${response.code}"
                    )
                    return@launch
                }

                val body = response.body ?: run {
                    _downloadState.value = DownloadState.Error(model.filename, "Empty response body.")
                    return@launch
                }

                val contentLength = body.contentLength()
                var bytesRead = 0L

                FileOutputStream(destFile).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                val progress = ((bytesRead * 100) / contentLength).toInt()
                                _downloadState.value = DownloadState.Downloading(model.filename, progress)
                            }
                        }
                    }
                }

                _downloadState.value = DownloadState.Idle
                withContext(Dispatchers.Main) {
                    refreshInstalled()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                destFile.delete()
                _downloadState.value = DownloadState.Error(model.filename, e.message ?: "Unknown error")
            }
        }
    }

    fun clearDownloadError() {
        _downloadState.value = DownloadState.Idle
    }

    private fun copyUriToModelsDir(uri: Uri, displayName: String?): File {
        val fileName = normaliseModelFilename(displayName ?: queryDisplayName(uri))
        val destination = File(modelsDir, fileName)
        val tempFile = File(modelsDir, "$fileName.part")

        app.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Could not open model file.")

        if (destination.exists() && !destination.delete()) {
            tempFile.delete()
            throw IOException("Could not replace existing model ${destination.name}.")
        }
        if (!tempFile.renameTo(destination)) {
            tempFile.delete()
            throw IOException("Could not move imported model into place.")
        }
        return destination
    }

    private fun queryDisplayName(uri: Uri): String? =
        DocumentFile.fromSingleUri(app, uri)?.name ?: uri.lastPathSegment

    private fun normaliseModelFilename(name: String?): String {
        val candidate = (name ?: "imported-model.litertlm").substringAfterLast('/')
        val trimmed = candidate.trim().ifEmpty { "imported-model.litertlm" }
        return if (trimmed.endsWith(SUPPORTED_MODEL_EXTENSION, ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed$SUPPORTED_MODEL_EXTENSION"
        }
    }

    private fun isSupportedModelName(name: String?): Boolean =
        name?.endsWith(SUPPORTED_MODEL_EXTENSION, ignoreCase = true) == true

    companion object {
        private const val TAG = "ModelManagerViewModel"
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/YOUR_USERNAME/vonnegut/main/models.json"
        private const val MANIFEST_CACHE_FILE = "models_manifest_cache.json"
        private const val BUNDLED_MANIFEST_ASSET = "models.json"
        private const val SUPPORTED_MODEL_EXTENSION = ".litertlm"
    }
}
