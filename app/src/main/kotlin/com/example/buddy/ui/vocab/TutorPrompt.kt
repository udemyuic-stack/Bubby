package com.example.buddy.ui.vocab

enum class TutorKind { PASSAGE, DIALOGUE }

class TutorTarget(val sense: Long, val entry: Long, val words: List<String>, val shown: String, val definition: String, val translations: List<String>)

object TutorPick {
    const val MIN = 3
    const val MAX = 6

    fun weak(items: List<Item>, cards: Map<Long, Card>, allowed: Set<Long>?, day: Long): List<TutorTarget> {
        val eligible = items.filter { item ->
            val card = cards[item.sense]
            card != null && card.source == "en" && (allowed == null || item.entry in allowed) &&
                item.progress.values.any { it.attempts > 0 } && item.progress.values.any { Planner.weak(it) }
        }
        fun tried(item: Item) = item.progress.values.filter { it.attempts > 0 }
        val ordered = eligible.sortedWith(
            compareByDescending<Item> { Planner.flagged(it) }
                .thenByDescending { it.progress.values.sumOf { p -> p.lapses } }
                .thenBy { tried(it).minOfOrNull { p -> Mastery.strength(p) } ?: 0 }
                .thenByDescending { tried(it).maxOfOrNull { p -> day - p.due } ?: 0L }
                .thenBy { it.sense }
        )
        val seen = HashSet<Long>()
        val out = ArrayList<TutorTarget>()
        for (item in ordered) {
            if (out.size >= MAX) break
            if (!seen.add(item.entry)) continue
            val c = cards[item.sense] ?: continue
            out.add(TutorTarget(item.sense, item.entry, c.words(), c.original, c.definition, c.translations))
        }
        return out
    }
}

object TutorPrompt {
    private fun langName(code: String) = Languages.byCode(code)?.en ?: code

    fun system(kind: TutorKind, targets: List<TutorTarget>, explainLang: String, topic: String?): String {
        val form = when (kind) {
            TutorKind.PASSAGE -> "a reading passage with a short title, in the style of IELTS Academic Reading"
            TutorKind.DIALOGUE -> "a dialogue between two speakers in the style of IELTS Speaking or Listening; every line starts with \"A: \" or \"B: \", and the title may be short or empty"
        }
        val theme = topic?.trim()?.takeIf { it.isNotEmpty() }?.let { "\nTheme of the text: $it." } ?: ""
        val list = targets.joinToString("\n") { t ->
            "- \"${t.shown}\"" + (if (t.definition.isNotBlank()) ": ${t.definition.trim()}" else "")
        }
        val kindKey = if (kind == TutorKind.PASSAGE) "passage" else "dialogue"
        return """You are an IELTS tutor for a B2-C1 English learner. Write $form, 120-220 words long.$theme
Use every target word below at least once, in the meaning given. Use the base form or a regular inflection only (-s, -es, -ed, -ing, -er, -est); never an irregular form.
Target words:
$list
Then write 3-5 questions, each about one target word:
- "gap": a NEW sentence (not copied from the text) with exactly one blank written as ____; the options are target words and the correct option fits the blank.
- "read": a question about the text whose answer depends on the meaning of the target word.
Each question has 3-4 distinct options, "answer" is the 0-based index of the correct option, "word" is the target word exactly as given above, "why" is one sentence in ${langName(explainLang)} explaining the answer.
Reply with ONE JSON object and nothing else:
{"kind":"$kindKey","title":"...","text":"...","questions":[{"type":"gap|read","word":"...","q":"...","options":["..."],"answer":0,"why":"..."}]}"""
    }

    fun user(targets: List<TutorTarget>): String = "Target words: " + targets.joinToString(", ") { it.shown }

    fun fix(reason: String, missing: List<String>): String {
        val text = when (reason) {
            "missing" -> "some target words are not used in the text"
            "questions" -> "fewer than 3 valid questions"
            else -> reason
        }
        val words = if (missing.isEmpty()) "" else "; missing words: " + missing.joinToString(", ")
        return "Your previous reply was rejected: $text$words. Reply again with valid JSON only."
    }
}
