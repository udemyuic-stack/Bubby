package com.example.buddy.ui

/** One look of a room item: a name in three languages and two colours the room view draws it with (main, accent). */
class RoomVariant(val ru: String, val en: String, val uz: String, val color: Int, val accent: Int)

/** A thing Bubby's room can have. [cost] is in stars; the first look comes with the purchase, changing look is free. */
class RoomItem(val id: String, val ru: String, val en: String, val uz: String, val emoji: String, val cost: Int, val variants: List<RoomVariant>)

/**
 * The renovation catalogue and its rules. Pure Kotlin (no Android), so it can be tested on the JVM.
 * Owned items are a map id -> index of the chosen look; saved as "wall:1,rug:0" under the preference key [PREF_KEY].
 */
object RoomItems {
    const val PREF_KEY = "bm_room"
    const val MAX_STARS_PER_LEVEL = 3

    private fun v(ru: String, en: String, uz: String, color: Long, accent: Long) = RoomVariant(ru, en, uz, color.toInt(), accent.toInt())

    /** Cheapest first; the order is also the order of the shop list. */
    val all: List<RoomItem> = listOf(
        RoomItem("wall", "Краска для стен", "Wall paint", "Devor bo'yog'i", "🎨", 2, listOf(
            v("Персик", "Peach", "Shaftoli", 0xFFF6D9C0, 0xFFE7B99A),
            v("Мята", "Mint", "Yalpiz", 0xFFCFE8D8, 0xFFA9D2BC),
            v("Небо", "Sky", "Osmon", 0xFFD3E4F7, 0xFFAFCDEB))),
        RoomItem("rug", "Коврик", "Rug", "Gilamcha", "🟠", 2, listOf(
            v("Розовый", "Rose", "Pushti", 0xFFE57A8E, 0xFFF7C1CB),
            v("Бирюзовый", "Teal", "Firuza", 0xFF3FB8A5, 0xFFA6E3D8),
            v("Солнечный", "Sunny", "Quyoshli", 0xFFF2B01E, 0xFFFFE08A))),
        RoomItem("window", "Окно", "Window", "Deraza", "🪟", 3, listOf(
            v("День", "Day", "Kun", 0xFF9CCBF5, 0xFFFFFFFF),
            v("Закат", "Sunset", "Shafaq", 0xFFF6B58C, 0xFFC8895E),
            v("Ночь", "Night", "Tun", 0xFF283A6B, 0xFFE8E4F2))),
        RoomItem("lamp", "Торшер", "Floor lamp", "Torsher", "💡", 3, listOf(
            v("Тёплый", "Warm", "Iliq", 0xFFF2C14E, 0xFF5A4A42),
            v("Розовый", "Rose", "Pushti", 0xFFEE7BA3, 0xFF5A4A42),
            v("Голубой", "Sky", "Havorang", 0xFF7DB4F0, 0xFF5A4A42))),
        RoomItem("poster", "Постер", "Poster", "Plakat", "🖼️", 3, listOf(
            v("Горы", "Mountains", "Tog'lar", 0xFF8FB8DE, 0xFF7A5A3C),
            v("Луна", "Moon", "Oy", 0xFF3A3F7A, 0xFFE8C77A),
            v("Сердце", "Heart", "Yurak", 0xFFF7D7E0, 0xFFE0517A))),
        RoomItem("shelf", "Полка с книгами", "Bookshelf", "Kitob javoni", "📚", 3, listOf(
            v("Дуб", "Oak", "Eman", 0xFFC48A55, 0xFF7A6BC4),
            v("Орех", "Walnut", "Yong'oq", 0xFF7A5238, 0xFFF2B01E),
            v("Белая", "White", "Oq", 0xFFF4F0EA, 0xFF3FB8A5))),
        RoomItem("plant", "Растение", "Plant", "O'simlik", "🌿", 4, listOf(
            v("Папоротник", "Fern", "Paporotnik", 0xFF4FAE62, 0xFFB5651D),
            v("Кактус", "Cactus", "Kaktus", 0xFF6BBE7A, 0xFFE57A8E),
            v("Цветок", "Flower", "Gul", 0xFFEE7BA3, 0xFF3FB8A5))),
        RoomItem("clock", "Часы", "Clock", "Soat", "🕒", 4, listOf(
            v("Оранжевые", "Orange", "To'q sariq", 0xFFF0883A, 0xFFFFFFFF),
            v("Бирюзовые", "Teal", "Firuza", 0xFF3FB8A5, 0xFFFFFFFF),
            v("Чёрные", "Black", "Qora", 0xFF2A2A33, 0xFFF4F0EA))),
        RoomItem("desk", "Стол", "Desk", "Stol", "💻", 4, listOf(
            v("Дерево", "Wood", "Yog'och", 0xFFC48A55, 0xFF8FD3F4),
            v("Белый", "White", "Oq", 0xFFF1EEE8, 0xFFB7F0C8),
            v("Тёмный", "Dark", "Qora", 0xFF4A3A44, 0xFFF9C6E0))),
        RoomItem("sofa", "Диван", "Sofa", "Divan", "🛋️", 5, listOf(
            v("Сиреневый", "Violet", "Binafsha", 0xFF7A6BC4, 0xFFA99BE6),
            v("Бирюзовый", "Teal", "Firuza", 0xFF3FB8A5, 0xFF7FD6C6),
            v("Розовый", "Rose", "Pushti", 0xFFE57A8E, 0xFFF3A9B8))),
        RoomItem("lights", "Гирлянда", "String lights", "Chiroq gulchambar", "✨", 5, listOf(
            v("Тёплая", "Warm", "Iliq", 0xFFFFD27A, 0xFFFFA94D),
            v("Разноцветная", "Colourful", "Rang-barang", 0xFFEE7BA3, 0xFF5B9BF0),
            v("Холодная", "Cool", "Sovuq", 0xFFBFE3FF, 0xFF8FC4F5))),
        RoomItem("fish", "Аквариум", "Fish bowl", "Akvarium", "🐟", 6, listOf(
            v("Золотая рыбка", "Goldfish", "Oltin baliq", 0xFFF28C28, 0xFF9AD5F2),
            v("Синий петушок", "Blue betta", "Ko'k baliq", 0xFF3F6FE0, 0xFFB5E3F5),
            v("Розовая рыбка", "Pink fish", "Pushti baliq", 0xFFEE7BA3, 0xFFA8DCEB))))

