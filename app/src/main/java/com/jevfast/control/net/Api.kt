package com.jevfast.control.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

internal val wireJson = Json { ignoreUnknownKeys = true }

private fun retrofit(baseUrl: String, readTimeoutSec: Long): Retrofit {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(wireJson.asConverterFactory("application/json".toMediaType()))
        .build()
}

/** TypeSafe System One — decisions from structured state and typed questions. */
interface SystemOneApi {
    @POST("v1/systemone")
    suspend fun systemOne(
        @Header("Authorization") auth: String,
        @Body body: JsonObject,
    ): JsonObject
}

/** OpenRouter (OpenAI-compatible) — text helper for TYPE_TEXT only. */
interface OpenRouterApi {
    @POST("api/v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Header("HTTP-Referer") referer: String,
        @Body body: ChatRequest,
    ): ChatResponse
}

@Serializable
data class ChatRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    @SerialName("response_format") val responseFormat: JsonObject? = null,
    val messages: List<ChatMessage>,
)

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
data class ChatChoice(val message: ChatMessage? = null)

internal fun systemOneApi(): SystemOneApi =
    retrofit("https://api.typesafe.ai/", 30).create(SystemOneApi::class.java)

internal fun openRouterApi(): OpenRouterApi =
    retrofit("https://openrouter.ai/", 60).create(OpenRouterApi::class.java)

/**
 * Gemini generateContent — primary text helper (direct, low latency).
 * POST /v1beta/models/{model}:generateContent with x-goog-api-key header.
 */
interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generate(
        @Path("model") model: String,
        @Header("x-goog-api-key") key: String,
        @Body body: GeminiRequest,
    ): GeminiResponse
}

@Serializable
data class GeminiRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenConfig? = null,
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(val text: String? = null)

@Serializable
data class GeminiGenConfig(
    val responseMimeType: String? = null,
    val maxOutputTokens: Int? = null,
    // thinkingBudget 0 disables the reasoning pass on 2.5-flash-lite —
    // fastest possible latency for short structured outputs.
    val thinkingConfig: GeminiThinkingConfig? = null,
)

@Serializable
data class GeminiThinkingConfig(val thinkingBudget: Int)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

internal fun geminiApi(): GeminiApi =
    retrofit("https://generativelanguage.googleapis.com/", 30).create(GeminiApi::class.java)
