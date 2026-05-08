package com.rokid.style.translate

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Wraps [TextToSpeech] with a coroutine-friendly API.
 *
 * Mirrors AVSpeechSynthesizer usage from the iOS source.
 * Call [speak] to enqueue an utterance and suspend until it finishes speaking
 * (or until the coroutine is cancelled).
 */
class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    /** Initialise the TTS engine.  Safe to call multiple times. */
    fun init(onReady: (() -> Unit)? = null) {
        if (tts != null) {
            onReady?.invoke()
            return
        }
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                Log.d(TAG, "TTS engine initialised")
                onReady?.invoke()
            } else {
                Log.e(TAG, "TTS initialisation failed with status $status")
            }
        }
    }

    /**
     * Speak [text] in [localeTag] (BCP-47, e.g. "en-US", "es-ES", "it-IT").
     * Suspends until the utterance finishes or the coroutine is cancelled.
     */
    suspend fun speak(text: String, localeTag: String) {
        val engine = tts ?: run {
            Log.w(TAG, "TTS not initialised — skipping speech")
            return
        }
        if (!isReady) {
            Log.w(TAG, "TTS not ready — skipping speech")
            return
        }

        val locale = Locale.forLanguageTag(localeTag)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS locale $localeTag not supported; falling back to device default")
            engine.setLanguage(Locale.getDefault())
        }

        val utteranceId = UUID.randomUUID().toString()

        suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(Unit)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        Log.e(TAG, "TTS error for utterance $id")
                        cont.resume(Unit)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == utteranceId && cont.isActive) {
                        Log.e(TAG, "TTS error $errorCode for utterance $utteranceId")
                        cont.resume(Unit)
                    }
                }
            })

            val speakResult = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (speakResult == TextToSpeech.ERROR) {
                Log.e(TAG, "engine.speak() returned ERROR")
                if (cont.isActive) cont.resume(Unit)
            }

            cont.invokeOnCancellation {
                engine.stop()
            }
        }
    }

    /** Stop any ongoing speech immediately. */
    fun stop() {
        tts?.stop()
    }

    /** Release TTS resources. Must be called when the owning component is destroyed. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
