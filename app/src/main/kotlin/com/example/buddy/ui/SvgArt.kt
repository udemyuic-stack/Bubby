package com.example.buddy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.Xml
import java.io.StringReader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A small SVG renderer for the theme mascots (the files in assets/mascot, taken from the design mockups): paths, circles, ellipses,
 * rectangles, groups, linear and radial gradients, clip paths, blur and glow filters (approximated), `animateMotion`, and the
 * CSS animations of the mockups, which are re-created here by class name. No libraries: only android.graphics.
 */
object SvgArt {
    private var app: Context? = null
    private val cache = HashMap<String, Art?>()

    fun init(ctx: Context) { if (app == null) app = ctx.applicationContext }

    fun get(name: String): Art? {
        synchronized(cache) {
            if (cache.containsKey(name)) return cache[name]
            val a = try {
                val c = app ?: return null
                Art(name, c.assets.open("mascot/$name.svg").bufferedReader().use { it.readText() })
            } catch (e: Exception) { null }
            cache[name] = a
            return a
        }
    }
}

private class Node(val tag: String, val attrs: Map<String, String>) {
    val children = ArrayList<Node>()
    val classes: Set<String> = (attrs["class"] ?: "").split(' ').filter { it.isNotEmpty() }.toSet()
    val style: Map<String, String> = (attrs["style"] ?: "").split(';').mapNotNull {
        val i = it.indexOf(':'); if (i < 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
    }.toMap()
    var shape: Path? = null
    var bounds: RectF? = null
    var matrix: Matrix? = null
    var motion: Path? = null
    var fillSh: Shader? = null
    var strokeSh: Shader? = null
    var motionDur = 1f
    var motionBegin = 0f
    val delay: Float get() = (style["animation-delay"] ?: "0").removeSuffix("s").toFloatOrNull() ?: 0f
    fun attr(k: String) = attrs[k]
}

private class Stop(val off: Float, val color: Int)
private class Grad(val radial: Boolean, val a: FloatArray, val stops: List<Stop>)

class Art(val name: String, svg: String) {
    private var root: Node? = null
    private val grads = HashMap<String, Grad>()
    private val clips = HashMap<String, Path>()
    private val blurs = HashMap<String, Pair<Float, Boolean>>()   // filter id -> (std deviation, also draws the source: a glow)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** True when something moves, so the view must keep redrawing. */
    val animated = true

    init { try { parse(svg) } catch (e: Exception) { root = null } }

    // ---------------------------------------------------------------------------------------------------------- parsing

    private val INHERIT = listOf("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill-opacity", "stroke-opacity")

