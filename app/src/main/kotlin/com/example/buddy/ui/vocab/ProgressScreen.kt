package com.example.buddy.ui.vocab

import android.widget.LinearLayout
import com.example.buddy.R
import com.example.buddy.ui.*

class ProgressScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Прогресс", "Progress", "Natija")

    private class Data(val pair: LangPair?, val saved: Int, val funnel: Funnel, val due: Int, val streak: Int, val accuracy: Int?, val hardest: List<Pair<String, Int>>, val types: List<Pair<String, Int>>, val patterns: Set<Pattern>, val sessions: Int)

    private var data: Data? = null
    private var loaded = false

    override fun onShow() {
        if (loaded) return
        loaded = true
        Thread {
            val d = try { load() } catch (_: Exception) { null }
            act.runOnUiThread { data = d; refresh() }
        }.start()
    }

    private fun load(): Data {
        val today = VocabDb.today()
        val id = VocabDb.activePair(ctx)
        val pair = VocabDb.pair(ctx, id)
        val stats = VocabDb.senseStats(ctx, id)
        val rows = VocabDb.attemptRows(ctx, id)
        val recent = rows.filter { today - it.day <= 60 }
        val days = VocabDb.sessionDays(ctx)
        val due = ReviewLoader.dueCount(ctx, today)
        return Data(pair, VocabDb.count(ctx, id), Analytics.funnel(VocabDb.count(ctx, id), stats), due, Analytics.streak(days, today), Analytics.accuracy(rows, today), Analytics.hardest(recent), Analytics.mistakesByType(recent), Analytics.patterns(stats, recent, today), rows.size)
    }

    override fun render(c: LinearLayout) {
        val d = data
        if (d == null) { c.add(ctx.text(t("Считаю…", "Calculating…", "Hisoblanmoqda…"), T.Small), MATCH, WRAP, top = 24); return }
        if (d.pair == null || d.saved == 0) {
            c.add(ctx.emptyState(t("Пока нет данных", "No data yet", "Hozircha ma'lumot yo'q"), t("Добавьте слова и пройдите повторение.", "Add words and do a practice round.", "So'z qo'shing va takrorlang.")), MATCH, WRAP)
            return
        }
        c.add(ctx.text(d.pair.label(), T.Small), MATCH, WRAP, bottom = 6)
        c.add(ctx.card(16) {
            add(ctx.row {
                add(stat(d.streak.toString(), t("дней подряд", "day streak", "kun ketma-ket")), 0, WRAP, 1f)
                add(stat(d.due.toString(), t("к повторению", "to practise", "takrorlash")), 0, WRAP, 1f)
                add(stat(d.accuracy?.let { "$it%" } ?: "–", t("точность 30 дн.", "accuracy 30 d", "aniqlik 30 kun")), 0, WRAP, 1f)
            })
        }, MATCH, WRAP, bottom = 10)

        c.add(ctx.section(t("Как слова запоминаются", "How words are sticking", "So'zlar qanday yodda qolmoqda"), R.drawable.ic_play))
        val f = d.funnel
        c.add(ctx.card(16) {
            bar(this, t("Сохранено", "Saved", "Saqlangan"), f.saved, f.saved, P.teal)
            bar(this, t("Узнаю", "Recognised", "Taniyman"), f.recognized, f.saved, P.sky)
            bar(this, t("Вспоминаю", "Recallable", "Eslay olaman"), f.recallable, f.saved, P.violet)
            bar(this, t("Использую", "Usable", "Ishlata olaman"), f.usable, f.saved, P.orange)
            bar(this, t("Надолго запомнено", "Retained", "Uzoq yodda"), f.retained, f.saved, P.success)
            add(ctx.text(t("Сохранённое слово ещё не значит выученное.", "A saved word is not a learned word.", "Saqlangan so'z hali o'rganilgan so'z emas."), T.Small), MATCH, WRAP, top = 8)
        }, MATCH, WRAP, bottom = 10)

        if (d.patterns.isNotEmpty()) {
            c.add(ctx.section(t("Что стоит подтянуть", "What to work on", "Nimani yaxshilash kerak"), R.drawable.ic_edit))
            c.add(ctx.card(16) {
                for (p in d.patterns) add(ctx.text("• " + patternText(p), T.Body), MATCH, WRAP, bottom = 6)
            }, MATCH, WRAP, bottom = 10)
        }
        if (d.hardest.isNotEmpty() || d.types.isNotEmpty()) {
            c.add(ctx.section(t("Трудности", "Trouble spots", "Qiyin joylar"), R.drawable.ic_edit))
            c.add(ctx.card(16) {
                if (d.hardest.isNotEmpty()) {
                    add(ctx.text(t("Самые трудные слова", "Hardest words", "Eng qiyin so'zlar"), T.Label), MATCH, WRAP, bottom = 4)
                    for ((w, n) in d.hardest) add(ctx.text("$w · ${t("ошибок", "misses", "xato")}: $n", T.Small), MATCH, WRAP)
                }
                if (d.types.isNotEmpty()) {
                    add(ctx.text(t("Где чаще ошибки", "Where you slip most", "Ko'proq xato qilinadigan mashqlar"), T.Label), MATCH, WRAP, top = 10, bottom = 4)
                    for ((ty, pct) in d.types) add(ctx.text(typeName(ty) + " · $pct%", T.Small), MATCH, WRAP)
                }
            }, MATCH, WRAP, bottom = 10)
        }
    }

    private fun stat(v: String, label: String): LinearLayout = ctx.col {
        add(ctx.text(v, T.Title).apply { textSize = 26f; gravity = android.view.Gravity.CENTER }, MATCH, WRAP)
        add(ctx.text(label, T.Small).apply { gravity = android.view.Gravity.CENTER }, MATCH, WRAP)
    }

    private fun bar(box: LinearLayout, label: String, n: Int, max: Int, color: Int) {
        box.add(ctx.row {
            add(ctx.text(label, T.Small), 0, WRAP, 1f)
            add(ctx.text(n.toString(), T.Label), WRAP, WRAP)
        }, MATCH, WRAP, top = 4)
        val track = LinearLayout(ctx).apply { background = ctx.rounded(P.alpha(color, 50), 6f); minimumHeight = ctx.dp(10) }
        val fill = android.view.View(ctx).apply { background = ctx.rounded(color, 6f) }
        val w = if (max <= 0) 0f else n.toFloat() / max
        track.addView(fill, LinearLayout.LayoutParams(0, ctx.dp(10), w))
        track.addView(android.view.View(ctx), LinearLayout.LayoutParams(0, ctx.dp(10), 1f - w))
        box.add(track, MATCH, ctx.dp(10), top = 2)
    }

    private fun patternText(p: Pattern) = when (p) {
        Pattern.RECOGNIZES_NOT_PRODUCES -> t("Узнаёте слова, но не можете написать их сами: больше упражнений на написание.", "You recognise words but cannot write them yet: practise typing them.", "So'zlarni taniysiz, lekin o'zingiz yoza olmaysiz: yozish mashqlarini ko'paytiring.")
        Pattern.FORGETS_AFTER_DAYS -> t("Слова забываются через несколько дней: не пропускайте повторения.", "Words fade after a few days: do not skip the reviews.", "So'zlar bir necha kundan keyin unutilmoqda: takrorlashni o'tkazib yubormang.")
        Pattern.WEAK_LANGUAGE_DETAIL -> t("Слабое место: артикли, чтения или тоны.", "Weak spot: articles, readings or tones.", "Zaif joy: artikl, o'qilish yoki ohang.")
        Pattern.OVERCONFIDENT -> t("Вы часто уверены и ошибаетесь: проверяйте себя перед ответом.", "You are often sure and wrong: test yourself before answering.", "Ko'pincha aniq deb xato qilasiz: javobdan oldin o'zingizni tekshiring.")
    }

    private fun typeName(ty: String) = when (ty) {
        "choice_meaning" -> t("Выбор перевода", "Choose the meaning", "Tarjimani tanlash")
        "choice_word" -> t("Выбор слова", "Choose the word", "So'zni tanlash")
        "recall_meaning" -> t("Перевод по памяти", "Recall the meaning", "Tarjimani eslash")
        "recall_definition" -> t("Слово по определению", "Word from definition", "Ta'rifdan so'z")
        "type_word" -> t("Написание слова", "Typing the word", "So'zni yozish")
        "cloze" -> t("Пропущенное слово", "Fill the gap", "Bo'sh joyni to'ldirish")
        "own_sentence" -> t("Своё предложение", "Own sentence", "O'z gapi")
        "lang_drill" -> t("Особенности языка", "Language detail", "Til xususiyati")
        else -> ty
    }
}