    fun byId(id: String): RoomItem? = all.firstOrNull { it.id == id }
    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id }

    /** Sum of the per-level stars (each clamped to 0..3, so bad data cannot make a fortune or a debt). */
    fun starsTotal(perLevel: List<Int>): Int {
        var s = 0
        for (x in perLevel) s += x.coerceIn(0, MAX_STARS_PER_LEVEL)
        return s
    }

    /** Stars the owned items cost (unknown ids cost nothing). */
    fun starsSpent(owned: Map<String, Int>): Int {
        var s = 0
        for (id in owned.keys) s += byId(id)?.cost ?: 0
        return s
    }

    /** Stars left to spend; never negative. */
    fun balance(total: Int, owned: Map<String, Int>): Int = maxOf(0, total - starsSpent(owned))

    fun canBuy(id: String, owned: Map<String, Int>, total: Int): Boolean {
        val item = byId(id) ?: return false
        return !owned.containsKey(id) && balance(total, owned) >= item.cost
    }

    /** The new owned map with [id] bought in its first look, or null when it cannot be bought. */
    fun buy(id: String, owned: Map<String, Int>, total: Int): Map<String, Int>? {
        if (!canBuy(id, owned, total)) return null
        val m = LinkedHashMap(owned)
        m[id] = 0
        return m
    }

    /** The new owned map with the look of an owned item changed (free); unchanged when not owned or out of range. */
    fun choose(id: String, variant: Int, owned: Map<String, Int>): Map<String, Int> {
        val item = byId(id) ?: return owned
        if (!owned.containsKey(id) || variant !in item.variants.indices) return owned
        val m = LinkedHashMap(owned)
        m[id] = variant
        return m
    }

    /** "wall:1,rug:0" in catalogue order; unknown ids and bad looks are left out. */
    fun serialize(owned: Map<String, Int>): String {
        val sb = StringBuilder()
        for (item in all) {
            val v = owned[item.id] ?: continue
            if (v !in item.variants.indices) continue
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(item.id).append(':').append(v)
        }
        return sb.toString()
    }

    /** Reads what [serialize] wrote. Forgiving: bad pieces are skipped, a look out of range falls back to the first one. */
    fun parse(s: String?): Map<String, Int> {
        val m = LinkedHashMap<String, Int>()
        if (s.isNullOrBlank()) return m
        for (part in s.split(',')) {
            val kv = part.split(':')
            if (kv.size != 2) continue
            val id = kv[0].trim()
            val item = byId(id) ?: continue
            val v = kv[1].trim().toIntOrNull() ?: continue
            m[id] = if (v in item.variants.indices) v else 0
        }
        return m
    }
}
