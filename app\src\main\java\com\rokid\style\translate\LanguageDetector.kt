package com.rokid.style.translate

/**
 * Lightweight lexicon-based language detector.
 *
 * Mirrors LanguageDetector from the iOS source. We intentionally avoid a
 * heavy ML model to keep the APK small and the latency near-zero on the
 * Snapdragon AR1 Gen 1.  We score by counting stop-word / high-frequency
 * word hits rather than n-gram probabilities, which is sufficient for the
 * three-language (es / it / en) scenario.
 */
object LanguageDetector {

    private val spanishWords = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "de", "del", "al", "en", "con", "por", "para", "que",
        "es", "son", "está", "están", "no", "si", "sí", "yo",
        "tú", "él", "ella", "nosotros", "vosotros", "ellos",
        "hola", "gracias", "bien", "cómo", "qué", "dónde",
        "cuando", "porque", "pero", "también", "muy", "más",
        "todo", "todos", "nada", "algo", "hay", "tiene", "tengo"
    )

    private val italianWords = setOf(
        "il", "lo", "la", "i", "gli", "le", "un", "una",
        "di", "del", "della", "dei", "degli", "delle",
        "in", "con", "per", "che", "è", "sono", "non",
        "si", "io", "tu", "lui", "lei", "noi", "voi", "loro",
        "ciao", "grazie", "bene", "come", "cosa", "dove",
        "quando", "perché", "ma", "anche", "molto", "più",
        "tutto", "tutti", "niente", "qualcosa", "ho", "ha",
        "questo", "questa", "quello", "quella", "essere", "avere"
    )

    private val englishWords = setOf(
        "the", "a", "an", "of", "in", "on", "at", "to", "for",
        "is", "are", "was", "were", "be", "been", "have", "has",
        "do", "does", "did", "not", "and", "or", "but", "so",
        "i", "you", "he", "she", "we", "they", "it",
        "hello", "hi", "thanks", "thank", "good", "how", "what",
        "where", "when", "why", "because", "also", "very", "more",
        "all", "nothing", "something", "there", "this", "that"
    )

    /**
     * Returns "es", "it", or "en".
     * Falls back to "en" when the text is ambiguous or empty.
     */
    fun detect(text: String): String {
        if (text.isBlank()) return "en"

        val tokens = text.lowercase()
            .replace(Regex("[^a-záéíóúàèéìòùüñ ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return "en"

        var esScore = 0
        var itScore = 0
        var enScore = 0

        for (token in tokens) {
            if (token in spanishWords) esScore++
            if (token in italianWords) itScore++
            if (token in englishWords) enScore++
        }

        return when {
            esScore > itScore && esScore > enScore -> "es"
            itScore > esScore && itScore > enScore -> "it"
            else -> "en"
        }
    }

    /**
     * Given the active [LanguagePair], decide which direction to translate.
     *
     * Returns a [TranslationDirection] that the pipeline uses to build the
     * Gemini prompt and set the TTS locale.
     */
    fun resolveDirection(text: String, pair: LanguagePair): TranslationDirection {
        val detected = detect(text)
        return if (detected == "en") {
            // Spoken in English → translate to foreign language
            TranslationDirection(
                sourceLang = "English",
                targetLang = pair.foreignLangName,
                ttsLocale = pair.foreignLocale
            )
        } else {
            // Spoken in foreign language → translate to English
            TranslationDirection(
                sourceLang = pair.foreignLangName,
                targetLang = "English",
                ttsLocale = "en-US"
            )
        }
    }
}

data class TranslationDirection(
    val sourceLang: String,
    val targetLang: String,
    val ttsLocale: String
)
