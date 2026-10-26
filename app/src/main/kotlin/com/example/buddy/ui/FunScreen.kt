package com.example.buddy.ui

import android.graphics.BitmapFactory
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

    private fun games() = listOf(
        t("Угадай число", "Guess the number", "Sonni top"),
        t("Камень-ножницы-бумага", "Rock-paper-scissors", "Tosh-qaychi-qog'oz"),
        t("Загадки", "Riddles", "Topishmoqlar"),
        t("Викторина", "Quiz", "Viktorina"),
        t("Слова-цепочка", "Word chain", "So'z zanjiri"),
    )

    override fun onShow() {
        if (started) return
        started = true
        for (m in moods) Thread {
            try {
                val bytes = Stickers.png(ctx, m, "")
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@Thread
                act.runOnUiThread { images[m] = bmp; refresh() }
            } catch (_: Exception) {}
        }.start()
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
        games().forEachIndexed { i, g ->
            flow.addView(ctx.row(Gravity.CENTER) {
                setPadding(ctx.dp(18), ctx.dp(12), ctx.dp(18), ctx.dp(12))
                background = ctx.rounded(P.alpha(accents[i % accents.size], if (P.dark) 70 else 48), 30f)
                add(ctx.text(g, T.Label), WRAP, WRAP)
                press {
                    val err = Backend.startGame(ctx, g)
                    ctx.toast(err ?: t("Нажмите на Bubby и говорите", "Tap Bubby and speak", "Bubby ni bosing va gapiring"))
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
                    add(img, MATCH, MATCH)
                    press { share(m) }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, ctx.dp(78), 1f).apply { setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4)) })
            }
            c.add(row, MATCH, WRAP)
        }
    }
}
