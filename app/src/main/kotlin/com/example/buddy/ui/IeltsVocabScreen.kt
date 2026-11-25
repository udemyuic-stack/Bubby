package com.example.buddy.ui

import android.view.Gravity
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.example.buddy.ui.vocab.Pronounce
import java.time.LocalDate

private class IvCard(val key: String, val front: String, val back: String, val extra: String = "")

class IeltsVocabScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("IELTS Writing и Speaking", "IELTS Writing & Speaking", "IELTS Writing va Speaking")
    override val padH = 12

    private val words by lazy { IeltsVocab.parseWords(asset("writing_vocab.txt")) }
    private val structs by lazy { IeltsVocab.parseStructs(asset("writing_structures.txt")) }
    private val phrases by lazy { IeltsVocab.parsePhrases(asset("writing_phrases.txt")) }
    private val topics by lazy { IeltsVocab.byTopic(words) }
    private val defs by lazy { IeltsVocab.parseDefs(asset("definitions.txt")) }
    private val defTopics by lazy { IeltsVocab.defsByTopic(defs) }
    private val isDef get() = view.startsWith("d:")
    private val dk get() = view.removePrefix("d:")
    private val speak by lazy { IeltsVocab.parseSpeaking(asset("speaking.txt")) }
    private val speakTopics by lazy { IeltsVocab.speakByTopic(speak.words) }
    private val isSpeak get() = view.startsWith("s:")
    private val sk get() = view.removePrefix("s:")
    private val colloc by lazy { IeltsVocab.parseWords(asset("collocations.txt")) }
    private val collocTopics by lazy { IeltsVocab.collocByTopic(colloc) }
    private val idioms by lazy { IeltsVocab.parseIdioms(asset("idioms.txt")) }
    private val models by lazy { IeltsVocab.parseAnswers(asset("speaking_answers.txt")) }
    private var pinned = -1
    private fun studied(id: Int) = Backend.int(ctx, "iv_ma_$id", 0) > 0
    private val isColl get() = view.startsWith("k:")
    private val ck get() = view.removePrefix("k:")
    private fun curWords(): List<IeltsWord> = if (isSpeak) speakTopics[sk].orEmpty() else if (isColl) collocTopics[ck].orEmpty() else topics[view].orEmpty()
    private fun wordPool(): List<IeltsWord> = if (isSpeak) speak.words else if (isColl) colloc else words
    private fun wkey(i: Int) = if (isSpeak) "s_${sk}_$i" else if (isColl) "k_${ck}_$i" else "${view}_$i"
    private fun phraseSet(): List<IeltsPhrase>? = when (view) { "ph" -> phrases; "sp" -> speak.phrases; "id" -> idioms; else -> null }

    private var view = ""
    private var mode = -1
    private lateinit var stage: LinearLayout

    private fun asset(name: String): String =
        try { ctx.assets.open("ielts/$name").bufferedReader().use { it.readText() } } catch (e: Exception) { "" }

    private fun topicName(k: String) = when (k) {
        "environment" -> t("Окружающая среда", "Environment", "Atrof-muhit")
        "technology" -> t("Технологии", "Technology", "Texnologiya")
        "health" -> t("Здоровье", "Health & well-being", "Salomatlik")
        "crime" -> t("Преступность", "Crime", "Jinoyat")
        "education" -> t("Образование", "Education", "Ta'lim")
        "work" -> t("Работа и глобализация", "Globalisation & work", "Ish va globallashuv")
        "tourism" -> t("Туризм и государство", "Tourism & government", "Turizm va davlat")
        "city" -> t("Городская жизнь", "City life", "Shahar hayoti")
        "food" -> t("Еда", "Food", "Oziq-ovqat")
        "globalization" -> t("Глобализация", "Globalisation", "Globallashuv")
        "art" -> t("Искусство", "Art", "San'at")
        "hometown" -> t("Родной город", "Hometown", "Tug'ilgan shahar")
        "home" -> t("Дом и жильё", "Home & accommodation", "Uy-joy")
        "work-study" -> t("Работа и учёба", "Work & study", "Ish va o'qish")
        "free time" -> t("Свободное время", "Free time & hobbies", "Bo'sh vaqt va hobbi")
        "family-friends" -> t("Семья и друзья", "Family & friends", "Oila va do'stlar")
        "travel" -> t("Путешествия", "Travel & holidays", "Sayohat")
        "shopping" -> t("Покупки", "Shopping", "Xarid")
        "weather" -> t("Погода и сезоны", "Weather & seasons", "Ob-havo va fasllar")
        "sport-health" -> t("Спорт и здоровье", "Sport & health", "Sport va salomatlik")
        "arts-media" -> t("Музыка, кино, книги", "Music, films & books", "Musiqa, kino, kitob")
        "people" -> t("Описание людей", "Describing people", "Odamlarni tasvirlash")
        else -> t("Общество", "Society", "Jamiyat")
    }

    private fun collocName(k: String) = when (k) {
        "work" -> t("Работа и занятость", "Work & employment", "Ish va bandlik")
        "tourism" -> t("Туризм и путешествия", "Tourism & travel", "Turizm va sayohat")
        "city" -> t("Урбанизация и город", "Urbanisation & city life", "Urbanizatsiya va shahar")
        "crime" -> t("Преступление и наказание", "Crime & punishment", "Jinoyat va jazo")
        "environment" -> t("Экология и загрязнение", "Environment & pollution", "Ekologiya va ifloslanish")
        "food" -> t("Сельское хозяйство и еда", "Agriculture & food", "Qishloq xo'jaligi va oziq-ovqat")
        "art" -> t("Искусство и музыка", "Art & music", "San'at va musiqa")
        "transport" -> t("Транспорт", "Transport", "Transport")
        "media" -> t("СМИ и реклама", "Media & advertising", "OAV va reklama")
        "culture" -> t("Культура и традиции", "Culture & traditions", "Madaniyat va an'analar")
        "government" -> t("Государство и политика", "Government & politics", "Davlat va siyosat")
        "social" -> t("Социальные проблемы", "Social issues", "Ijtimoiy muammolar")
        "sport" -> t("Спорт и досуг", "Sport & leisure", "Sport va dam olish")
        "science" -> t("Наука и инновации", "Science & innovation", "Fan va innovatsiya")
        "family" -> t("Семья и отношения", "Family & relationships", "Oila va munosabatlar")
        "population" -> t("Рост населения", "Population growth", "Aholi o'sishi")
        "housing" -> t("Жильё и архитектура", "Housing & architecture", "Uy-joy va arxitektura")
        "climate" -> t("Изменение климата", "Climate change", "Iqlim o'zgarishi")
        "economy" -> t("Экономика и финансы", "Economy & finance", "Iqtisodiyot va moliya")
        "immigration" -> t("Иммиграция и мультикультурализм", "Immigration & multiculturalism", "Immigratsiya va ko'p madaniyatlilik")
        "space" -> t("Освоение космоса", "Space exploration", "Koinotni o'rganish")
        else -> topicName(k)
    }

    private fun starsText(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)
    private fun box(key: String) = Backend.int(ctx, "iv_box_$key", 1)
    private fun today() = LocalDate.now().toEpochDay().toInt()
    private fun dueKey(key: String) = IeltsSrs.due(box(key), Backend.int(ctx, "iv_last_$key", 0), today())
    private fun rate(key: String, right: Boolean) {
        if (key.isEmpty()) return
        Backend.setInt(ctx, "iv_box_$key", IeltsSrs.next(box(key), right))
        Backend.setInt(ctx, "iv_last_$key", today())
    }
    private fun order(key: String) = if (dueKey(key)) box(key) else 10 + box(key)

    private fun bar(frac: Float): View = ctx.row {
        addView(View(ctx).apply { background = ctx.rounded(P.orange, 6f) }, LinearLayout.LayoutParams(0, ctx.dp(6), frac.coerceIn(0.001f, 1f)))
        addView(View(ctx).apply { background = ctx.rounded(P.alpha(P.violet, 60), 6f) }, LinearLayout.LayoutParams(0, ctx.dp(6), (1f - frac).coerceIn(0.001f, 1f)))
    }

    private data class Cand(val key: String, val make: (Boolean) -> IeltsDrill?)

    private fun candidates(): List<Cand> {
        val out = ArrayList<Cand>()
        for ((k, list) in topics) for ((i, w) in list.withIndex()) out.add(Cand("${k}_$i") { typed -> IeltsDrills.word(w, "${k}_$i", typed, words, kotlin.random.Random.Default) })
        for ((k, list) in defTopics) for ((i, d) in list.withIndex()) out.add(Cand("d_${k}_$i") { typed -> IeltsDrills.def(d, "d_${k}_$i", typed, defs, kotlin.random.Random.Default) })
        for ((k, list) in speakTopics) for ((i, w) in list.withIndex()) out.add(Cand("s_${k}_$i") { typed -> IeltsDrills.word(w, "s_${k}_$i", typed, speak.words, kotlin.random.Random.Default) })
        for ((i, p) in speak.phrases.withIndex()) out.add(Cand("sp_$i") { _ -> IeltsDrills.phrase(p, "sp_$i", speak.phrases, kotlin.random.Random.Default) })
        for ((k, list) in collocTopics) for ((i, w) in list.withIndex()) out.add(Cand("k_${k}_$i") { typed -> IeltsDrills.word(w, "k_${k}_$i", typed, colloc, kotlin.random.Random.Default) })
        for ((i, p) in idioms.withIndex()) out.add(Cand("id_$i") { _ -> IeltsDrills.phrase(p, "id_$i", idioms, kotlin.random.Random.Default) })
        return out
    }

    private fun dueCount() = candidates().count { dueKey(it.key) && Backend.int(ctx, "iv_last_${it.key}", 0) > 0 }

    private fun back(label: String, to: () -> Unit) = ctx.button(label, BtnStyle.Text) { to(); refresh() }

    override fun render(c: LinearLayout) {
        when {
            view == "" -> home(c)
            view == "s1" || view == "s2" || view == "s3" -> {
                if (view == "s2" && pinned >= 0) c.add(back(t("← Карточка", "← Cue card", "← Kartochka")) { view = "mc:$pinned"; pinned = -1 }, WRAP, ctx.dp(48))
                else c.add(back(t("← Темы", "← Topics", "← Mavzular")) { view = ""; mode = -1 }, WRAP, ctx.dp(48))
                stage = ctx.col {}
                c.add(stage, MATCH, WRAP)
                if (view == "s2") partTwo() else partAsk(view == "s3")
            }
            view == "daily" -> {
                c.add(back(t("← Темы", "← Topics", "← Mavzular")) { view = ""; mode = -1 }, WRAP, ctx.dp(48))
                stage = ctx.col {}
                c.add(stage, MATCH, WRAP)
                daily()
            }
            view == "ma" -> modelKinds(c)
            view.startsWith("ma:") -> modelList(c, view.removePrefix("ma:"))
            view.startsWith("mc:") -> modelCard(c, view.removePrefix("mc:").toIntOrNull() ?: -1)
            mode < 0 -> modes(c)
            else -> {
                c.add(back(t("← Режимы", "← Modes", "← Rejimlar")) { mode = -1 }, WRAP, ctx.dp(48))
                stage = ctx.col {}
                c.add(stage, MATCH, WRAP)
                play()
            }
        }
    }

    private fun home(c: LinearLayout) {
        val seen = candidates().count { Backend.int(ctx, "iv_last_${it.key}", 0) > 0 }
        val due = dueCount()
        val total = candidates().size
        val mastered = candidates().count { IeltsSrs.mastered(box(it.key)) && Backend.int(ctx, "iv_last_${it.key}", 0) > 0 }
        c.add(ctx.card(pad = 16, kind = CardKind.Hero, onClick = { view = "daily"; mode = 0; refresh() }) {
            add(ctx.text(t("Ежедневное повторение", "Daily review", "Kunlik takrorlash"), T.Title), MATCH, WRAP)
            add(ctx.text(
                if (due > 0) t("К повторению: $due. Смесь тем, чем реже слово — тем чаще оно тут.", "Due now: $due. A mix of topics: the weaker the word, the sooner it returns.", "Takrorlash kerak: $due ta. Mavzular aralash: so'z qanchalik zaif bo'lsa, shunchalik tez qaytadi.")
                else if (seen == 0) t("Начни с карточек любой темы, потом приходи сюда.", "Start with cards in any topic, then come here.", "Avval istalgan mavzuda kartochkalardan boshla, keyin shu yerga kel.")
                else t("Сегодня всё повторено. Можно взять новые слова.", "All reviewed for today. Take new words.", "Bugungi takrorlash tugadi. Yangi so'zlarni ol."), T.Small), MATCH, WRAP, top = 4)
            add(bar(mastered.toFloat() / total.coerceAtLeast(1)), MATCH, WRAP, top = 10)
            add(ctx.text(t("Усвоено $mastered из $total", "Mastered $mastered of $total", "O'zlashtirildi $mastered / $total"), T.Small), MATCH, WRAP, top = 4)
        }, MATCH, WRAP, bottom = 10)
        c.add(ctx.text(t("Метод: вспоминай до подсказки, повторяй с нарастающими паузами, вводи слово сам, смешивай темы.", "Method: recall before the hint, spaced repetition, type the word yourself, mixed topics.", "Usul: yordamdan oldin eslashga urin, oraliqli takrorlash, so'zni o'zing yoz, mavzularni aralashtir."), T.Small), MATCH, WRAP, bottom = 10)
        c.add(ctx.section("Writing"), MATCH, WRAP)
        for ((k, list) in topics) topicCard(c, k, list.size, "${k}_", k)
        c.add(ctx.section(t("Определения на английском", "English definitions", "Inglizcha ta'riflar")), MATCH, WRAP)
        for ((k, list) in defTopics) topicCard(c, k, list.size, "d_${k}_", "d:$k")
        c.add(ctx.section(t("Фразы и структуры", "Phrases and structures", "Iboralar va strukturalar")), MATCH, WRAP)
        val phKnown = phrases.indices.count { box("ph_$it") >= 3 }
        c.add(ctx.card(pad = 14, kind = CardKind.Tint, onClick = { view = "ph"; mode = -1; refresh() }) {
            add(ctx.text(t("Фразы для эссе", "Essay phrases", "Essay iboralari"), T.Label), MATCH, WRAP)
            add(ctx.text(t("Проблема, решения, усиление, введение, заключение · знаю $phKnown из ${phrases.size}", "Problems, solutions, emphasis, intro, conclusion · known $phKnown of ${phrases.size}", "Muammo, yechim, kuchaytirish, kirish, xulosa · bildim $phKnown / ${phrases.size}"), T.Small), MATCH, WRAP, top = 2)
        }, MATCH, WRAP, bottom = 8)
        val stKnown = structs.indices.count { box("st_$it") >= 3 }
        c.add(ctx.card(pad = 14, kind = CardKind.Tint, onClick = { view = "st"; mode = -1; refresh() }) {
            add(ctx.text(t("30 структур Task 2", "30 Task 2 structures", "Task 2 uchun 30 struktura"), T.Label), MATCH, WRAP)
            add(ctx.text(t("Формула, значение, примеры · знаю $stKnown из ${structs.size}", "Formula, meaning, examples · known $stKnown of ${structs.size}", "Formula, ma'no, misollar · bildim $stKnown / ${structs.size}"), T.Small), MATCH, WRAP, top = 2)
        }, MATCH, WRAP, bottom = 8)
        c.add(ctx.section(t("Тематические коллокации", "Topic collocations", "Mavzu kollokatsiyalari")), MATCH, WRAP)
        c.add(ctx.text(t("Для Writing и Speaking: словосочетание, перевод, пример с переводом.", "For Writing and Speaking: collocation, meaning, example with translation.", "Writing va Speaking uchun: birikma, tarjima, misol va uning tarjimasi."), T.Small), MATCH, WRAP, bottom = 8)
        for ((k, list) in collocTopics) topicCard(c, k, list.size, "k_${k}_", "k:$k", collocName(k))
        speakingHome(c)
    }

    private fun topicCard(c: LinearLayout, k: String, size: Int, prefix: String, target: String, name: String = topicName(k)) {
        val known = (0 until size).count { IeltsSrs.mastered(box("$prefix$it")) && Backend.int(ctx, "iv_last_$prefix$it", 0) > 0 }
        c.add(ctx.card(pad = 14, onClick = { view = target; mode = -1; refresh() }) {
            add(ctx.row {
                add(ctx.col {
                    add(ctx.text(name, T.Label), MATCH, WRAP)
                    add(ctx.text(t("Усвоено $known из $size", "Mastered $known of $size", "O'zlashtirildi $known / $size"), T.Small), MATCH, WRAP, top = 2)
                    add(bar(known.toFloat() / size.coerceAtLeast(1)), MATCH, WRAP, top = 6)
                }, 0, WRAP, 1f)
                add(ctx.text("›", T.Title), WRAP, WRAP, start = 8)
            })
        }, MATCH, WRAP, bottom = 8)
    }

    private fun tintCard(c: LinearLayout, title: String, sub: String, target: String) {
        c.add(ctx.card(pad = 14, kind = CardKind.Tint, onClick = { view = target; mode = -1; refresh() }) {
            add(ctx.text(title, T.Label), MATCH, WRAP)
            add(ctx.text(sub, T.Small), MATCH, WRAP, top = 2)
        }, MATCH, WRAP, bottom = 8)
    }

    private fun speakingHome(c: LinearLayout) {
        c.add(ctx.section("Speaking"), MATCH, WRAP)
        c.add(ctx.text(t("Говори вслух. Ответ: мысль + причина + пример. Таймер как на экзамене.", "Speak out loud. Answer: point + reason + example. Exam-style timer.", "Ovoz chiqarib gapir. Javob: fikr + sabab + misol. Imtihondagi kabi taymer."), T.Small), MATCH, WRAP, bottom = 8)
        tintCard(c, t("Part 1: короткие вопросы", "Part 1: short questions", "Part 1: qisqa savollar"), t("${speak.part1.size} вопросов · ответ 20 секунд", "${speak.part1.size} questions · 20-second answers", "${speak.part1.size} ta savol · 20 soniyalik javob"), "s1")
        tintCard(c, t("Part 2: карточка, 1 + 2 минуты", "Part 2: cue card, 1 + 2 minutes", "Part 2: kartochka, 1 + 2 daqiqa"), t("${speak.cues.size} карточек · подготовка и монолог", "${speak.cues.size} cue cards · prepare, then talk", "${speak.cues.size} ta kartochka · tayyorlan, keyin gapir"), "s2")
        val maDone = models.count { studied(it.id) }
        tintCard(c, t("Образцы ответов Part 2 и 3", "Part 2 & 3 model answers", "Part 2 va 3 namuna javoblari"), t("${models.size} карточек: говоришь сам, потом читаешь образец · изучено $maDone", "${models.size} cue cards: talk first, then read the model · studied $maDone", "${models.size} ta kartochka: avval o'zing gapir, keyin namunani o'qi · o'rganildi $maDone"), "ma")
        tintCard(c, t("Part 3: обсуждение", "Part 3: discussion", "Part 3: muhokama"), t("${speak.part3.size} вопросов · ответ 45 секунд", "${speak.part3.size} questions · 45-second answers", "${speak.part3.size} ta savol · 45 soniyalik javob"), "s3")
        val spKnown = speak.phrases.indices.count { box("sp_$it") >= 3 }
        tintCard(c, t("Разговорные фразы", "Speaking phrases", "Speaking iboralari"), t("Мнение, пауза, пример, история · знаю $spKnown из ${speak.phrases.size}", "Opinion, buying time, examples, stories · known $spKnown of ${speak.phrases.size}", "Fikr, vaqt yutish, misol, hikoya · bildim $spKnown / ${speak.phrases.size}"), "sp")
        val idKnown = idioms.indices.count { box("id_$it") >= 3 }
        tintCard(c, t("Идиомы", "Idioms", "Idiomalar"), t("${idioms.size} идиом с примерами · знаю $idKnown", "${idioms.size} idioms with examples · known $idKnown", "${idioms.size} ta idioma misollar bilan · bildim $idKnown"), "id")
        c.add(ctx.text(t("Слова по темам Speaking", "Speaking topic words", "Speaking mavzu so'zlari"), T.Label), MATCH, WRAP, top = 6, bottom = 6)
        for ((k, list) in speakTopics) topicCard(c, k, list.size, "s_${k}_", "s:$k")
    }

    private fun modeNames(): List<Triple<Int, String, String>> = when (view) {
        "st" -> listOf(
            Triple(0, t("Карточки", "Flashcards", "Kartochkalar"), t("Формула и примеры: вспомни структуру", "Formula and examples: recall the structure", "Formula va misollar: strukturani eslab qol")),
            Triple(1, t("Пропуски", "Gap-fill", "Bo'sh joy"), t("Вставь нужное слово структуры", "Fill in the structure word", "Struktura so'zini qo'y")),
            Triple(3, t("Список", "Read all", "Hammasini o'qish"), t("Все структуры с примерами", "All structures with examples", "Barcha strukturalar misollar bilan"))
        )
        "ph", "sp", "id" -> listOf(
            Triple(0, t("Карточки", "Flashcards", "Kartochkalar"), t("Фраза и её назначение", "Phrase and its job", "Ibora va uning vazifasi")),
            Triple(2, t("Тест", "Quiz", "Test"), t("Выбери назначение фразы", "Pick what the phrase does", "Ibora vazifasini tanla")),
            Triple(3, t("Список", "Read all", "Hammasini o'qish"), t("Все фразы по группам", "All phrases by group", "Barcha iboralar guruh bo'yicha"))
        )
        else -> if (isDef) listOf(
            Triple(0, t("Карточки", "Flashcards", "Kartochkalar"), t("Слово и определение", "Word and definition", "So'z va ta'rif")),
            Triple(2, t("Тест", "Quiz", "Test"), t("Выбери определение слова", "Pick the definition", "Ta'rifni tanla")),
            Triple(4, t("Вспомни и напиши", "Recall and type", "Eslab yoz"), t("Определение есть, слово пиши сам", "See the definition, type the word", "Ta'rifni ko'r, so'zni o'zing yoz")),
            Triple(3, t("Список", "Read all", "Hammasini o'qish"), t("Все слова с определениями", "All words with definitions", "Barcha so'zlar ta'rif bilan"))
        ) else listOf(
            Triple(0, t("Карточки", "Flashcards", "Kartochkalar"), t("Слово, перевод и пример", "Word, meaning and example", "So'z, tarjima va misol")),
            Triple(2, t("Тест", "Quiz", "Test"), t("Выбери перевод слова", "Pick the translation", "Tarjimani tanla")),
            Triple(4, t("Вспомни и напиши", "Recall and type", "Eslab yoz"), t("Видишь перевод, пишешь слово сам", "See the meaning, type the word", "Tarjimani ko'r, so'zni o'zing yoz")),
            Triple(1, t("Пропуски", "Gap-fill", "Bo'sh joy"), t("Вставь слово в предложение", "Put the word in the sentence", "So'zni gapga qo'y")),
            Triple(3, t("Список", "Read all", "Hammasini o'qish"), t("Все слова с примерами", "All words with examples", "Barcha so'zlar misollar bilan"))
        )
    }

    private fun modes(c: LinearLayout) {
        c.add(back(t("← Темы", "← Topics", "← Mavzular")) { view = "" }, WRAP, ctx.dp(48))
        val name = when (view) { "ph" -> t("Фразы для эссе", "Essay phrases", "Essay iboralari"); "sp" -> t("Разговорные фразы", "Speaking phrases", "Speaking iboralari"); "st" -> t("30 структур Task 2", "30 Task 2 structures", "Task 2 uchun 30 struktura"); "id" -> t("Идиомы", "Idioms", "Idiomalar"); else -> if (isColl) collocName(ck) else topicName(if (isSpeak) sk else dk) }
        c.add(ctx.text(name, T.Title), MATCH, WRAP, bottom = 8)
        for ((id, n, d) in modeNames()) {
            c.add(ctx.card(pad = 14, onClick = { mode = id; refresh() }) {
                add(ctx.text(n, T.Label), MATCH, WRAP)
                add(ctx.text(d, T.Small), MATCH, WRAP, top = 2)
            }, MATCH, WRAP, bottom = 8)
        }
    }

    private fun play() {
        when (mode) {
            0 -> cards(cardItems())
            4 -> typedSession()
            2 -> if (isDef) quiz(IeltsVocab.quizDefs(defTopics[dk].orEmpty(), defs, kotlin.random.Random.Default, 10), "d_${dk}_quiz") else if (phraseSet() != null) quiz(IeltsVocab.phraseQuiz(phraseSet().orEmpty(), kotlin.random.Random.Default, 10), "${view}_quiz") else quiz(IeltsVocab.quiz(curWords(), wordPool(), kotlin.random.Random.Default, 10), "${view}_quiz")
            1 -> if (view == "st") quiz(IeltsVocab.structGaps(structs, kotlin.random.Random.Default, 10), "st_gap") else quiz(IeltsVocab.gaps(curWords(), wordPool(), kotlin.random.Random.Default, 10), "${view}_gap")
            else -> list()
        }
    }

    private fun cardItems(): List<IvCard> = when (view) {
        "ph" -> phrases.mapIndexed { i, p -> IvCard("ph_$i", p.phrase, p.uz, p.example) }
        "sp" -> speak.phrases.mapIndexed { i, p -> IvCard("sp_$i", p.phrase, p.uz, p.example) }
        "id" -> idioms.mapIndexed { i, p -> IvCard("id_$i", p.phrase, p.uz, p.example) }
        "st" -> structs.mapIndexed { i, s -> IvCard("st_$i", s.title, s.uz + "\n\n" + s.formula, s.examples.joinToString("\n\n")) }
        else -> if (isDef) defTopics[dk].orEmpty().mapIndexed { i, d -> IvCard("d_${dk}_$i", d.word, d.uz.ifEmpty { d.def }, if (d.uz.isEmpty()) "" else d.def) } else curWords().mapIndexed { i, w -> IvCard(wkey(i), w.word, w.uz, if (w.exUz.isEmpty()) w.example else w.example + "\n" + w.exUz) }
    }

    private fun cards(all: List<IvCard>) {
        if (all.isEmpty()) { stage.add(ctx.text(t("Нет данных", "No data", "Ma'lumot yo'q"), T.Body), MATCH, WRAP); return }
        val items = all.shuffled().sortedBy { order(it.key) }.take(10)
        var pos = 0
        var known = 0
        fun show() {
            stage.removeAllViews()
            if (pos >= items.size) {
                stage.add(ctx.card(pad = 18) {
                    add(ctx.text(t("Готово!", "Done!", "Tayyor!"), T.Title).apply { gravity = Gravity.CENTER; textSize = 20f }, MATCH, WRAP)
                    add(ctx.text(t("Знаю: $known из ${items.size}", "Known: $known of ${items.size}", "Bildim: $known / ${items.size}"), T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
                    add(ctx.button(t("Ещё раз", "Again", "Qayta")) { refresh() }, MATCH, WRAP, top = 12)
                }, MATCH, WRAP, top = 8)
                return
            }
            val it = items[pos]
            var flipped = false
            val front = ctx.text(it.front, T.Headline).apply { textSize = if (it.front.length > 24) 22f else 32f; gravity = Gravity.CENTER; typeface = glassFont(bold = true) }
            val backV = ctx.col {
                visibility = View.INVISIBLE
                add(ctx.text(it.back, T.Title).apply { gravity = Gravity.CENTER; textSize = 18f }, MATCH, WRAP)
                if (it.extra.isNotEmpty()) add(ctx.text(it.extra, T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 12)
                add(ctx.text(t("Скажи вслух своё предложение с этим", "Say your own sentence with this out loud", "Shu bilan o'z gapingni ovoz chiqarib ayt"), T.Small, P.orange).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 12)
            }
            val buttons = ctx.row(Gravity.CENTER) { visibility = View.INVISIBLE }
            stage.add(ctx.text("${pos + 1}/${items.size} · " + t("коробка", "box", "quti") + " ${box(it.key)}", T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
            stage.add(ctx.card(pad = 24, onClick = {
                if (!flipped) { flipped = true; backV.visibility = View.VISIBLE; buttons.visibility = View.VISIBLE; if (it.front.length < 60) Pronounce.speak(ctx, it.front) }
            }) {
                minimumHeight = ctx.dp(200)
                gravity = Gravity.CENTER
                add(front, MATCH, WRAP)
                add(backV, MATCH, WRAP, top = 14)
            }, MATCH, WRAP, top = 6, bottom = 10)
            stage.add(ctx.text(t("Сначала вспомни ответ, потом нажми", "Try to recall first, then tap", "Avval eslashga urin, keyin bos"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, bottom = 8)
            buttons.addView(ctx.button(t("Снова", "Again", "Qayta"), BtnStyle.Outline) {
                rate(it.key, false); pos++; show()
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, ctx.dp(6), 0) })
            buttons.addView(ctx.button(t("Знаю", "I know", "Bilaman")) {
                rate(it.key, true); known++; pos++; show()
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(ctx.dp(6), 0, 0, 0) })
            stage.add(buttons, MATCH, WRAP)
        }
        show()
    }

    private fun result(correct: Int, total: Int, paidKey: String, best: Int = 0) {
        stage.removeAllViews()
        val scaled = correct * 10 / total.coerceAtLeast(1)
        val st = when { scaled >= 10 -> 3; scaled >= 8 -> 2; scaled >= 6 -> 1; else -> 0 }
        val today = LocalDate.now().toEpochDay()
        val key = "iv_paid_$paidKey"
        val paid = Backend.prefs(ctx).getLong(key, -1L) == today
        val earned = if (!paid && st > 0) st else 0
        if (earned > 0) { Backend.prefs(ctx).edit().putLong(key, today).apply(); DailyStreak.addStars(ctx, earned) }
        stage.add(ctx.card(pad = 18) {
            add(ctx.text(t("Готово!", "Done!", "Tayyor!"), T.Title).apply { gravity = Gravity.CENTER; textSize = 20f }, MATCH, WRAP)
            add(ctx.text(starsText(st), T.Headline, P.orange).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            add(ctx.text("$correct / $total", T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            if (best >= 3) add(ctx.text(t("🔥 Лучшая серия: $best", "🔥 Best combo: $best", "🔥 Eng uzun seriya: $best"), T.Small, P.orange).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            if (earned > 0) add(ctx.text("+$earned ⭐", T.Title, P.orange).apply { gravity = Gravity.CENTER; textSize = 18f }, MATCH, WRAP, top = 6)
            add(ctx.button(t("Ещё раз", "Again", "Qayta")) { refresh() }, MATCH, WRAP, top = 12)
        }, MATCH, WRAP, top = 8)
    }

    private fun quiz(qs: List<IeltsQuestion>, paidKey: String) = drill(IeltsDrills.fromQuestions(qs), paidKey)

    private fun daily() {
        val cand = candidates().filter { dueKey(it.key) }.shuffled().sortedBy { box(it.key) }.take(12)
        val list = cand.shuffled().mapNotNull { it.make(box(it.key) >= 3) }
        if (list.isEmpty()) {
            stage.add(ctx.card(pad = 18) {
                add(ctx.text(t("Пока нечего повторять", "Nothing to review yet", "Hozircha takrorlash uchun hech narsa yo'q"), T.Title).apply { gravity = Gravity.CENTER }, MATCH, WRAP)
                add(ctx.text(t("Пройди карточки в любой теме: слова появятся здесь по графику.", "Do cards in any topic: words will show up here on schedule.", "Istalgan mavzuda kartochkalarni o't: so'zlar jadval bo'yicha shu yerda chiqadi."), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
            }, MATCH, WRAP, top = 8)
            return
        }
        drill(list, "daily")
    }

    private fun typedSession() {
        val rnd = kotlin.random.Random.Default
        val list: List<IeltsDrill> = if (isDef) {
            val src = defTopics[dk].orEmpty()
            src.indices.sortedBy { order("d_${dk}_$it") }.take(10).mapNotNull { IeltsDrills.def(src[it], "d_${dk}_$it", true, defs, rnd) }
        } else {
            val src = curWords()
            src.indices.sortedBy { order(wkey(it)) }.take(10).mapNotNull { IeltsDrills.word(src[it], wkey(it), true, wordPool(), rnd) }
        }
        drill(list.shuffled(), if (isDef) "d_${dk}_type" else "${view}_type")
    }

    private fun drill(list: List<IeltsDrill>, paidKey: String) {
        if (list.isEmpty()) { stage.add(ctx.text(t("Нет данных", "No data", "Ma'lumot yo'q"), T.Body), MATCH, WRAP); return }
        val queue = ArrayList(list)
        val total = list.size
        var pos = 0
        var correct = 0
        var combo = 0
        var best = 0
        fun show() {
            stage.removeAllViews()
            if (pos >= queue.size) { result(correct, total, paidKey, best); return }
            val q = queue[pos]
            val retry = pos >= total
            var answered = false
            var hints = 0
            val header = if (retry) t("Ещё раз", "Once more", "Yana bir bor") else "${pos + 1}/$total"
            stage.add(ctx.text(header + if (combo >= 2) "   🔥 $combo" else "", T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
            stage.add(ctx.card(pad = 18) {
                add(ctx.text(q.prompt, T.Title).apply { gravity = Gravity.CENTER; textSize = if (q.prompt.length > 40) 17f else 24f }, MATCH, WRAP)
            }, MATCH, WRAP, top = 4, bottom = 12)
            val note = ctx.text("", T.Small).apply { visibility = View.GONE }
            val next = ctx.button(if (pos == queue.size - 1) t("Результат", "Result", "Natija") else t("Дальше", "Next", "Keyingi")) { pos++; show() }.apply { visibility = View.GONE }
            fun settle(right: Boolean, clean: Boolean) {
                answered = true
                if (right) { combo++; best = maxOf(best, combo) } else combo = 0
                if (!retry) {
                    if (right && clean) { correct++; rate(q.key, true) }
                    else if (!right) { rate(q.key, false); queue.add(q) }
                }
                val msg = StringBuilder()
                if (!right) msg.append(t("Верно: ", "Answer: ", "To'g'ri javob: ")).append(q.answer).append("\n")
                if (q.explain.isNotBlank()) msg.append(q.explain)
                if (msg.isNotBlank()) { note.text = msg.toString().trim(); note.visibility = View.VISIBLE }
                next.visibility = View.VISIBLE
            }
            if (q.typed) {
                val field = ctx.field(t("Напиши по-английски", "Type it in English", "Inglizcha yoz"))
                field.setSingleLine(true)
                field.imeOptions = EditorInfo.IME_ACTION_DONE
                field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                val hintView = ctx.text("", T.Title, P.orange).apply { gravity = Gravity.CENTER; letterSpacing = 0.12f }
                fun check() {
                    if (answered || field.text.toString().isBlank()) return
                    field.isEnabled = false
                    val r = IeltsTyped.check(field.text.toString(), q.answer)
                    if (r == 1) note.text = ""
                    settle(r > 0, hints == 0)
                    if (r == 1) { note.text = t("Почти: ", "Almost: ", "Deyarli: ") + q.answer + "\n" + q.explain; note.visibility = View.VISIBLE }
                    if (r > 0) field.setTextColor(P.success) else field.setTextColor(P.error)
                }
                field.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { check(); true } else false }
                stage.add(hintView, MATCH, WRAP, bottom = 8)
                stage.add(field, MATCH, WRAP, bottom = 10)
                stage.add(ctx.row {
                    addView(ctx.button(t("Подсказка", "Hint", "Maslahat"), BtnStyle.Outline) {
                        if (!answered) { hints++; hintView.text = IeltsTyped.hint(q.answer, hints) }
                    }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, ctx.dp(6), 0) })
                    addView(ctx.button(t("Проверить", "Check", "Tekshirish")) { check() }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(ctx.dp(6), 0, 0, 0) })
                }, MATCH, WRAP, bottom = 8)
                stage.add(note, MATCH, WRAP, top = 4, bottom = 8)
                stage.add(next, MATCH, WRAP)
                field.requestFocus()
                return
            }
            val boxes = ArrayList<LinearLayout>()
            val answerIdx = q.options.indexOf(q.answer)
            for ((i, o) in q.options.withIndex()) {
                val b = ctx.row(Gravity.CENTER) {
                    background = ctx.rounded(P.alpha(P.teal, 55), 22f, P.teal, 2f)
                    add(ctx.text(o, T.Title).apply { textSize = 16f; gravity = Gravity.CENTER }, MATCH, WRAP)
                    setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(10), ctx.dp(8))
                    minimumHeight = ctx.dp(56)
                    press {
                        if (answered) return@press
                        val right = i == answerIdx
                        boxes[i].background = ctx.rounded(P.alpha(if (right) P.success else P.error, 170), 22f, if (right) P.success else P.error, 2f)
                        if (!right) boxes[answerIdx].background = ctx.rounded(P.alpha(P.success, 170), 22f, P.success, 2f)
                        settle(right, true)
                    }
                }
                boxes.add(b)
                stage.add(b, MATCH, WRAP, bottom = 8)
            }
            stage.add(note, MATCH, WRAP, top = 4, bottom = 8)
            stage.add(next, MATCH, WRAP)
        }
        show()
    }

    private fun list() {
        when (view) {
            "ph", "sp", "id" -> {
                var g = ""
                for (p in phraseSet().orEmpty()) {
                    if (p.group != g) { g = p.group; stage.add(ctx.section(g), MATCH, WRAP) }
                    stage.add(ctx.card(pad = 12) {
                        add(ctx.text(p.phrase, T.Label), MATCH, WRAP)
                        add(ctx.text(p.uz, T.Body), MATCH, WRAP, top = 2)
                        if (p.example.isNotEmpty()) add(ctx.text(p.example, T.Small), MATCH, WRAP, top = 4)
                    }, MATCH, WRAP, bottom = 6)
                }
            }
            "st" -> for (s in structs) {
                stage.add(ctx.card(pad = 12) {
                    add(ctx.text("${s.n}. ${s.title}", T.Label), MATCH, WRAP)
                    add(ctx.text(s.uz, T.Body), MATCH, WRAP, top = 2)
                    add(ctx.text(s.formula, T.Title, P.orange).apply { textSize = 14f }, MATCH, WRAP, top = 4)
                    for (e in s.examples) add(ctx.text("• $e", T.Small), MATCH, WRAP, top = 4)
                }, MATCH, WRAP, bottom = 6)
            }
            else -> if (isDef) for (d in defTopics[dk].orEmpty()) {
                stage.add(ctx.card(pad = 12) {
                    add(ctx.text(d.word, T.Label), MATCH, WRAP)
                    if (d.uz.isNotEmpty()) add(ctx.text(d.uz, T.Body, P.orange), MATCH, WRAP, top = 2)
                    add(ctx.text(d.def, T.Body), MATCH, WRAP, top = 2)
                }, MATCH, WRAP, bottom = 6)
            } else for (w in curWords()) {
                stage.add(ctx.card(pad = 12) {
                    add(ctx.text(w.word, T.Label), MATCH, WRAP)
                    add(ctx.text(w.uz, T.Body), MATCH, WRAP, top = 2)
                    add(ctx.text(w.example, T.Small), MATCH, WRAP, top = 4)
                    if (w.exUz.isNotEmpty()) add(ctx.text(w.exUz, T.Small, P.orange), MATCH, WRAP, top = 2)
                }, MATCH, WRAP, bottom = 6)
            }
        }
    }

    private fun clock(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    private fun countdown(label: TextView, secs: Int, done: () -> Unit) {
        var left = secs
        val gen = ((label.tag as? Int) ?: 0) + 1
        label.tag = gen
        val tick = object : Runnable {
            override fun run() {
                if (label.tag != gen || (left < secs && !label.isAttachedToWindow)) return
                label.text = clock(left)
                if (left <= 0) { done(); return }
                left--
                label.postDelayed(this, 1000)
            }
        }
        label.post(tick)
    }

    private fun payOnce(key: String) {
        val today = LocalDate.now().toEpochDay()
        val k = "iv_paid_$key"
        if (Backend.prefs(ctx).getLong(k, -1L) == today) return
        Backend.prefs(ctx).edit().putLong(k, today).apply()
        DailyStreak.addStars(ctx, 1)
    }

    private fun helpers(topic: String, groups: List<String>, n: Int): LinearLayout = ctx.col {
        val ws = speakTopics[topic].orEmpty().shuffled().take(5)
        if (ws.isNotEmpty()) {
            add(ctx.text(t("Слова, которые можно вставить", "Words to use", "Ishlatish mumkin bo'lgan so'zlar"), T.Label), MATCH, WRAP)
            for (w in ws) add(ctx.text("• ${w.word} — ${w.uz}", T.Small), MATCH, WRAP, top = 2)
        }
        val ps = speak.phrases.filter { it.group in groups }.shuffled().take(n)
        if (ps.isNotEmpty()) {
            add(ctx.text(t("Фразы", "Phrases", "Iboralar"), T.Label), MATCH, WRAP, top = 10)
            for (p in ps) add(ctx.text("• ${p.phrase} — ${p.uz}", T.Small), MATCH, WRAP, top = 2)
        }
    }

    private fun partAsk(three: Boolean) {
        val pool = if (three) speak.part3 else speak.part1
        if (pool.isEmpty()) { stage.add(ctx.text(t("Нет данных", "No data", "Ma'lumot yo'q"), T.Body), MATCH, WRAP); return }
        val order = pool.shuffled()
        val secs = if (three) 45 else 20
        val groups = if (three) listOf("Part 3 umumlashtirish", "Qisman rozilik", "Kelajak va taxmin", "Javobni kengaytirish")
            else listOf("Fikr bildirish", "Vaqt yutish", "Misol keltirish", "O'tmish va hozir", "His-tuyg'u")
        var pos = 0
        var done = 0
        fun show() {
            stage.removeAllViews()
            val q = order[pos % order.size]
            stage.add(ctx.text((if (three) "Part 3" else "Part 1") + " · " + topicName(q.topic) + " · " + t("отвечено: $done", "answered: $done", "javob berildi: $done"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
            stage.add(ctx.card(pad = 18, kind = CardKind.Hero, onClick = { Pronounce.speak(ctx, q.question) }) {
                add(ctx.text(q.question, T.Title).apply { gravity = Gravity.CENTER; textSize = 20f }, MATCH, WRAP)
                add(ctx.text(t("🔊 нажми, чтобы услышать", "🔊 tap to hear it", "🔊 eshitish uchun bos"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
            }, MATCH, WRAP, top = 4, bottom = 10)
            stage.add(ctx.card(pad = 14, kind = CardKind.Tint) {
                add(ctx.text(t("Формула ответа", "Answer formula", "Javob formulasi"), T.Label), MATCH, WRAP)
                add(ctx.text(
                    if (three) t("Мнение → причина → пример → другая сторона или итог. 4–6 предложений.", "Opinion → reason → example → other side or result. 4–6 sentences.", "Fikr → sabab → misol → boshqa tomon yoki natija. 4–6 gap.")
                    else t("Ответ → причина → пример. 2–4 предложения, не одно слово.", "Answer → reason → example. 2–4 sentences, never one word.", "Javob → sabab → misol. 2–4 gap, bitta so'z emas."), T.Body), MATCH, WRAP, top = 4)
            }, MATCH, WRAP, bottom = 10)
            val timer = ctx.text(clock(secs), T.Headline, P.orange).apply { gravity = Gravity.CENTER; textSize = 36f }
            val status = ctx.text(t("Подумай 2–3 секунды и говори вслух", "Think for 2–3 seconds, then speak out loud", "2–3 soniya o'yla va ovoz chiqarib gapir"), T.Small).apply { gravity = Gravity.CENTER }
            stage.add(timer, MATCH, WRAP)
            stage.add(status, MATCH, WRAP, bottom = 10)
            var started = false
            val nextBtn = ctx.button(t("Следующий вопрос", "Next question", "Keyingi savol"), BtnStyle.Outline) { pos++; show() }
            var onStart: () -> Unit = {}
            val startBtn = ctx.button(t("Начать ответ", "Start answer", "Javobni boshlash")) { onStart() }
            onStart = {
                if (!started) {
                started = true
                startBtn.visibility = View.GONE
                status.text = t("Говори!", "Speak now!", "Gapir!")
                countdown(timer, secs) {
                    done++
                    if (done == 5) payOnce(if (three) "s3" else "s1")
                    status.text = t("Время. Добавил ли ты причину и пример?", "Time. Did you give a reason and an example?", "Vaqt tugadi. Sabab va misol aytdingmi?")
                }
                }
            }
            stage.add(startBtn, MATCH, WRAP, bottom = 8)
            stage.add(nextBtn, MATCH, WRAP, bottom = 12)
            stage.add(ctx.card(pad = 14) { add(helpers(q.topic, groups, 3), MATCH, WRAP) }, MATCH, WRAP, bottom = 8)
        }
        show()
    }

    private fun partTwo() {
        val pool = (speak.cues.map { it to null } + models.map { IeltsCue(it.title, it.bullets, "") to it }).shuffled()
        if (pool.isEmpty()) { stage.add(ctx.text(t("Нет данных", "No data", "Ma'lumot yo'q"), T.Body), MATCH, WRAP); return }
        val first = models.firstOrNull { it.id == pinned }
        val order: List<Pair<IeltsCue, IeltsModel?>> = if (first == null) pool else listOf(IeltsCue(first.title, first.bullets, "") to first) + pool.filter { it.second?.id != first.id }
        var pos = 0
        fun show() {
            stage.removeAllViews()
            val (cue, model) = order[pos % order.size]
            stage.add(ctx.text("Part 2 · " + (if (model != null) kindName(model.kind) else topicName(cue.topic)), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
            stage.add(ctx.card(pad = 18, kind = CardKind.Hero, onClick = { Pronounce.speak(ctx, cue.title) }) {
                add(ctx.text(cue.title, T.Title).apply { textSize = 19f }, MATCH, WRAP)
                add(ctx.text("You should say:", T.Small), MATCH, WRAP, top = 8)
                for (b in cue.bullets) add(ctx.text("• $b", T.Body), MATCH, WRAP, top = 2)
            }, MATCH, WRAP, top = 4, bottom = 10)
            val timer = ctx.text(clock(60), T.Headline, P.orange).apply { gravity = Gravity.CENTER; textSize = 40f }
            val status = ctx.text(t("1 минута на подготовку: по одному ключевому слову на каждый пункт", "1 minute to prepare: one keyword per bullet", "Tayyorlanishga 1 daqiqa: har bir band uchun bitta kalit so'z"), T.Small).apply { gravity = Gravity.CENTER }
            stage.add(timer, MATCH, WRAP)
            stage.add(status, MATCH, WRAP, bottom = 10)
            val follow = ctx.card(pad = 14, kind = CardKind.Tint) { visibility = View.GONE }
            val answerBtn = ctx.button(t("Посмотреть образец ответа", "See the model answer", "Namuna javobni ko'rish"), BtnStyle.Tonal) { if (model != null) { pinned = -1; view = "mc:${model.id}"; refresh() } }.apply { visibility = View.GONE }
            var phase = 0
            var onPrep: () -> Unit = {}
            var onTalk: () -> Unit = {}
            val main = ctx.button(t("Начать подготовку", "Start preparing", "Tayyorlanishni boshlash")) { onPrep() }
            val ready = ctx.button(t("Готов, говорить", "Ready, start talking", "Tayyorman, gapiraman")) { onTalk() }.apply { visibility = View.GONE }
            fun talk() {
                if (phase >= 2) return
                phase = 2
                main.visibility = View.GONE
                ready.visibility = View.GONE
                status.text = t("Говори 2 минуты: когда, где, кто, что было, почему важно", "Talk for 2 minutes: when, where, who, what happened, why it matters", "2 daqiqa gapir: qachon, qayerda, kim, nima bo'ldi, nega muhim")
                countdown(timer, 120) {
                    payOnce("s2")
                    status.text = t("Отлично! Теперь ответь на вопрос экзаменатора:", "Well done! Now answer the examiner's follow-up:", "Zo'r! Endi imtihonchining qo'shimcha savoliga javob ber:")
                    val own = model?.follow?.randomOrNull()
                    val p3 = own?.question ?: speak.part3.filter { it.topic == cue.topic }.ifEmpty { speak.part3 }.randomOrNull()?.question
                    if (p3 != null) {
                        follow.addView(ctx.text(p3, T.Label))
                        if (own != null) reveal(follow, own.answer)
                        follow.visibility = View.VISIBLE
                    }
                    if (model != null) answerBtn.visibility = View.VISIBLE
                }
            }
            onTalk = { talk() }
            onPrep = {
                if (phase == 0) {
                    phase = 1
                    main.visibility = View.GONE
                    ready.visibility = View.VISIBLE
                    countdown(timer, 60) { if (phase == 1) talk() }
                }
            }
            stage.add(main, MATCH, WRAP, bottom = 8)
            stage.add(ready, MATCH, WRAP, bottom = 8)
            stage.add(follow, MATCH, WRAP, bottom = 8)
            stage.add(answerBtn, MATCH, WRAP, bottom = 8)
            stage.add(ctx.button(t("Другая карточка", "Another cue card", "Boshqa kartochka"), BtnStyle.Outline) { pos++; show() }, MATCH, WRAP, bottom = 12)
            stage.add(ctx.card(pad = 14) {
                add(ctx.text(t("План рассказа", "Story plan", "Hikoya rejasi"), T.Label), MATCH, WRAP)
                for (p in speak.phrases.filter { it.group == "Part 2 hikoya" }) add(ctx.text("• ${p.phrase}", T.Small), MATCH, WRAP, top = 2)
            }, MATCH, WRAP, bottom = 8)
            stage.add(ctx.card(pad = 14) { add(helpers(cue.topic, listOf("His-tuyg'u", "Javobni kengaytirish"), 3), MATCH, WRAP) }, MATCH, WRAP, bottom = 8)
        }
        show()
    }

    private fun kindName(k: String) = when (k) {
        "person" -> t("Люди", "People", "Odamlar")
        "place" -> t("Места", "Places", "Joylar")
        "object" -> t("Вещи", "Objects & things", "Narsalar")
        "media" -> t("Книги, фильмы, медиа", "Books, films & media", "Kitob, film, media")
        "event" -> t("События и опыт", "Events & experiences", "Voqea va tajriba")
        else -> t("Занятия, навыки и другое", "Activities, skills & more", "Mashg'ulot, ko'nikma va boshqalar")
    }

    private fun reveal(parent: LinearLayout, answer: String, label: String = t("Показать ответ", "Show answer", "Javobni ko'rish")) {
        val body = ctx.text(answer, T.Body).apply { visibility = View.GONE }
        lateinit var btn: View
        btn = ctx.button(label, BtnStyle.Text) { body.visibility = View.VISIBLE; btn.visibility = View.GONE }
        parent.add(btn, WRAP, ctx.dp(48), top = 4)
        parent.add(body, MATCH, WRAP, top = 4)
    }

    private fun modelKinds(c: LinearLayout) {
        c.add(back(t("← Темы", "← Topics", "← Mavzular")) { view = ""; mode = -1 }, WRAP, ctx.dp(48))
        c.add(ctx.section(t("Образцы ответов", "Model answers", "Namuna javoblar")), MATCH, WRAP)
        c.add(ctx.text(t("Как учиться: 1) расскажи сам по таймеру 2) прочитай и послушай образец 3) выпиши полезные слова 4) ответь на вопросы Part 3. Одну историю можно использовать для похожих карточек.", "How to study: 1) talk yourself with the timer 2) read and listen to the model 3) note the useful words 4) answer the Part 3 questions. One story can serve several similar cards.", "Qanday o'rganish: 1) taymer bilan o'zing gapir 2) namunani o'qi va tingla 3) foydali so'zlarni yozib ol 4) Part 3 savollariga javob ber. Bitta hikoyani o'xshash kartochkalarda ham ishlatsa bo'ladi."), T.Small), MATCH, WRAP, bottom = 10)
        val groups = models.groupBy { it.kind }
        for (k in IeltsVocab.kindOrder) {
            val list = groups[k] ?: continue
            val done = list.count { studied(it.id) }
            c.add(ctx.card(pad = 14, onClick = { view = "ma:$k"; refresh() }) {
                add(ctx.row {
                    add(ctx.col {
                        add(ctx.text(kindName(k), T.Label), MATCH, WRAP)
                        add(ctx.text(t("Изучено $done из ${list.size}", "Studied $done of ${list.size}", "O'rganildi $done / ${list.size}"), T.Small), MATCH, WRAP, top = 2)
                        add(bar(done.toFloat() / list.size), MATCH, WRAP, top = 6)
                    }, 0, WRAP, 1f)
                    add(ctx.text("›", T.Title), WRAP, WRAP, start = 8)
                })
            }, MATCH, WRAP, bottom = 8)
        }
    }

    private fun modelList(c: LinearLayout, k: String) {
        c.add(back(t("← Группы", "← Groups", "← Guruhlar")) { view = "ma" }, WRAP, ctx.dp(48))
        c.add(ctx.section(kindName(k)), MATCH, WRAP)
        val list = models.filter { it.kind == k }.sortedBy { studied(it.id) }
        for (m in list) {
            c.add(ctx.card(pad = 14, onClick = { view = "mc:${m.id}"; refresh() }) {
                add(ctx.row {
                    add(ctx.col {
                        add(ctx.text(m.title, T.Body), MATCH, WRAP)
                        val extra = listOfNotNull(
                            if (m.vocab.isNotEmpty()) t("${m.vocab.size} слов", "${m.vocab.size} words", "${m.vocab.size} ta so'z") else null,
                            if (m.follow.isNotEmpty()) t("${m.follow.size} вопросов Part 3", "${m.follow.size} Part 3 questions", "${m.follow.size} ta Part 3 savol") else null
                        )
                        if (extra.isNotEmpty()) add(ctx.text(extra.joinToString(" · "), T.Small), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    add(ctx.text(if (studied(m.id)) "✓" else "›", T.Title, if (studied(m.id)) P.success else P.onSurface), WRAP, WRAP, start = 8)
                })
            }, MATCH, WRAP, bottom = 8)
        }
    }

    private fun modelCard(c: LinearLayout, id: Int) {
        val m = models.firstOrNull { it.id == id }
        if (m == null) { view = "ma"; modelKinds(c); return }
        c.add(back("← " + kindName(m.kind)) { view = "ma:${m.kind}" }, WRAP, ctx.dp(48))
        c.add(ctx.card(pad = 18, kind = CardKind.Hero, onClick = { Pronounce.speak(ctx, m.title) }) {
            add(ctx.text(m.title, T.Title).apply { textSize = 19f }, MATCH, WRAP)
            add(ctx.text("You should say:", T.Small), MATCH, WRAP, top = 8)
            for (b in m.bullets) add(ctx.text("• $b", T.Body), MATCH, WRAP, top = 2)
        }, MATCH, WRAP, top = 4, bottom = 12)

        c.add(ctx.text(t("Шаг 1. Сначала расскажи сам", "Step 1. Talk first yourself", "1-qadam. Avval o'zing gapir"), T.Label), MATCH, WRAP, bottom = 4)
        c.add(ctx.button(t("Тренировка: 1 + 2 минуты", "Practise: 1 + 2 minutes", "Mashq: 1 + 2 daqiqa"), BtnStyle.Outline) { pinned = m.id; view = "s2"; refresh() }, MATCH, WRAP, bottom = 12)

        c.add(ctx.text(t("Шаг 2. Образец ответа", "Step 2. Model answer", "2-qadam. Namuna javob"), T.Label), MATCH, WRAP, bottom = 4)
        val answer = ctx.col {}
        answer.visibility = View.GONE
        answer.add(ctx.text(t("Нажми на абзац, чтобы послушать.", "Tap a paragraph to listen.", "Tinglash uchun bo'lakni bos."), T.Small), MATCH, WRAP, bottom = 6)
        for ((i, para) in m.paras.withIndex()) {
            answer.add(ctx.card(pad = 14, kind = if (i == 0) CardKind.Tint else CardKind.Raised, onClick = { Pronounce.speak(ctx, para) }) {
                add(ctx.text(para, T.Body), MATCH, WRAP)
            }, MATCH, WRAP, bottom = 6)
        }
        lateinit var open: View
        open = ctx.button(t("Показать образец", "Show the model answer", "Namuna javobni ochish"), BtnStyle.Tonal) { answer.visibility = View.VISIBLE; open.visibility = View.GONE }
        c.add(open, MATCH, WRAP, bottom = 8)
        c.add(answer, MATCH, WRAP, bottom = 6)

        if (m.vocab.isNotEmpty()) {
            c.add(ctx.text(t("Шаг 3. Полезные слова", "Step 3. Useful words", "3-qadam. Foydali so'zlar"), T.Label), MATCH, WRAP, top = 6, bottom = 4)
            c.add(ctx.card(pad = 14) {
                for ((w, mean) in m.vocab) {
                    add(ctx.text(w, T.Label, P.orange).apply { setOnClickListener { Pronounce.speak(ctx, w) } }, MATCH, WRAP, top = 6)
                    add(ctx.text(mean, T.Small), MATCH, WRAP, top = 1)
                }
            }, MATCH, WRAP, bottom = 12)
        }

        if (m.follow.isNotEmpty()) {
            c.add(ctx.text(t("Шаг 4. Вопросы Part 3: ответь вслух, потом сравни", "Step 4. Part 3 questions: answer aloud, then compare", "4-qadam. Part 3 savollari: ovoz chiqarib javob ber, keyin solishtir"), T.Label), MATCH, WRAP, top = 6, bottom = 4)
            for (q in m.follow) {
                c.add(ctx.card(pad = 14, kind = CardKind.Tint) {
                    add(ctx.text(q.question, T.Label).apply { setOnClickListener { Pronounce.speak(ctx, q.question) } }, MATCH, WRAP)
                    reveal(this, q.answer, t("Показать образец", "Show model answer", "Namuna javob"))
                }, MATCH, WRAP, bottom = 8)
            }
        }

        val done = studied(m.id)
        c.add(ctx.button(if (done) t("✓ Изучено (снять отметку)", "✓ Studied (undo)", "✓ O'rganildi (bekor qilish)") else t("Я это выучил", "I've studied this", "O'rgandim"), if (done) BtnStyle.Outline else BtnStyle.Filled) {
            Backend.setInt(ctx, "iv_ma_${m.id}", if (done) 0 else 1)
            if (!done) payOnce("ma")
            refresh()
        }, MATCH, WRAP, top = 8, bottom = 8)
        val same = models.filter { it.kind == m.kind }
        val next = same.firstOrNull { it.id != m.id && !studied(it.id) } ?: same.getOrNull((same.indexOf(m) + 1) % same.size)
        if (next != null && next.id != m.id) c.add(ctx.button(t("Следующая карточка ›", "Next cue card ›", "Keyingi kartochka ›"), BtnStyle.Text) { view = "mc:${next.id}"; refresh() }, MATCH, ctx.dp(48), bottom = 12)
    }
}
