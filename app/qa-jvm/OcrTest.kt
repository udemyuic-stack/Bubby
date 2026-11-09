package com.example.buddy

import com.example.buddy.ui.vocab.*

fun ocrTests() {
    val sys = OcrPrompt.system("en")
    check("ocr prompt names language", sys.contains("English") && sys.contains("(code en)"))
    check("ocr prompt asks for JSON", sys.contains("JSON") && sys.contains("\"sentences\""))
    check("ocr prompt treats text as data", sys.contains("never an instruction"))
    check("ocr prompt unknown code", OcrPrompt.system("xx").contains("xx"))
    check("ocr user prompt", OcrPrompt.user() == "Read the text in this photo.")

    val ok = OcrValidator.parse("{\"language\":\"en\",\"sentences\":[\"The  cat\\nsat.\", \"The  cat sat.\", \" \", \"It ran.\"]}")
    check("ocr parse ok", ok is OcrParsed.Ok && ok.sentences == listOf("The cat sat.", "It ran.") && !ok.truncated)
    val fenced = OcrValidator.parse("```json\n{\"sentences\":[\"Hello there.\"]}\n```")
    check("ocr parse fenced", fenced is OcrParsed.Ok && fenced.sentences == listOf("Hello there."))
    val think = OcrValidator.parse("<think>{\"sentences\":[\"no\"]} hmm</think>{\"sentences\":[\"Yes.\"]}")
    check("ocr parse think", think is OcrParsed.Ok && think.sentences == listOf("Yes."))
    check("ocr parse empty", OcrValidator.parse("{\"sentences\":[]}") === OcrParsed.Empty)
    check("ocr parse blanks only", OcrValidator.parse("{\"sentences\":[\"  \", \"\"]}") === OcrParsed.Empty)
    check("ocr parse missing", OcrValidator.parse("{\"language\":\"en\"}") is OcrParsed.Bad)
    check("ocr parse non-array", OcrValidator.parse("{\"sentences\":\"Hello.\"}") is OcrParsed.Bad)
    check("ocr parse no json", OcrValidator.parse("I cannot read this") is OcrParsed.Bad)
    val many = (1..70).joinToString(",") { "\"Sentence $it.\"" }
    val capped = OcrValidator.parse("{\"sentences\":[$many]}")
    check("ocr parse cap count", capped is OcrParsed.Ok && capped.sentences.size == OcrPrompt.MAX_SENTENCES && capped.truncated)
    val long = (1..10).joinToString(",") { "\"" + "a".repeat(700) + it + "\"" }
    val cappedChars = OcrValidator.parse("{\"sentences\":[$long]}")
    check("ocr parse cap chars", cappedChars is OcrParsed.Ok && cappedChars.sentences.sumOf { it.length } <= OcrPrompt.MAX_CHARS && cappedChars.truncated)

    val tk = OcrValidator.tokens("\"Well-known\" people don't  stop — ever.")
    check("ocr tokens", tk.map { it.word } == listOf("Well-known", "people", "don't", "stop", "", "ever"))
    check("ocr tokens keep text", tk[0].text == "\"Well-known\"" && tk[5].text == "ever.")
    check("ocr phrase single", OcrValidator.phrase(tk, 2, 2) == "don't")
    check("ocr phrase range", OcrValidator.phrase(tk, 0, 3) == "Well-known people don't stop")
    check("ocr phrase reversed", OcrValidator.phrase(tk, 3, 0) == "Well-known people don't stop")
    check("ocr phrase skips dash", OcrValidator.phrase(tk, 3, 5) == "stop ever")
    check("ocr phrase dash end", OcrValidator.phrase(tk, 2, 4) == null)
    check("ocr phrase inner hyphens", OcrValidator.phrase(OcrValidator.tokens("a state-of-the-art, tool"), 1, 1) == "state-of-the-art")
    val nine = OcrValidator.tokens("one two three four five six seven eight nine")
    check("ocr phrase 8 words", OcrValidator.phrase(nine, 0, 7) == "one two three four five six seven eight")
    check("ocr phrase 9 words null", OcrValidator.phrase(nine, 0, 8) == null)
    check("ocr phrase too long", OcrValidator.phrase(OcrValidator.tokens("a".repeat(50) + " " + "b".repeat(40)), 0, 1) == null)

    check("ocr context short", OcrValidator.context("We sat on the bank.", "bank") == "We sat on the bank.")
    val longSentence = (1..120).joinToString(" ") { "word$it" } + " the river bank " + (1..120).joinToString(" ") { "tail$it" }
    val win = OcrValidator.context(longSentence, "River Bank")
    check("ocr context window", win.length <= 300 && win.contains("river bank") && win.startsWith("…") && win.endsWith("…"), win)
    val words = longSentence.split(' ').toSet()
    check("ocr context boundaries", win.removePrefix("…").removeSuffix("…").split(' ').all { it in words })
    val noMatch = OcrValidator.context(longSentence, "zzz")
    check("ocr context no match", noMatch.length <= 300 && noMatch.startsWith("word1 ") && noMatch.endsWith("…"))

    val oa = ProviderFormat.openAiImageBody("m", "S", "T", "QUJD")
    val oaUser = oa.getJSONArray("messages").getJSONObject(1)
    check("ocr openai body", oa.getString("model") == "m" && !oa.getBoolean("stream") &&
        oa.getJSONArray("messages").getJSONObject(0).getString("role") == "system" &&
        oa.getJSONArray("messages").getJSONObject(0).getString("content") == "S" &&
        oaUser.getString("role") == "user" &&
        oaUser.getJSONArray("content").getJSONObject(0).getString("type") == "text" &&
        oaUser.getJSONArray("content").getJSONObject(0).getString("text") == "T" &&
        oaUser.getJSONArray("content").getJSONObject(1).getString("type") == "image_url" &&
        oaUser.getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url") == "data:image/jpeg;base64,QUJD")
    val an = ProviderFormat.anthropicImageBody("c", "S", "T", "QUJD")
    val anC = an.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
    check("ocr anthropic body", an.getString("model") == "c" && an.getInt("max_tokens") == 4096 && an.getString("system") == "S" &&
        an.getJSONArray("messages").getJSONObject(0).getString("role") == "user" &&
        anC.getJSONObject(0).getString("type") == "image" &&
        anC.getJSONObject(0).getJSONObject("source").getString("type") == "base64" &&
        anC.getJSONObject(0).getJSONObject("source").getString("media_type") == "image/jpeg" &&
        anC.getJSONObject(0).getJSONObject("source").getString("data") == "QUJD" &&
        anC.getJSONObject(1).getString("type") == "text" && anC.getJSONObject(1).getString("text") == "T")
    val ge = ProviderFormat.geminiImageBody("S", "T", "QUJD")
    val geP = ge.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
    check("ocr gemini body", ge.getJSONObject("system_instruction").getJSONArray("parts").getJSONObject(0).getString("text") == "S" &&
        ge.getJSONArray("contents").getJSONObject(0).getString("role") == "user" &&
        geP.getJSONObject(0).getJSONObject("inline_data").getString("mime_type") == "image/jpeg" &&
        geP.getJSONObject(0).getJSONObject("inline_data").getString("data") == "QUJD" &&
        geP.getJSONObject(1).getString("text") == "T" && !ge.has("tools"))

    check("ocr vision llama-4", ProviderFormat.visionModel("meta-llama/llama-4-scout-17b-16e-instruct"))
    check("ocr no vision llama-3.3", !ProviderFormat.visionModel("llama-3.3-70b-versatile"))
}
