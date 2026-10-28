package com.example.buddy

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.buddy.ui.FlowLayout

/**
 * A question from Claude Code on the floating island, in the same dark look as [ApprovalCard]: option chips per question,
 * Send enabled only when every question has the right number of picks. The caller asks for the screen lock before the
 * answer leaves the phone. On the lock screen the question itself is hidden.
 */
object QuestionCard {
    private const val ACCENT = 0xFF8AB4FF.toInt()

    /** A scroll view that never grows past [maxPx], so four questions of eight options stay usable on the island. */
    private class Capped(ctx: Context, private val maxPx: Int) : ScrollView(ctx) {
        override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST))
    }

    fun build(ctx: Context, q: AgentQuestion, locked: Boolean, onSend: (List<List<String>>) -> Unit, onLater: () -> Unit): View {
        val u = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * u).toInt()
        var used = false
        var sel = AgentProto.emptySelection(q)

        fun button(label: String, color: Int, textColor: Int, action: () -> Unit) = TextView(ctx).apply {
            text = label; gravity = Gravity.CENTER; textSize = 14f
            setTypeface(typeface, Typeface.BOLD); setTextColor(textColor); minHeight = dp(48)
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(22).toFloat() }
            isClickable = true
            IslandUi.pressable(this, 0.95f)
            setOnClickListener { if (!used && isEnabled) { action() } }
        }

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val titleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(ctx).apply {
            text = Tr.t(ctx, "У Claude вопрос", "Claude has a question", "Claude'da savol bor")
            textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(ctx).apply {
            text = Tr.t(ctx, "ВОПРОС", "QUESTION", "SAVOL")
            textSize = 12f; setTypeface(typeface, Typeface.BOLD); setTextColor(ACCENT)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply { setColor((ACCENT and 0x00FFFFFF) or 0x26000000); cornerRadius = dp(10).toFloat() }
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        body.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        val send = button(Tr.t(ctx, "Отправить", "Send", "Yuborish"), 0xFF3DDC84.toInt(), 0xFF0B1A10.toInt()) {
            val picks = AgentProto.picksFor(q, sel) ?: return@button
            used = true
            onSend(picks)
        }
        fun refreshSend() {
            val ok = AgentProto.picksFor(q, sel) != null
            send.isEnabled = ok; send.alpha = if (ok) 1f else 0.4f
        }

        if (locked) {
            body.addView(TextView(ctx).apply {
                text = Tr.t(ctx, "Разблокируйте телефон, чтобы увидеть вопрос", "Unlock the phone to see the question", "Savolni ko'rish uchun telefonni oching")
                setTypeface(typeface, Typeface.ITALIC); textSize = 13f; setTextColor(0xFFB8B8C0.toInt())
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply { setColor(0xFF111114.toInt()); cornerRadius = dp(10).toFloat() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        } else {
            val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val chipsPer = ArrayList<List<TextView>>()
            fun paint() {
                for ((qi, chips) in chipsPer.withIndex()) for ((oi, c) in chips.withIndex()) {
                    val on = oi in sel[qi]
                    c.setTextColor(if (on) 0xFF0B1A10.toInt() else 0xFFFFFFFF.toInt())
                    c.background = GradientDrawable().apply {
                        setColor(if (on) ACCENT else 0xFF2A2A31.toInt()); cornerRadius = dp(16).toFloat()
                        setStroke(maxOf(1, dp(1)), if (on) ACCENT else 0x33FFFFFF)
                    }
                }
                refreshSend()
            }
            for ((qi, item) in q.items.withIndex()) {
                list.addView(TextView(ctx).apply {
                    text = item.question; textSize = 13.5f; setTextColor(0xFFE8E8EE.toInt())
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (qi == 0) 8 else 12) })
                list.addView(TextView(ctx).apply {
                    text = if (item.multiSelect) Tr.t(ctx, "Можно выбрать несколько", "Pick one or more", "Bir nechtasini tanlash mumkin")
                        else Tr.t(ctx, "Выберите один", "Pick one", "Bittasini tanlang")
                    textSize = 11.5f; setTextColor(0xFF9A9AA6.toInt())
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
                val flow = FlowLayout(ctx, 6)
                val chips = ArrayList<TextView>()
                for ((oi, op) in item.options.withIndex()) {
                    val c = TextView(ctx).apply {
                        text = op.shown; textSize = 13f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                        setPadding(dp(12), dp(7), dp(12), dp(7)); isClickable = true
                        if (op.description.isNotEmpty()) contentDescription = op.shown + ". " + op.description
                        IslandUi.pressable(this, 0.95f)
                        setOnClickListener { if (!used) { sel = AgentProto.toggle(q, sel, qi, oi); paint() } }
                    }
                    chips.add(c)
                    flow.addView(c)
                }
                chipsPer.add(chips)
                list.addView(flow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
            paint()
            body.addView(Capped(ctx, dp(300)).apply { addView(list) }, LinearLayout.LayoutParams(-1, -2))
        }

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button(Tr.t(ctx, "На компьютере", "On the computer", "Kompyuterda"), 0xFF3A3A42.toInt(), 0xFFFFFFFF.toInt()) { used = true; onLater() },
            LinearLayout.LayoutParams(0, -2, 1f))
        if (!locked) {
            refreshSend()
            row.addView(send, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        }
        body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val bar = View(ctx).apply { background = GradientDrawable().apply { setColor(ACCENT); cornerRadius = dp(2).toFloat() } }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(0xFF1E1E22.toInt()); cornerRadius = dp(18).toFloat(); setStroke(maxOf(1, dp(1)), 0x22FFFFFF) }
        }
        root.addView(bar, LinearLayout.LayoutParams(dp(4), -1).apply { rightMargin = dp(10) })
        root.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
        if (IslandUi.animOn(ctx)) {
            root.alpha = 0f; root.translationY = dp(14).toFloat()
            root.animate().alpha(1f).translationY(0f).setStartDelay(60).setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
        return root
    }
}
