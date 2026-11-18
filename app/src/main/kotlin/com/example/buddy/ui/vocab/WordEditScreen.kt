package com.example.buddy.ui.vocab

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.example.buddy.R
import com.example.buddy.ui.*

class WordEditScreen(act: UiActivity, private val entryId: Long, private val senseId: Long?) : Screen(act) {
    override fun title() = if (senseId == null) t("Новое значение", "New meaning", "Yangi ma'no") else t("Изменить значение", "Edit meaning", "Ma'noni tahrirlash")

    private val relationKinds = listOf("synonym", "antonym", "collocation")

    private fun relationLabel(kind: String) = when (kind) {
        "synonym" -> t("Синонимы через запятую", "Synonyms, comma-separated", "Sinonimlar, vergul bilan")
        "antonym" -> t("Антонимы через запятую", "Antonyms, comma-separated", "Antonimlar, vergul bilan")
        else -> t("Сочетания через запятую", "Collocations, comma-separated", "Birikmalar, vergul bilan")
    }

    private fun sameSense(a: SenseData, b: SenseData) =
        a.pos.trim() == b.pos.trim() && a.definition.trim() == b.definition.trim() &&
            a.translations.map { it.trim() } == b.translations && a.examples.map { it.text.trim() } == b.examples.map { it.text }

    private fun splitList(s: String) = s.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    override fun render(c: LinearLayout) {
        val d = VocabDb.detail(ctx, entryId)
        if (d == null) {
            c.add(ctx.text(t("Слово не найдено", "Word not found", "So'z topilmadi"), T.Body), MATCH, WRAP, top = 16)
            return
        }
        val sense = senseId?.let { id -> d.senses.firstOrNull { it.id == id } }
        if (senseId != null && sense == null) {
            c.add(ctx.text(t("Значение уже удалено", "This meaning was already deleted", "Bu ma'no allaqachon o'chirilgan"), T.Body), MATCH, WRAP, top = 16)
            return
        }
        val data = sense?.data
        c.add(ctx.text(d.entry.original, T.Title), MATCH, WRAP, top = 8, bottom = 4)
        if (sense != null) {
            val state = when {
                sense.verified -> t("Проверено вами", "Verified by you", "Siz tasdiqlagansiz")
                sense.origin == "edited" -> t("Изменено вами, ИИ эту версию не перепишет", "Edited by you, AI will not overwrite it", "Siz o'zgartirgansiz, AI uni qayta yozmaydi")
                sense.origin == "user" -> t("Добавлено вами", "Added by you", "Siz qo'shgansiz")
                else -> t("Предложено ИИ, проверьте и исправьте при необходимости", "Suggested by AI, check and fix it if needed", "AI taklifi, tekshirib, kerak bo'lsa tuzating")
            }
            c.add(ctx.text(state, T.Small), MATCH, WRAP, bottom = 12)
        }

        c.add(ctx.section(t("Значение", "Meaning", "Ma'no"), R.drawable.ic_edit))
        val translations = ctx.field(t("Переводы через запятую", "Translations, comma-separated", "Tarjimalar, vergul bilan"), data?.translations?.joinToString(", ") ?: "")
        c.add(translations, MATCH, WRAP, bottom = 8)
        val pos = ctx.field(t("Часть речи (noun, verb…)", "Part of speech (noun, verb…)", "So'z turkumi (noun, verb…)"), data?.pos ?: "")
        c.add(pos, MATCH, WRAP, bottom = 8)
        val definition = ctx.field(t("Определение", "Definition", "Ta'rif"), data?.definition ?: "", multiline = true)
        c.add(definition, MATCH, WRAP, bottom = 8)
        val examples = ctx.field(t("Примеры, по одному в строке", "Examples, one per line", "Misollar, har biri alohida qatorda"), data?.examples?.joinToString("\n") { it.text } ?: "", multiline = true)
        c.add(examples, MATCH, WRAP, bottom = 8)
        var verified = sense?.verified ?: false
        c.add(ctx.switchRow(t("Проверено", "Verified", "Tasdiqlangan"), t("ИИ никогда не изменит это значение", "AI will never change this meaning", "AI bu ma'noni hech qachon o'zgartirmaydi"), verified) { verified = it }, MATCH, WRAP, bottom = 8)

        c.add(ctx.section(t("Слово целиком", "Whole word", "Butun so'z"), R.drawable.ic_edit))
        val ipa = ctx.field(t("Транскрипция (IPA)", "Pronunciation (IPA)", "Talaffuz (IPA)"), d.ipa)
        c.add(ipa, MATCH, WRAP, bottom = 8)
        val relFields = LinkedHashMap<String, Pair<EditText, String>>()
        for (k in relationKinds) {
            val initial = d.relations[k].orEmpty().joinToString(", ")
            val f = ctx.field(relationLabel(k), initial)
            relFields[k] = f to initial
            c.add(f, MATCH, WRAP, bottom = 8)
        }

        val save = ctx.button(t("Сохранить", "Save", "Saqlash"), BtnStyle.Filled, R.drawable.ic_check) {
            val tr = splitList(translations.text.toString())
            val def = definition.text.toString().trim()
            if (tr.isEmpty() && def.isEmpty()) return@button
            val old = data?.examples.orEmpty().associateBy { it.text.trim() }
            val ex = examples.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.map { old[it] ?: ExampleData(it, "", "") }
            val sd = SenseData(pos.text.toString().trim(), def, data?.level ?: "", data?.usage ?: "", data?.formality ?: "", tr, ex)
            VocabDb.inTransaction(ctx) {
                val sid = when {
                    senseId == null -> VocabDb.addSense(ctx, entryId, sd)
                    data != null && sameSense(data, sd) -> senseId
                    else -> { VocabDb.updateSense(ctx, senseId, sd); senseId }
                }
                if (verified != (sense?.verified ?: false)) VocabDb.setVerified(ctx, sid, verified)
                val newIpa = ipa.text.toString().trim()
                if (newIpa != d.ipa) VocabDb.setPron(ctx, entryId, newIpa)
                for ((k, p) in relFields) {
                    val now = p.first.text.toString()
                    if (splitList(now) != splitList(p.second)) VocabDb.setRelations(ctx, entryId, k, splitList(now))
                }
            }
            ctx.toast(t("Сохранено", "Saved", "Saqlandi"))
            act.pop()
        }
        fun syncSave() {
            val ok = splitList(translations.text.toString()).isNotEmpty() || definition.text.toString().isNotBlank()
            save.isEnabled = ok
            save.alpha = if (ok) 1f else 0.4f
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, n: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, n: Int) {}
            override fun afterTextChanged(s: Editable?) = syncSave()
        }
        translations.addTextChangedListener(watcher)
        definition.addTextChangedListener(watcher)
        syncSave()
        c.add(save, MATCH, WRAP, top = 8)
        c.add(ctx.text(t("Нужен хотя бы один перевод или определение", "At least one translation or a definition is needed", "Kamida bitta tarjima yoki ta'rif kerak"), T.Small), MATCH, WRAP, top = 4)

