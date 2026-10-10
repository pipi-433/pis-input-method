/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.json.JSONObject

/** Instrumentation-only checks against the actual Fcitx thread and serialized configuration. */
object PisMigrationProbe {
    @JvmStatic fun run(context: Context): String = runBlocking {
        val preferences = context.getSharedPreferences("pis_input",0)
        val previous = preferences.all.toMap()
        val name = "pis-migration-probe"
        val connection = FcitxDaemon.connect(name)
        val report = JSONObject()
        try {
            connection.runOnReady {
                activate(1000,"io.pis.voiceprobe"); setCapFlags(CapabilityFlags.DefaultFlags); focus(true)
                for ((mode,keys) in listOf("full" to "nihao","shoudao" to "nihd")) {
                    PisInputOptions.save(context,mode,mapOf("prediction" to false,"fuzzy_flat" to false,"fuzzy_nasal" to false,"fuzzy_southern" to false))
                    PisInputOptions.apply(this,context)
                    activateIme(if (mode == "full") "pinyin" else "shuangpin")
                    reset(); keys.forEach { sendKey(it) }
                    val choices = getCandidates(0,32).map { it.text }
                    check("你好" in choices) { "$mode $keys candidates $choices" }
                    report.put(mode,choices.take(8).joinToString(" / "))
                    if (mode == "shoudao") {
                        val profile = getAddonConfig("pinyin")["cfg"]["ShuangpinProfile"].value
                        check(profile == "Custom") { profile }
                        val snapshot = getAddonConfig("pinyin")["cfg"]["Fuzzy"]["AN_ANG"].value
                        check(snapshot == "False")
                        report.put("custom_profile","actual Custom; old nihd gives 你好")
                    }
                    reset()
                }
                PisInputOptions.save(context,"full",mapOf("fuzzy_flat" to true,"fuzzy_nasal" to true,"fuzzy_southern" to true,"prediction" to true))
                PisInputOptions.apply(this,context)
                val fuzzy = getAddonConfig("pinyin")["cfg"]["Fuzzy"]
                check(listOf("C_CH","AN_ANG","L_N").all { fuzzy[it].value == "True" })
                report.put("fuzzy","all three option groups apply to native pinyin PASS")
            }
            val send = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION }
            check(PisInputOptions.enterAction(context,send) == EditorInfo.IME_ACTION_SEND)
            send.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            check(PisInputOptions.enterAction(context,send) == null)
            send.imeOptions = EditorInfo.IME_ACTION_SEARCH; check(PisInputOptions.enterAction(context,send) == EditorInfo.IME_ACTION_SEARCH)
            send.actionId = 701; send.actionLabel = "自定义"; check(PisInputOptions.enterAction(context,send) == 701)
            report.put("enter_policy","multiline SEND; multiline newline; search; custom id PASS")
            send.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            check(!PisInputOptions.learningAllowed(context,send))
            report.put("learning_gate","NO_PERSONALIZED_LEARNING respected PASS")
            check(PisGlossary.lookup(context,"好的").hint == "good")
            check(PisGlossary.lookup(context,"好的").definition.contains("English definitions"))
            check(PisGlossary.lookup(context,"好得").hint == "well done")
            check(PisGlossary.lookup(context,"你好").hint == "hello")
            val english = PisGlossary.lookup(context,"hello")
            check(english.definition.contains("an expression of greeting") && english.definition.contains("喂"))
            check(PisEnglishDictionary.lookup(context,"went").contains("v."))
            check(PisGlossary.lookup(context,"pisnonexistentwordxyz").definition.isEmpty())
            report.put("dictionary","中文英译; English-Chinese; WordNet English definition; irregular went; absent word PASS")
            report.put("outcome","PASS").toString(2)
        } finally {
            preferences.edit().clear().apply { previous.forEach { (key,value) -> when(value) { is String -> putString(key,value); is Boolean -> putBoolean(key,value); is Int -> putInt(key,value) } } }.commit()
            connection.runOnReady { reset(); PisInputOptions.apply(this,context); focus(false); save() }
            FcitxDaemon.disconnect(name)
        }
    }
}
