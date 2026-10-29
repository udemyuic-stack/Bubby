package com.example.buddy.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * The meta layer of "Bubby's Room": stars earned in the match-3 levels pay for renovating Bubby's own room.
 * Shows the room on top, the star balance, and a list of items to buy; bought items can be restyled for free.
 * Saved in SharedPreferences "buddy": key [RoomItems.PREF_KEY] ("bm_room"), stars are read from "bm_stars_<level>".
 *
 * [fromGame] is true when this page was opened from the puzzle screen: its "play" button then just goes back.
 */
class RoomShopScreen(act: UiActivity, private val fromGame: Boolean = false) : Screen(act) {
    override fun title() = t("Комната Bubby", "Bubby's room", "Bubby xonasi")

    private val prefs get() = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    private var owned: Map<String, Int> = emptyMap()
    private var total = 0
    private val cards = HashMap<String, View>()
    private var pendingFlash: String? = null

    private fun loadStars(): Int {
        val list = ArrayList<Int>()
        for (i in 0 until MatchLevels.all.size) list.add(prefs.getInt("bm_stars_$i", 0))
        return RoomItems.starsTotal(list)
    }

    private fun save() { prefs.edit().putString(RoomItems.PREF_KEY, RoomItems.serialize(owned)).apply() }

    override fun onShow() { reshow() }

    override fun render(c: LinearLayout) {
        owned = RoomItems.parse(prefs.getString(RoomItems.PREF_KEY, ""))
        total = loadStars()
        cards.clear()
        val balance = RoomItems.balance(total, owned)
        val spent = RoomItems.starsSpent(owned)

        // the room
        val room = RoomView(ctx)
        room.setOwned(owned)
        c.add(room, MATCH, WRAP, bottom = 12)
        pendingFlash?.let { room.flash(it); pendingFlash = null }

        // balance
        c.add(ctx.card(16) {
            add(ctx.row(Gravity.CENTER_VERTICAL) {
                add(ctx.text("⭐ $balance", T.Headline).apply { textSize = 34f }, WRAP, WRAP, end = 14)
                add(ctx.col {
                    add(ctx.text(t("Звёзды для ремонта", "Stars to spend", "Jihozlash uchun yulduzlar"), T.Title), MATCH, WRAP)
                    add(ctx.text(t("Заработано $total · потрачено $spent", "Earned $total · spent $spent", "Topilgan $total · sarflangan $spent"), T.Small), MATCH, WRAP, top = 2)
                    add(ctx.text(t("В комнате: ${owned.size} из ${RoomItems.all.size}", "In the room: ${owned.size} of ${RoomItems.all.size}", "Xonada: ${owned.size} / ${RoomItems.all.size}"), T.Small), MATCH, WRAP, top = 2)
                }, 0, WRAP, 1f)
            }, MATCH, WRAP)
            if (total == 0 || balance < 2) {
                add(ctx.text(
                    t("Проходи уровни «Комнаты Bubby» и получай звёзды — на них можно обставить эту комнату.",
                        "Beat levels of Bubby's Room to earn stars and furnish this room.",
                        "Yulduz olish uchun «Bubby xonasi» darajalarini o'tib, bu xonani jihozlang."), T.Small), MATCH, WRAP, top = 10)
                add(ctx.button(t("Играть и зарабатывать звёзды", "Play and earn stars", "O'ynab yulduz topish"), BtnStyle.Tonal) {
                    if (fromGame) act.pop() else act.push(RoomScreen(act))
                }, MATCH, WRAP, top = 10)
            }
        }, MATCH, WRAP, bottom = 4)

        c.add(ctx.section(t("Ремонт", "Renovation", "Ta'mirlash")))
        for (item in RoomItems.all) itemCard(c, item)
    }

    private fun itemCard(c: LinearLayout, item: RoomItem) {
        val have = owned[item.id]
        val isOwned = have != null
        val can = RoomItems.canBuy(item.id, owned, total)
        val card = ctx.card(14) {
            add(ctx.row(Gravity.CENTER_VERTICAL) {
                add(ctx.text(item.emoji, T.Headline).apply { textSize = 28f; gravity = Gravity.CENTER }, ctx.dp(46), WRAP, end = 8)
                add(ctx.col {
                    add(ctx.text(t(item.ru, item.en, item.uz), T.Title), MATCH, WRAP)
                    val sub = if (isOwned) t("Куплено · выбери вид", "Owned · pick a look", "Sotib olingan · ko'rinishni tanlang")
                    else t("Стоит ${item.cost} ⭐", "Costs ${item.cost} ⭐", "Narxi ${item.cost} ⭐")
                    add(ctx.text(sub, T.Small), MATCH, WRAP, top = 2)
                }, 0, WRAP, 1f)
                if (!isOwned) {
                    val buyBtn = ctx.button(t("Купить · ${item.cost} ⭐", "Buy · ${item.cost} ⭐", "Olish · ${item.cost} ⭐"), if (can) BtnStyle.Filled else BtnStyle.Tonal) { buy(item) }
                    buyBtn.alpha = if (can) 1f else 0.6f
                    add(buyBtn, WRAP, WRAP, start = 8)
                }
            }, MATCH, WRAP)
            if (isOwned) {
                val flow = FlowLayout(ctx, 8)
                item.variants.forEachIndexed { i, v -> flow.addView(variantChip(item, i, v, i == have)) }
                add(flow, MATCH, WRAP, top = 10)
            }
        }
        c.add(card, MATCH, WRAP, bottom = 10)
        cards[item.id] = card
    }

    private fun variantChip(item: RoomItem, index: Int, v: RoomVariant, selected: Boolean): View = ctx.row(Gravity.CENTER) {
        setPadding(ctx.dp(10), ctx.dp(7), ctx.dp(14), ctx.dp(7))
        background = ctx.glass(if (selected) GlassKind.Active else GlassKind.Dim, 20f)
        add(View(ctx).apply { background = ctx.rounded(v.color, 8f, Glass.white(.85f)) }, ctx.dp(16), ctx.dp(16), end = 7)
        add(ctx.text(t(v.ru, v.en, v.uz), T.Label, if (selected) Glass.ON_ACTIVE else Glass.TEXT_LABEL), WRAP, WRAP)
        press { choose(item, index) }
    }

    private fun buy(item: RoomItem) {
        val next = RoomItems.buy(item.id, owned, total)
        if (next == null) {
            val bal = RoomItems.balance(total, owned)
            if (owned.containsKey(item.id)) return
            cards[item.id]?.shake()
            ctx.toast(t("Не хватает звёзд: нужно ${item.cost}, есть $bal", "Not enough stars: need ${item.cost}, you have $bal", "Yulduz yetmaydi: kerak ${item.cost}, sizda $bal"))
            return
        }
        owned = next
        save()
        celebrate(item.id, big = item.cost >= 5 || owned.size == RoomItems.all.size)
    }

    private fun choose(item: RoomItem, index: Int) {
        if (owned[item.id] == index) return
        owned = RoomItems.choose(item.id, index, owned)
        save()
        celebrate(item.id, big = false)
    }

    /** Redraws the page, then pops the changed card and sends a little burst from it (confetti for the big ones). */
    private fun celebrate(id: String, big: Boolean) {
        pendingFlash = id
        refresh()
        content.post {
            cards[id]?.let { it.pop(); it.burst(Glass.ACCENT) }
            if (big) act.confetti()
        }
    }
}
