package com.example.buddy

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

object ApprovalCard {
    fun build(ctx: Context, a: Approval, locked: Boolean, onAllow: () -> Unit, onDeny: () -> Unit, riskLevel: Int? = null, onAlways: (() -> Unit)? = null): View {
        val u = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * u).toInt()
        var used = false
        val risk = riskLevel ?: IslandUi.risk(a.tool, a.command)
        val accent = IslandUi.riskColor(risk)

        fun button(label: String, color: Int, textColor: Int, action: () -> Unit) = TextView(ctx).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textColor)
            minHeight = dp(48)
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(22).toFloat() }
            isClickable = true
            IslandUi.pressable(this, 0.95f)
            setOnClickListener {
                if (used) return@setOnClickListener
                used = true
                alpha = 0.6f
                action()
            }
        }

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val titleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(ctx).apply {
            text = a.tool.ifBlank { Tr.t(ctx, "Агент", "Agent", "Agent") }
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(ctx).apply {
            text = when (risk) { IslandUi.RISK_HIGH -> Tr.t(ctx, "ОПАСНО", "DANGEROUS", "XAVFLI"); IslandUi.RISK_MID -> Tr.t(ctx, "ВНИМАНИЕ", "CAUTION", "DIQQAT"); else -> Tr.t(ctx, "ЧТЕНИЕ", "READ", "O'QISH") }
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accent)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply {
                setColor((accent and 0x00FFFFFF) or 0x26000000); cornerRadius = dp(10).toFloat()
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        body.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        body.addView(TextView(ctx).apply {
            if (locked) {
                text = Tr.t(ctx, "Команда скрыта на экране блокировки", "Command hidden on the lock screen", "Buyruq qulflangan ekranda yashirilgan")
                setTypeface(typeface, Typeface.ITALIC)
                textSize = 13f
            } else {
                text = a.command
                typeface = Typeface.MONOSPACE
                textSize = 12f
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
            }
            setTextColor(0xFFB8B8C0.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { setColor(0xFF111114.toInt()); cornerRadius = dp(10).toFloat() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        if (a.truncated) {
            body.addView(TextView(ctx).apply {
                text = Tr.t(ctx, "Команда длиннее, чем показано", "The command is longer than shown", "Buyruq ko'rsatilganidan uzunroq")
                textSize = 12f
                setTextColor(0xFFFFB454.toInt())
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button(Tr.t(ctx, "Отклонить", "Deny", "Rad etish"), 0xFF3A3A42.toInt(), 0xFFFFFFFF.toInt(), onDeny), LinearLayout.LayoutParams(0, -2, 1f))
        if (!a.truncated) {
            row.addView(
                button(Tr.t(ctx, "Разрешить", "Allow", "Ruxsat berish"), 0xFF3DDC84.toInt(), 0xFF0B1A10.toInt(), onAllow),
                LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) }
            )
        }
        body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        if (onAlways != null && !a.truncated) {
            body.addView(
                button(Tr.t(ctx, "Всегда (до конца сессии)", "Always (this session)", "Doim (shu sessiya davomida)"), 0xFF2C3A33.toInt(), 0xFFBFF3D2.toInt(), onAlways),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            )
        }

        val bar = View(ctx).apply {
            background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(2).toFloat() }
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF1E1E22.toInt()); cornerRadius = dp(18).toFloat(); setStroke(maxOf(1, dp(1)), 0x22FFFFFF)
            }
        }
        root.addView(bar, LinearLayout.LayoutParams(dp(4), -1).apply { rightMargin = dp(10) })
        root.addView(body, LinearLayout.LayoutParams(0, -2, 1f))

        if (IslandUi.animOn(ctx)) {
            root.alpha = 0f
            root.translationY = dp(14).toFloat()
            root.animate().alpha(1f).translationY(0f).setStartDelay(60).setDuration(260)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
        return root
    }
}
