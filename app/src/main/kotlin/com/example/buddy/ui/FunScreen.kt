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

    override fun render(c: LinearLayout) {
        c.add(ctx.section(t("Игры", "Games", "O'yinlar"), R.drawable.ic_play))
        val flow = FlowLayout(ctx)
        val accents = intArrayOf(P.orange, P.violet, P.rose, P.teal, P.sky)
        games().forEachIndexed { i, (g, kind) ->
            flow.addView(ctx.row(Gravity.CENTER) {
                setPadding(ctx.dp(18), ctx.dp(12), ctx.dp(18), ctx.dp(12))
                background = ctx.rounded(P.alpha(accents[i % accents.size], if (P.dark) 70 else 48), 30f)
                add(ctx.text(g, T.Label), WRAP, WRAP)
                press {
                    if (kind == "room") act.push(RoomScreen(act)) else if (kind != null) act.push(GameScreen(act, kind))
                    else {
                        val err = Backend.startGame(ctx, g)
                        ctx.toast(err ?: t("Нажмите на Bubby и говорите", "Tap Bubby and speak", "Bubby ni bosing va gapiring"))
                    }
                }
            })
        }
        c.add(flow, MATCH, WRAP)
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
                    press { sticker(); share(m) }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, ctx.dp(78), 1f).apply { setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4)) })
            }
            c.add(row, MATCH, WRAP)
        }
    }

    private companion object { val thumbs = HashMap<String, android.graphics.Bitmap>() }
}
