# APP_LOG.md

## Current State
*(Updated each session — not append-only)*

- **Purpose:** Android UI for running local LLMs on-device. Lets users download, manage, and chat with Gemma 4 models via Google's LiteRT-LM inference engine.
- **Architecture:** Single-activity (MainActivity) with Navigation Component. Fragments: ChatFragment, SessionsFragment, ModelManagerFragment (tabs: InstalledModelsFragment + AvailableModelsFragment), SettingsFragment. Application-scoped singletons in VonnegutApplication (InferenceEngine, ChatRepository, UserPreferences).
- **Key libraries/APIs:** LiteRT-LM 0.10.0 (on-device inference, `.litertlm` model files), Room 2.8.4 (chat history), OkHttp 4.12.0 (model downloads), Navigation Component 2.7.7, ViewPager2, Gson, Coroutines/Flow.
- **Working reliably:** [unknown — no test suite observed; infer from code review]
- **Known issues / deferred:** [none recorded yet — see change history for resolved long-press delete bug]
- **Last stable point:** ef5ae04 (various fixes)

---

## Change History
*(Append-only — do not edit or compress past entries)*

### 2026-04-19 — Update new-chat empty state
- **Files changed:** `res/layout/fragment_chat.xml`, added `res/drawable-nodpi/vonn_main.png`
- **What:** Replaced `app_icon` ImageView in the empty state with `vonn_main.png` (200dp square), added quote TextView ("God Damn it, you've got to be kind", max 220dp, centred, titleMedium), and "speak *" label below (italic, labelLarge, muted colour).
- **Why:** User requested new illustration and quote for the new chat screen.
- **Deferred:** None.

### 2026-04-18 — Fix long-press delete in burger menu
- **Files changed:** `ChatFragment.kt`
- **What:** Replaced nested `PopupWindow` (`showDeletePopup`) with an `AlertDialog` shown after dismissing the `ListPopupWindow`. Removed `activeDeletePopup` variable, `setOnDismissListener`, and `showDeletePopup` method. Removed unused `PopupWindow` and `ColorDrawable` imports.
- **Why:** The nested popup (a `PopupWindow` anchored to a view inside a `ListPopupWindow`) was silently failing to show — likely due to window token/touch routing issues. The same confirm-then-delete pattern already works correctly in `SessionsFragment`. User reported haptic feedback on long-press but no delete UI appearing.
- **Deferred:** None.

### 2026-04-18 — Bootstrap
- Created APP_LOG.md from code scan (no prior file existed).
- Files read: app/build.gradle.kts, assets/models.json, InferenceEngine.kt, ChatViewModel.kt, full source tree listing.
- No code changes made.
