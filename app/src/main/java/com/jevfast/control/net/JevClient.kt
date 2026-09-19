package com.jevfast.control.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.HttpException
import kotlin.math.abs

/** TypeSafe System One (Jev) via Retrofit — one request picks action + target. */
object JevClient {

    private val api = systemOneApi()

    suspend fun systemOne(
        apiKey: String,
        model: String,
        state: JsonObject,
        questions: JsonObject,
    ): JsonObject {
        val body = buildJsonObject {
            put("model", model)
            put("state", state)
            put("questions", questions)
        }
        var lastError = "unreachable"
        for (attempt in 0..2) {
            try {
                return api.systemOne("Bearer $apiKey", body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val detail = e.response()?.errorBody()?.string()?.take(200).orEmpty()
                if (e.code() in setOf(429, 503, 529) && attempt < 2) {
                    lastError = "HTTP ${e.code()}: $detail"
                } else {
                    throw RuntimeException("Jev HTTP ${e.code()}: $detail")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "connection failed"
            }
            delay(500L * (1 shl attempt))
        }
        throw RuntimeException("Jev request failed: $lastError")
    }

    /** Strict validation (jev-ultrafast): choice in ids, probs cover ids, 0..1, sum≈1. */
    fun validChoice(answer: JsonObject?, ids: Set<String>): Boolean {
        if (answer == null) return false
        val choice = answer["choice"]?.jsonPrimitive?.contentOrNull ?: return false
        val probs = answer["probabilities"]?.jsonObject ?: return false
        if (choice !in ids || probs.size != ids.size) return false
        var sum = 0.0
        for (id in ids) {
            val p = probs[id]?.jsonPrimitive?.doubleOrNull ?: return false
            if (p < 0.0 || p > 1.0) return false
            sum += p
        }
        return abs(sum - 1.0) < 0.05
    }
}
