package com.vonnegut.app

import android.app.Application
import com.vonnegut.app.data.db.AppDatabase
import com.vonnegut.app.data.preferences.UserPreferences
import com.vonnegut.app.data.repository.ChatRepository
import com.vonnegut.app.inference.InferenceEngine

class VonnegutApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferences: UserPreferences by lazy { UserPreferences(this) }
    val inferenceEngine: InferenceEngine by lazy { InferenceEngine() }
    val chatRepository: ChatRepository by lazy {
        ChatRepository(database.sessionDao(), database.messageDao())
    }
}
