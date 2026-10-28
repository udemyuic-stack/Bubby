package com.example.buddy

// Pure Kotlin: turns a flat accessibility tree into the compact, numbered screen description the model reads.

/** One accessibility node, flattened in pre-order ([parent] = index of the parent, -1 for the root). */
class ScreenNode(
    val parent: Int,
    val text: String = "", val desc: String = "", val hint: String = "", val viewId: String = "", val cls: String = "",
    val clickable: Boolean = false, val longClickable: Boolean = false, val scrollable: Boolean = false,
    val checkable: Boolean = false, val checked: Boolean = false, val focused: Boolean = false, val editable: Boolean = false,
    val enabled: Boolean = true, val password: Boolean = false, val selected: Boolean = false, val visible: Boolean = true,
    val l: Int = 0, val t: Int = 0, val r: Int = 0, val b: Int = 0,
)

/**
 * The description: [text] for the model, [sig] a signature of what is on screen (numbers left out), [numbered] maps
 * element number n to the node index numbered[n-1], [labels] the label of each numbered element.
 */
class ScreenDesc(val text: String, val sig: Int, val numbered: List<Int>, val labels: List<String>, val changed: Boolean?, val hidden: Boolean = false)

object ScreenDescriber {
    const val MAX_CHARS = 3600
    const val MAX_ITEMS = 70
    const val LIST_MAX = 14

    private fun clean(s: String, max: Int = 80): String {
        val t = s.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').replace(Regex("\\s+"), " ").trim()
        return if (t.length > max) t.take(max - 1) + "…" else t
    }

    fun role(n: ScreenNode): String {
        val c = n.cls.substringAfterLast('.')
        return when {
            n.editable || c.contains("EditText") -> "edit"
            c.contains("Switch") -> "switch"
            c.contains("CheckBox") -> "checkbox"
            c.contains("RadioButton") -> "radio"
            c.contains("ToggleButton") -> "toggle"
            c.contains("SeekBar") || c.contains("Slider") -> "slider"
            c.contains("Button") -> "button"
            c.contains("Spinner") -> "dropdown"
            c.contains("Tab") && n.clickable -> "tab"
            c.contains("WebView") -> "web"
            n.scrollable || c.contains("RecyclerView") || c.contains("ListView") || c.contains("ScrollView") || c.contains("GridView") || c.contains("ViewPager") -> "list"
            c.contains("Image") -> if (n.clickable) "button" else "image"
            n.checkable -> "checkbox"
            n.clickable || n.longClickable -> "button"
            else -> "text"
        }
    }

    private fun interactive(n: ScreenNode) = n.clickable || n.longClickable || n.checkable || n.editable || n.scrollable
    private fun own(n: ScreenNode): String = n.text.ifBlank { n.desc }.trim()

    /** Short id name: "com.app:id/search_box" -> "search_box". */
    fun idName(v: String): String = v.substringAfter(":id/", v).substringAfterLast('/')

