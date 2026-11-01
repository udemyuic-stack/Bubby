package com.example.buddy.ui

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.buddy.R
import com.example.buddy.Stickers

class FunScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Игры и стикеры", "Games & stickers", "O'yinlar va stikerlar")
    private val moods = listOf("happy", "love", "laugh", "cool", "sleepy", "crying", "excited", "wink")
    private val images = HashMap<String, android.graphics.Bitmap>()
    private var started = false

    /** Name and, for the games that play inside the app (instant answers), their kind; the others are played with the assistant. */
    private fun games() = listOf(
        t("Угадай число", "Guess the number", "Sonni top") to "guess",
        t("Камень-ножницы-бумага", "Rock-paper-scissors", "Tosh-qaychi-qog'oz") to "rps",
        t("Комната Bubby", "Bubby's room", "Bubby xonasi") to "room",
        t("Космо-раннер", "Space runner", "Kosmo-yuguruvchi") to "runner",
        t("Битва с Bubby", "Bubby Battle", "Bubby jangi") to "battle",
        t("Слово дня", "Daily word", "Kun so'zi") to "word",
        t("Английский", "English", "Inglizcha") to "english",
        t("Словарь", "Vocabulary", "Lug'at") to "vocab",
        t("Найди пару", "Memory match", "Juftini top") to "memory",
        t("20 вопросов", "20 questions", "20 ta savol") to "twenty",
        t("Загадки", "Riddles", "Topishmoqlar") to null,
        t("Викторина", "Quiz", "Viktorina") to null,
        t("Слова-цепочка", "Word chain", "So'z zanjiri") to null,
    )

    private val tiles = HashMap<String, ImageView>()

    /**
     * The eight stickers are drawn one after another on a single thread (each needs ~20 MB of scratch bitmaps, eight at
     * once could exhaust a small phone's heap) and handed over as bitmaps (no PNG encode and decode); each one fills its
     * own tile instead of redrawing the whole page.
     */
    override fun onShow() {
        if (started) return
        started = true
        Thread {
            for (m in moods) {
                val bmp = try { thumb(m) } catch (_: Throwable) { null } ?: continue
                act.runOnUiThread { images[m] = bmp; tiles[m]?.setImageBitmap(bmp) }
            }
        }.start()
    }

    /** Grid thumbnails are drawn at a third of the sharing size and kept for the next visit (same pet, outfit and look). */
    private fun thumb(mood: String): android.graphics.Bitmap {
        val key = mood + "|" + Stickers.prefOutfitSet(ctx).sorted().joinToString(",") + "|" + Backend.str(ctx, "species", "glass") + "|" + Backend.str(ctx, "theme", "glass")
        synchronized(thumbs) { thumbs[key]?.let { return it } }
        val b = Stickers.preview(ctx, mood)
        synchronized(thumbs) { if (thumbs.size > 24) thumbs.clear(); thumbs[key] = b }
        return b
    }

    private fun share(mood: String) {
        Thread {
            val err = Stickers.share(ctx, mood, "")
            if (err != null) act.runOnUiThread { ctx.toast(err) }
        }.start()
    }

    private fun dailyCard(c: LinearLayout) {
        val can = DailyStreak.canClaim(ctx)
        val done = DailyStreak.doneCount(ctx)
        val streak = DailyStreak.streak(ctx)
        val chest = ChestView(ctx).apply { ready = can; big = can && done == DailyStreak.ladder.size - 1 }
        c.add(ctx.section(t("Ежедневная награда", "Daily reward", "Kunlik sovg'a"), R.drawable.ic_play))
        c.add(ctx.card(16) {
            add(ctx.row(Gravity.CENTER_VERTICAL) {
                add(chest, ctx.dp(130), ctx.dp(110), end = 12)
                add(ctx.col {
                    add(ctx.text("🔥 " + t("Серия: $streak", "Streak: $streak", "Seriya: $streak"), T.Title).apply { textSize = 20f }, MATCH, WRAP)
                    add(ctx.text(
                        if (can) t("Сегодня: +${DailyStreak.ladder[done]} ⭐", "Today: +${DailyStreak.ladder[done]} ⭐", "Bugun: +${DailyStreak.ladder[done]} ⭐")
                        else t("Приходи завтра за новой наградой", "Come back tomorrow for the next reward", "Keyingi sovg'a uchun ertaga keling"),
                        T.Small), MATCH, WRAP, top = 2, bottom = 8)
                    if (can) add(ctx.button(t("Забрать", "Claim", "Olish")) {
                        val r = DailyStreak.claim(ctx)
                        chest.openUp()
                        ctx.toast("+$r ⭐")
                        chest.postDelayed({ refresh() }, 1400)
                    }, WRAP, WRAP)
                }, 0, WRAP, 1f)
            }, MATCH, WRAP)
            add(ctx.row(Gravity.CENTER) {
                DailyStreak.ladder.forEachIndexed { i, r ->
                    val isDone = i < done || (!can && i == done - 1)
                    val today = can && i == done
                    val cell = ctx.col {
                        gravity = Gravity.CENTER
                        minimumHeight = ctx.dp(48)
                        background = ctx.rounded(
                            if (isDone) P.alpha(P.success, 120) else P.alpha(P.sky, if (today) 110 else 40), 16f,
                            if (today) P.orange else 0, 2f)
                        add(ctx.text(if (isDone) "✓" else "⭐$r", T.Label).apply { gravity = Gravity.CENTER }, WRAP, WRAP)
                        add(ctx.text("${i + 1}", T.Small).apply { gravity = Gravity.CENTER; textSize = 10.5f }, WRAP, WRAP)
                    }
                    addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(ctx.dp(2), 0, ctx.dp(2), 0) })
                }
            }, MATCH, WRAP, top = 12)
        }, MATCH, WRAP, bottom = 6)
    }

    override fun render(c: LinearLayout) {
        dailyCard(c)
        val accents = intArrayOf(P.orange, P.violet, P.rose, P.teal, P.sky)
        val all = games()
        val sections = listOf(
            t("Играть в приложении", "Play in the app", "Ilovada o'ynash") to all.filter { it.second != null },
            t("Играть голосом с Bubby", "Play by voice with Bubby", "Bubby bilan ovozda o'ynash") to all.filter { it.second == null },
        )
        for ((title, list) in sections) {
        val voice = list.firstOrNull()?.second == null
        c.add(ctx.section(title, if (voice) R.drawable.ic_mic else R.drawable.ic_play))
        val flow = FlowLayout(ctx)
        list.forEachIndexed { i, (g, kind) ->
            flow.addView(ctx.row(Gravity.CENTER) {
                minimumHeight = ctx.dp(48)
                setPadding(ctx.dp(18), ctx.dp(12), ctx.dp(18), ctx.dp(12))
                background = ctx.rounded(P.alpha(accents[i % accents.size], if (P.dark) 70 else 48), 30f)
                if (voice) add(ctx.icon(R.drawable.ic_mic, P.onSurface, 18), WRAP, WRAP, end = 8)
                add(ctx.text(g, T.Label), WRAP, WRAP)
                press {
                    if (kind == "room") act.push(MatchMapScreen(act)) else if (kind == "runner") act.push(RunnerScreen(act)) else if (kind == "battle") act.push(BattleScreen(act)) else if (kind == "word") act.push(WordScreen(act)) else if (kind == "english") act.push(EnglishScreen(act)) else if (kind == "vocab") act.push(com.example.buddy.ui.vocab.VocabScreen(act)) else if (kind != null) act.push(GameScreen(act, kind))
                    else {
                        val err = Backend.startGame(ctx, g)
                        ctx.toast(err ?: t("Нажмите на Bubby и говорите", "Tap Bubby and speak", "Bubby ni bosing va gapiring"))
                    }
                }
            })
        }
        c.add(flow, MATCH, WRAP, bottom = 6)
        }
        c.add(ctx.section(t("Стикеры (нажмите, чтобы отправить)", "Stickers (tap to send)", "Stikerlar (yuborish uchun bosing)"), R.drawable.ic_face))
        for (rowMoods in moods.chunked(4)) {
            val row = ctx.row(Gravity.CENTER) {}
            for (m in rowMoods) {
                val tile = ctx.col {
                    setPadding(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
                    background = ctx.rounded(P.container, 18f, P.outlineVariant)
                    val img = ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        images[m]?.let { setImageBitmap(it) }
                    }
                    tiles[m] = img
                    add(img, MATCH, MATCH)
                    press {
                        sticker()
                        ctx.confirm(
                            t("Отправить стикер?", "Send this sticker?", "Stiker yuborilsinmi?"),
                            t("Откроется меню отправки.", "The share menu opens next.", "Keyin ulashish menyusi ochiladi."),
                            t("Отправить", "Send", "Yuborish"), t("Отмена", "Cancel", "Bekor qilish")
                        ) { share(m) }
                    }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, ctx.dp(78), 1f).apply { setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4)) })
            }
            c.add(row, MATCH, WRAP)
        }
    }

    private companion object { val thumbs = HashMap<String, android.graphics.Bitmap>() }
}
