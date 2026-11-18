package com.example.buddy.ui

import android.graphics.Rect
import android.view.Gravity
import android.widget.LinearLayout
import java.time.LocalDate

class MatchMapScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Комната Bubby", "Bubby's room", "Bubby xonasi")
    override val padH = 0

    private val prefs get() = Backend.prefs(ctx)
    private fun unlocked() = prefs.getInt("bm_level", 1).coerceIn(1, MatchLevels.all.size)
    private fun today() = LocalDate.now().toEpochDay()
    private fun giftReady() = prefs.getLong("bm_gift_day", -1L) != today()

    override fun onShow() { reshow() }

    override fun render(c: LinearLayout) {
        val total = MatchLevels.all.size
        val worldNames = listOf(t("Гостиная", "Living room", "Mehmonxona"), t("Кухня", "Kitchen", "Oshxona"), t("Сад", "Garden", "Bog'"), t("Чердак", "Attic", "Cherdak"))
        val colors = intArrayOf(P.orange, P.teal, P.success, P.violet)
        val stars = (0 until total).sumOf { prefs.getInt("bm_stars_$it", 0) }
        val bonus = prefs.getInt("bm_bonus", 0)

        c.add(ctx.card(14) {
            add(ctx.row(Gravity.CENTER_VERTICAL) {
                add(ctx.col {
                    add(ctx.text("⭐ $stars / ${total * 3}", T.Title).apply { textSize = 20f }, MATCH, WRAP)
                    add(ctx.text(
                        if (bonus > 0) t("Бонус: +$bonus ходов до победы", "Bonus: +$bonus moves until you win", "Bonus: g'alabagacha +$bonus yurish")
                        else t("Уровень ${unlocked()} из $total", "Level ${unlocked()} of $total", "${unlocked()}-daraja / $total"),
                        T.Small), MATCH, WRAP, top = 2)
                }, 0, WRAP, 1f)
                if (giftReady()) add(ctx.button("🎁 " + t("+5 ходов", "+5 moves", "+5 yurish")) {
                    prefs.edit().putLong("bm_gift_day", today()).putInt("bm_bonus", bonus + 5).apply()
                    ctx.toast(t("Подарок дня: +5 ходов", "Gift of the day: +5 moves", "Kunlik sovg'a: +5 yurish"))
                    refresh()
                }, WRAP, WRAP)
                else add(ctx.text("🎁 " + t("завтра", "tomorrow", "ertaga"), T.Small), WRAP, WRAP)
            }, MATCH, WRAP)
        }, MATCH, WRAP, start = 12, end = 12, bottom = 8)

        val map = MatchMapView(ctx, total, unlocked(), { prefs.getInt("bm_stars_$it", 0) }, colors, worldNames, 8) { i ->
            if (i < unlocked()) act.push(RoomScreen(act, i))
            else ctx.toast(t("Сначала пройди уровень ${unlocked()}", "Beat level ${unlocked()} first", "Avval ${unlocked()}-darajani yeching"))
        }
        c.add(map, MATCH, WRAP)
        map.post {
            val y = map.currentY().toInt()
            map.requestRectangleOnScreen(Rect(0, y - ctx.dp(260), map.width, y + ctx.dp(160)), true)
        }
    }
}
