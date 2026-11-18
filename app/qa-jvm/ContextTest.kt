package com.example.buddy

import com.example.buddy.ui.vocab.*

private const val SENSES = "\"senses\":[{\"pos\":\"noun\",\"definition\":\"a financial institution\",\"translations\":[\"moliya muassasasi\"],\"examples\":[]}]"

private fun reply(context: String) = "{\"language\":\"en\",\"confidence\":0.95,\"lemma\":\"bank\",$SENSES$context}"

private fun cs(def: String, tr: String) = SenseData("noun", def, "", "", "", listOf(tr), emptyList())

fun contextTests() {
    val sentence = "We sat on the \"bank\" of the river"
    val withCtx = EnrichPrompt.system("bank", "en", "uz", sentence)
    check("context prompt has sentence", withCtx.contains("We sat on the 'bank' of the river") && withCtx.contains("\"context\""))
    check("context prompt without sentence", !EnrichPrompt.system("bank", "en", "uz").contains("\"context\"") && !EnrichPrompt.system("bank", "en", "uz", "  ").contains("\"context\""))
    check("context prompt caps sentence", !EnrichPrompt.system("bank", "en", "uz", "a".repeat(400)).contains("a".repeat(301)))

    val good = ",\"context\":{\"translation\":\"Biz daryo qirg'og'ida o'tirdik\",\"sense\":{\"pos\":\"noun\",\"definition\":\"the land beside a river\",\"translations\":[\"qirg'oq\"],\"examples\":[{\"text\":\"We sat on the bank of the river\",\"translation\":\"\",\"reading\":\"\"}]}}"
    val ok = EnrichValidator.parse(reply(good), "uz", "bank", "en", hasContext = true)
    check("context parsed", ok is Parsed.Ok && ok.data.context?.sense?.translations == listOf("qirg'oq") && ok.data.context?.translation?.startsWith("Biz") == true && ok.data.context?.sense?.examples?.size == 1)
    val ignored = EnrichValidator.parse(reply(good), "uz", "bank", "en", hasContext = false)
    check("context ignored without sentence", ignored is Parsed.Ok && ignored.data.context == null)
    val bad = EnrichValidator.parse(reply(",\"context\":{\"translation\":\"x\",\"sense\":{\"pos\":\"noun\",\"definition\":\"\",\"translations\":[]}}"), "uz", "bank", "en", hasContext = true)
    check("bad context dropped, senses kept", bad is Parsed.Ok && bad.data.context == null && bad.data.senses.size == 1)
    val missing = EnrichValidator.parse(reply(""), "uz", "bank", "en", hasContext = true)
    check("missing context ok", missing is Parsed.Ok && missing.data.context == null)

    val river = cs("the land beside a river", "qirg'oq")
    check("plan context insert", EnrichMerge.planContext(null, river) === ContextPlan.Insert)
    check("plan context none", EnrichMerge.planContext(null, null) === ContextPlan.Keep)
    check("plan context protected keep", EnrichMerge.planContext(ExistingSense(5, river, protected = true, hasHistory = false), river) === ContextPlan.Keep)
    val u = EnrichMerge.planContext(ExistingSense(5, river, protected = false, hasHistory = true), river)
    check("plan context update keeps id", u is ContextPlan.Update && u.id == 5L)
    check("plan context history keep", EnrichMerge.planContext(ExistingSense(5, river, protected = false, hasHistory = true), null) === ContextPlan.Keep)
    val d = EnrichMerge.planContext(ExistingSense(5, river, protected = false, hasHistory = false), null)
    check("plan context delete", d is ContextPlan.Delete && d.id == 5L)
}
