package com.example.buddy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.RemoteViews
import kotlin.math.min

/**
 * Home-screen widgets for the agents running on the computer: what they are doing, how much of the Claude limit is used,
 * how much was done today, and the list of agents. Each is drawn as one bitmap (our own look, drawn in code).
 */
object AgentWidgets {
    private val KINDS = listOf("status", "usage", "done", "agents", "bar", "mini")

    class StatusWidget : Base("status")
    class UsageWidget : Base("usage")
    class DoneWidget : Base("done")
    class AgentsWidget : Base("agents")
    class BarWidget : Base("bar")
    class MiniWidget : Base("mini")

    abstract class Base(private val kind: String) : AppWidgetProvider() {
        override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { for (id in ids) draw(ctx, mgr, id, kind) }
        override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: android.os.Bundle?) { draw(ctx, mgr, id, kind) }
    }

    private fun cls(kind: String): Class<*> = when (kind) {
        "status" -> StatusWidget::class.java; "usage" -> UsageWidget::class.java
        "done" -> DoneWidget::class.java; "bar" -> BarWidget::class.java; "mini" -> MiniWidget::class.java; else -> AgentsWidget::class.java
    }

    /** Redraws every placed widget (called when agents or limits change). */
    fun refresh(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (k in KINDS) for (id in mgr.getAppWidgetIds(ComponentName(ctx, cls(k)))) draw(ctx, mgr, id, k)
    }

    /** A widget picture for the in-app preview (sizes in dp). */
    fun preview(ctx: Context, kind: String, wDp: Int, hDp: Int): Bitmap {
        val d = ctx.resources.displayMetrics.density
        return render(ctx, kind, (wDp * d).toInt(), (hDp * d).toInt(), d)
    }

    private fun draw(ctx: Context, mgr: AppWidgetManager, id: Int, kind: String) {
        val o = mgr.getAppWidgetOptions(id)
        val d = ctx.resources.displayMetrics.density
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).let { if (it <= 0) 110 else it }.coerceAtMost(420)
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).let { if (it <= 0) 110 else it }.coerceAtMost(420)
        val bmp = try { render(ctx, kind, (wDp * d).toInt(), (hDp * d).toInt(), d) } catch (_: Throwable) { return }
        val bar = kind == "bar"
        val rv = RemoteViews(ctx.packageName, if (bar) R.layout.widget_bar else R.layout.widget_image)
        rv.setImageViewBitmap(R.id.widget_img, bmp)
        val imm = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        fun openApp(name: String, req: Int) = PendingIntent.getActivity(ctx, req,
            Intent(ctx, com.example.buddy.ui.UiActivity::class.java).putExtra("open", name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), imm)
        // a tap opens the agents page, which also shows the request that is waiting for an answer
        rv.setOnClickPendingIntent(R.id.widget_root, openApp(if (kind == "mini") "home" else "agents", kind.hashCode()))
        if (bar) {
            rv.setOnClickPendingIntent(R.id.btn_home, openApp("home", 1001))
            rv.setOnClickPendingIntent(R.id.btn_settings, openApp("settings", 1003))
            rv.setOnClickPendingIntent(R.id.btn_talk, PendingIntent.getForegroundService(ctx, 1002,
                Intent(ctx, BuddyService::class.java).setAction(BuddyService.ACTION_LISTEN), imm))
        }
        mgr.updateAppWidget(id, rv)
    }

    // ---- what the agents are doing --------------------------------------------------------------------------------------

    private val BMP = Paint(Paint.FILTER_BITMAP_FLAG)   // opaque paint for pictures (the text paint carries a see-through colour)
    private val BUSY = setOf("working", "thinking", "searching")

    /** Overall state: "offline", "approval", "question", "working", "problem", "finished" or "idle". A question waits like an approval. */
    fun overall(): String {
        if (!BuddyService.agentConnected) return "offline"
        val s = BuddyService.agentSessions
        return when {
            BuddyService.agentApprovals.isNotEmpty() || s.any { it.state == "approval" } -> "approval"
            BuddyService.agentQuestions.isNotEmpty() || s.any { it.state == "question" } -> "question"
            s.any { it.state in BUSY } -> "working"
            s.any { it.state == "error" || it.state == "ratelimit" } -> "problem"
            s.any { it.state == "finished" } -> "finished"
            else -> "idle"
        }
    }

    fun title(ctx: Context, st: String): String = when (st) {
        "offline" -> Tr.t(ctx, "Нет связи", "Offline", "Aloqa yo'q")
        "approval" -> Tr.t(ctx, "Нужно разрешение", "Needs permission", "Ruxsat kerak")
        "question" -> Tr.t(ctx, "У Claude вопрос", "Claude has a question", "Claude'da savol bor")
        "working" -> Tr.t(ctx, "Работает", "Working", "Ishlayapti")
        "problem" -> Tr.t(ctx, "Проблема", "Problem", "Muammo")
        "finished" -> Tr.t(ctx, "Готово", "Done", "Tayyor")
        else -> Tr.t(ctx, "Ждёт", "Idle", "Kutyapti")
    }

    fun color(st: String): Int = when (st) {
        "working" -> 0xFF4C9BFF.toInt(); "approval", "question" -> 0xFFFFA43A.toInt(); "problem" -> 0xFFFF6B6B.toInt()
        "finished" -> 0xFF47D18C.toInt(); "offline" -> 0xFF7D8499.toInt(); else -> 0xFF9AA3BF.toInt()
    }

    fun stateText(ctx: Context, state: String): String = when (state) {
        "working" -> Tr.t(ctx, "Работает", "Working", "Ishlayapti"); "thinking" -> Tr.t(ctx, "Думает", "Thinking", "O'ylayapti")
        "searching" -> Tr.t(ctx, "Ищет", "Searching", "Qidiryapti"); "approval" -> Tr.t(ctx, "Нужно разрешение", "Needs permission", "Ruxsat kerak")
        "question" -> Tr.t(ctx, "Вопрос", "Question", "Savol"); "error" -> Tr.t(ctx, "Ошибка", "Error", "Xato")
        "finished" -> Tr.t(ctx, "Готово", "Done", "Tayyor"); "ratelimit" -> Tr.t(ctx, "Лимит", "Limit", "Limit")
        "sleeping" -> Tr.t(ctx, "Спит", "Sleeping", "Uxlayapti"); else -> Tr.t(ctx, "Ждёт", "Idle", "Kutyapti")
    }

    fun mascotMood(st: String) = when (st) {
        "working" -> "think"; "approval", "question" -> "surprised"; "problem" -> "sad"; "finished" -> "happy"; "offline" -> "sleepy"; else -> "idle"
    }

    /** "2 h 10 min" until [at], or "" when unknown. */
    fun until(ctx: Context, at: Long): String {
        if (at <= 0L) return ""
        val m = ((at - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
        val h = m / 60; val mm = m % 60
        return when {
            h >= 48 -> Tr.t(ctx, "${h / 24} д", "${h / 24} d", "${h / 24} kun")
            h > 0 -> Tr.t(ctx, "$h ч $mm мин", "$h h $mm min", "$h soat $mm daq")
            else -> Tr.t(ctx, "$mm мин", "$mm min", "$mm daq")
        }
    }

    /** Each agent keeps its own colour everywhere (cards, widget rows): Claude Code its warm orange, others from the name. */
    fun agentColor(name: String): Int {
        val n = name.ifEmpty { "Claude Code" }
        if (n.equals("Claude Code", true) || n.equals("claude", true)) return 0xFFE07B53.toInt()
        val pal = intArrayOf(0xFF3FB8A5.toInt(), 0xFF5B9BF0.toInt(), 0xFF9C7BEA.toInt(), 0xFFEE7BA3.toInt(), 0xFFE0B23A.toInt(), 0xFF5BC26A.toInt())
        var hsh = 0
        for (c in n.lowercase()) hsh = hsh * 31 + c.code
        return pal[Math.floorMod(hsh, pal.size)]
    }

    fun ringColor(used: Float): Int = when { used >= 0.9f -> 0xFFFF6B6B.toInt(); used >= 0.7f -> 0xFFFFB84A.toInt(); else -> 0xFF35C2E0.toInt() }

    private val mascots = HashMap<String, Bitmap>()
    private fun mascot(ctx: Context, mood: String): Bitmap = synchronized(mascots) {
        val key = mood + "|" + Stickers.prefOutfitSet(ctx).sorted().joinToString(",") + "|" + ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString("species", "glass")
        mascots.getOrPut(key) { if (mascots.size > 6) mascots.clear(); Stickers.preview(ctx, mood) }
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------

    /** The whole widget takes the colour of the state: blue working, amber waiting for you, green finished, red problem. */
    private fun tint(st: String): Pair<Int, Int> = when (st) {
        "working" -> 0xFF16346A.toInt() to 0xFF0B1B3C.toInt()
        "approval", "question" -> 0xFF5A3A10.toInt() to 0xFF2E1C08.toInt()
        "finished" -> 0xFF14463A.toInt() to 0xFF0A261F.toInt()
        "problem" -> 0xFF5A1E22.toInt() to 0xFF2E0F12.toInt()
        else -> 0xFF1A2650.toInt() to 0xFF0F1630.toInt()
    }

    private fun render(ctx: Context, kind: String, w: Int, h: Int, d: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val st = overall()
        val accent = color(st)
        val r = 24f * d
        val box = RectF(1f * d, 1f * d, w - 1f * d, h - 1f * d)
        val (t1, t2) = tint(st)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), t1, t2, Shader.TileMode.CLAMP)
        cv.drawRoundRect(box, r, r, p)
        p.shader = RadialGradient(w * 0.5f, h * 0.35f, min(w, h) * 0.7f, (accent and 0xFFFFFF) or 0x40000000, 0x00000000, Shader.TileMode.CLAMP)
        cv.drawRoundRect(box, r, r, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * d; p.color = 0x33FFFFFF
        cv.drawRoundRect(box, r, r, p)
        p.style = Paint.Style.FILL
        when (kind) {
            "status" -> status(ctx, cv, p, w, h, d, st)
            "usage" -> usage(ctx, cv, p, w, h, d)
            "done" -> done(ctx, cv, p, w, h, d)
            "bar" -> bar(ctx, cv, p, w, h, d, st)
            "mini" -> mini(ctx, cv, p, w, h, d, st)
            else -> agents(ctx, cv, p, w, h, d, st)
        }
        return bmp
    }

    private fun text(cv: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, center: Boolean = false, maxW: Float = 0f) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = color; p.textSize = size
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        p.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        var t = s
        if (maxW > 0f) while (t.length > 1 && p.measureText(t) > maxW) t = t.dropLast(2) + "…"
        cv.drawText(t, x, y, p)
    }

    private fun bar(ctx: Context, cv: Canvas, p: Paint, w: Int, h: Int, d: Float, st: String) {
        p.color = color(st); cv.drawCircle(20f * d, h / 2f, 5.5f * d, p)
        val title = "Claude"
        text(cv, p, title, 32f * d, h / 2f + 6f * d, 17f * d, 0xFFFFFFFF.toInt(), true)
        val tw = p.measureText(title)
        text(cv, p, title(ctx, st), 32f * d + tw + 8f * d, h / 2f + 5.5f * d, 13f * d, 0xBFFFFFFF.toInt(), false, false, w - 32f * d - tw - 8f * d - 160f * d)
        // three round buttons on the right: home, talk to Bubby, settings
        val icons = intArrayOf(R.drawable.ic_home, R.drawable.ic_mic, R.drawable.ic_settings)
        for (i in 0..2) {
            val cx = w - 10f * d - (2 - i) * 50f * d - 22f * d
            p.color = 0x26FFFFFF; cv.drawCircle(cx, h / 2f, 17f * d, p)
            val dr = ctx.getDrawable(icons[i])?.mutate() ?: continue
            dr.setTint(0xFFFFFFFF.toInt())
            val r = (10f * d).toInt()
            dr.setBounds((cx - r).toInt(), (h / 2f - r).toInt(), (cx + r).toInt(), (h / 2f + r).toInt())
            dr.draw(cv)
        }
    }

    private fun mini(ctx: Context, cv: Canvas, p: Paint, w: Int, h: Int, d: Float, st: String) {
        val side = min(w, h) * 0.84f
        cv.drawBitmap(mascot(ctx, mascotMood(st)), null, RectF((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f), BMP)
        if (st == "approval" || st == "question" || st == "working" || st == "finished" || st == "problem") {
            p.color = color(st); cv.drawCircle(w - 10f * d, 10f * d, 5f * d, p)
        }
    }

    private fun status(ctx: Context, cv: Canvas, p: Paint, w: Int, h: Int, d: Float, st: String) {
        val m = mascot(ctx, mascotMood(st))
        val side = min(w * 0.62f, h * 0.55f)
        val left = (w - side) / 2f
        cv.drawBitmap(m, null, RectF(left, h * 0.08f, left + side, h * 0.08f + side), BMP)
        p.color = color(st); cv.drawCircle(left + side * 0.12f, h * 0.08f + side * 0.12f, 6f * d, p)
        text(cv, p, title(ctx, st), w / 2f, h * 0.80f, 17f * d, 0xFFFFFFFF.toInt(), true, true, w - 20f * d)
        val n = BuddyService.agentSessions.size
        val sub = if (!BuddyService.agentConnected) (BuddyService.agentDesktop ?: "Claude Code")
        else Tr.t(ctx, "Агентов: $n", "Agents: $n", "Agentlar: $n")
        text(cv, p, sub, w / 2f, h * 0.92f, 12f * d, 0xB3FFFFFF.toInt(), false, true, w - 20f * d)
    }

    private fun usage(ctx: Context, cv: Canvas, p: Paint, w: Int, h: Int, d: Float) {
        val u = AgentStats.current(ctx)
        val used = u?.fiveHour
        val size = min(w, h) * 0.66f
        val cx = w / 2f; val cy = h * 0.46f
        val box = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        p.style = Paint.Style.STROKE; p.strokeWidth = 9f * d; p.strokeCap = Paint.Cap.ROUND
        p.color = 0x22FFFFFF; cv.drawArc(box, 135f, 270f, false, p)
        if (used != null) { p.color = ringColor(used); cv.drawArc(box, 135f, 270f * used.coerceAtLeast(0.01f), false, p) }
        p.style = Paint.Style.FILL
        text(cv, p, if (used != null) "${(used * 100).toInt()}%" else "—", cx, cy + 8f * d, 26f * d, 0xFFFFFFFF.toInt(), true, true)
        val reset = until(ctx, u?.fiveHourResets ?: 0L)
        text(cv, p, "Claude · 5 h" + if (reset.isNotEmpty()) "  ·  $reset" else "", cx, h * 0.88f, 11.5f * d, 0xCCFFFFFF.toInt(), false, true, w - 16f * d)
        val seven = u?.sevenDay
        if (seven != null) text(cv, p, "7 d: ${(seven * 100).toInt()}%", cx, h * 0.97f, 10.5f * d, 0x99FFFFFF.toInt(), false, true)
    }

    private fun done(ctx: Context, cv: Canvas, p: Paint, w: Int, h: Int, d: Float) {
        val n = AgentStats.current(ctx)?.doneToday
        val m = mascot(ctx, "happy")
        val side = min(w * 0.4f, h * 0.38f)
        cv.drawBitmap(m, null, RectF((w - side) / 2f, h * 0.08f, (w + side) / 2f, h * 0.08f + side), BMP)
        p.color = 0xFF47D18C.toInt(); cv.drawCircle((w + side) / 2f - 4f * d, h * 0.08f + 6f * d, 8f * d, p)
        text(cv, p, "✓", (w + side) / 2f - 4f * d, h * 0.08f + 10f * d, 11f * d, 0xFF0F1630.toInt(), true, true)
        text(cv, p, n?.toString() ?: "—", w / 2f, h * 0.76f, 40f * d, 0xFFFFFFFF.toInt(), true, true)
        text(cv, p, Tr.t(ctx, "сделано сегодня", "done today", "bugun bajarildi"), w / 2f, h * 0.92f, 12.5f * d, 0xCCFFFFFF.toInt(), false, true, w - 16f * d)
    }

    private fun agents(ctx: Context, cv: Canvas, p: Paint, w: Int, h: Int, d: Float, st: String) {
        val list = BuddyService.agentSessions
        val busy = list.count { it.state in BUSY }
        val head = when {
            !BuddyService.agentConnected -> Tr.t(ctx, "Нет связи с компьютером", "Computer is offline", "Kompyuter bilan aloqa yo'q")
            st == "question" -> title(ctx, st)
            st == "approval" -> title(ctx, st)
            list.isEmpty() -> Tr.t(ctx, "Агентов нет", "No agents running", "Agentlar yo'q")
            busy > 0 -> Tr.t(ctx, "Работает агентов: $busy", "$busy agents working", "Ishlayotgan agentlar: $busy")
            else -> Tr.t(ctx, "Агентов: ${list.size}", "${list.size} agents", "Agentlar: ${list.size}")
        }
        p.color = color(st); cv.drawCircle(18f * d, 22f * d, 5f * d, p)
        text(cv, p, head, 30f * d, 27f * d, 15f * d, 0xFFFFFFFF.toInt(), true, false, w - 44f * d)
        val rowH = 38f * d
        var y = 40f * d
        for (s in list.take(((h - y) / rowH).toInt().coerceIn(0, 4))) {
            p.color = agentColor(s.agent); cv.drawRoundRect(RectF(14f * d, y + 2f * d, 17f * d, y + rowH - 6f * d), 2f * d, 2f * d, p)
            val asks = s.state == "question" || BuddyService.agentQuestions.any { it.pillId == s.pillId }
            val name = s.agent.ifEmpty { "Claude Code" } + if (s.project.isNotEmpty()) " · " + s.project else ""
            text(cv, p, name, 26f * d, y + 15f * d, 14f * d, 0xFFFFFFFF.toInt(), true, false, w - 70f * d)
            val line = if (asks) title(ctx, "question") else s.statusText.ifEmpty { s.finalLine.ifEmpty { stateText(ctx, s.state) } }
            text(cv, p, line, 26f * d, y + 31f * d, 11.5f * d, 0xB3FFFFFF.toInt(), false, false, w - 70f * d)
            p.color = color(when { asks -> "question"; s.state in BUSY -> "working"; s.state == "approval" -> "approval"; s.state == "error" || s.state == "ratelimit" -> "problem"; s.state == "finished" -> "finished"; else -> "idle" })
            cv.drawCircle(w - 24f * d, y + rowH / 2f - 2f * d, 5f * d, p)
            y += rowH
        }
    }
}
