package com.example.buddy

import com.example.buddy.ui.*
import java.io.File
import kotlin.random.Random

fun ieltsVocabTests() {
    val dir = File(System.getenv("BUBBY_SRC") + "/../../../../assets/ielts")
    val words = IeltsVocab.parseWords(File(dir, "writing_vocab.txt").readText())
    val structs = IeltsVocab.parseStructs(File(dir, "writing_structures.txt").readText())
    val phrases = IeltsVocab.parsePhrases(File(dir, "writing_phrases.txt").readText())
    val by = IeltsVocab.byTopic(words)
    check("ielts: 9 topics", by.size == 9, by.keys.toString())
    check("ielts: words loaded", words.size >= 190, words.size.toString())
    check("ielts: topic words unique", by.all { (_, l) -> l.map { it.word }.distinct().size == l.size })
    check("ielts: every word has uz and example", words.all { it.uz.isNotBlank() && it.example.length > 10 })
    check("ielts: 30 structures", structs.size == 30 && structs.map { it.n } == (1..30).toList(), structs.size.toString())
    check("ielts: structures have formula and examples", structs.all { it.formula.isNotBlank() && it.uz.isNotBlank() && it.examples.size >= 2 }, structs.filter { it.examples.size < 2 }.map { it.n }.toString())
    check("ielts: phrases loaded", phrases.size >= 40, phrases.size.toString())
    val rnd = Random(7)
    val q = IeltsVocab.quiz(words, words, rnd, 10)
    check("ielts: quiz 10 with 4 distinct options", q.size == 10 && q.all { it.options.distinct().size == 4 && it.options[it.answer].isNotBlank() })
    val g = IeltsVocab.gaps(words, words, rnd, 10)
    check("ielts: gap 10 blanked", g.size == 10 && g.all { it.prompt.contains("_____") && it.options.distinct().size == 4 })
    val sg = IeltsVocab.structGaps(structs, rnd, 10)
    check("ielts: structure gaps", sg.size >= 5 && sg.all { it.prompt.contains("_____") && it.options.distinct().size == 4 }, sg.size.toString())
    check("ielts: every structure gaps all examples", structs.all { it.gaps().size == it.examples.size }, structs.filter { it.gaps().size != it.examples.size }.map { it.n }.toString())
    check("ielts: structure titles english only", structs.none { it.title.contains("—") || it.formula.contains("|") })
    check("ielts: structure answers distinct", structs.map { it.answer }.distinct().size == 30)
    val nb = structs.first { it.n == 5 }.gap("Exercise not only strengthens the body but also improves mental health.")
    check("ielts: two-slot gap", nb?.first == "Exercise _____ strengthens the body _____ improves mental health." && nb.second == "not only … but also", nb.toString())
    val asg = structs.first { it.n == 6 }.gap("As cities grow rapidly, overcrowding has become a problem.")
    check("ielts: alternative slot gap", asg?.second == "as" && asg.first.startsWith("_____ cities"), asg.toString())
    check("ielts: struct quiz 10", IeltsVocab.structGaps(structs, rnd, 10).size == 10)
    val pq = IeltsVocab.phraseQuiz(phrases, rnd, 10)
    check("ielts: phrase quiz", pq.size == 10 && pq.all { it.options.distinct().size == 4 })
}

fun ieltsLearnTests() {
    check("srs: box 1 always due", IeltsSrs.due(1, 100, 100))
    check("srs: box 3 waits 3 days", !IeltsSrs.due(3, 100, 102) && IeltsSrs.due(3, 100, 103))
    check("srs: never seen is due", IeltsSrs.due(5, 0, 100))
    check("srs: right moves up, wrong resets", IeltsSrs.next(2, true) == 3 && IeltsSrs.next(5, true) == 5 && IeltsSrs.next(4, false) == 1)
    check("typed: exact", IeltsTyped.check("Habitat", "habitat") == 2)
    check("typed: alternatives", IeltsTyped.check("outdated", "obsolete / outdated") == 2)
    check("typed: parenthetical and to", IeltsTyped.check("attend", "to attend (classes)") == 2)
    check("typed: one typo long word is almost", IeltsTyped.check("biodiversty", "biodiversity") == 1)
    check("typed: short typo wrong", IeltsTyped.check("cat", "car") == 0)
    check("typed: empty wrong", IeltsTyped.check("  ", "habitat") == 0)
    check("typed: hint shape", IeltsTyped.hint("waste disposal", 2) == "wa___ ________")
    val w = IeltsWord("x", "habitat", "yashash muhiti", "A habitat is lost.")
    val others = (1..5).map { IeltsWord("x", "w$it", "m$it", "e$it") } + w
    val d = IeltsDrills.word(w, "k", false, others, Random(1))
    check("drill: mcq has 4 options with answer", d != null && d.options.size == 4 && d.options.contains(d.answer) && !d.typed)
    check("drill: typed has no options", IeltsDrills.word(w, "k", true, others, Random(1))?.typed == true)
    val dd = IeltsDef("x", "habitat", "natural home of an animal", "yashash muhiti")
    check("drill: typed def shows uz", IeltsDrills.def(dd, "k", true, listOf(dd), Random(1))?.prompt?.startsWith("yashash muhiti") == true)
}

fun ieltsSpeakingTests() {
    val dir = File(System.getenv("BUBBY_SRC") + "/../../../../assets/ielts")
    val defs = IeltsVocab.parseDefs(File(dir, "definitions.txt").readText())
    check("defs: 245 loaded", defs.size == 245, defs.size.toString())
    check("defs: every def has uz", defs.all { it.uz.isNotBlank() }, defs.filter { it.uz.isBlank() }.joinToString { it.word })
    val sp = IeltsVocab.parseSpeaking(File(dir, "speaking.txt").readText())
    val by = IeltsVocab.speakByTopic(sp.words)
    check("speak: 13 word topics", by.size == 13, by.keys.toString())
    check("speak: words loaded", sp.words.size >= 130, sp.words.size.toString())
    check("speak: topic words unique", by.all { (_, l) -> l.map { it.word }.distinct().size == l.size })
    check("speak: phrases loaded", sp.phrases.size >= 50 && sp.phrases.all { it.uz.isNotBlank() && it.example.isNotBlank() }, sp.phrases.size.toString())
    check("speak: part 1 questions", sp.part1.size >= 30 && sp.part1.all { it.topic in IeltsVocab.speakOrder }, sp.part1.filter { it.topic !in IeltsVocab.speakOrder }.joinToString { it.topic })
    check("speak: cue cards have 4 bullets", sp.cues.size >= 15 && sp.cues.all { it.bullets.size == 4 && it.topic in IeltsVocab.speakOrder })
    check("speak: part 3 questions", sp.part3.size >= 12 && sp.part3.all { it.topic in IeltsVocab.speakOrder })
    check("speak: story plan group", sp.phrases.count { it.group == "Part 2 hikoya" } >= 4)
    val pd = IeltsDrills.phrase(sp.phrases[0], "sp_0", sp.phrases, Random(2))
    check("speak: phrase drill mcq", pd != null && pd.options.size == 4 && pd.options.contains(pd.answer))
    val q = IeltsVocab.quiz(by.getValue("travel"), sp.words, Random(3), 10)
    check("speak: word quiz", q.size == 10 && q.all { it.options.distinct().size == 4 })
}