    fun describe(nodes: List<ScreenNode>, pkg: String, activity: String?, screenW: Int, screenH: Int, prevSig: Int?,
                 maxChars: Int = MAX_CHARS, maxItems: Int = MAX_ITEMS): ScreenDesc {
        val n = nodes.size
        val children = Array(n) { ArrayList<Int>(2) }
        for (i in 0 until n) { val p = nodes[i].parent; if (p in 0 until n) children[p].add(i) }
        val consumed = BooleanArray(n)
        val derived = arrayOfNulls<String>(n)
        // a clickable row without its own label is named after the texts inside it (which are then not listed again)
        for (i in 0 until n) {
            val x = nodes[i]
            if (!x.visible || x.editable || x.scrollable || !(x.clickable || x.longClickable || x.checkable) || own(x).isNotEmpty()) continue
            val texts = ArrayList<Int>()
            fun collect(j: Int, d: Int) {
                if (d > 6 || texts.size >= 3) return
                for (c in children[j]) {
                    val y = nodes[c]
                    if (interactive(y)) continue
                    if (y.visible && own(y).isNotEmpty()) texts.add(c)
                    collect(c, d + 1)
                }
            }
            collect(i, 0)
            if (texts.isNotEmpty()) {
                derived[i] = texts.joinToString(" · ") { clean(own(nodes[it]), 40) }
                for (c in texts) consumed[c] = true
            }
        }
        // the nearest scrollable ancestor of each node (to collapse long lists)
        val listOf = IntArray(n) { -1 }
        for (i in 0 until n) { val p = nodes[i].parent; if (p in 0 until n) listOf[i] = if (nodes[p].scrollable) p else listOf[p] }

        class Item(val idx: Int, val line: String, val label: String, val inter: Boolean, val list: Int)
        val items = ArrayList<Item>()
        val perList = HashMap<Int, Int>()
        val hiddenPerList = HashMap<Int, Int>()
        val sigParts = StringBuilder(pkg)
        for (i in 0 until n) {
            val x = nodes[i]
            if (!x.visible || consumed[i]) continue
            if (x.r - x.l <= 0 || x.b - x.t <= 0) continue
            val label = own(x).ifEmpty { derived[i] ?: "" }
            val inter = interactive(x)
            if (!inter && label.isEmpty()) continue
            val r = role(x)
            sigParts.append('|').append(r).append(':').append(label.take(40)).append(if (x.checked) "+" else "")
            val lst = listOf[i]
            if (lst >= 0) {
                val c = (perList[lst] ?: 0) + 1
                perList[lst] = c
                if (c > LIST_MAX) { hiddenPerList[lst] = (hiddenPerList[lst] ?: 0) + 1; continue }
            }
            val sb = StringBuilder()
            sb.append(r)
            if (label.isNotEmpty()) sb.append(" \"").append(clean(label)).append('"')
            if (x.text.isNotBlank() && x.desc.isNotBlank() && x.desc.trim() != x.text.trim()) sb.append(" (").append(clean(x.desc, 40)).append(')')
            if (x.editable && x.hint.isNotBlank() && x.hint.trim() != label) sb.append(" hint=\"").append(clean(x.hint, 40)).append('"')
            val id = idName(x.viewId)
            if (id.isNotEmpty() && (label.isEmpty() || inter) && id.length <= 32) sb.append(" #").append(id)
            val flags = ArrayList<String>()
            if (x.checkable) flags.add(if (x.checked) "on" else "off")
            else if (x.selected) flags.add("selected")
            if (x.focused) flags.add("focused")
            if (x.password) flags.add("password")
            if (!x.enabled) flags.add("disabled")
            if (x.scrollable) flags.add("scrollable")
            if (x.longClickable && !x.clickable) flags.add("long-press")
            if (flags.isNotEmpty()) sb.append(" [").append(flags.joinToString(",")).append(']')
            // coordinates only where a label cannot identify the element
            if (label.isEmpty() || r == "image" || r == "slider") sb.append(" @").append((x.l + x.r) / 2).append(',').append((x.t + x.b) / 2)
            items.add(Item(i, sb.toString(), clean(label, 40), inter, lst))
        }
        val sig = sigParts.toString().hashCode()
        val changed = if (prevSig == null) null else prevSig != sig
        val head = StringBuilder("App: ").append(pkg.ifEmpty { "?" })
        if (!activity.isNullOrBlank()) head.append(" (").append(activity.substringAfterLast('.')).append(')')
        if (screenW > 0) head.append(" | screen ").append(screenW).append('x').append(screenH)
        head.append(" | ").append(when (changed) { null -> "first look"; true -> "screen changed since last step"; false -> "screen UNCHANGED since last step" })

        val allText = items.joinToString("\n") { it.line }
        if (Safety.hasPrivateText(allText)) return ScreenDesc(head.toString() + "\n(screen hidden for privacy: it shows card or payment details)", sig, emptyList(), emptyList(), changed, hidden = true)

        // too many elements: plain texts go first, interactive elements are kept
        var keep = items.toMutableList()
        if (keep.size > maxItems) {
            val over = keep.size - maxItems
            val drop = keep.withIndex().filter { !it.value.inter }.map { it.index }.takeLast(over).toSet()
            keep = keep.filterIndexed { k, _ -> k !in drop }.toMutableList()
            if (keep.size > maxItems) keep = keep.take(maxItems).toMutableList()
        }
        // too long: drop plain texts from the end until it fits, so the buttons further down stay reachable
        var total = keep.sumOf { it.line.length + 8 } + 120
        var k = keep.size - 1
        while (total > maxChars && k >= 0) {
            if (!keep[k].inter) { total -= keep[k].line.length + 8; keep.removeAt(k) }
            k--
        }
        val out = StringBuilder(head).append('\n')
        val numbered = ArrayList<Int>()
        val labels = ArrayList<String>()
        var truncated = items.size - keep.size
        var lastList = -1
        for (it in keep) {
            if (lastList >= 0 && it.list != lastList) hiddenPerList[lastList]?.let { h -> out.append("  … ").append(h).append(" more items in this list (scroll to see them)\n"); hiddenPerList.remove(lastList) }
            val line = (if (it.list >= 0) "  " else "") + "[" + (numbered.size + 1) + "] " + it.line + "\n"
            if (out.length + line.length > maxChars) { truncated += keep.size - numbered.size; break }
            out.append(line)
            numbered.add(it.idx); labels.add(it.label)
            lastList = it.list
        }
        for ((_, h) in hiddenPerList) out.append("  … ").append(h).append(" more items in a list (scroll to see them)\n")
        if (truncated > 0) out.append("(").append(truncated).append(" more elements not shown)\n")
        if (numbered.isEmpty()) out.append("(no readable elements)\n")
        return ScreenDesc(out.toString().trimEnd(), sig, numbered, labels, changed)
    }
}
