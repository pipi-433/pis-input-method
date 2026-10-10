/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.core.FcitxAPI
import java.io.File

/** Settings are applied to the actual Fcitx engine, not just to a UI label. */
object PisInputOptions {
    private fun prefs(context: Context) = (if (context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) context else context.createDeviceProtectedStorageContext()).getSharedPreferences("pis_input", 0)
    fun bool(context: Context, key: String, fallback: Boolean = true) = prefs(context).getBoolean(key, fallback)
    fun scheme(context: Context) = prefs(context).getString("scheme", "full")!!
    fun save(context: Context, scheme: String, values: Map<String, Boolean>) {
        require(scheme in listOf("full", "shoudao"))
        prefs(context).edit().putString("scheme", scheme).remove("private").remove("matrix").apply { values.forEach { (key, value) -> if(key !in setOf("private","matrix")) putBoolean(key, value) } }.apply()
    }
    fun signature(context: Context) = listOf("embedded-pinyin-v2", "shoudao-exact-v1", scheme(context), bool(context,"prediction"), bool(context,"fuzzy_flat",false), bool(context,"fuzzy_nasal",false), bool(context,"fuzzy_southern",false)).joinToString("|")
    suspend fun apply(engine: FcitxAPI, context: Context) {
        val config = engine.getAddonConfig("pinyin")["cfg"]
        val selected = scheme(context)
        if (selected == "shoudao") {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            val profile = File(base,"config/pinyin/sp.dat")
            profile.parentFile!!.mkdirs()
            val bytes = context.assets.open("pis-input/shoudao.dat").use { it.readBytes() }
            if (!profile.exists() || !profile.readBytes().contentEquals(bytes)) profile.writeBytes(bytes)
            config.getOrCreate("ShuangpinProfile").value = "Custom"
        }
        config.getOrCreate("Prediction").value = if (bool(context,"prediction")) "True" else "False"
        config.getOrCreate("CloudPinyinEnabled").value = "False"
        // Fcitx persists the enum's configured display name, not its C++ identifier.
        config.getOrCreate("PreeditMode").value = "Composing pinyin"
        config.getOrCreate("PreeditCursorPositionAtBeginning").value = "False"
        val fuzzy = config.getOrCreate("Fuzzy")
        // Adjacent-key correction turns deliberate two-key codes such as UI into other syllables.
        fuzzy.getOrCreate("Correction").value = if (selected == "shoudao") "None" else "QWERTY"
        fun set(name: String, value: Boolean) { fuzzy.getOrCreate(name).value = if (value) "True" else "False" }
        listOf("C_CH","S_SH","Z_ZH").forEach { set(it,bool(context,"fuzzy_flat",false)) }
        listOf("AN_ANG","EN_ENG","IN_ING","IAN_IANG","UAN_UANG").forEach { set(it,bool(context,"fuzzy_nasal",false)) }
        listOf("L_N","F_H").forEach { set(it,bool(context,"fuzzy_southern",false)) }
        engine.setAddonConfig("pinyin",config)
        val chinese = if (selected == "full") "pinyin" else "shuangpin"
        val current = engine.currentIme().uniqueName
        engine.reset()
        engine.setEnabledIme(arrayOf(chinese,"keyboard-us"))
        if (current != "keyboard-us") engine.activateIme(chinese)
        engine.save()
    }
    fun learningAllowed(context: Context, info: EditorInfo?) = bool(context,"learning") && info != null && info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
    fun enterAction(context: Context, info: EditorInfo): Int? {
        if (!bool(context,"smart_enter") || info.inputType == 0) return null
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action == EditorInfo.IME_ACTION_SEND) return if (info.actionId != 0) info.actionId else action
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        if (info.actionId != 0) return info.actionId
        return action.takeIf { it in EditorInfo.IME_ACTION_GO..EditorInfo.IME_ACTION_PREVIOUS }
    }
}
