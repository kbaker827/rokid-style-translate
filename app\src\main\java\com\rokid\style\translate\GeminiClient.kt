package com.rokid.style.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP client for the Gemini 2.0 Flash generateContent endpoint.
 *
 * Mirrors GeminiTranslator.swift:
 *   - POST to generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent
 *   - temperature 0.3, maxOutputTokens 256
 *   - prompt: "Translate the following [src] text to [tgt]. Return ONLY the translation."
 */
class GeminiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Translate [text] from [sourceLang] to [targetLang].
     *
     * @throws IOException on network failure or non-2xx response.
     * @throws IllegalArgumentException when [apiKey] is blank.
     */
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is not set. Please add it in Settings.")
        }

        val url = "$BASE_URL?key=$apiKey"

        val prompt = "Translate the following $sourceLang text to $targetLang. " +
                "Return ONLY the translation.\n\n$text"

        val requestJson = buildRequestJson(prompt)
        val body = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        val response = http.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw IOException("Empty response from Gemini API")

        if (!response.isSuccessful) {
            val errorMsg = extractErrorMessage(responseBody)
            throw IOException("Gemini API error ${response.code}: $errorMsg")
        }

        extractTranslation(responseBody)
    }

    private fun buildRequestJson(prompt: String): JSONObject {
        val textPart = JSONObject().put("text", prompt)
        val partsArray = JSONArray().put(textPart)
        val content = JSONObject().put("parts", partsArray)
        val contentsArray = JSONArray().put(content)

        val generationConfig = JSONObject()
            .put("temperature", 0.3)
            .put("maxOutputTokens", 256)

        return JSONObject()
            .put("contents", contentsArray)
            .put("generationConfig", generationConfig)
    }

    private fun extractTranslation(responseBody: String): String {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IOException("No candidates in Gemini response")

        val firstCandidate = candidates.optJSONObject(0)
            ?: throw IOException("Empty candidates array")

        val content = firstCandidate.optJSONObject("content")
            ?: throw IOException("No content in candidate")

        val parts = content.optJSONArray("parts")
            ?: throw IOException("No parts in content")

        val translation = parts.optJSONObject(0)?.optString("text")?.trim()
        if (translation.isNullOrBlank()) {
            throw IOException("Gemini returned an empty translation")
        }
        return translation
    }

    private fun extractErrorMessage(responseBody: String): String {
        return try {
            JSONObject(responseBody)
                .optJSONObject("error")
                ?.optString("message") ?: responseBody
        } catch (e: Exception) {
            responseBody
        }
    }

    companion object {
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
