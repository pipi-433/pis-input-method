/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.json.JSONObject

object PisWordFrequencyProbe {
    @JvmStatic fun run(context:Context):String=runBlocking {
        val name="pis-wordfreq-probe"
        var connection=FcitxDaemon.connect(name)
        val row=PisWordFrequency.Row("词频验证喵","ci'pin'yan'zheng'miao",0,0f,0)
        val report=JSONObject()
        var enabled=emptyArray<String>(); var current="pinyin"
        try {
            connection.runOnReady {
                enabled=enabledIme().map { it.uniqueName }.toTypedArray(); current=currentIme().uniqueName
                activate(1000,"io.pis.voiceprobe");setCapFlags(CapabilityFlags.DefaultFlags);focus(true)
                PisWordFrequency.change(this,context,"set",row,5,1)
                val stored=PisWordFrequency.rows(this).single { it.word==row.word }
                check(stored.count==5 && stored.priority==1) { stored.toString() }
                report.put("native_set",stored.toString())
                setEnabledIme(arrayOf("pinyin","shuangpin","keyboard-us"))
                for ((mode,keys) in listOf("pinyin" to "cipinyanzhengmiao","shuangpin" to PisWordFrequency.halfKeys(context,row.reading))) {
                    check(keys.isNotEmpty()); activateIme(mode);reset();keys.forEach { sendKey(it) }
                    check(getCandidates(0,12).first().text==row.word) { "$mode $keys pin did not change real candidates" }
                    report.put(mode+"_pin","first candidate via $keys PASS");reset()
                }
                PisWordFrequency.change(this,context,"set",row,7,0)
                check(PisWordFrequency.rows(this).single { it.word==row.word }.let { it.count==7 && it.priority==0 })
                report.put("adjust_unpin","actual 7 and priority 0 PASS")
                PisWordFrequency.change(this,context,"set",row,7,1)
                save();focus(false)
            }
            FcitxDaemon.restartFcitx()
            connection.runOnReady {
                check(PisWordFrequency.rows(this).single { it.word==row.word }.let { it.count==7 && it.priority==1 })
                report.put("persist","runtime reload restores counts and priority PASS")
                PisWordFrequency.change(this,context,"reset",row)
                check(PisWordFrequency.rows(this).single { it.word==row.word }.count==0)
                PisWordFrequency.change(this,context,"delete",row)
                check(PisWordFrequency.rows(this).none { it.word==row.word })
                report.put("reset_delete","native reset, delete, no leftover record PASS")
            }
            report.put("outcome","PASS").toString(2)
        } catch (error:Throwable) {
            throw IllegalStateException(report.toString()+"; "+error.message,error)
        } finally {
            connection.runOnReady { reset();PisWordFrequency.change(this,context,"delete",row);setEnabledIme(enabled);activateIme(current);save() }
            FcitxDaemon.disconnect(name)
        }
    }
}
