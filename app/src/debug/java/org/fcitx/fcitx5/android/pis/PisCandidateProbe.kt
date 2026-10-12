/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.json.JSONObject

/** Native candidate and zero-initial shuangpin regression through the real API delegate. */
object PisCandidateProbe {
    @JvmStatic fun run(context: Context): String = runBlocking {
        val prefs = context.getSharedPreferences("pis_input", 0)
        val previous = prefs.all.toMap()
        val name = "pis-candidate-regression"
        val connection = FcitxDaemon.connect(name)
        val report = JSONObject()
        try {
            val translations = JSONObject()
            for ((word, expected) in listOf("技术" to "technology", "建设" to "to build", "基数" to "cardinal number", "恶化" to "to worsen")) {
                val result = PisGlossary.englishTranslation(context, word)
                check(result == expected) { "$word English mapping: $result" }
                translations.put(word, result)
            }
            check(PisGlossary.englishTranslation(context, "根本不存在的示例词条") == null)
            check(PisGlossary.englishTranslation(context, "technology") == null)
            report.put("english_mappings", translations)
            connection.runOnReady {
                activate(1001, "io.pis.voiceprobe")
                setCapFlags(CapabilityFlags.DefaultFlags)
                focus(true)
                candidateListCached // The crash was a missing delegate getter at this boundary.
                val codes = listOf("aa", "ai", "an", "ay", "ao", "ue", "ui", "en", "uf", "er", "oo", "ou", "bm", "gv", "nihd")
                for ((mode, keys) in listOf("full" to "shi") + codes.map { "shoudao" to it }) {
                    PisInputOptions.save(context, mode, mapOf("prediction" to false))
                    PisInputOptions.apply(this, context)
                    activateIme(if (mode == "full") "pinyin" else "shuangpin")
                    reset()
                    keys.forEach { sendKey(it) }
                    val cached = candidateListCached
                    val first = getCandidates(0, 64).map { it.text }
                    check(first.isNotEmpty()) { "$mode/$keys has no candidates" }
                    if (mode == "full") {
                        check(getCandidates(16, 32).isNotEmpty()) { "Candidate paging stopped at the seed list" }
                        check(candidateListCached === cached) { "Reading a page changed the native event snapshot" }
                    }
                    report.put("$mode/$keys", first.take(12).joinToString(" / "))
                    if (mode == "shoudao") {
                        check(getAddonConfig("pinyin")["cfg"]["Fuzzy"]["Correction"].value == "None")
                        if (keys == "ui") {
                            check(first.first() in listOf("诶", "欸")) { "UI is not ei: $first" }
                            check("一" !in first && "吃" !in first) { "UI contains unrelated syllables: $first" }
                        }
                        if (keys == "uf") check(first.first() == "鞥" && "成" !in first)
                        if (keys == "ue") check("也" !in first)
                        if (keys == "bm") check(first.any { it in listOf("被", "北", "背", "杯") })
                        if (keys == "gv") check(first.any { it in listOf("归", "贵", "鬼", "龟") })
                        if (keys == "nihd") check("你好" in first)
                    }
                    if (mode == "shoudao" && keys == "ui") {
                        val config = getAddonConfig("pinyin")["cfg"]
                        val previousCorrection = config["Fuzzy"]["Correction"].value
                        report.put("original_correction", previousCorrection)
                        try {
                            config.getOrCreate("Fuzzy").getOrCreate("Correction").value = "None"
                            setAddonConfig("pinyin", config)
                            reset()
                            keys.forEach { sendKey(it) }
                            val exact = getCandidates(0, 64).map { it.text }
                            report.put("shoudao/ui_no_correction", exact.take(12).joinToString(" / "))
                            check(exact.firstOrNull() in listOf("诶", "欸")) { "UI did not resolve to ei: $exact" }
                            check("一" !in exact && "吃" !in exact) { "Unrelated syllables mixed into UI: $exact" }
                        } finally {
                            config.getOrCreate("Fuzzy").getOrCreate("Correction").value = previousCorrection
                            setAddonConfig("pinyin", config)
                        }
                    }
                    reset()
                }
            }
            report.put("api_delegate", "candidate getter and native page PASS")
            report.toString()
        } finally {
            prefs.edit().clear().apply {
                previous.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                } }
            }.commit()
            connection.runOnReady { reset(); focus(false); PisInputOptions.apply(this, context) }
            FcitxDaemon.disconnect(name)
        }
    }
}
