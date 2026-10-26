package com.example.buddy

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object Stickers {
    const val AUTHORITY = "com.example.buddy.stickers"
    private const val SIZE = 512
    private const val MARGIN = 28
    private const val BORDER = 14f
    private const val CAP_TEXT = 36f
    private const val CAP_LINE = 42
    private const val INK = 0xFF1B1530.toInt()

    internal fun dir(ctx: Context): File = File(ctx.cacheDir, "stickers")

    fun render(ctx: Context, mood: String, caption: String, outfit: Set<String>): Bitmap {
        val art = renderArt(ctx, if (mood in Moods.KNOWN) mood else "happy", outfit)
        val cap = wrapCaption(caption.trim())
        val capH = if (cap.lines.isEmpty()) 0 else cap.lines.size * CAP_LINE + 8

        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)

        val inset = MARGIN + BORDER
        val areaW = SIZE - 2 * inset
        val areaH = SIZE - 2 * inset - capH
        val sc = min(areaW / art.width, areaH / art.height)
        val dw = art.width * sc
        val dh = art.height * sc
        val left = (SIZE - dw) / 2f
        val top = inset + (areaH - dh) / 2f

        val layer = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        val fp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        lc.drawBitmap(art, Rect(0, 0, art.width, art.height), RectF(left, top, left + dw, top + dh), fp)
        art.recycle()

        val alpha = layer.extractAlpha()
        val wp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        wp.color = 0xFFFFFFFF.toInt()
        for (ring in 0..1) {
            val rad = if (ring == 0) BORDER else BORDER * 0.5f
            for (i in 0 until 24) {
                val a = i * (2.0 * Math.PI / 24.0)
                cv.drawBitmap(alpha, (cos(a) * rad).toFloat(), (sin(a) * rad).toFloat(), wp)
            }
        }
        cv.drawBitmap(alpha, 0f, 0f, wp)
        alpha.recycle()
        cv.drawBitmap(layer, 0f, 0f, null)
        layer.recycle()

        if (cap.lines.isNotEmpty()) drawCaption(cv, cap)
        return out
    }

    fun png(ctx: Context, mood: String, caption: String): ByteArray {
        val bmp = render(ctx, mood, caption, Outfits.parse(prefOutfit(ctx)))
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        bmp.recycle()
        return bos.toByteArray()
    }

    fun share(ctx: Context, mood: String, caption: String): String? {
        return try {
            val d = dir(ctx)
            if (!d.exists() && !d.mkdirs()) return Tr.t(ctx, "Не удалось создать папку для стикера", "Could not create the sticker folder", "Stiker papkasini yaratib bo'lmadi")
            val cutoff = System.currentTimeMillis() - 3_600_000L
            d.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
            val f = File(d, "sticker_" + System.currentTimeMillis() + ".png")
            f.writeBytes(png(ctx, mood, caption))
            val uri = Uri.parse("content://$AUTHORITY/" + Uri.encode(f.name))
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("sticker", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, Tr.t(ctx, "Поделиться стикером", "Share sticker", "Stikerni ulashish")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(chooser)
            null
        } catch (e: Exception) {
            Tr.t(ctx, "Не удалось поделиться стикером", "Could not share the sticker", "Stikerni ulashib bo'lmadi")
        }
    }

    private fun prefOutfit(ctx: Context): String =
        ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString("outfit", "") ?: ""

    // draws the mascot at a high fixed scale and crops to its visible bounds
    private fun renderArt(ctx: Context, mood: String, outfit: Set<String>): Bitmap {
        val v = BuddyView(ctx)
        val dens = ctx.resources.displayMetrics.density
        v.scaleF = 7f / dens
        v.mood = mood
        v.outfit = outfit
        v.species = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString("species", "cat").orEmpty()
        v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val w = max(1, v.measuredWidth)
        val h = max(1, v.measuredHeight)
        v.layout(0, 0, w, h)

        val pad = 200
        val big = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(big)
        c.translate(pad.toFloat(), pad.toFloat())
        v.draw(c)

        val px = IntArray(big.width * big.height)
        big.getPixels(px, 0, big.width, 0, 0, big.width, big.height)
        var x0 = big.width; var y0 = big.height; var x1 = -1; var y1 = -1
        for (y in 0 until big.height) {
            val row = y * big.width
            for (x in 0 until big.width) {
                if ((px[row + x] ushr 24) > 8) {
                    if (x < x0) x0 = x
                    if (x > x1) x1 = x
                    if (y < y0) y0 = y
                    if (y > y1) y1 = y
                }
            }
        }
        if (x1 < 0) return big
        val crop = Bitmap.createBitmap(big, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
        if (crop !== big) big.recycle()
        return crop
    }

    private class Cap(val lines: List<String>, val size: Float, val paint: Paint)

    private fun wrapCaption(text: String): Cap {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        p.textSize = CAP_TEXT
        if (text.isEmpty()) return Cap(emptyList(), CAP_TEXT, p)
        val maxW = SIZE - 40f
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        // shrink so the longest word fits
        while (p.textSize > 20f && words.any { p.measureText(it) > maxW }) p.textSize -= 2f
        val lines = ArrayList<String>()
        var cur = ""
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(t) <= maxW || cur.isEmpty()) { cur = t; i++ }
            else { lines.add(cur); cur = "" }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        if (lines.size > 2) {
            var l2 = lines.subList(1, lines.size).joinToString(" ")
            while (l2.length > 1 && p.measureText("$l2…") > maxW) l2 = l2.dropLast(1)
            val l1 = lines[0]
            lines.clear(); lines.add(l1); lines.add("$l2…")
        }
        return Cap(lines, p.textSize, p)
    }

    private fun drawCaption(cv: Canvas, cap: Cap) {
        val p = cap.paint
        val n = cap.lines.size
        val bottom = SIZE - 14f - p.descent()
        for ((i, line) in cap.lines.withIndex()) {
            val y = bottom - (n - 1 - i) * CAP_LINE
            val x = SIZE / 2f - p.measureText(line) / 2f
            p.style = Paint.Style.STROKE
            p.strokeWidth = 8f
            p.strokeJoin = Paint.Join.ROUND
            p.color = INK
            cv.drawText(line, x, y, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFFFFFFF.toInt()
            cv.drawText(line, x, y, p)
        }
    }
}
