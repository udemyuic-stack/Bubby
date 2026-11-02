package com.example.buddy.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView

class BeatScreen(act: UiActivity) : Screen(act) {
    override val coversScene = true
    override fun title() = t("Bubby Beat", "Bubby Beat", "Bubby Beat")
    override val padH = 12

    private var current = -1
    private var game: BeatGame? = null
    private var synth: BeatSynth? = null
    private var view: BeatView? = null
    private lateinit var panel: LinearLayout
    private lateinit var scoreText: TextView
    private lateinit var healthBar: ProgressBar
    private var playing = false

    private fun stars(i: Int) = Backend.int(ctx, "beat_stars_$i", 0)
    private fun best(i: Int) = Backend.int(ctx, "beat_best_$i", 0)
    private fun unlocked(i: Int) = i == 0 || stars(i - 1) >= 2
    private fun offsetMs() = Backend.int(ctx, "beat_offset", 0)

    private fun rankName(r: BeatRank) = t(r.ru, r.en, r.uz)

    private fun starsText(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

    override fun render(c: LinearLayout) {
        if (current < 0) renderPicker(c) else renderPlay(c)
    }

    private fun renderPicker(c: LinearLayout) {
        stop()
        c.add(ctx.card(pad = 16) {
            add(ctx.text(t("Bubby Beat", "Bubby Beat", "Bubby Beat"), T.Title), MATCH, WRAP)
            add(ctx.text(t("Нажимай на дорожки в такт музыке. 2 звезды открывают следующий ранг.",
                "Tap the lanes in time with the music. 2 stars unlock the next rank.",
                "Musiqa ritmida yo'laklarga bos. 2 yulduz keyingi rankni ochadi."), T.Body), MATCH, WRAP, top = 4)
        }, MATCH, WRAP, bottom = 10)
        for ((i, r) in BeatTracks.ranks.withIndex()) {
            val open = unlocked(i)
            c.add(ctx.card(pad = 14, onClick = if (open) ({ start(i) }) else null) {
                alpha = if (open) 1f else 0.5f
                add(ctx.row {
                    add(ctx.row(Gravity.CENTER) {
                        background = ctx.rounded(r.glow and 0xFFFFFF or 0x55000000, 14f)
                        add(ctx.text(if (open) "${i + 1}" else "🔒", T.Title), WRAP, WRAP)
                    }, ctx.dp(48), ctx.dp(48), end = 12)
                    add(ctx.col {
                        add(ctx.text(rankName(r), T.Label), MATCH, WRAP)
                        add(ctx.text("${r.bpm} BPM · ${r.lanes} " + t("дорожки", "lanes", "yo'lak") + " · " + t("рекорд", "best", "rekord") + " ${best(i)}", T.Small), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    add(ctx.text(starsText(stars(i)), T.Title, P.orange), WRAP, WRAP)
                })
            }, MATCH, WRAP, bottom = 8)
        }
        c.add(ctx.card(pad = 12) {
            add(ctx.switchRow(t("Вибрация при Perfect", "Vibrate on Perfect", "Perfect da tebranish"), null,
                Backend.bool(ctx, "beat_haptic", true)) { Backend.setBool(ctx, "beat_haptic", it) })
        }, MATCH, WRAP, top = 6)
    }

    private fun start(i: Int) {
        current = i
        refresh()
    }

    private fun stop() {
        playing = false
        view?.play(false)
        synth?.release()
        synth = null
        view = null
        game = null
    }

    private fun renderPlay(c: LinearLayout) {
        stop()
        val chart = BeatTracks.build(current)
        val g = BeatGame(chart)
        val s = BeatSynth()
        game = g
        synth = s
        val v = BeatView(ctx, g) { s.position() - offsetMs() / 1000f }.apply {
            contentDescription = t("Игровое поле", "Game field", "O'yin maydoni")
            haptic = Backend.bool(ctx, "beat_haptic", true)
            onHide = { pauseGame() }
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) { s.release() }
            })
        }
        view = v
        g.onEvent = { e, lane -> v.onGameEvent(e, lane); onEvent(e) }

        scoreText = ctx.text("0", T.Title).apply { textSize = 20f; gravity = Gravity.CENTER }
        healthBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(P.success)
        }
        val pauseBtn = ctx.row(Gravity.CENTER) {
            minimumHeight = ctx.dp(48)
            minimumWidth = ctx.dp(48)
            background = ctx.rounded(P.alpha(P.sky, 60), 24f)
            add(ctx.text("❚❚", T.Label), WRAP, WRAP)
            contentDescription = t("Пауза", "Pause", "To'xtatish")
            press { pauseGame() }
        }
        c.add(ctx.row {
            add(pauseBtn, WRAP, WRAP, end = 10)
            add(scoreText, WRAP, WRAP, weight = 1f)
            add(healthBar, ctx.dp(120), ctx.dp(16))
        }, MATCH, WRAP, top = 6, bottom = 8)

        val fieldH = minOf(ctx.dp(560), (ctx.resources.displayMetrics.heightPixels * 0.68f).toInt())
        panel = ctx.card(pad = 18) {}
        val frame = FrameLayout(ctx).apply {
            addView(v, FrameLayout.LayoutParams(MATCH, fieldH))
            addView(panel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER).apply {
                setMargins(ctx.dp(14), ctx.dp(10), ctx.dp(14), ctx.dp(10))
            })
        }
        c.add(frame, MATCH, WRAP)

