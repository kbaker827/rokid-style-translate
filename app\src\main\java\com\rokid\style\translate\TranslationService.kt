package com.rokid.style.translate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ForegroundService that hosts the full translation pipeline:
 *
 *   microphone → SpeechManager → LanguageDetector → GeminiClient → TtsManager → speaker
 *
 * Stays alive as long as the glasses are running; shows a persistent
 * "Translator listening…" notification in the status bar.
 *
 * Mirrors TranslatorViewModel.swift from the iOS source, adapted to the
 * Android service lifecycle.
 */
class TranslationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var prefs: Prefs
    private lateinit var speechManager: SpeechManager
    private lateinit var ttsManager: TtsManager
    private val geminiClient = GeminiClient()

    /** Set to true while TTS is speaking to avoid re-triggering recognition on our own voice. */
    private var isSpeaking = false

    /** Set to true once onDestroy has been called. */
    private var serviceDestroyed = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(applicationContext)

        ttsManager = TtsManager(applicationContext)
        ttsManager.init()

        speechManager = SpeechManager(applicationContext, serviceScope)
        speechManager.onFinalResult = { text -> handleSpeechResult(text) }
        speechManager.onError = { msg ->
            Log.e(TAG, "SpeechManager error: $msg")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTranslating()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceDestroyed = true
        serviceScope.cancel()
        speechManager.stop()
        ttsManager.shutdown()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Pipeline
    // -------------------------------------------------------------------------

    private fun startTranslating() {
        startForeground(NOTIFICATION_ID, buildNotification())
        val locale = prefs.languagePair.foreignLocale
        speechManager.start(locale)
        Log.i(TAG, "Translation service started with locale $locale")
    }

    private fun handleSpeechResult(text: String) {
        if (isSpeaking) {
            Log.d(TAG, "Ignoring speech while TTS is active")
            return
        }
        if (text.isBlank()) return

        val pair = prefs.languagePair
        val direction = LanguageDetector.resolveDirection(text, pair)
        val apiKey = prefs.geminiApiKey

        Log.i(TAG, "Translating [$text] (${direction.sourceLang} → ${direction.targetLang})")

        serviceScope.launch(Dispatchers.Main) {
            try {
                isSpeaking = true
                speechManager.stop()   // pause mic while we speak

                val translation = geminiClient.translate(
                    text = text,
                    sourceLang = direction.sourceLang,
                    targetLang = direction.targetLang,
                    apiKey = apiKey
                )

                Log.i(TAG, "Translation: $translation")
                ttsManager.speak(translation, direction.ttsLocale)

            } catch (e: Exception) {
                Log.e(TAG, "Translation failed: ${e.message}")
                // Announce the error aloud so the user is not left in silence
                ttsManager.speak("Translation error. ${e.message?.take(80) ?: ""}", "en-US")
            } finally {
                isSpeaking = false
                // Resume listening after speaking
                if (!serviceDestroyed) {
                    speechManager.start(prefs.languagePair.foreignLocale)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun buildNotification(): Notification {
        createNotificationChannel()

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TranslationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.action_stop), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "TranslationService"
        const val ACTION_START = "com.rokid.style.translate.ACTION_START"
        const val ACTION_STOP  = "com.rokid.style.translate.ACTION_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "translation_channel"

        fun startIntent(context: Context) =
            Intent(context, TranslationService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context) =
            Intent(context, TranslationService::class.java).apply { action = ACTION_STOP }
    }
}
