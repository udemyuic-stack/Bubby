package com.example.buddy.ui

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.buddy.BuddyView
import com.example.buddy.Outfits
import com.example.buddy.R
import com.example.buddy.Stickers
import kotlin.random.Random

class OutfitsScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Питомец и наряды", "Pet & outfits", "Uy hayvoni va kiyimlar")

    private val slotOrder = listOf("head", "eyes", "neck", "aura", "hand")
    private val slots = Outfits.SLOTS
    private var species = Backend.str(ctx, "species", "cat")
    private var items: Set<String> = Backend.str(ctx, "outfit", "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    private var slot = "head"
    private var preview: Bitmap? = null
    private var drawing = false
    private var drawAgain = false
    private val rng = Random.Default

    private lateinit var tilt: Tilt3D
    private var popId: String? = null
    private lateinit var previewTitle: android.widget.TextView
    private lateinit var animalsBox: LinearLayout
    private lateinit var presetsBox: LinearLayout
    private lateinit var slotBox: LinearLayout

    private fun slotTitle(s: String) = when (s) {
        "head" -> t("Голова", "Head", "Bosh"); "eyes" -> t("Глаза", "Eyes", "Ko'zlar"); "neck" -> t("Шея", "Neck", "Bo'yin")
        "aura" -> t("Аура", "Aura", "Aura"); else -> t("В руке", "In hand", "Qo'lda")
    }

    private fun label(id: String): String = when (id) {
        "hat" -> t("Шляпа", "Hat", "Shlyapa"); "cap" -> t("Кепка", "Cap", "Kepka"); "crown" -> t("Корона", "Crown", "Toj")
        "bow" -> t("Бант", "Bow", "Bant"); "headphones" -> t("Наушники", "Headphones", "Quloqchin"); "flower" -> t("Цветок", "Flower", "Gul")
        "party" -> t("Колпак", "Party hat", "Bayram qalpog'i"); "santa" -> t("Шапка Санты", "Santa hat", "Santa shapkasi")
        "wizard" -> t("Колпак волшебника", "Wizard hat", "Sehrgar qalpog'i"); "ninja" -> t("Повязка ниндзя", "Ninja headband", "Ninja bog'ichi")
        "samurai" -> t("Шлем самурая", "Samurai helmet", "Samuray dubulg'asi"); "pirate" -> t("Шляпа пирата", "Pirate hat", "Qaroqchi shlyapasi")
        "astronaut" -> t("Шлем космонавта", "Astronaut helmet", "Kosmonavt dubulg'asi"); "halo" -> t("Нимб", "Halo", "Nur halqasi")
        "horns" -> t("Рожки", "Horns", "Shoxlar"); "catears" -> t("Кошачьи ушки", "Cat ears", "Mushuk quloqlari")
        "beanie" -> t("Вязаная шапка", "Beanie", "To'qima qalpoq"); "pumpkin" -> t("Тыква", "Pumpkin", "Qovoq")
        "glasses" -> t("Очки", "Glasses", "Ko'zoynak"); "sunglasses" -> t("Солнечные очки", "Sunglasses", "Quyosh ko'zoynagi")
        "mask" -> t("Маска героя", "Hero mask", "Qahramon niqobi"); "visor" -> t("Кибер-визор", "Cyber visor", "Kiber ko'zoynak")
        "scarf" -> t("Шарф", "Scarf", "Sharf"); "bowtie" -> t("Бабочка", "Bow tie", "Kapalak galstuk"); "cape" -> t("Плащ", "Cape", "Plash")
        "tie" -> t("Галстук", "Tie", "Galstuk"); "fire" -> t("Огонь", "Fire", "Olov"); "ice" -> t("Лёд", "Ice", "Muz")
        "lightning" -> t("Молния", "Lightning", "Chaqmoq"); "hearts" -> t("Сердечки", "Hearts", "Yuraklar"); "sakura" -> t("Сакура", "Sakura", "Sakura")
        "stars" -> t("Звёзды", "Stars", "Yulduzlar"); "wand" -> t("Волшебная палочка", "Wand", "Sehrli tayoqcha"); "sword" -> t("Меч", "Sword", "Qilich")
        "umbrella" -> t("Зонтик", "Umbrella", "Soyabon"); "lollipop" -> t("Леденец", "Lollipop", "Lolipop"); else -> id
    }

    private fun iconFor(slot: String, id: String): Int = when (id) {
        "fire" -> R.drawable.ic_fire; "lightning" -> R.drawable.ic_bolt; "hearts" -> R.drawable.ic_heart
        "ice", "sakura", "stars", "wand" -> R.drawable.ic_sparkle
        "headphones" -> R.drawable.ic_music
        else -> when (slot) { "head" -> R.drawable.ic_face; "eyes" -> R.drawable.ic_palette; "neck" -> R.drawable.ic_bell; "aura" -> R.drawable.ic_sparkle; else -> R.drawable.ic_key }
    }

    private class Preset(val id: String, val label: String, val items: Set<String>, val color: Int)

    private fun presets() = listOf(
        Preset("ninja", t("Ниндзя", "Ninja", "Ninja"), setOf("ninja", "scarf"), 0xFF3B3552.toInt()),
        Preset("wizard", t("Волшебник", "Wizard", "Sehrgar"), setOf("wizard", "wand", "stars"), 0xFF6E4BD8.toInt()),
        Preset("samurai", t("Самурай", "Samurai", "Samuray"), setOf("samurai", "sword"), 0xFFC0392B.toInt()),
        Preset("superhero", t("Супергерой", "Superhero", "Superqahramon"), setOf("mask", "cape", "lightning"), 0xFFE5365B.toInt()),
        Preset("pirate", t("Пират", "Pirate", "Qaroqchi"), setOf("pirate", "sword"), 0xFF8A5A34.toInt()),
        Preset("astronaut", t("Космонавт", "Astronaut", "Kosmonavt"), setOf("astronaut", "stars"), 0xFF3A7BD5.toInt()),
        Preset("fire_mage", t("Огненный маг", "Fire mage", "Olovli sehrgar"), setOf("wizard", "fire", "wand"), 0xFFF0643A.toInt()),
        Preset("ice_queen", t("Снежная королева", "Ice queen", "Qor malikasi"), setOf("crown", "ice", "wand"), 0xFF3FB5E8.toInt()),
        Preset("angel", t("Ангел", "Angel", "Farishta"), setOf("halo", "sakura"), 0xFFE0A93B.toInt()),
        Preset("devil", t("Демон", "Devil", "Iblis"), setOf("horns", "fire", "cape"), 0xFF9B2335.toInt()),
    )

    private fun animals() = listOf(
        "cat" to t("Кот", "Cat", "Mushuk"), "dog" to t("Пёс", "Dog", "It"), "bunny" to t("Зайчик", "Bunny", "Quyon"),
        "bear" to t("Мишка", "Bear", "Ayiq"), "panda" to t("Панда", "Panda", "Panda"), "fox" to t("Лис", "Fox", "Tulki"),
        "frog" to t("Лягушонок", "Frog", "Qurbaqa"), "penguin" to t("Пингвин", "Penguin", "Pingvin"), "capybara" to t("Капибара", "Capybara", "Kapibara"),
        "hamster" to t("Хомяк", "Hamster", "Xomyak"), "owl" to t("Сова", "Owl", "Boyqush"), "duck" to t("Утёнок", "Duck", "O'rdak"),
        "pig" to t("Поросёнок", "Pig", "Cho'chqa"), "koala" to t("Коала", "Koala", "Koala"), "lion" to t("Лев", "Lion", "Sher"), "tiger" to t("Тигр", "Tiger", "Yo'lbars"),
    )

    private fun csv(set: Set<String>): String = slotOrder.flatMap { s -> slots[s].orEmpty().filter { it in set } }.joinToString(",")

    // ---- state changes ----

    private fun setItems(next: Set<String>) {
        items = next
        Backend.setStr(ctx, "outfit", csv(next))
        val ps = presets().associateBy { it.id }
        redraw()
        updateCards(presetViews, { id -> ps[id]?.let(::presetSelected) == true }) { id -> presetCard(ps.getValue(id)) }
        updateCards(tileViews, { id -> id in items }) { id -> itemTile(id) }
        updateTitle(); wobble()
    }

    private fun setSpecies(id: String) {
        if (id == species) return
        val old = species
        popId = id
        species = id
        Backend.setStr(ctx, "species", id)
        // only the two cards whose selection changed are rebuilt (each holds a live mascot), not all sixteen
        val names = animals().toMap()
        for (a in listOf(old, id)) animalViews[a]?.let { (v, _) -> animalViews[a] = swap(v, animalCard(a, names[a] ?: a)) to (a == id) }
        redraw(); updateTitle(); wobble()
    }

    // ---- cards that are updated in place: id -> (view, whether it was drawn selected) ----
    private val animalViews = HashMap<String, Pair<View, Boolean>>()
    private val presetViews = HashMap<String, Pair<View, Boolean>>()
    private val tileViews = HashMap<String, Pair<View, Boolean>>()

    private fun presetSelected(p: Preset) = items.size == p.items.size && items.containsAll(p.items)

    /** Puts [fresh] where [old] was (same parent, index and layout params) and returns it. */
    private fun swap(old: View, fresh: View): View {
        val parent = old.parent as? android.view.ViewGroup ?: return old
        val i = parent.indexOfChild(old)
        val lp = old.layoutParams
        parent.removeViewAt(i)
        parent.addView(fresh, i, lp)
        return fresh
    }

    /** Rebuilds only the cards whose selected state changed (or that should pop), instead of whole rows. */
    private fun updateCards(views: HashMap<String, Pair<View, Boolean>>, selected: (String) -> Boolean, build: (String) -> View) {
        for ((id, vs) in views.entries.toList()) {
            val sel = selected(id)
            if (sel != vs.second || (sel && id == popId)) views[id] = swap(vs.first, build(id)) to sel
        }
    }

    private fun toggle(item: String) {
        popId = item
        val next = items.toMutableSet()
        val had = item in next
        next.removeAll(slots[slot].orEmpty().toSet())
        if (!had) next.add(item)
        setItems(next)
    }

    private fun shuffle() {
        val ps = presets()
        if (rng.nextDouble() < 0.4) { setItems(ps[rng.nextInt(ps.size)].items); return }
        val next = mutableSetOf<String>()
        for (s in slotOrder) if (rng.nextDouble() < 0.55) { val l = slots[s].orEmpty(); next.add(l[rng.nextInt(l.size)]) }
        if (next.isEmpty()) { val l = slots["head"].orEmpty(); next.add(l[rng.nextInt(l.size)]) }
        setItems(next)
    }

    /** Draws the preview off the UI thread; taps that arrive meanwhile are merged into one more redraw. */
    private fun redraw() {
        if (drawing) { drawAgain = true; return }
        drawing = true
        tilt.image.alpha = 0.6f
        Thread {
            var bmp: Bitmap? = null
            try {
                bmp = Stickers.preview(ctx, "happy")
            } catch (_: Throwable) {}
            act.runOnUiThread {
                drawing = false
                if (drawAgain) { drawAgain = false; redraw(); return@runOnUiThread }
                if (bmp != null) { preview = bmp; tilt.setBitmap(bmp) }
                tilt.image.alpha = 1f
            }
        }.start()
    }

    private fun wobble() = tilt.swing()

    private fun updateTitle() {
        val a = animals().firstOrNull { it.first == species }?.second ?: species
        previewTitle.text = "$a · " + if (items.isEmpty()) t("без наряда", "no outfit", "kiyimsiz") else t("${items.size} шт.", "${items.size} pcs", "${items.size} ta")
    }

    // ---- views ----

    private fun selectableCard(selected: Boolean, pad: Int = 8, id: String? = null, onTap: () -> Unit, block: LinearLayout.() -> Unit): LinearLayout =
        ctx.card(pad = pad, fill = if (selected) P.primaryContainer else P.low, stroke = if (selected) P.primary else P.outlineVariant, radius = 20f, onClick = onTap, block = block).also { card ->
            // the card that was just picked pops
            if (selected && id != null && id == popId) { popId = null; card.post { card.pop() } }
        }

    private fun previewCard(): View = ctx.card(pad = 16, fill = P.primaryContainer, stroke = 0) {
        tilt = Tilt3D(ctx)
        tilt.setBitmap(preview)
        add(tilt, MATCH, ctx.dp(210))
        add(ctx.row {
            previewTitle = ctx.text("", T.Title)
            add(previewTitle, 0, WRAP, 1f)
            add(ctx.button(t("Ещё", "More", "Yana"), BtnStyle.Tonal, R.drawable.ic_shuffle) { shuffle() }, WRAP, WRAP)
        }, MATCH, WRAP, top = 8)
        updateTitle()
    }

    private fun animalCard(id: String, name: String): View = selectableCard(species == id, pad = 8, id = id, onTap = { setSpecies(id) }) {
        gravity = Gravity.CENTER_HORIZONTAL
        val b = BuddyView(ctx).apply {
            scaleF = 0.55f
            this.species = id
            mood = if (id == species) "happy" else "idle"
            setActive(id == species)
        }
        add(b, WRAP, WRAP)
        add(ctx.text(name, T.Small, if (id == species) P.onPrimaryContainer else P.onSurface).apply { maxLines = 1; gravity = Gravity.CENTER }, MATCH, WRAP, top = 2)
    }

    private fun fillAnimals() {
        animalsBox.removeAllViews(); animalViews.clear()
        val flow = FlowLayout(ctx)
        for ((id, name) in animals()) { val v = animalCard(id, name); animalViews[id] = v to (id == species); flow.addView(v) }
        animalsBox.add(flow, MATCH, WRAP)
    }

    private fun presetCard(p: Preset): View {
        val selected = presetSelected(p)
        return selectableCard(selected, pad = 10, id = p.id, onTap = { popId = p.id; setItems(p.items) }) {
            gravity = Gravity.CENTER_HORIZONTAL
            add(ctx.row(Gravity.CENTER) {
                background = ctx.rounded(P.alpha(p.color, 46), 30f)
                add(ctx.icon(iconFor("head", p.items.first()), p.color, 28), WRAP, WRAP)
            }, ctx.dp(60), ctx.dp(60))
            add(ctx.text(p.label, T.Label, if (selected) P.onPrimaryContainer else P.onSurface).apply { maxLines = 2; gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
        }
    }

    private fun fillPresets() {
        presetsBox.removeAllViews(); presetViews.clear()
        val row = ctx.row { setPadding(0, ctx.dp(4), 0, ctx.dp(8)) }
        for (p in presets()) { val v = presetCard(p); presetViews[p.id] = v to presetSelected(p); row.add(v, ctx.dp(108), WRAP, end = 10) }
        presetsBox.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; addView(row) })
    }

    private fun itemTile(id: String): View {
        val selected = id in items
        return selectableCard(selected, pad = 8, id = id, onTap = { toggle(id) }) {
            gravity = Gravity.CENTER
            if (slot == "aura") {
                add(AuraView(ctx, id), MATCH, ctx.dp(54))
            } else {
                add(ctx.icon(iconFor(slot, id), if (selected) P.onPrimaryContainer else P.onSurfaceVariant, 30), WRAP, WRAP, top = 8)
            }
            add(ctx.text(label(id), T.Small, if (selected) P.onPrimaryContainer else P.onSurface).apply { maxLines = 2; gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
        }
    }

    private fun fillSlot() {
        slotBox.removeAllViews(); tileViews.clear()
        val chips = FlowLayout(ctx)
        for (s in slotOrder) chips.addView(ctx.chip(slotTitle(s), slot == s) { slot = s; fillSlot() })
        slotBox.add(chips, MATCH, WRAP, bottom = 8)
        for (rowIds in slots[slot].orEmpty().chunked(3)) {
            val row = ctx.row {}
            for (i in 0 until 3) {
                val id = rowIds.getOrNull(i)
                val v = if (id != null) itemTile(id).also { tileViews[id] = it to (id in items) } else View(ctx)
                row.addView(v, LinearLayout.LayoutParams(0, ctx.dp(94), 1f).apply {
                    setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
                })
            }
            slotBox.add(row, MATCH, WRAP)
        }
    }

    override fun render(c: LinearLayout) {
        c.add(previewCard(), MATCH, WRAP, top = 4)
        c.add(ctx.row {
            add(ctx.button(t("Снять всё", "Remove all", "Hammasini yechish"), BtnStyle.Text, R.drawable.ic_delete) { setItems(emptySet()) }, WRAP, WRAP)
            gravity = Gravity.END
        }, MATCH, WRAP, top = 4)
        c.add(ctx.section(t("Питомец", "Pet", "Uy hayvoni")))
        animalsBox = ctx.col(); c.add(animalsBox, MATCH, WRAP)
        c.add(ctx.section(t("Образы", "Looks", "Obrazlar")))
        presetsBox = ctx.col(); c.add(presetsBox, MATCH, WRAP)
        c.add(ctx.section(t("Наряд по частям", "Outfit piece by piece", "Kiyim qismlar bo'yicha")))
        slotBox = ctx.col(); c.add(slotBox, MATCH, WRAP)
        fillAnimals(); fillPresets(); fillSlot()
        if (preview == null) redraw()
    }
}
