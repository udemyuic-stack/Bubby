package com.example.buddy.ui.vocab

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.example.buddy.Brain
import com.example.buddy.ImageUnsupported
import com.example.buddy.PhotoProvider
import com.example.buddy.ui.*
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PhotoScreen(act: UiActivity, private val pairId: Long) : Screen(act) {
    private sealed class St {
        object Start : St()
        object NoKey : St()
        class NoVision(val model: String?) : St()
        object Offline : St()
        object Loading : St()
        class Error(val msg: String, val detail: String? = null) : St()
        object Empty : St()
        object Result : St()
    }

    private class Sel(val text: String, val sentence: Int, val from: Int, val to: Int)

    private var state: St = St.Start
    @Volatile private var gen = 0
    @Volatile private var left = false
    private var jpeg: ByteArray? = null
    private var sentences: List<String> = emptyList()
    private var tokens: List<List<Token>> = emptyList()
    private var truncated = false
    private val selections = LinkedHashMap<String, Sel>()
    private var anchor: Pair<Int, Int>? = null
    private var bar: LinearLayout? = null
    private var wrapped: View? = null

    init {
        PhotoProvider.clear(ctx)
        state = readiness()
    }

    override fun title() = t("Слова с фото", "Words from a photo", "Rasmdan so'zlar")

    override fun onShow() {
        if (state === St.Start || state === St.NoKey || state is St.NoVision) state = readiness()
        reshow()
        renderBar()
    }

    override fun view(): View {
        wrapped?.let { return it }
        val inner = super.view()
        val b = ctx.col {}
        bar = b
        val w = ctx.col {
            add(inner, MATCH, 0, 1f)
            add(b, MATCH, WRAP)
        }
        w.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { leave() }
        })
        wrapped = w
        renderBar()
        return w
    }

    override fun refresh() {
        super.refresh()
        renderBar()
    }

    private fun leave() {
        left = true
        gen++
        jpeg = null
        PhotoProvider.clear(ctx.applicationContext)
    }

    private fun readiness(): St {
        val brain = Brain(ctx.applicationContext)
        if (brain.keyList().isEmpty()) return St.NoKey
        if (!brain.canSeeImages()) return St.NoVision(brain.imageModelName())
        return St.Start
    }

    private fun show(s: St) {
        state = s
        if (s === St.Empty || s === St.Result || s is St.NoVision) jpeg = null
        refresh()
    }

    override fun render(c: LinearLayout) {
        when (val s = state) {
            St.Start -> renderStart(c)
            St.NoKey -> {
                c.add(ctx.emptyState(
                    t("Нет подключения ИИ", "No AI connection", "AI ulanishi yo'q"),
                    t("Чтобы читать текст с фото, добавьте ключ ИИ.", "To read text from a photo, add an AI key.", "Rasmdagi matnni o'qish uchun AI kalitini qo'shing.")
                ), MATCH, WRAP)
                c.add(openAiButton(), MATCH, WRAP, top = 8)
            }
            is St.NoVision -> {
                val m = s.model ?: "?"
                c.add(ctx.emptyState(
                    t("Модель не читает фото", "This model can not read photos", "Bu model rasmni o'qiy olmaydi"),
                    t("Ваша модель ИИ ($m) не умеет читать фото. В Ещё › Подключение ИИ выберите модель с изображениями: Gemini, Claude или модель со зрением, например Llama 4 на Groq.",
                        "Your AI model ($m) can not read photos. In More › AI connection choose an image model: Gemini, Claude, or a vision model such as Llama 4 on Groq.",
                        "AI modelingiz ($m) rasmni o'qiy olmaydi. Yana › AI ulanishi bo'limida rasm bilan ishlaydigan modelni tanlang: Gemini, Claude yoki Groq'dagi Llama 4 kabi ko'ruvchi model.")
                ), MATCH, WRAP)
                c.add(openAiButton(), MATCH, WRAP, top = 8)
            }
            St.Offline -> {
                c.add(ctx.offlineCard(t("Нет интернета. Фото не отправлено.", "No internet. The photo was not sent.", "Internet yo'q. Rasm yuborilmadi.")) {
                    if (jpeg != null) send() else show(St.Start)
                }, MATCH, WRAP, top = 8)
                c.add(anotherButton(), MATCH, WRAP, top = 12)
            }
            St.Loading -> {
                c.add(ctx.card(20) {
                    gravity = Gravity.CENTER_HORIZONTAL
                    add(android.widget.ProgressBar(ctx), WRAP, WRAP, bottom = 12)
                    add(ctx.text(t("Читаю текст…", "Reading the text…", "Matnni o'qiyapman…"), T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP)
                }, MATCH, WRAP, top = 8)
                c.add(ctx.button(t("Отмена", "Cancel", "Bekor qilish"), BtnStyle.Outline) {
                    gen++
                    show(St.Start)
                    jpeg = null
                }, MATCH, WRAP, top = 12)
            }
            is St.Error -> {
                c.add(ctx.card(16, fill = P.errorContainer, stroke = 0, kind = CardKind.Tint) {
                    add(ctx.text(s.msg, T.Body, P.onErrorContainer), MATCH, WRAP)
                    if (!s.detail.isNullOrBlank()) add(ctx.text(s.detail, T.Small, P.onErrorContainer), MATCH, WRAP, top = 6)
                }, MATCH, WRAP, top = 8)
                if (jpeg != null) c.add(ctx.button(t("Попробовать снова", "Try again", "Qayta urinish")) { send() }, MATCH, WRAP, top = 12)
                c.add(anotherButton(), MATCH, WRAP, top = 8)
            }
            St.Empty -> {
                c.add(ctx.emptyState(
                    t("Текст не найден", "No text found", "Matn topilmadi"),
                    t("Попробуйте более чёткое фото при хорошем свете.", "Try a sharper photo with more light.", "Yorug'roq joyda aniqroq rasm oling.")
                ), MATCH, WRAP)
                c.add(anotherButton(), MATCH, WRAP, top = 8)
            }
            St.Result -> renderResult(c)
        }
    }

    private fun openAiButton() = ctx.button(t("Открыть подключение ИИ", "Open AI connection", "AI ulanishini ochish")) { act.open("ai") }

    private fun anotherButton() = ctx.button(t("Другое фото", "Another photo", "Boshqa rasm"), BtnStyle.Outline) {
        gen++
        jpeg = null
        show(readiness())
    }

    private fun renderStart(c: LinearLayout) {
        c.add(ctx.card(16) {
            add(ctx.text(t("Сфотографируйте печатную страницу", "Take a photo of a printed page", "Bosma sahifani rasmga oling"), T.Label), MATCH, WRAP)
            add(ctx.text(t(
                "Bubby прочитает текст через ваше подключение ИИ. Нажмите на слова, чтобы сохранить их. Долгое нажатие на слово и нажатие на другое выделяют фразу. Фото не сохраняется.",
                "Bubby reads the text through your AI connection. Tap words to save them. Long-press a word, then tap another to mark a phrase. The photo is not kept.",
                "Bubby matnni AI ulanishingiz orqali o'qiydi. Saqlash uchun so'zlarga bosing. Iborani belgilash uchun so'zni uzoq bosing, keyin boshqasiga bosing. Rasm saqlanmaydi."
            ), T.Small), MATCH, WRAP, top = 6)
        }, MATCH, WRAP, top = 8)
        c.add(ctx.button(t("Сфотографировать", "Take a photo", "Rasmga olish")) { takePhoto() }, MATCH, WRAP, top = 12)
        c.add(ctx.button(t("Выбрать из галереи", "Choose from gallery", "Galereyadan tanlash"), BtnStyle.Outline) { pickPhoto() }, MATCH, WRAP, top = 8)
        c.add(ctx.row { add(sourcePill(), WRAP, WRAP) }, MATCH, WRAP, top = 12)
    }

    private fun renderResult(c: LinearLayout) {
        if (truncated) c.add(ctx.text(t("Прочитана только первая часть страницы.", "Only the first part of the page was read.", "Sahifaning faqat birinchi qismi o'qildi."), T.Small), MATCH, WRAP, top = 4, bottom = 4)
        val a = anchor
        if (a != null) c.add(ctx.text(t("Теперь нажмите последнее слово фразы", "Now tap the last word of the phrase", "Endi iboraning oxirgi so'zini bosing"), T.Small, P.violet), MATCH, WRAP, top = 4, bottom = 4)
        for (i in sentences.indices) {
            val flow = FlowLayout(ctx, 2)
            val toks = tokens[i]
            for (j in toks.indices) flow.addView(wordView(i, j, toks[j]))
            c.add(ctx.card(12) { add(flow, MATCH, WRAP) }, MATCH, WRAP, top = 8)
        }
        c.add(anotherButton(), MATCH, WRAP, top = 12)
    }

    private fun covering(i: Int, j: Int): Sel? = selections.values.firstOrNull { it.sentence == i && j in it.from..it.to }

    private fun wordView(i: Int, j: Int, tok: Token): View {
        val selected = covering(i, j) != null
        val anchored = anchor == (i to j)
        val tv = ctx.text(tok.text, T.Body, if (selected) 0xFFFFFFFF.toInt() else 0)
        tv.gravity = Gravity.CENTER
        tv.minimumHeight = ctx.dp(48)
        tv.minimumWidth = ctx.dp(48)
        tv.setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(8), ctx.dp(6))
        if (tok.word.isEmpty()) return tv
        tv.background = when {
            selected -> ctx.rounded(P.violet, 12f)
            anchored -> ctx.rounded(P.alpha(P.violet, 60), 12f, P.violet, 2f)
            else -> null
        }
        tv.contentDescription = tok.word
        tv.pressLong({ startPhrase(i, j) }) { tapWord(i, j) }
        return tv
    }

    private fun key(text: String) = VocabText.normalize(text)

    private fun startPhrase(i: Int, j: Int) {
        anchor = i to j
        refresh()
    }

    private fun tapWord(i: Int, j: Int) {
        val a = anchor
        if (a != null && a.first == i) {
            anchor = null
            val p = OcrValidator.phrase(tokens[i], a.second, j)
            if (p == null) {
                ctx.toast(t("Во фразе до 8 слов", "Up to 8 words in a phrase", "Iborada 8 tagacha so'z"))
            } else {
                select(Sel(p, i, minOf(a.second, j), maxOf(a.second, j)))
            }
            refresh()
            return
        }
        anchor = null
        val cur = covering(i, j)
        if (cur != null) selections.remove(key(cur.text))
        else OcrValidator.phrase(tokens[i], j, j)?.let { select(Sel(it, i, j, j)) }
        refresh()
    }

    private fun select(s: Sel) {
        val k = key(s.text)
        selections.remove(k)
        selections[k] = s
    }

    private fun currentSource(): Long? = VocabDb.lastSource(ctx)

    private fun sourcePill(): View {
        val cur = currentSource()
        val label = if (cur == null) t("Без источника", "No source", "Manbasiz")
        else SourceTree.path(VocabDb.sources(ctx), cur).let { p -> t("Источник: $p", "Source: $p", "Manba: $p") }
        return ctx.pill(label, P.violet) { sourceDialog() }
    }

    private fun sourceDialog() {
        val items = arrayOf(t("Выбрать источник", "Choose source", "Manbani tanlash"), t("Без источника", "No source", "Manbasiz"))
        ctx.dialogBuilder().setTitle(t("Источник", "Source", "Manba"))
            .setItems(items) { _, i ->
                if (i == 0) act.push(SourcesScreen(act) { s, _ -> if (s != null) VocabDb.setLastSource(ctx, s) })
                else { VocabDb.setLastSource(ctx, null); refresh() }
            }
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .show()
    }

    private fun renderBar() {
        val b = bar ?: return
        b.removeAllViews()
        if (state !== St.Result) return
        val n = selections.size
        b.add(ctx.card(12) {
            add(ctx.row {
                add(ctx.text(t("Выбрано: $n", "Selected: $n", "Tanlandi: $n"), T.Label), 0, WRAP, 1f)
                add(sourcePill(), WRAP, WRAP, start = 8)
            }, MATCH, WRAP)
            add(ctx.button(t("Сохранить ($n)", "Save ($n)", "Saqlash ($n)")) { save() }.apply {
                isEnabled = n > 0
                alpha = if (n > 0) 1f else 0.45f
            }, MATCH, WRAP, top = 8)
        }, MATCH, WRAP, start = 12, end = 12, top = 6, bottom = 8)
    }

    private fun save() {
        if (selections.isEmpty()) return
        val src = currentSource()
        var saved = 0
        var skipped = 0
        val dupIds = ArrayList<Long>()
        var dups = 0
        for (s in selections.values) {
            val sentence = sentences.getOrNull(s.sentence) ?: ""
            when (val r = VocabDb.addEntry(ctx, pairId, s.text, OcrValidator.context(sentence, s.text), src)) {
                is AddResult.Added -> { saved++; VocabEnrich.start(ctx, r.id) }
                is AddResult.Duplicate -> { dups++; if (r.id != 0L) dupIds.add(r.id) }
                else -> skipped++
            }
        }
        selections.clear()
        anchor = null
        val parts = ArrayList<String>()
        parts.add(t("Сохранено: $saved", "Saved $saved", "Saqlandi: $saved"))
        if (dups > 0) parts.add(t("уже есть: $dups", "$dups already saved", "allaqachon bor: $dups"))
        if (skipped > 0) parts.add(t("не сохранено: $skipped", "$skipped not saved", "saqlanmadi: $skipped"))
        ctx.toast(parts.joinToString(", "))
        if (dupIds.isNotEmpty() && src != null) {
            ctx.confirm(
                t("Уже сохранено", "Already saved", "Allaqachon saqlangan"),
                t("Добавить этот источник к сохранённым словам (${dupIds.size})?", "Add this source to ${dupIds.size} saved words?", "Bu manba saqlangan ${dupIds.size} ta so'zga qo'shilsinmi?"),
                t("Добавить", "Add", "Qo'shish"), t("Отмена", "Cancel", "Bekor qilish")
            ) {
                for (id in dupIds) VocabDb.linkSource(ctx, id, src)
                ctx.toast(t("Источник добавлен", "Source added", "Manba qo'shildi"))
            }
        }
        refresh()
    }

    private fun takePhoto() {
        val ready = readiness()
        if (ready !== St.Start) { show(ready); return }
        PhotoProvider.clear(ctx)
        val u = PhotoProvider.uri()
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, u)
        i.clipData = ClipData.newRawUri("", u)
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { act.startActivityForResult(i, REQ_CAMERA) } catch (e: ActivityNotFoundException) {
            show(St.Error(t("Не найдено приложение камеры", "No camera app found", "Kamera ilovasi topilmadi")))
        }
    }

    private fun pickPhoto() {
        val ready = readiness()
        if (ready !== St.Start) { show(ready); return }
        if (Build.VERSION.SDK_INT >= 33) {
            try { act.startActivityForResult(Intent(MediaStore.ACTION_PICK_IMAGES), REQ_GALLERY); return } catch (_: ActivityNotFoundException) { }
        }
        try {
            act.startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_GALLERY)
        } catch (e: ActivityNotFoundException) {
            show(St.Error(t("Не найдено приложение галереи", "No gallery app found", "Galereya ilovasi topilmadi")))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_CAMERA && requestCode != REQ_GALLERY) return
        val app = ctx.applicationContext
        if (resultCode != Activity.RESULT_OK) {
            PhotoProvider.clear(app)
            show(readiness())
            return
        }
        val camera = requestCode == REQ_CAMERA
        val uri: Uri? = data?.data
        if (!camera && uri == null) { show(St.Error(cantOpen())); return }
        val my = ++gen
        jpeg = null
        show(St.Loading)
        pool.execute {
            val bytes = try {
                if (camera) Shrink.jpeg { FileInputStream(PhotoProvider.file(app)) }
                else Shrink.jpeg { app.contentResolver.openInputStream(uri!!) }
            } catch (e: Throwable) { null } finally { PhotoProvider.clear(app) }
            act.runIfAlive {
                if (my != gen || left) return@runIfAlive
                if (bytes == null) show(St.Error(cantOpen())) else { jpeg = bytes; send() }
            }
        }
    }

    private fun cantOpen() = t("Не удалось открыть эту картинку", "Could not open this picture", "Bu rasmni ochib bo'lmadi")

    private fun send() {
        val img = jpeg ?: run { show(St.Start); return }
        if (!ctx.isOnline()) { show(St.Offline); return }
        val pair = VocabDb.pair(ctx, pairId)
        if (pair == null) { show(St.Error(t("Языковая пара не найдена", "Language pair not found", "Til juftligi topilmadi"))); return }
        val my = ++gen
        show(St.Loading)
        val app = ctx.applicationContext
        val system = OcrPrompt.system(pair.source)
        pool.execute {
            val f = pool.submit(Callable { Brain(app).chatOnceWithImage(system, OcrPrompt.user(), img) })
            val out: Any = try { f.get(TIMEOUT_S, TimeUnit.SECONDS) } catch (e: TimeoutException) { f.cancel(true); e }
                catch (e: ExecutionException) { e.cause ?: e } catch (e: Exception) { e }
            act.runIfAlive {
                if (my != gen || left) return@runIfAlive
                onReply(out)
            }
        }
    }

    private fun onReply(out: Any) {
        when (out) {
            is String -> when (val p = OcrValidator.parse(out)) {
                is OcrParsed.Ok -> {
                    sentences = p.sentences
                    tokens = p.sentences.map { OcrValidator.tokens(it) }
                    truncated = p.truncated
                    selections.clear()
                    anchor = null
                    show(St.Result)
                }
                OcrParsed.Empty -> show(St.Empty)
                is OcrParsed.Bad -> show(St.Error(t("Не удалось разобрать ответ ИИ", "Could not read the AI reply", "AI javobini tushunib bo'lmadi"), p.reason))
            }
            is ImageUnsupported -> show(St.NoVision(out.model))
            is TimeoutException -> show(if (!ctx.isOnline()) St.Offline else St.Error(t("ИИ не ответил вовремя", "The AI did not answer in time", "AI o'z vaqtida javob bermadi")))
            is IOException -> show(if (!ctx.isOnline()) St.Offline else St.Error(t("Нет связи с ИИ", "Could not reach the AI", "AI bilan bog'lanib bo'lmadi"), out.message))
            is Throwable -> {
                val m = out.message ?: ""
                if (m.startsWith("не задан ключ")) show(St.NoKey)
                else show(if (!ctx.isOnline()) St.Offline else St.Error(t("Не удалось прочитать текст", "Could not read the text", "Matnni o'qib bo'lmadi"), m.take(200)))
            }
            else -> show(St.Error(t("Не удалось прочитать текст", "Could not read the text", "Matnni o'qib bo'lmadi")))
        }
    }

    private object Shrink {
        private const val EDGE = 1568
        private const val SMALL_EDGE = 1280
        private const val LIMIT = 1_500_000

        fun jpeg(open: () -> InputStream?): ByteArray? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            (open() ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val long = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (long / (sample * 2) >= EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = (open() ?: return null).use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val orientation = try {
                open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
            val fitted = fit(raw, EDGE, orientation)
            try {
                var out = encode(fitted, 85)
                if (out.size > LIMIT) out = encode(fitted, 70)
                if (out.size > LIMIT) {
                    val small = fit(fitted, SMALL_EDGE, ExifInterface.ORIENTATION_NORMAL)
                    out = encode(small, 70)
                    if (small !== fitted) small.recycle()
                }
                return out
            } finally {
                fitted.recycle()
            }
        }

        private fun fit(src: Bitmap, edge: Int, orientation: Int): Bitmap {
            val long = maxOf(src.width, src.height)
            val scale = if (long > edge) edge.toFloat() / long else 1f
            val m = Matrix()
            if (scale != 1f) m.postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            }
            if (m.isIdentity) return src
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            if (out !== src) src.recycle()
            return out
        }

        private fun encode(b: Bitmap, quality: Int): ByteArray {
            val bos = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            return bos.toByteArray()
        }
    }

    private companion object {
        const val REQ_CAMERA = 4101
        const val REQ_GALLERY = 4102
        const val TIMEOUT_S = 90L
        val pool = Executors.newCachedThreadPool()
    }
}
