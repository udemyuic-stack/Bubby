package com.example.buddy

import android.content.Context
import java.util.Locale

/**
 * UI language for native text. Follows pref `lang` (auto | ru | uz | en). Auto follows the language the user last spoke or
 * wrote to Bubby (pref `last_lang`, set by the brain), and the phone's language until they have said anything.
 */
object Tr {
    fun lang(ctx: Context): String {
        val l = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString("lang", "auto") ?: "auto"
        if (l == "ru" || l == "uz" || l == "en") return l
        val spoken = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString("last_lang", null)
        if (spoken == "ru" || spoken == "uz" || spoken == "en") return spoken
        return Locale.getDefault().language.let { if (it == "ru" || it == "uz") it else "en" }
    }

    /** Picks the text for the current language; Russian is the base text. */
    fun t(ctx: Context, ru: String, en: String, uz: String): String = when (lang(ctx)) {
        "en" -> en
        "uz" -> uz
        else -> ru
    }
}
