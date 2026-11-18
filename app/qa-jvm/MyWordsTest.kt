package com.example.buddy

import com.example.buddy.ui.*
import java.util.Random

fun myWordsTests() {
    val day = 20000L
    fun w(en: String = "ubiquitous", uz: String = "hamma joyda bor") = MyWord(en, uz, "present everywhere", "Phones are ubiquitous today.", "", day)

    val a = w()
    MyWords.apply(a, Recall.SURE, 'A', day, 1000)
    check("mw sure step1", a.step == 1 && a.due == day + 1)
    MyWords.apply(a, Recall.SURE, 'B', day + 1, 1000)
    check("mw sure step2 gap3", a.step == 2 && a.due == day + 1 + MyWords.gap(2, a.ease) && MyWords.gap(2, 2.0) == 3)
    MyWords.apply(a, Recall.UNSURE, 'C', day + 4, 1000)
    check("mw unsure keeps step", a.step == 2)
    MyWords.apply(a, Recall.WRONG, 'A', day + 7, 1000)
    check("mw wrong drops 2 steps and due tomorrow", a.step == 0 && a.due == day + 8)
    check("mw ease floor", run { val x = w(); repeat(20) { MyWords.apply(x, Recall.WRONG, 'A', day, 1) }; x.ease >= 1.3 })
    check("mw ease cap", run { val x = w(); repeat(20) { MyWords.apply(x, Recall.SURE, 'A', day, 1) }; x.ease <= 2.5 && x.step == 5 })
    check("mw unchecked no change", run { val x = w(); MyWords.apply(x, Recall.UNCHECKED, 'E', day, 1); x.step == 0 && x.reviews.size == 1 })
    check("mw retry no schedule change", run { val x = w(); MyWords.apply(x, Recall.WRONG, 'A', day, 1); val d = x.due; MyWords.apply(x, Recall.SURE, 'B', day, 1, true); x.due == d && x.step == 0 })
    check("mw state new", MyWords.state(w()) == WordState.NEW)

    val m = w()
    repeat(5) { MyWords.apply(m, Recall.SURE, 'D', day + it, 1) }
    check("mw only D never mastered", MyWords.state(m) == WordState.ALMOST)
    val m2 = w()
    for (i in 0 until 5) MyWords.apply(m2, Recall.SURE, if (i % 2 == 0) 'A' else 'B', day + i, 1)
    check("mw mastered after 5 steps over days and types", MyWords.state(m2) == WordState.MASTERED)
    MyWords.apply(m2, Recall.WRONG, 'A', day + 9, 1)
    check("mw wrong demotes mastered", MyWords.state(m2) == WordState.REVIEWING)

    check("mw check exact", MyWords.check(" Ubiquitous ", "ubiquitous") == 2)
    check("mw check typo", MyWords.check("ubiquitus", "ubiquitous") == 1)
    check("mw check short typo is wrong", MyWords.check("cet", "cat") == 0)
    check("mw check wrong", MyWords.check("banana", "ubiquitous") == 0)
    check("mw cloze", MyWords.cloze("Phones are ubiquitous today.", "ubiquitous") == "Phones are _____ today.")
    check("mw cloze inflected", MyWords.cloze("She runs fast", "run") == "She _____ fast")
    check("mw cloze none", MyWords.cloze("Nothing here", "run") == null)
    check("mw meaning uz", MyWords.meaningMatch("hamma joyda bor", w()) == true)
    check("mw meaning definition keywords", MyWords.meaningMatch("it is present everywhere", w("x", "")) == true)
    check("mw meaning unclear", MyWords.meaningMatch("something else entirely", w()) == null)

    val words = MyWords.fromJson(MyWords.toJson(listOf(a, m2)))
    check("mw json roundtrip", words.size == 2 && words[0].en == "ubiquitous" && words[1].reviews.size == m2.reviews.size && words[1].step == m2.step)
    check("mw json garbage", MyWords.fromJson("not json").isEmpty())

    val list = ArrayList<MyWord>()
    check("mw add", MyWords.add(list, "  Serendipity ", "", "", "", day) != null && list.size == 1)
    check("mw add duplicate", MyWords.add(list, "serendipity", "", "", "", day) == null && list.size == 1)
    check("mw add empty", MyWords.add(list, "  ", "", "", "", day) == null)

    val pool = ArrayList<MyWord>()
    for (i in 1..14) pool.add(MyWord("word$i", "tarjima$i", "", "", "", day - 10))
    for (i in 0 until 10) { MyWords.apply(pool[i], Recall.SURE, 'A', day - 5, 1); pool[i].due = day - 1 }
    val s = MyWords.buildSession(pool, day, false, Random(1))
    check("mw session has due capped at 8 plus new 3 plus mix 3", s.size in 8..14 && s.map { it.word }.toSet().size == s.size)
    check("mw session no repeated type in a row", (1 until s.size).all { s[it].type != s[it - 1].type || MyWords.available(s[it].word, false).size == 1 })
    check("mw session offline never E or F", s.none { it.type == 'E' || it.type == 'F' })
    check("mw session online allows E", (1..30).any { MyWords.buildSession(pool, day, true, Random(it.toLong())).any { q -> q.type == 'E' || q.type == 'F' } })
    check("mw empty list session", MyWords.buildSession(emptyList(), day, true, Random(1)).isEmpty())
    val idle = ArrayList<MyWord>().apply { add(w().also { MyWords.apply(it, Recall.SURE, 'A', day, 1) }) }
    check("mw nothing due is empty", MyWords.buildSession(idle, day, false, Random(1)).isEmpty())
    check("mw practice anyway", MyWords.buildSession(idle, day, false, Random(1), true).size == 1)

    val d = MyWords.distractors(pool[0], pool, Random(2))
    check("mw distractors 3 distinct without answer", d.size == 3 && d.toSet().size == 3 && "tarjima1" !in d)
    check("mw bare word only B", MyWords.available(MyWord("run", "", "", "", "", day), false) == listOf('B'))

    check("mw accuracy", run { val x = w(); MyWords.apply(x, Recall.SURE, 'A', day, 1); MyWords.apply(x, Recall.WRONG, 'B', day, 1); MyWords.accuracy(listOf(x), day) == 50 })
    check("mw accuracy empty", MyWords.accuracy(emptyList(), day) == null)
    check("mw verdict", MyWords.parseVerdict("sure {\"ok\": true, \"why\": \"good\"}") == (true to "good") && MyWords.parseVerdict("nope") == null)

    fun g(s: String) = MyWordsGrammar.parse(QuickIntent.normalize(s).split(' ').filter { it != "my" && it != "bubby" }.joinToString(" "))
    check("mw grammar en 1", g("Bubby add ubiquitous to my words") == "ubiquitous")
    check("mw grammar en 2", g("save the word serendipity") == "serendipity")
    check("mw grammar en phrase", g("add give up to my vocabulary") == "give up")
    check("mw grammar ru 1", g("добавь ubiquitous в мои слова") == "ubiquitous")
    check("mw grammar ru 2", g("сохрани слово serendipity") == "serendipity")
    check("mw grammar uz 1", g("ubiquitous ni so'zlarimga qo'sh") == "ubiquitous")
    check("mw grammar uz 2", g("ubiquitous so'zini saqla") == "ubiquitous")
    check("mw grammar uz 3", g("ubiquitous ni so'zlar ro'yxatiga qo'shvor") == "ubiquitous")
    check("mw grammar no shopping list", g("add milk to the shopping list") == null)
    check("mw grammar too long", g("add one two three four to my words") == null)
}
