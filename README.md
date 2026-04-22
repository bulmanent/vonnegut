# Vonnegut

An Android app for running local large language models entirely on-device. It provides a chat UI over Google's LiteRT-LM inference runtime, lets the user download and manage `.litertlm` model files (currently Gemma 4 E2B / E4B), stores chat history locally in SQLite via Room, and supports voice input through Android's built-in `SpeechRecognizer`.

No account, no cloud inference, no telemetry. The only network calls are (a) fetching a model manifest and (b) downloading model files from Hugging Face.

## What it does

- Chat with a local LLM in a streaming-token UI.
- Maintain multiple named chat sessions with persistent history.
- Prune old turns per-session to keep context within a configurable window.
- Download quantised Gemma 4 models from Hugging Face, or import `.litertlm` files from a folder on the device.
- Switch between installed models from the toolbar.
- Edit a system prompt, a structured user profile (name, occupation, location, etc.), and free-form custom instructions that are injected into each turn's system message.
- Dictate input via the on-device speech recogniser (mic button in the composer).

## How it works

### High-level architecture

Single-activity app (`MainActivity`) hosting a Jetpack Navigation graph of fragments. Application-scoped singletons live on `VonnegutApplication`:

| Singleton | Purpose |
|---|---|
| `InferenceEngine` | Wraps the LiteRT-LM `Engine`. Holds the currently loaded model. |
| `ChatRepository` | Facade over Room DAOs for sessions and messages. |
| `AppDatabase` | Room database `vonnegut.db` with `sessions` and `messages` tables. |
| `UserPreferences` | Typed wrapper around `SharedPreferences` for all app settings. |

### Fragments and navigation

Start destination: `ChatFragment`. From the toolbar and chat menu the user can reach:

- `SessionsFragment` — list, rename, delete, open chat sessions.
- `ModelManagerFragment` — tabbed host (`ViewPager2`) for `InstalledModelsFragment` and `AvailableModelsFragment`.
- `SettingsFragment` — system prompt, user profile, custom instructions, context window, temperature, etc.

### Inference pipeline

Defined in `inference/InferenceEngine.kt`:

1. `load(context, modelPath, temperature)` constructs an `EngineConfig` with `Backend.CPU()` and `cacheDir = context.cacheDir` (used for the GPU shader cache when the backend is later swapped). The engine is initialised off the main thread on `Dispatchers.IO`.
2. State is exposed as a `StateFlow<State>` with `Idle | Loading | Ready | Generating | Error`. The UI observes this.
3. `generate(...)` builds a `ConversationConfig` with:
   - `systemInstruction`: the concatenated system prompt + user profile block + custom instructions.
   - `initialMessages`: prior turns as a `List<Message>` (role + content).
   - `samplerConfig`: `topK = 40`, `topP = 0.95`, `temperature` from preferences.
   - Calls `engine.createConversation(...).sendMessageAsync(userMessage).collect { ... }` and pushes each streamed `Content.Text` chunk to an `onToken` callback, which the ViewModel appends to the in-progress assistant message.
4. Only one generation is in flight at a time; `ChatViewModel` enforces this.

The backend is currently hard-wired to CPU. The source comments note that `Backend.GPU()` or `Backend.NPU(nativeLibraryDir)` can be substituted in the `EngineConfig`.

### Prompt assembly

`UserPreferences.buildUserProfileBlock()` produces a `[USER PROFILE]` block with labelled lines (NAME, OCCUPATION, LOCATION, AGE, FAMILY, INTERESTS, PREFERRED TONE). `ChatViewModel` combines `systemPrompt + userProfileBlock + customInstructions` into the LiteRT-LM `systemInstruction` for every turn. The default system prompt is a terse operational brief that defines tone, voice-input tolerance, response format, and hard limits.

### Persistence

Room database `vonnegut.db`, version 1, schema export disabled.

- `sessions(id, name, created_at, updated_at, prune_limit)`.
- `messages(id, session_id, role, content, timestamp)` with `ON DELETE CASCADE` on `session_id` and an index on that column.

`ChatRepository` exposes `Flow`-based reads (for live UI updates) and suspending writes. Inserting a message also touches the parent session's `updated_at`. `pruneMessages(sessionId, keepCount)` trims to the most recent N turns according to the session's prune limit.

### Model management

`ModelManagerViewModel` (plus the installed / available fragments) handles all model I/O:

- **Manifest.** A bundled `app/src/main/assets/models.json` is loaded on start (authoritative fallback). A remote manifest URL is wired in but currently a placeholder (`YOUR_USERNAME/vonnegut/main/models.json`). When the URL is real, manifest JSON is fetched via OkHttp, parsed with Gson into `ModelManifest`, and cached to `cacheDir/models_manifest_cache.json`.
- **Download.** `downloadModel(...)` streams the response body to `app.getExternalFilesDir(null)/models/<filename>` with progress ticks via a `StateFlow<DownloadState>`.
- **Import.** `importModel(uri, ...)` copies a single `.litertlm` file picked via SAF to the models directory. `importModelsFromTree(treeUri)` uses `DocumentFile` to scan a folder the user has granted access to and copies all `.litertlm` files in; the grant URI is persisted so the same folder can be reopened.
- **Active model.** Selecting an installed model writes its absolute path to `activeModelPath` in prefs and releases the current engine. `ChatFragment` picks up the change on next use and loads the new model.

