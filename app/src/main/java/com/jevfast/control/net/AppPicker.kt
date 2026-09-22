package com.jevfast.control.net

import com.jevfast.control.InstalledApp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Goal → app matching via Gemini 2.5 Flash-Lite. The installed-app list is far
 * larger than Jev's 255-option Choice limit and matching is fuzzy ("insta" →
 * Instagram), so this is one structured-output call, once, at run start.
 * Returns null on any failure — the caller proceeds without a launch.
 */
object AppPicker {

    enum class Pick { LAUNCH, STORE, NONE }

    data class Result(val pick: Pick, val packageName: String?)

    private const val GEMINI_MODEL = "gemini-2.5-flash-lite"

    private const val SYSTEM_PROMPT =
        "Pick which installed Android app the goal should start in. Rules: " +
            "action=launch with package_name when the goal names or clearly implies an " +
            "installed app — match loosely by label ('insta' means Instagram) or by " +
            "function ('order a cab' can match a ride app). action=store when the goal " +
            "clearly needs an app that is NOT in `installed_apps`. action=none when the " +
            "goal does not need a specific app opened first or nothing reasonably " +
            "matches. Copy package_name exactly from `installed_apps` — never invent one."

    private val PICK_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("launch"); add("store"); add("none") }
                put("description",
                    "launch = open package_name now; store = goal needs an app that " +
                        "isn't installed, open the Play Store; none = no launch needed")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description",
                    "Exact package name from `installed_apps`. Required when action " +
                        "is launch; empty otherwise.")
            }
        }
        putJsonArray("required") { add("action") }
    }

    private val gemini = geminiApi()

    suspend fun pick(apiKey: String, goal: String, apps: List<InstalledApp>): Result? {
        val user = buildJsonObject {
            put("goal", goal)
            putJsonArray("installed_apps") {
                apps.forEach {
                    add(buildJsonObject {
                        put("label", it.label)
                        put("package", it.packageName)
                    })
                }
            }
        }
        val body = GeminiRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(SYSTEM_PROMPT))),
            contents = listOf(
                GeminiContent(role = "user", parts = listOf(GeminiPart(user.toString())))
            ),
            generationConfig = GeminiGenConfig(
                responseMimeType = "application/json",
                responseJsonSchema = PICK_SCHEMA,
                maxOutputTokens = 128,
                thinkingConfig = GeminiThinkingConfig(thinkingBudget = 0),
            ),
        )
        val text = gemini.generate(GEMINI_MODEL, apiKey, body)
            .candidates.firstOrNull()?.content?.parts
            ?.firstNotNullOfOrNull { it.text } ?: return null
        val obj = runCatching { wireJson.parseToJsonElement(text).jsonObject }
            .getOrNull() ?: return null
        val pkg = obj["package_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        return when (obj["action"]?.jsonPrimitive?.contentOrNull) {
            "launch" -> Result(Pick.LAUNCH, pkg)
            "store" -> Result(Pick.STORE, null)
            else -> Result(Pick.NONE, null)
        }
    }
}
