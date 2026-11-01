package com.example.buddy.ui.vocab

enum class Dimension(val core: Boolean) {
    RECOGNITION(true),
    MEANING_RECALL(true),
    PRODUCTION(true),
    CONTEXT(true),
    SENTENCE(false),
    LISTENING(false),
    LANGUAGE(false)
}

enum class Confidence { GUESS, SOME, SURE }

enum class Label { NEW, LEARNING, DEVELOPING, STRONG, WELL_RETAINED }

class Attempt(
    val day: Long,
    val type: String,
    val ok: Boolean,
    val confidence: Confidence = Confidence.SOME,
    val ms: Int = 0,
    val context: String = ""
)

class Progress(
    val step: Int = 0,
    val ease: Double = 2.0,
    val due: Long = 0,
    val lapses: Int = 0,
    val last: Long = -1,
    val attempts: Int = 0,
    val illusion: Boolean = false,
    val streak: Int = 0,
    val longTypes: Set<String> = emptySet(),
    val longContexts: Set<String> = emptySet()
) {
    fun copy(
        step: Int = this.step, ease: Double = this.ease, due: Long = this.due, lapses: Int = this.lapses, last: Long = this.last,
        attempts: Int = this.attempts, illusion: Boolean = this.illusion, streak: Int = this.streak,
        longTypes: Set<String> = this.longTypes, longContexts: Set<String> = this.longContexts
    ) = Progress(step, ease, due, lapses, last, attempts, illusion, streak, longTypes, longContexts)
}

object ExerciseTypes {
    val OF: Map<Dimension, List<String>> = mapOf(
        Dimension.RECOGNITION to listOf("choice_meaning", "choice_word"),
        Dimension.MEANING_RECALL to listOf("recall_meaning", "recall_definition"),
        Dimension.PRODUCTION to listOf("type_word"),
        Dimension.CONTEXT to listOf("cloze"),
        Dimension.SENTENCE to listOf("own_sentence"),
        Dimension.LISTENING to listOf("listen"),
        Dimension.LANGUAGE to listOf("lang_drill")
    )
    val NEEDS_AI = setOf("own_sentence")
    val RAMP = listOf(Dimension.RECOGNITION, Dimension.MEANING_RECALL, Dimension.PRODUCTION, Dimension.CONTEXT)
}