        showLoading()
        Thread {
            val ok = s.prepare(chart)
            v.post { if (synth === s) { if (ok) showReady() else showError() } }
        }.start()
    }

    private fun onEvent(e: BeatGame.Ev) {
        val g = game ?: return
        scoreText.text = g.score.toString()
        healthBar.progress = g.health
        if (e == BeatGame.Ev.WIN || e == BeatGame.Ev.FAIL) {
            playing = false
            view?.play(false)
            synth?.pause()
            view?.postDelayed({ showResult() }, 500)
        }
    }

    private fun begin() {
        val g = game ?: return
        val s = synth ?: return
        g.clearDown()
        panel.visibility = View.GONE
        playing = true
        s.play()
        view?.play(true)
    }

    private fun pauseGame() {
        if (!playing) return
        playing = false
        game?.clearDown()
        view?.play(false)
        synth?.pause()
        showPause()
    }

    private fun resumeGame() {
        panel.visibility = View.GONE
        playing = true
        synth?.play()
        view?.play(true)
    }

    private fun showPanel(block: LinearLayout.() -> Unit) {
        panel.removeAllViews()
        panel.visibility = View.VISIBLE
        panel.block()
    }

    private fun title(text: String): TextView = ctx.text(text, T.Title).apply { textSize = 20f; gravity = Gravity.CENTER }

    private fun showLoading() = showPanel {
        add(title(t("Готовлю трек…", "Preparing the track…", "Trek tayyorlanmoqda…")), MATCH, WRAP)
    }

    private fun showError() = showPanel {
        add(title(t("Нет звука", "No audio", "Ovoz yo'q")), MATCH, WRAP)
        add(ctx.text(t("Не удалось запустить звук на этом телефоне.", "Could not start audio on this phone.", "Bu telefonda ovozni ishga tushirib bo'lmadi."), T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
        add(ctx.button(t("К рангам", "Back to ranks", "Ranklarga")) { leave() }, MATCH, WRAP, top = 12)
    }

    private fun showReady() = showPanel {
        val r = BeatTracks.ranks[current]
        add(title(rankName(r)), MATCH, WRAP)
        add(ctx.text("${r.bpm} BPM · ${r.lanes} " + t("дорожки", "lanes", "yo'lak"), T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
        add(ctx.button(t("Старт", "Start", "Boshlash")) { begin() }, MATCH, WRAP, top = 12)
    }

    private fun showPause() = showPanel {
        add(title(t("Пауза", "Paused", "To'xtatildi")), MATCH, WRAP)
        val label = ctx.text(t("Задержка звука", "Audio delay", "Ovoz kechikishi") + ": ${offsetMs()} ms", T.Small)
        add(label, MATCH, WRAP, top = 10)
        add(SeekBar(ctx).apply {
            max = 300
            progress = offsetMs() + 150
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    Backend.setInt(ctx, "beat_offset", p - 150)
                    label.text = t("Задержка звука", "Audio delay", "Ovoz kechikishi") + ": ${p - 150} ms"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }, MATCH, ctx.dp(48))
        add(ctx.button(t("Продолжить", "Resume", "Davom etish")) { resumeGame() }, MATCH, WRAP, top = 8)
        add(ctx.button(t("Выйти", "Quit", "Chiqish"), BtnStyle.Outline) { leave() }, MATCH, WRAP, top = 8)
    }

    private fun leave() {
        current = -1
        refresh()
    }

    private fun showResult() {
        val g = game ?: return
        val i = current
        val st = g.stars
        val prev = stars(i)
        val newBest = g.score > best(i)
        if (newBest) Backend.setInt(ctx, "beat_best_$i", g.score)
        if (st > prev) Backend.setInt(ctx, "beat_stars_$i", st)
        val earned = (st - prev).coerceAtLeast(0) * (i + 1) + if (prev == 0 && st >= 1) 2 else 0
        DailyStreak.addStars(ctx, earned)
        showPanel {
            add(title(if (g.won) t("Пройдено!", "Cleared!", "O'tildi!") else t("Не получилось", "Failed", "Bo'lmadi")), MATCH, WRAP)
            add(ctx.text(starsText(st), T.Headline, P.orange).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            add(ctx.text("${g.score} · ${(g.accuracy * 100).toInt()}% · max ${g.maxCombo}", T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            add(ctx.text("Perfect ${g.perfect} · Good ${g.good} · Miss ${g.miss}", T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 2)
            if (newBest) add(ctx.text(t("Новый рекорд!", "New best!", "Yangi rekord!"), T.Small, P.orange).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 2)
            if (earned > 0) add(ctx.text("+$earned ⭐", T.Title, P.orange).apply { gravity = Gravity.CENTER; textSize = 18f }, MATCH, WRAP, top = 8)
            add(ctx.button(t("Ещё раз", "Play again", "Qayta")) { refresh() }, MATCH, WRAP, top = 12)
            add(ctx.button(t("К рангам", "Back to ranks", "Ranklarga"), BtnStyle.Outline) { leave() }, MATCH, WRAP, top = 8)
        }
    }
}
