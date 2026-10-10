/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.RawConfig

/** Management reads and changes the engine's own dictionary/history. No second count store. */
object PisWordFrequency {
    data class Row(val word: String,val reading: String,val count: Int,val weight: Float,val priority: Int)
    suspend fun rows(engine: FcitxAPI): List<Row> {
        val config = engine.getAddonSubConfig("pinyin","piswordfrequency")["cfg"]
        return config["Entries"].value.lineSequence().mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 5) null else Row(fields[0],fields[1],fields[2].toInt(),fields[3].toFloat(),fields[4].toInt())
        }.toList()
    }
    fun normalizeReading(reading: String): String {
        val value = reading.trim().lowercase().replace('ü','v').replace(Regex("\\s+"),"'")
        require(value.matches(Regex("[a-z]+('[a-z]+)*")) && value.length <= 128) { "请输入全拼音节，用空格分开，例如 ni hao" }
        return value
    }
    internal fun halfKeys(context: Context,reading: String): String {
        val pairs = context.assets.open("pis-input/shoudao.dat").bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith('#') }.mapNotNull { val f = it.split('='); if (f.size == 2) f[0] to f[1] else null }.toList()
        }
        val initials = pairs.filter { it.first in listOf("b","p","m","f","d","t","n","l","g","k","h","j","q","x","r","z","c","s","y","w","zh","ch","sh") && it.second.length == 1 }.toMap()
        val finals = pairs.filter { it.second.length == 1 }.toMap()
        val zeros = pairs.filter { it.second.length == 2 }.toMap()
        return reading.split('\'').joinToString("") { syllable ->
            zeros[syllable] ?: run {
                val initial = initials.keys.sortedByDescending { it.length }.firstOrNull { syllable.startsWith(it) }
                if (initial == null) "" else initials[initial].orEmpty()+finals[syllable.removePrefix(initial)].orEmpty()
            }
        }
    }
    suspend fun change(engine: FcitxAPI,context: Context,action: String,row: Row? = null,count: Int = 0,priority: Int = 0) {
        require(action in listOf("set","reset","delete","clear"))
        val fields = mutableListOf(RawConfig("Action",action))
        row?.let {
            require(it.word.isNotBlank() && it.word.length <= 32 && it.word.none { c -> c.isWhitespace() || c.isISOControl() }) { "词条最多32字，不能包含空格或控制字符" }
            val reading = normalizeReading(it.reading)
            fields += RawConfig("Word",it.word); fields += RawConfig("Reading",reading); fields += RawConfig("HalfKeys",halfKeys(context,reading))
            fields += RawConfig("Count",count.toString()); fields += RawConfig("Priority",priority.toString())
        }
        engine.reset(); engine.setAddonSubConfig("pinyin","piswordfrequency",RawConfig(fields.toTypedArray()))
        val status = engine.getAddonSubConfig("pinyin","piswordfrequency")["cfg"]["Error"].value
        require(status.isBlank()) { status }
        engine.save()
    }
}