### Voice input

`speech/SpeechInputManager` wraps `android.speech.SpeechRecognizer`:

- Uses `RecognizerIntent.LANGUAGE_MODEL_FREE_FORM` with `EXTRA_PARTIAL_RESULTS = true` and `EXTRA_PREFER_OFFLINE = true`.
- Tuned silence thresholds: 1500 ms complete / 1200 ms possibly-complete.
- Surfaces partial transcripts as the user speaks; the final transcript replaces the composer text. Errors are mapped to human-readable messages.

## Technology stack

- **Language / build:** Kotlin 2.2.21, Android Gradle Plugin 8.2.2, KSP 2.2.21-2.0.4, Gradle wrapper. `compileSdk` / `targetSdk` 34, `minSdk` 24. Java 1.8 source/target.
- **Inference:** `com.google.ai.edge.litertlm:litertlm-android:0.10.0`.
- **UI:** AppCompat 1.6.1, Material Components 1.11.0, ConstraintLayout 2.1.4, RecyclerView 1.3.2, SwipeRefreshLayout 1.1.0, ViewPager2 1.0.0, ViewBinding, Navigation Component 2.7.7 (fragment + ui KTX).
- **Lifecycle / async:** Lifecycle 2.7.0 (ViewModel, LiveData, Runtime KTX), Activity KTX 1.8.2, Fragment KTX 1.6.2, Kotlin Coroutines 1.7.3.
- **Persistence:** Room 2.8.4 (runtime + ktx + compiler via KSP).
- **Networking / serialisation:** OkHttp 4.12.0, Gson 2.10.1.
- **Document access:** `androidx.documentfile:documentfile:1.0.1` for folder imports.

## Permissions

From `AndroidManifest.xml`:

- `RECORD_AUDIO` — speech input.
- `INTERNET` — model downloads and manifest fetch.
- `READ_EXTERNAL_STORAGE` (maxSdkVersion 32) — legacy model imports on older devices. On modern devices the Storage Access Framework is used instead and no runtime storage permission is required.

The application declares `largeHeap="true"` and `hardwareAccelerated="true"` because multi-GB quantised models and on-device inference benefit from both.

## Models

Currently bundled in `app/src/main/assets/models.json`:

| Model | Params | Quant | Size | Notes |
|---|---|---|---|---|
| Gemma 4 E2B IT | ~2B effective | INT4 | ~2.58 GB | Recommended mobile default. |
| Gemma 4 E4B IT | ~4B effective | INT4 | ~3.65 GB | Higher quality, more RAM, for high-end devices. |

Both come from the `litert-community` organisation on Hugging Face and ship in the `.litertlm` format consumed by LiteRT-LM. Other `.litertlm` files can be dropped into the app's external-files `models/` directory or imported via the folder picker.

## Build and run

Standard Gradle project, no extra tooling required beyond the Android SDK and a JDK:

```
./gradlew :app:assembleDebug
```

Install the resulting APK, then in-app: open the Model Manager, download or import a model, return to chat.

## Project layout

```
app/
  src/main/
    java/com/vonnegut/app/
      MainActivity.kt
      VonnegutApplication.kt
      data/
        db/          Room database, DAOs, entities
        preferences/ SharedPreferences wrapper (UserPreferences)
        repository/  ChatRepository
      inference/     InferenceEngine (LiteRT-LM wrapper)
      speech/        SpeechInputManager
      ui/
        chat/        ChatFragment, ChatViewModel, MessageAdapter, ChatAttachment
        sessions/    SessionsFragment, SessionsViewModel, SessionAdapter
        model/       ModelManagerFragment + tabs (Installed / Available), ViewModel, adapters
        settings/    SettingsFragment, SettingsViewModel
    res/             Layouts, drawables, navigation graph, themes, strings
    assets/models.json   Bundled model manifest
    AndroidManifest.xml
  build.gradle.kts
build.gradle.kts         Root
settings.gradle.kts      :app module
gradle.properties        Gradle / Kotlin / Android flags
models.json              Mirror of assets/models.json (intended for remote manifest host)
APP_LOG.md               Running change log / current state notes
```

## Notes and known limitations

- Inference backend is pinned to CPU in `InferenceEngine.load()`. GPU/NPU backends are supported by LiteRT-LM but require swapping `Backend.CPU()` for `Backend.GPU()` or `Backend.NPU(nativeLibraryDir)` in the `EngineConfig`.
- The remote manifest URL in `ModelManagerViewModel` is a placeholder; the app falls back to the bundled manifest and surfaces a non-fatal status message.
- No automated tests are present in the repository.
- Release builds are configured with `isMinifyEnabled = false`; enabling R8 would require tuning `proguard-rules.pro` for LiteRT-LM and Room.
