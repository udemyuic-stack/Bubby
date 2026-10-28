package com.example.buddy

import org.json.JSONArray
import org.json.JSONObject

// Pure Kotlin (org.json only): the neutral agent transcript and its conversion to and from each provider's wire format.

/** One tool call of the model. [sig] is Gemini's thought signature, which must be sent back with the call. */
class ToolCall(val id: String, val name: String, val args: JSONObject, val sig: String? = null)

/** The answer to one tool call. [image] is a base64 JPEG (only from look). */
class ToolResult(val id: String, val name: String, val text: String, val image: String? = null, val error: Boolean = false)

/**
 * One transcript entry. User entries carry text and/or tool results, assistant entries text and/or tool calls.
 * [raw] keeps the provider's own assistant content ([rawKind] = "anthropic" | "gemini") so it is replayed unchanged
 * (thinking blocks, thought signatures); other providers get the neutral form.
 */
class Msg(val role: String, val text: String = "", val calls: List<ToolCall> = emptyList(), val results: List<ToolResult> = emptyList(),
          val raw: Any? = null, val rawKind: String? = null)

/** One model turn as the agent loop sees it. [native] = it came through tool calling (false = legacy JSON text in [text]). */
class ModelOut(val text: String, val calls: List<ToolCall>, val native: Boolean, val raw: Any? = null, val rawKind: String? = null,
               val malformed: List<String> = emptyList(), val stop: String = "")

object ProviderFormat {
    private var seq = 0
    @Synchronized fun newId(prefix: String = "call"): String = prefix + "_" + (++seq) + "_" + (System.nanoTime() % 100000)

