package com.vonnegut.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Wraps Android SpeechRecognizer for voice-to-text input.
 * All callbacks arrive on the main thread.
 *
 * Lifecycle: call [destroy] when the owning Fragment is destroyed.
 */
class SpeechInputManager(private val context: Context) {

    interface Listener {
        fun onListeningStarted()
        fun onPartialResult(text: String)
        fun onResult(text: String)
        fun onError(message: String)
        fun onListeningStopped()
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var isListening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun setListener(l: Listener) {
        listener = l
    }

    fun startListening() {
        if (isListening) return
        if (!isAvailable()) {
            listener?.onError("Speech recognition is not available on this device.")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer?.startListening(intent)
        isListening = true
        listener?.onListeningStarted()
    }

    fun stopListening() {
        if (!isListening) return
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        listener = null
        isListening = false
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isListening = false
            listener?.onListeningStopped()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val results = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!results.isNullOrEmpty()) {
                listener?.onPartialResult(results[0])
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            listener?.onListeningStopped()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                listener?.onResult(matches[0])
            } else {
                listener?.onError("No speech recognised.")
            }
        }

        override fun onError(error: Int) {
            isListening = false
            listener?.onListeningStopped()
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_CLIENT -> "Client error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                SpeechRecognizer.ERROR_NETWORK -> "Network error."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognised."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser busy."
                SpeechRecognizer.ERROR_SERVER -> "Server error."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                else -> "Speech recognition error ($error)."
            }
            Log.w(TAG, "SpeechRecognizer error: $message (code=$error)")
            listener?.onError(message)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "SpeechInputManager"
    }
}
