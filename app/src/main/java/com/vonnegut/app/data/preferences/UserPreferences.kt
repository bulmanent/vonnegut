package com.vonnegut.app.data.preferences

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "vonnegut_prefs"

        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_CUSTOM_INSTRUCTIONS = "custom_instructions"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_OCCUPATION = "user_occupation"
        const val KEY_USER_LOCATION = "user_location"
        const val KEY_USER_AGE = "user_age"
        const val KEY_USER_FAMILY = "user_family"
        const val KEY_USER_INTERESTS = "user_interests"
        const val KEY_USER_TONE = "user_tone"
        const val KEY_ACTIVE_MODEL_PATH = "active_model_path"
        const val KEY_CONTEXT_WINDOW_LIMIT = "context_window_limit"
        const val KEY_MAX_RESPONSE_TOKENS = "max_response_tokens"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_CURRENT_SESSION_ID = "current_session_id"

        val DEFAULT_SYSTEM_PROMPT = """
You are Vonnegut, a personal AI assistant running locally on the user's device.
You are named after Kurt Vonnegut. Carry something of his spirit: clear-eyed,
unsentimental, darkly honest, and quietly compassionate. Never perform this.
Let it inform tone only.

Role: thinking partner, technical collaborator, editorial sounding board.
Not: authority, therapist, motivator, manager.

The user decides what to do next. Do not manage their agency.

Communication rules:
- Direct and precise
- Plain language unless technical depth is requested
- No filler phrases
- No motivational language
- No unsolicited next steps
- No closing questions unless clarification is genuinely required
- Match register: if the user is brief, be brief
- If the user is thinking out loud, reflect clearly, do not redirect

Voice input note: input may be transcribed speech. Expect incomplete
sentences, hesitation artefacts, informal phrasing. Interpret charitably.
One clarifying question only if genuinely needed.

Response format:
- Prose for conversation
- Markdown structure only when it genuinely aids comprehension
- Code: include language tags, explain failure modes, note dependencies
- Creative feedback: diagnose first, never rewrite unless explicitly asked
- Do not pad responses

Uncertainty: say you are not sure rather than confabulate.
Flag knowledge limits plainly.

Hard limits:
Do not roleplay as a different AI.
Do not claim capabilities you lack.
Do not fabricate citations or sources.
        """.trimIndent()
    }

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    var customInstructions: String
        get() = prefs.getString(KEY_CUSTOM_INSTRUCTIONS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_INSTRUCTIONS, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "Neil O'Sullivan") ?: "Neil O'Sullivan"
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userOccupation: String
        get() = prefs.getString(KEY_USER_OCCUPATION, "IT Analyst") ?: "IT Analyst"
        set(value) = prefs.edit().putString(KEY_USER_OCCUPATION, value).apply()

    var userLocation: String
        get() = prefs.getString(KEY_USER_LOCATION, "Cork City, Ireland") ?: "Cork City, Ireland"
        set(value) = prefs.edit().putString(KEY_USER_LOCATION, value).apply()

    var userAge: String
        get() = prefs.getString(KEY_USER_AGE, "61") ?: "61"
        set(value) = prefs.edit().putString(KEY_USER_AGE, value).apply()

    var userFamily: String
        get() = prefs.getString(KEY_USER_FAMILY, "Married to Jean. Son Louie (autistic). Daughter Vivienne (ADHD).")
            ?: "Married to Jean. Son Louie (autistic). Daughter Vivienne (ADHD)."
        set(value) = prefs.edit().putString(KEY_USER_FAMILY, value).apply()

    var userInterests: String
        get() = prefs.getString(KEY_USER_INTERESTS, "AI, Physics, Poetry, Philosophy, Music") ?: "AI, Physics, Poetry, Philosophy, Music"
        set(value) = prefs.edit().putString(KEY_USER_INTERESTS, value).apply()

    var userTone: String
        get() = prefs.getString(KEY_USER_TONE, "Minimalist, accurate, direct") ?: "Minimalist, accurate, direct"
        set(value) = prefs.edit().putString(KEY_USER_TONE, value).apply()

    var activeModelPath: String?
        get() = prefs.getString(KEY_ACTIVE_MODEL_PATH, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_MODEL_PATH, value).apply()

    var contextWindowLimit: Int
        get() = prefs.getInt(KEY_CONTEXT_WINDOW_LIMIT, 4096)
        set(value) = prefs.edit().putInt(KEY_CONTEXT_WINDOW_LIMIT, value).apply()

    var maxResponseTokens: Int
        get() = prefs.getInt(KEY_MAX_RESPONSE_TOKENS, 512)
        set(value) = prefs.edit().putInt(KEY_MAX_RESPONSE_TOKENS, value).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var currentSessionId: Long
        get() = prefs.getLong(KEY_CURRENT_SESSION_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_CURRENT_SESSION_ID, value).apply()

    fun buildUserProfileBlock(): String {
        return buildString {
            appendLine("[USER PROFILE]")
            appendLine("NAME: $userName")
            appendLine("OCCUPATION: $userOccupation")
            appendLine("LOCATION: $userLocation")
            appendLine("AGE: $userAge")
            appendLine("FAMILY: $userFamily")
            appendLine("INTERESTS: $userInterests")
            appendLine("PREFERRED TONE: $userTone")
        }
    }
}
