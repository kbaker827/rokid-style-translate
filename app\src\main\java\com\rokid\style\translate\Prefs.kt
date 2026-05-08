package com.rokid.style.translate

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences helper — mirrors iOS UserDefaults usage in the Swift source.
 */
class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    /**
     * Language pair: "ES_EN" or "IT_EN".
     * Default is Spanish ↔ English.
     */
    var languagePair: LanguagePair
        get() {
            val raw = prefs.getString(KEY_LANGUAGE_PAIR, LanguagePair.ES_EN.name) ?: LanguagePair.ES_EN.name
            return try {
                LanguagePair.valueOf(raw)
            } catch (e: IllegalArgumentException) {
                LanguagePair.ES_EN
            }
        }
        set(value) = prefs.edit().putString(KEY_LANGUAGE_PAIR, value.name).apply()

    var useCloudTranslation: Boolean
        get() = prefs.getBoolean(KEY_USE_CLOUD, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_CLOUD, value).apply()

    companion object {
        private const val PREFS_NAME = "rokid_translate_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_LANGUAGE_PAIR = "language_pair"
        private const val KEY_USE_CLOUD = "use_cloud_translation"
    }
}

enum class LanguagePair(
    val foreignLocale: String,       // BCP-47 locale for the foreign language
    val foreignLangName: String,     // human-readable name used in Gemini prompt
    val nativeLangName: String       // English
) {
    ES_EN(foreignLocale = "es-ES", foreignLangName = "Spanish",  nativeLangName = "English"),
    IT_EN(foreignLocale = "it-IT", foreignLangName = "Italian",  nativeLangName = "English")
}