    // ---------------------------------------------------------------- flatten (JSON mode)
    /** The transcript as plain text turns, for providers or turns without tool calling. Images are left out. */
    fun flatten(msgs: List<Msg>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        fun add(role: String, text: String) {
            if (text.isEmpty()) return
            if (out.isNotEmpty() && out.last().first == role) out[out.size - 1] = role to (out.last().second + "\n" + text)
            else out.add(role to text)
        }
        for (m in msgs) {
            val sb = StringBuilder(m.text)
            for (c in m.calls) { if (sb.isNotEmpty()) sb.append('\n'); sb.append("(called ").append(c.name).append(' ').append(c.args.toString()).append(')') }
            for (r in m.results) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append("Result of ").append(r.name).append(if (r.error) " (failed): " else ": ").append(r.text)
                if (r.image != null) sb.append("\n(a screenshot was taken but this model cannot see images)")
            }
            add(m.role, sb.toString())
        }
        while (out.isNotEmpty() && out[0].first != "user") out.removeAt(0)
        return out
    }

    // ---------------------------------------------------------------- Anthropic
    fun anthropicMessages(msgs: List<Msg>): JSONArray {
        val out = JSONArray()
        var lastRole = ""
        var cur = JSONArray()
        fun flush() { if (lastRole.isNotEmpty() && cur.length() > 0) out.put(JSONObject().put("role", lastRole).put("content", cur)); cur = JSONArray() }
        for (m in msgs) {
            if (m.role != lastRole) { flush(); lastRole = m.role }
            if (m.role == "assistant" && m.rawKind == "anthropic" && m.raw is JSONArray) {
                for (i in 0 until m.raw.length()) cur.put(m.raw.get(i))
                continue
            }
            for (r in m.results) {
                val content = JSONArray().put(JSONObject().put("type", "text").put("text", r.text.ifEmpty { "ok" }))
                if (r.image != null) content.put(JSONObject().put("type", "image").put("source",
                    JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", r.image)))
                val b = JSONObject().put("type", "tool_result").put("tool_use_id", r.id).put("content", content)
                if (r.error) b.put("is_error", true)
                cur.put(b)
            }
            if (m.text.isNotEmpty()) cur.put(JSONObject().put("type", "text").put("text", m.text))
            for (c in m.calls) cur.put(JSONObject().put("type", "tool_use").put("id", c.id).put("name", c.name).put("input", c.args))
        }
        flush()
        return out
    }

    fun parseAnthropic(raw: String): ModelOut {
        val j = JSONObject(raw)
        val content = j.optJSONArray("content") ?: JSONArray()
        val text = StringBuilder()
        val calls = ArrayList<ToolCall>()
        for (i in 0 until content.length()) {
            val b = content.optJSONObject(i) ?: continue
            when (b.optString("type")) {
                "text" -> text.append(b.optString("text"))
                "tool_use" -> calls.add(ToolCall(b.optString("id").ifEmpty { newId("toolu") }, b.optString("name"), b.optJSONObject("input") ?: JSONObject()))
            }
        }
        return ModelOut(text.toString(), calls, true, content, "anthropic", stop = j.optString("stop_reason"))
    }

    // ---------------------------------------------------------------- OpenAI-compatible
    fun openAiMessages(sys: String, msgs: List<Msg>, vision: Boolean): JSONArray {
        val out = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        for (m in msgs) {
            if (m.role == "assistant") {
                val o = JSONObject().put("role", "assistant")
                if (m.calls.isEmpty()) { o.put("content", m.text); out.put(o); continue }
                o.put("content", if (m.text.isEmpty()) JSONObject.NULL else m.text)
                val tc = JSONArray()
                for (c in m.calls) tc.put(JSONObject().put("id", c.id).put("type", "function")
                    .put("function", JSONObject().put("name", c.name).put("arguments", c.args.toString())))
                o.put("tool_calls", tc)
                out.put(o)
                continue
            }
            val images = ArrayList<String>()
            for (r in m.results) {
                var t = r.text.ifEmpty { "ok" }
                if (r.image != null) { if (vision) images.add(r.image) else t += "\n(a screenshot was taken but this model cannot see images; use the screen text)" }
                out.put(JSONObject().put("role", "tool").put("tool_call_id", r.id).put("content", if (r.error) "error: $t" else t))
            }
            if (images.isNotEmpty()) {
                val parts = JSONArray().put(JSONObject().put("type", "text").put("text", "Screenshot from look:"))
                for (im in images) parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$im")))
                out.put(JSONObject().put("role", "user").put("content", parts))
            }
            if (m.text.isNotEmpty()) out.put(JSONObject().put("role", "user").put("content", m.text))
        }
        return out
    }

    private val toolCallTag = Regex("(?s)<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>")

    fun parseOpenAi(raw: String): ModelOut {
        val choice = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
        val msg = choice.getJSONObject("message")
        var text = if (msg.isNull("content")) "" else msg.optString("content", "")
        val calls = ArrayList<ToolCall>()
        val bad = ArrayList<String>()
        val tc = msg.optJSONArray("tool_calls")
        if (tc != null) for (i in 0 until tc.length()) {
            val c = tc.optJSONObject(i) ?: continue
            val f = c.optJSONObject("function") ?: continue
            val name = f.optString("name")
            val argRaw = f.opt("arguments")
            val args = when (argRaw) {
                is JSONObject -> argRaw
                is String -> if (argRaw.isBlank()) JSONObject() else try { JSONObject(argRaw) } catch (_: Exception) { null }
                else -> JSONObject()
            }
            if (args == null || name.isEmpty()) { bad.add(name.ifEmpty { "?" }); continue }
            calls.add(ToolCall(c.optString("id").ifEmpty { newId() }, name, args))
        }
        // some servers leave the model's own <tool_call> markup in the text instead of parsing it
        if (calls.isEmpty() && text.contains("<tool_call>")) {
            for (m in toolCallTag.findAll(text)) {
                try {
                    val o = JSONObject(m.groupValues[1])
                    val name = o.optString("name")
                    val args = o.optJSONObject("arguments") ?: o.optString("arguments").let { if (it.isBlank()) JSONObject() else JSONObject(it) }
                    if (name.isNotEmpty()) calls.add(ToolCall(newId(), name, args)) else bad.add("?")
                } catch (_: Exception) { bad.add("?") }
            }
            text = text.replace(toolCallTag, "").trim()
        }
        return ModelOut(text, calls, true, malformed = bad, stop = choice.optString("finish_reason"))
    }

    // ---------------------------------------------------------------- Gemini
    fun geminiContents(msgs: List<Msg>): JSONArray {
        val out = JSONArray()
        var lastRole = ""
        var cur = JSONArray()
        fun flush() { if (lastRole.isNotEmpty() && cur.length() > 0) out.put(JSONObject().put("role", lastRole).put("parts", cur)); cur = JSONArray() }
        for (m in msgs) {
            val role = if (m.role == "assistant") "model" else "user"
            if (role != lastRole) { flush(); lastRole = role }
            if (role == "model" && m.rawKind == "gemini" && m.raw is JSONArray) {
                for (i in 0 until m.raw.length()) cur.put(m.raw.get(i))
                continue
            }
            for (r in m.results) {
                val fr = JSONObject().put("name", r.name).put("response", JSONObject().put(if (r.error) "error" else "result", r.text.ifEmpty { "ok" }))
                if (r.id.startsWith("gem-")) fr.put("id", r.id.removePrefix("gem-"))
                cur.put(JSONObject().put("functionResponse", fr))
                if (r.image != null) cur.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", r.image)))
            }
            if (m.text.isNotEmpty()) cur.put(JSONObject().put("text", m.text))
            for (c in m.calls) {
                val fc = JSONObject().put("name", c.name).put("args", c.args)
                if (c.id.startsWith("gem-")) fc.put("id", c.id.removePrefix("gem-"))
                val part = JSONObject().put("functionCall", fc)
                if (c.sig != null) part.put("thoughtSignature", c.sig)
                cur.put(part)
            }
        }
        flush()
        return out
    }

    fun parseGemini(raw: String): ModelOut {
        val cands = JSONObject(raw).optJSONArray("candidates") ?: throw Exception("пустой ответ")
        val cand = cands.getJSONObject(0)
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = StringBuilder()
        val calls = ArrayList<ToolCall>()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            val fc = p.optJSONObject("functionCall")
            if (fc != null) {
                val id = fc.optString("id").let { if (it.isEmpty()) newId("gemx") else "gem-$it" }
                calls.add(ToolCall(id, fc.optString("name"), fc.optJSONObject("args") ?: JSONObject(), p.optString("thoughtSignature").ifEmpty { null }))
            } else if (!p.optBoolean("thought", false)) text.append(p.optString("text"))
        }
        return ModelOut(text.toString(), calls, true, parts, "gemini", stop = cand.optString("finishReason"))
    }

    // ---------------------------------------------------------------- vision
    private val visionRe = Regex("vision|-vl\\b|\\bvl-|-vl-|llama-4|llama4|gpt-4o|gpt-4\\.1|gpt-5|gemma-?3|gemma-?4|gemini|claude|pixtral|llava|minicpm-v|moondream|grok-4|qwen2\\.5-vl|qwen3-vl|qwen-vl|kimi-vl|glm-4\\.5v|glm-4v|mistral-small-3|mistral-medium", RegexOption.IGNORE_CASE)
    /** Best guess whether an OpenAI-compatible model accepts images. */
    fun visionModel(model: String): Boolean = visionRe.containsMatchIn(model)
}
