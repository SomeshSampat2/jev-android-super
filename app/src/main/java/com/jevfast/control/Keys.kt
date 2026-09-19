package com.jevfast.control

import android.content.Context
import android.content.SharedPreferences

object Keys {
    private const val PREFS = "jevfast_prefs"
    private const val TYPESAFE = "typesafe_key"
    private const val OPENROUTER = "openrouter_key"
    private const val GEMINI = "gemini_key"
    private const val JEV_MODEL = "jev_model"
    private const val TEXT_MODEL = "text_model"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun typeSafe(ctx: Context): String =
        prefs(ctx).getString(TYPESAFE, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_TYPESAFE_KEY

    fun openRouter(ctx: Context): String =
        prefs(ctx).getString(OPENROUTER, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_OPENROUTER_KEY

    fun gemini(ctx: Context): String =
        prefs(ctx).getString(GEMINI, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_GEMINI_KEY

    fun jevModel(ctx: Context): String =
        prefs(ctx).getString(JEV_MODEL, null)?.takeIf { it.isNotBlank() } ?: "jev-latest"

    fun textModel(ctx: Context): String =
        prefs(ctx).getString(TEXT_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: "nvidia/nemotron-3-ultra-550b-a55b:free"

    fun save(ctx: Context, typeSafe: String? = null, openRouter: String? = null,
             gemini: String? = null, jevModel: String? = null, textModel: String? = null) {
        prefs(ctx).edit().apply {
            typeSafe?.let { putString(TYPESAFE, it.trim()) }
            openRouter?.let { putString(OPENROUTER, it.trim()) }
            gemini?.let { putString(GEMINI, it.trim()) }
            jevModel?.let { putString(JEV_MODEL, it.trim()) }
            textModel?.let { putString(TEXT_MODEL, it.trim()) }
        }.apply()
    }
}
