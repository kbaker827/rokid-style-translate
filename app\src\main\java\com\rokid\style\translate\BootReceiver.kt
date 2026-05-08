package com.rokid.style.translate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Starts the translation service automatically when the device (glasses) boots.
 *
 * Only auto-starts if a Gemini API key has already been configured, so the
 * service never fails silently on first boot before setup.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = Prefs(context)
        if (prefs.geminiApiKey.isNotBlank()) {
            ContextCompat.startForegroundService(context, TranslationService.startIntent(context))
        }
    }
}
