package com.example.buddy

import android.content.Context
import java.util.Locale

/** UI language for native text. Follows pref `lang` (auto | ru | uz | en); auto follows the phone, anything else falls back to English. */
object Tr {
    fun lang(ctx: Context): String {
        val l = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString("lang", "auto") ?: "auto"
        if (l == "ru" || l == "uz" || l == "en") return l
        return Locale.getDefault().language.let { if (it == "ru" || it == "uz") it else "en" }
    }

    /** Picks the text for the current language; Russian is the base text. */
    fun t(ctx: Context, ru: String, en: String, uz: String): String = when (lang(ctx)) {
        "en" -> en
        "uz" -> uz
        else -> ru
    }
}