    private fun parse(svg: String) {
        val p = Xml.newPullParser()
        p.setInput(StringReader(svg))
        val stack = ArrayList<Node>()
        var inDefs = 0
        var gradId: String? = null
        var gradNode: Node? = null
        var gradStops = ArrayList<Stop>()
        var clipId: String? = null
        var filterId: String? = null
        var ev = p.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (ev) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    val a = HashMap<String, String>()
                    for (i in 0 until p.attributeCount) a[p.getAttributeName(i)] = p.getAttributeValue(i)
                    val name = p.name
                    if (name == "defs") inDefs++
                    else if (name == "animateMotion") {
                        stack.lastOrNull()?.let { par ->
                            par.motion = pathOf(a["path"] ?: "")
                            par.motionDur = (a["dur"] ?: "1s").removeSuffix("s").toFloatOrNull() ?: 1f
                            par.motionBegin = (a["begin"] ?: "0s").removeSuffix("s").toFloatOrNull() ?: 0f
                        }
                    } else if (inDefs > 0) {
                        val n = Node(name, a)
                        when (name) {
                            "linearGradient", "radialGradient" -> { gradId = a["id"]; gradNode = n; gradStops = ArrayList() }
                            "stop" -> {
                                val c = color(a["stop-color"] ?: n.style["stop-color"] ?: "#000", (a["stop-opacity"] ?: n.style["stop-opacity"])?.toFloatOrNull() ?: 1f)
                                gradStops.add(Stop(num(a["offset"] ?: "0"), c))
                            }
                            "clipPath" -> clipId = a["id"]
                            "path" -> if (clipId != null) clips[clipId!!] = pathOf(a["d"] ?: "")
                            "filter" -> { filterId = a["id"]; blurs[filterId!!] = 0f to false }
                            "feGaussianBlur" -> filterId?.let { id -> blurs[id] = (a["stdDeviation"]?.toFloatOrNull() ?: 0f) to (blurs[id]?.second ?: false) }
                            "feMergeNode" -> if (a["in"] == "SourceGraphic") filterId?.let { id -> blurs[id] = (blurs[id]?.first ?: 0f) to true }
                        }
                    } else {
                        val par = stack.lastOrNull()
                        val merged = HashMap<String, String>()
                        if (par != null) for (k in INHERIT) par.attrs[k]?.let { merged[k] = it }
                        merged.putAll(a)
                        val n = Node(name, merged)
                        prepare(n)
                        par?.children?.add(n)
                        if (par == null) root = n
                        stack.add(n)
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    val name = p.name
                    when {
                        name == "defs" -> inDefs--
                        name == "animateMotion" -> {}
                        inDefs > 0 -> when (name) {
                            "linearGradient", "radialGradient" -> {
                                val g = gradNode
                                if (g != null && gradId != null) {
                                    val a = if (name == "radialGradient") floatArrayOf(num(g.attr("cx") ?: ".5"), num(g.attr("cy") ?: ".5"), num(g.attr("r") ?: ".5"), 0f)
                                    else floatArrayOf(num(g.attr("x1") ?: "0"), num(g.attr("y1") ?: "0"), num(g.attr("x2") ?: "1"), num(g.attr("y2") ?: "0"))
                                    grads[gradId!!] = Grad(name == "radialGradient", a, gradStops)
                                }
                                gradId = null; gradNode = null
                            }
                            "clipPath" -> clipId = null
                            "filter" -> filterId = null
                        }
                        stack.isNotEmpty() -> stack.removeAt(stack.size - 1)
                    }
                }
            }
            ev = p.next()
        }
    }

    private fun prepare(n: Node) {
        val a = n.attrs
        when (n.tag) {
            "path" -> n.shape = pathOf(a["d"] ?: "")
            "circle" -> n.shape = Path().apply { addCircle(num(a["cx"]), num(a["cy"]), num(a["r"]), Path.Direction.CW) }
            "ellipse" -> n.shape = Path().apply { addOval(RectF(num(a["cx"]) - num(a["rx"]), num(a["cy"]) - num(a["ry"]), num(a["cx"]) + num(a["rx"]), num(a["cy"]) + num(a["ry"])), Path.Direction.CW) }
            "rect" -> n.shape = Path().apply { val x = num(a["x"]); val y = num(a["y"]); val w = num(a["width"]); val h = num(a["height"]); val rx = num(a["rx"]); addRoundRect(RectF(x, y, x + w, y + h), rx, if (a["ry"] != null) num(a["ry"]) else rx, Path.Direction.CW) }
            "line" -> n.shape = Path().apply { moveTo(num(a["x1"]), num(a["y1"])); lineTo(num(a["x2"]), num(a["y2"])) }
        }
        n.shape?.let { s -> n.bounds = RectF().also { s.computeBounds(it, true) } }
        a["transform"]?.let { n.matrix = matrixOf(it) }
    }

    private fun num(s: String?): Float = s?.trim()?.removeSuffix("px")?.let { if (it.endsWith("%")) (it.dropLast(1).toFloatOrNull() ?: 0f) / 100f else it.toFloatOrNull() } ?: 0f

    private fun color(s: String, alpha: Float = 1f): Int {
        val v = s.trim()
        var c = Color.BLACK; var a = 1f
        try {
            when {
                v.startsWith("#") -> {
                    val h = v.substring(1)
                    c = when (h.length) {
                        3 -> Color.rgb(h.substring(0, 1).repeat(2).toInt(16), h.substring(1, 2).repeat(2).toInt(16), h.substring(2, 3).repeat(2).toInt(16))
                        6 -> Color.rgb(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
                        else -> Color.BLACK
                    }
                }
                v.startsWith("rgba") || v.startsWith("rgb") -> {
                    val parts = v.substringAfter('(').substringBefore(')').split(',').map { it.trim() }
                    c = Color.rgb(parts[0].toFloat().toInt(), parts[1].toFloat().toInt(), parts[2].toFloat().toInt())
                    if (parts.size > 3) a = parts[3].toFloat()
                }
                v == "white" -> c = Color.WHITE
                v == "black" -> c = Color.BLACK
            }
        } catch (e: Exception) { }
        return Color.argb((255 * a * alpha).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
    }

    private fun matrixOf(s: String): Matrix {
        val m = Matrix()
        for (mt in Regex("(\\w+)\\(([^)]*)\\)").findAll(s)) {
            val v = mt.groupValues[2].split(Regex("[ ,]+")).filter { it.isNotEmpty() }.map { it.toFloat() }
            val t = Matrix()
            when (mt.groupValues[1]) {
                "rotate" -> if (v.size >= 3) t.setRotate(v[0], v[1], v[2]) else t.setRotate(v[0])
                "translate" -> t.setTranslate(v[0], v.getOrElse(1) { 0f })
                "scale" -> t.setScale(v[0], v.getOrElse(1) { v[0] })
                "skewX" -> t.setSkew(tan(Math.toRadians(v[0].toDouble())).toFloat(), 0f)
                "matrix" -> t.setValues(floatArrayOf(v[0], v[2], v[4], v[1], v[3], v[5], 0f, 0f, 1f))
            }
            m.preConcat(t)
        }
        return m
    }

    // SVG path data: M L H V C S Q T A Z, absolute and relative
    private fun pathOf(d: String): Path {
        val p = Path()
        val tk = Regex("[MmLlHhVvCcSsQqTtAaZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE]-?\\d+)?").findAll(d).map { it.value }.toList()
        var i = 0
        var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f; var lcx = 0f; var lcy = 0f
        var cmd = 'M'; var prev = 'M'
        fun f() = tk[i++].toFloat()
        while (i < tk.size) {
            if (tk[i][0].isLetter()) cmd = tk[i++][0]
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> { val x = f() + if (rel) cx else 0f; val y = f() + if (rel) cy else 0f; p.moveTo(x, y); cx = x; cy = y; sx = x; sy = y; cmd = if (rel) 'l' else 'L' }
                'L' -> { val x = f() + if (rel) cx else 0f; val y = f() + if (rel) cy else 0f; p.lineTo(x, y); cx = x; cy = y }
                'H' -> { val x = f() + if (rel) cx else 0f; p.lineTo(x, cy); cx = x }
                'V' -> { val y = f() + if (rel) cy else 0f; p.lineTo(cx, y); cy = y }
                'C' -> {
                    val x1 = f() + if (rel) cx else 0f; val y1 = f() + if (rel) cy else 0f
                    val x2 = f() + if (rel) cx else 0f; val y2 = f() + if (rel) cy else 0f
                    val x = f() + if (rel) cx else 0f; val y = f() + if (rel) cy else 0f
                    p.cubicTo(x1, y1, x2, y2, x, y); lcx = x2; lcy = y2; cx = x; cy = y
                }
                'S' -> {
                    val x2 = f() + if (rel) cx else 0f; val y2 = f() + if (rel) cy else 0f
                    val x = f() + if (rel) cx else 0f; val y = f() + if (rel) cy else 0f
                    val x1 = if (prev.uppercaseChar() == 'C' || prev.uppercaseChar() == 'S') 2 * cx - lcx else cx
                    val y1 = if (prev.uppercaseChar() == 'C' || prev.uppercaseChar() == 'S') 2 * cy - lcy else cy
                    p.cubicTo(x1, y1, x2, y2, x, y); lcx = x2; lcy = y2; cx = x; cy = y
                }
                'Q' -> {
                    val x1 = f() + if (rel) cx else 0f; val y1 = f() + if (rel) cy else 0f
                    val x = f() + if (rel) cx else 0f; val y = f() + if (rel) cy else 0f
                    p.quadTo(x1, y1, x, y); lcx = x1; lcy = y1; cx = x; cy = y
                }
                'T' -> {
                    val x = f() + if (rel) cx else 0f; val y = f() + if (rel) cy else 0f
                    val x1 = if (prev.uppercaseChar() == 'Q' || prev.uppercaseChar() == 'T') 2 * cx - lcx else cx
                    val y1 = if (prev.uppercaseChar() == 'Q' || prev.uppercaseChar() == 'T') 2 * cy - lcy else cy
                    p.quadTo(x1, y1, x, y); lcx = x1; lcy = y1; cx = x; cy = y
                }
                'A' -> {
                    val rx = f(); val ry = f(); val rot = f(); val large = f() != 0f; val sweep = f() != 0f
                    val x = f() + if (rel) cx else 0f; val y = f() + if (rel) cy else 0f
                    arc(p, cx, cy, rx, ry, rot, large, sweep, x, y); cx = x; cy = y
                }
                'Z' -> { p.close(); cx = sx; cy = sy }
                else -> i++
            }
            prev = cmd
        }
        return p
    }

    private fun arc(p: Path, x0: Float, y0: Float, rxIn: Float, ryIn: Float, rotDeg: Float, large: Boolean, sweep: Boolean, x: Float, y: Float) {
        if (rxIn == 0f || ryIn == 0f || (x0 == x && y0 == y)) { p.lineTo(x, y); return }
        var rx = abs(rxIn); var ry = abs(ryIn)
        val phi = Math.toRadians(rotDeg.toDouble())
        val cp = cos(phi); val sp = sin(phi)
        val dx = (x0 - x) / 2.0; val dy = (y0 - y) / 2.0
        val x1 = cp * dx + sp * dy; val y1 = -sp * dx + cp * dy
        val lam = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
        if (lam > 1) { val s = sqrt(lam); rx *= s.toFloat(); ry *= s.toFloat() }
        val num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        val den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        var co = if (den == 0.0) 0.0 else sqrt(maxOf(0.0, num / den))
        if (large == sweep) co = -co
        val cxp = co * rx * y1 / ry; val cyp = -co * ry * x1 / rx
        val ccx = cp * cxp - sp * cyp + (x0 + x) / 2.0; val ccy = sp * cxp + cp * cyp + (y0 + y) / 2.0
        fun ang(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val a = atan2(ux * vy - uy * vx, ux * vx + uy * vy); return a
        }
        val th1 = ang(1.0, 0.0, (x1 - cxp) / rx, (y1 - cyp) / ry)
        var dth = ang((x1 - cxp) / rx, (y1 - cyp) / ry, (-x1 - cxp) / rx, (-y1 - cyp) / ry)
        if (!sweep && dth > 0) dth -= 2 * PI else if (sweep && dth < 0) dth += 2 * PI
        // approximate with cubic segments of at most 90 degrees
        val n = Math.ceil(abs(dth) / (PI / 2)).toInt().coerceAtLeast(1)
        val step = dth / n
        val t = 4.0 / 3.0 * tan(step / 4)
        var a = th1
        for (k in 0 until n) {
            val c1 = cos(a); val s1 = sin(a); val c2 = cos(a + step); val s2 = sin(a + step)
            fun px(ex: Double, ey: Double) = (cp * rx * ex - sp * ry * ey + ccx).toFloat()
            fun py(ex: Double, ey: Double) = (sp * rx * ex + cp * ry * ey + ccy).toFloat()
            p.cubicTo(px(c1 - t * s1, s1 + t * c1), py(c1 - t * s1, s1 + t * c1),
                px(c2 + t * s2, s2 - t * c2), py(c2 + t * s2, s2 - t * c2), px(c2, s2), py(c2, s2))
            a += step
        }
    }

    // --------------------------------------------------------------------------------------------------------- drawing

    private val HIDDEN = setOf("sun-sad", "tear", "cl-bolt", "sf-o", "glint")

    private fun ease(x: Float) = (1f - cos(PI * x.coerceIn(0f, 1f)).toFloat()) / 2f
    /** 0..1..0 over one period (for ease-in-out infinite) */
    private fun tri(t: Float, period: Float): Float { val p = (t / period) % 1f; val q = if (p < 0) p + 1f else p; return if (q < .5f) q * 2 else (1 - q) * 2 }
    /** keyframes: stops at 0..1 with values */
    private fun keys(t: Float, period: Float, at: FloatArray, v: FloatArray): Float {
        var p = (t / period) % 1f; if (p < 0) p += 1f
        for (i in 0 until at.size - 1) if (p <= at[i + 1]) return v[i] + (v[i + 1] - v[i]) * ease((p - at[i]) / (at[i + 1] - at[i]))
        return v.last()
    }

    private var time = 0f
    private var blink = 0f
    private var alphaMul = 1f

    /** Draws the art on a 170x136 canvas; [t] is seconds, [blinkClosed] 0..1 shuts the eyes. */
    fun draw(cv: Canvas, t: Float, blinkClosed: Float = 0f) {
        val r = root ?: return
        time = t; blink = blinkClosed; alphaMul = 1f
        node(cv, r)
    }

    private fun node(cv: Canvas, n: Node) {
        if (n.style["display"] == "none" || n.classes.any { it in HIDDEN }) return
        cv.save()
        val savedAlpha = alphaMul
        n.matrix?.let { cv.concat(it) }
        motion(cv, n)
        alphaMul *= animate(cv, n)
        val op = n.attr("opacity")?.toFloatOrNull() ?: 1f
        alphaMul *= op
        val clip = n.attr("clip-path")?.let { ref(it) }?.let { clips[it] }
        if (clip != null) cv.clipPath(clip)
        when (n.tag) {
            "svg", "g" -> for (c in n.children) node(cv, c)
            else -> n.shape?.let { drawShape(cv, n, it) }
        }
        alphaMul = savedAlpha
        cv.restore()
    }

    private fun ref(s: String): String? = Regex("url\\(#([^)]+)\\)").find(s)?.groupValues?.get(1)

    private fun motion(cv: Canvas, n: Node) {
        val m = n.motion ?: return
        val pm = PathMeasure(m, false)
        val f = (((time - n.motionBegin) / n.motionDur) % 1f).let { if (it < 0) it + 1f else it }
        val pos = FloatArray(2)
        pm.getPosTan(pm.length * f, pos, null)
        cv.translate(pos[0], pos[1])
    }

    private fun about(cv: Canvas, ox: Float, oy: Float, rot: Float = 0f, sx: Float = 1f, sy: Float = 1f, tx: Float = 0f, ty: Float = 0f) {
        cv.translate(ox + tx, oy + ty)
        if (rot != 0f) cv.rotate(rot)
        if (sx != 1f || sy != 1f) cv.scale(sx, sy)
        cv.translate(-ox, -oy)
    }

    /** The CSS animations of the mockups, by class. Returns an opacity factor. */
    private fun animate(cv: Canvas, n: Node): Float {
        val t = time
        var a = 1f
        for (c in n.classes) when (c) {
            "sun-rays" -> about(cv, 85f, 70f, rot = 360f * (t / 18f))
            "sun-rays2" -> about(cv, 85f, 70f, rot = -360f * (t / 26f))
            "sun-body" -> { val s = 1f + .04f * tri(t, 3f).let { ease(it) }; about(cv, 85f, 70f, sx = s, sy = s) }
            "gh-body" -> { val p = ease(tri(t, 3f)); about(cv, 85f, 124f, rot = -2f + 4f * p, sx = 1f + .03f * p, sy = 1f - .03f * p) }
            "pk-face" -> a = .7f + .3f * ease(tri(t, 2.4f))
            "cl-body" -> { val at = floatArrayOf(0f, .4f, .7f, 1f)
                about(cv, 85f, 104f, sx = keys(t, 3.8f, at, floatArrayOf(1f, 1.03f, .98f, 1f)), sy = keys(t, 3.8f, at, floatArrayOf(1f, .97f, 1.02f, 1f))) }
            "cl-drop" -> { val f = (((t - n.delay) / 1.1f) % 1f).let { if (it < 0) it + 1f else it }
                cv.translate(0f, 34f * f * f); a = if (f < .2f) f / .2f else (1f - f) / .8f }
            "sf" -> { val p = ease(tri(t, 3.6f)); about(cv, 85f, 74f, rot = -3f + 6f * p, ty = -6f * p) }
            "jm-bell" -> { val at = floatArrayOf(0f, .4f, .7f, 1f)
                about(cv, 85f, 74f, sx = keys(t, 1.8f, at, floatArrayOf(1f, 1.07f, .97f, 1f)), sy = keys(t, 1.8f, at, floatArrayOf(1f, .9f, 1.04f, 1f))) }
            "jm-tent" -> { val at = floatArrayOf(0f, .4f, 1f)
                about(cv, 85f, 74f, sx = keys(t, 1.8f, at, floatArrayOf(1f, .92f, 1f)), sy = keys(t, 1.8f, at, floatArrayOf(1f, 1.08f, 1f))) }
            "mt" -> { val b = n.bounds
                if (b != null) { val k = ease(tri(t - n.delay, 3.2f)); val sk = tan(Math.toRadians((-10f + 20f * k).toDouble())).toFloat()
                    cv.translate(b.centerX(), b.top); cv.skew(sk, 0f); cv.translate(-b.centerX(), -b.top) } }
            "hat" -> about(cv, 86f, 22f, rot = -3f + 7f * ease(tri(t, 6.4f)))
            "pom" -> about(cv, 134f, -10f, rot = -12f + 26f * ease(tri(t, 3f)))
            "nose" -> { val b = n.bounds ?: RectF(84f, 84f, 116f, 97f)
                val r = keys(t, 4f, floatArrayOf(0f, .8f, .85f, .9f, .95f, 1f), floatArrayOf(0f, 0f, -8f, 7f, -3f, 0f)); about(cv, b.left, b.centerY(), rot = r) }
            "cheek" -> if (name == "snowman") a = .35f + .35f * ease(tri(t, 6f))
            "eye" -> {
                val bl = keys(t, 4.6f, floatArrayOf(0f, .86f, .9f, .94f, 1f), floatArrayOf(1f, 1f, .2f, 1.2f, 1f))
                val sy = (bl * (1f - .85f * blink)).coerceAtLeast(.05f)
                val b = n.bounds
                if (b != null) { cv.translate(b.centerX(), b.bottom); cv.scale(1f, sy); cv.translate(-b.centerX(), -b.bottom) }
            }
        }
        return a
    }

    /** Sets up [p] for the fill/stroke spec [v]; false when nothing is to be painted. */
    private fun paintFor(n: Node, v: String?, stroke: Boolean, bounds: RectF, p: Paint, alpha: Float): Boolean {
        if (v == null || v == "none") return false
        val gid = ref(v)
        if (gid != null) {
            val g = grads[gid] ?: return false
            val sh = (if (stroke) n.strokeSh else n.fillSh) ?: gradient(g, bounds).also { if (stroke) n.strokeSh = it else n.fillSh = it }
            p.shader = sh
            p.color = Color.argb((255 * alpha).toInt().coerceIn(0, 255), 255, 255, 255)
            return true
        }
        p.shader = null
        p.color = color(v, alpha)
        return true
    }

    private fun gradient(g: Grad, b: RectF): Shader {
        val cols = IntArray(g.stops.size) { g.stops[it].color }
        val pos = FloatArray(g.stops.size) { g.stops[it].off }
        val sh: Shader = if (g.radial) RadialGradient(g.a[0], g.a[1], g.a[2].coerceAtLeast(.001f), cols, pos, Shader.TileMode.CLAMP)
        else LinearGradient(g.a[0], g.a[1], g.a[2], g.a[3], cols, pos, Shader.TileMode.CLAMP)
        val m = Matrix()
        m.setScale(maxOf(b.width(), .001f), maxOf(b.height(), .001f))
        m.postTranslate(b.left, b.top)
        sh.setLocalMatrix(m)
        return sh
    }

    private fun drawShape(cv: Canvas, n: Node, shape: Path) {
        val b = n.bounds ?: RectF().also { shape.computeBounds(it, true); n.bounds = it }
        val fo = n.attr("fill-opacity")?.toFloatOrNull() ?: 1f
        val so = n.attr("stroke-opacity")?.toFloatOrNull() ?: 1f
        val fillSpec = n.attr("fill") ?: "#000"
        val strokeSpec = n.attr("stroke")
        val filter = n.attr("filter")?.let { ref(it) }?.let { blurs[it] }
        if (filter != null && filter.first > 0f) {
            val std = filter.first
            if (n.tag == "ellipse" || n.tag == "circle") {
                // a blurred round shape: a radial falloff of its colour
                if (fillSpec != "none") {
                    val rx = b.width() / 2f; val ry = b.height() / 2f
                    val rr = rx + std * 2f
                    val base = if (ref(fillSpec) != null) Color.WHITE else color(fillSpec)
                    val al = (Color.alpha(base) * alphaMul * fo).toInt().coerceIn(0, 255)
                    val c0 = Color.argb(al, Color.red(base), Color.green(base), Color.blue(base))
                    val c1 = Color.argb(al * 6 / 10, Color.red(base), Color.green(base), Color.blue(base))
                    val c2 = Color.argb(0, Color.red(base), Color.green(base), Color.blue(base))
                    fill.shader = RadialGradient(0f, 0f, rr, intArrayOf(c0, c1, c2), floatArrayOf(0f, (rx / rr).coerceIn(.05f, .9f), 1f), Shader.TileMode.CLAMP)
                    fill.style = Paint.Style.FILL
                    cv.save(); cv.translate(b.centerX(), b.centerY()); cv.scale(1f, (ry + std * 2f) / rr)
                    cv.drawCircle(0f, 0f, rr, fill)
                    cv.restore(); fill.shader = null
                }
                return
            }
            // other shapes: a few wider, fainter strokes behind, then the shape itself when the filter keeps the source (a glow)
            val gs = if (strokeSpec != null && strokeSpec != "none" && ref(strokeSpec) == null) strokeSpec else if (fillSpec != "none" && ref(fillSpec) == null) fillSpec else "#fff"
            val glowCol = color(gs)
            line.shader = null; line.pathEffect = null
            line.strokeCap = Paint.Cap.ROUND; line.strokeJoin = Paint.Join.ROUND
            for (i in 3 downTo 1) {
                line.strokeWidth = num(n.attr("stroke-width")) + std * i * 1.2f
                line.color = Color.argb((Color.alpha(glowCol) * alphaMul * .16f).toInt().coerceIn(0, 255), Color.red(glowCol), Color.green(glowCol), Color.blue(glowCol))
                cv.drawPath(shape, line)
            }
            if (!filter.second) return
        }
        if (paintFor(n, fillSpec, false, b, fill, alphaMul * fo)) {
            fill.style = Paint.Style.FILL
            cv.drawPath(shape, fill)
        }
        if (strokeSpec != null && strokeSpec != "none") {
            line.strokeWidth = num(n.attr("stroke-width") ?: "1")
            line.strokeCap = when (n.attr("stroke-linecap")) { "round" -> Paint.Cap.ROUND; "square" -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
            line.strokeJoin = when (n.attr("stroke-linejoin")) { "round" -> Paint.Join.ROUND; "bevel" -> Paint.Join.BEVEL; else -> Paint.Join.MITER }
            line.pathEffect = n.attr("stroke-dasharray")?.let { d ->
                val v = d.split(Regex("[ ,]+")).mapNotNull { it.toFloatOrNull() }
                if (v.size >= 2) android.graphics.DashPathEffect(floatArrayOf(v[0], v[1]), 0f) else null
            }
            if (paintFor(n, strokeSpec, true, b, line, alphaMul * so)) cv.drawPath(shape, line)
            line.pathEffect = null
        }
    }
}
