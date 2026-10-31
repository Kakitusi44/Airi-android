package com.airi.assistant

import android.content.Context

object Prefs {
    private const val NAME = "airi_prefs"

    fun getApiKey(ctx: Context) = prefs(ctx).getString("api_key", "") ?: ""
    fun getPersonaName(ctx: Context) = prefs(ctx).getString("persona_name", "Айри") ?: "Айри"
    fun getPersonaDesc(ctx: Context) = prefs(ctx).getString("persona_desc",
        "Ты Айри — умная, добрая и немного игривая голосовая ассистентка. Отвечай кратко и по-русски.") ?: ""
    fun getModel(ctx: Context) = prefs(ctx).getString("model",
        "meta-llama/llama-3.2-11b-vision-instruct:free") ?: ""

    fun setApiKey(ctx: Context, v: String) = prefs(ctx).edit().putString("api_key", v).apply()
    fun setPersonaName(ctx: Context, v: String) = prefs(ctx).edit().putString("persona_name", v).apply()
    fun setPersonaDesc(ctx: Context, v: String) = prefs(ctx).edit().putString("persona_desc", v).apply()
    fun setModel(ctx: Context, v: String) = prefs(ctx).edit().putString("model", v).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
