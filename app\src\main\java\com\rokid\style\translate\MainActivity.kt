package com.rokid.style.translate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rokid.style.translate.databinding.ActivityMainBinding

/**
 * Minimal launcher activity — a Start/Stop toggle and a link to Settings.
 *
 * No visual display is required for normal glasses operation; this screen
 * exists for initial setup and manual testing.  All audio I/O is handled
 * inside [TranslationService].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServiceRunning = false

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startTranslationService()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied_audio), Toast.LENGTH_LONG).show()
            }
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Proceed regardless; notification is nice-to-have
            ensureAudioPermissionAndStart()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggle.setOnClickListener {
            if (isServiceRunning) stopTranslationService() else requestPermissionsAndStart()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    // -------------------------------------------------------------------------
    // Permission flow
    // -------------------------------------------------------------------------

    private fun requestPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        ensureAudioPermissionAndStart()
    }

    private fun ensureAudioPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED -> {
                startTranslationService()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(this, getString(R.string.permission_rationale_audio), Toast.LENGTH_LONG).show()
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Service control
    // -------------------------------------------------------------------------

    private fun startTranslationService() {
        val prefs = Prefs(this)
        if (prefs.geminiApiKey.isBlank()) {
            Toast.makeText(this, getString(R.string.no_api_key_warning), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        val intent = TranslationService.startIntent(this)
        ContextCompat.startForegroundService(this, intent)
        isServiceRunning = true
        updateUi()
    }

    private fun stopTranslationService() {
        startService(TranslationService.stopIntent(this))
        isServiceRunning = false
        updateUi()
    }

    private fun updateUi() {
        if (isServiceRunning) {
            binding.btnToggle.text = getString(R.string.action_stop)
            binding.tvStatus.text = getString(R.string.status_listening)
        } else {
            binding.btnToggle.text = getString(R.string.action_start)
            binding.tvStatus.text = getString(R.string.status_stopped)
        }

        val pair = Prefs(this).languagePair
        binding.tvLanguagePair.text = getString(R.string.label_language_pair, pair.foreignLangName)
    }
}
