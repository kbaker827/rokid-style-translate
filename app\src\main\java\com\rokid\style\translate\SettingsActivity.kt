package com.rokid.style.translate

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rokid.style.translate.databinding.ActivitySettingsBinding

/**
 * Simple settings screen.
 *
 * Fields:
 *  - Gemini API key (EditText, password input type)
 *  - Language pair toggle: ES↔EN / IT↔EN
 *
 * Changes are persisted immediately via [Prefs] when the user taps Save.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(applicationContext)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        loadCurrentValues()

        binding.btnSave.setOnClickListener { saveSettings() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadCurrentValues() {
        binding.etApiKey.setText(prefs.geminiApiKey)

        when (prefs.languagePair) {
            LanguagePair.ES_EN -> binding.rgLanguagePair.check(R.id.rbEsEn)
            LanguagePair.IT_EN -> binding.rgLanguagePair.check(R.id.rbItEn)
        }
    }

    private fun saveSettings() {
        val apiKey = binding.etApiKey.text.toString().trim()
        if (apiKey.isBlank()) {
            Toast.makeText(this, getString(R.string.settings_api_key_empty_warning), Toast.LENGTH_SHORT).show()
            return
        }

        prefs.geminiApiKey = apiKey
        prefs.languagePair = when (binding.rgLanguagePair.checkedRadioButtonId) {
            R.id.rbItEn -> LanguagePair.IT_EN
            else        -> LanguagePair.ES_EN
        }

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }
}