        if (sense != null && sense.canRestore && sense.origin == "edited") {
            c.add(ctx.button(t("Вернуть версию ИИ", "Restore AI version", "AI variantini qaytarish"), BtnStyle.Outline) {
                ctx.confirm(
                    t("Вернуть версию ИИ?", "Restore the AI version?", "AI varianti qaytarilsinmi?"),
                    t("Ваши правки этого значения будут заменены исходным текстом ИИ.", "Your edits to this meaning are replaced with the original AI text.", "Bu ma'nodagi tuzatishlaringiz AI ning asl matni bilan almashtiriladi."),
                    t("Вернуть", "Restore", "Qaytarish"), t("Отмена", "Cancel", "Bekor qilish")
                ) {
                    if (VocabDb.restoreAi(ctx, sense.id)) act.pop() else ctx.toast(t("Версия ИИ недоступна", "The AI version is not available", "AI varianti mavjud emas"))
                }
            }, MATCH, WRAP, top = 12)
        }
        if (sense != null) {
            c.add(ctx.button(t("Удалить значение", "Delete meaning", "Ma'noni o'chirish"), BtnStyle.Text, R.drawable.ic_delete) {
                val msg = if (sense.hasHistory) t("История повторений этого значения тоже удалится.", "The practice history of this meaning is deleted too.", "Bu ma'noning mashq tarixi ham o'chiriladi.")
                else t("Это значение исчезнет из карточки.", "This meaning is removed from the card.", "Bu ma'no kartochkadan o'chiriladi.")
                ctx.confirm(t("Удалить значение?", "Delete this meaning?", "Ma'no o'chirilsinmi?"), msg, t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")) {
                    VocabDb.deleteSense(ctx, sense.id)
                    act.pop()
                }
            }, MATCH, WRAP, top = 8)
        }
        c.add(View(ctx), MATCH, ctx.dp(24))
    }
}
