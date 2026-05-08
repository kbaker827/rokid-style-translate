package com.rokid.style.translate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wraps [SpeechRecognizer] with automatic restart logic.
 *
 * Mirrors SpeechService.swift:
 *   - Uses a locale-specific recogniser (es-ES or it-IT)
 *   - Auto-restarts every [RESTART_INTERVAL_MS] ms (Android caps continuous
 *     recognition similar to iOS's 50 s limit)
 *   - Calls [onFinalResult] when the recogniser returns a high-confidence result
 *   - Calls [onError] on unrecoverable failures
 *
 * **Must be created and used on the main thread** — SpeechRecognizer requires it.
 */
class SpeechManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    var onFinalResult: ((String) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var currentLocale: String = "es-ES"
    private var isListening = false
    private var restartJob: Job? = null

    /** Start continuous recognition using the given BCP-47 [locale]. */
    fun start(locale: String) {
        currentLocale = locale
        isListening = true
        startSession()
        scheduleRestart()
    }

    /** Stop recognition and cancel any pending restart. */
    fun stop() {
        isListening = false
        restartJob?.cancel()
        destroyRecognizer()
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun startSession() {
        destroyRecognizer()

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech ($currentLocale)")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
            }

            override fun onError(error: Int) {
                val msg = errorMessage(error)
                Log.w(TAG, "Recognition error: $msg ($error)")

                if (!isListening) return

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Restart immediately — nothing was said
                        restartNow()
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        scope.launch(Dispatchers.Main) {
                            delay(500)
                            restartNow()
                        }
                    }
                    SpeechRecognizer.ERROR_AUDIO,
                    SpeechRecognizer.ERROR_NETWORK -> {
                        scope.launch(Dispatchers.Main) {
                            delay(2_000)
                            restartNow()
                        }
                    }
                    else -> {
                        onError?.invoke("Speech recognition error: $msg")
                        scope.launch(Dispatchers.Main) {
                            delay(3_000)
                            restartNow()
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0].trim()
                    if (text.isNotBlank()) {
                        Log.d(TAG, "Final result: $text")
                        onFinalResult?.invoke(text)
                    }
                }
                if (isListening) restartNow()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    onPartialResult?.invoke(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLocale)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_INDEPENDENT_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Keep listening as long as there is audio
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_000L)
        }

        sr.startListening(intent)
    }

    private fun restartNow() {
        if (!isListening) return
        Log.d(TAG, "Restarting recognition session")
        startSession()
    }

    /**
     * Schedule a forced restart every [RESTART_INTERVAL_MS].
     * This mirrors the 50 s timer in SpeechService.swift.
     */
    private fun scheduleRestart() {
        restartJob?.cancel()
        restartJob = scope.launch(Dispatchers.Main) {
            while (isListening) {
                delay(RESTART_INTERVAL_MS)
                if (isListening) {
                    Log.d(TAG, "Scheduled restart after ${RESTART_INTERVAL_MS / 1000}s")
                    restartNow()
                }
            }
        }
    }

    private fun destroyRecognizer() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    private fun errorMessage(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        else -> "Unknown error ($errorCode)"
    }

    companion object {
        private const val TAG = "SpeechManager"

        /** Force-restart interval (45 s) — slightly under typical server cut-off */
        private const val RESTART_INTERVAL_MS = 45_000L
    }
}
