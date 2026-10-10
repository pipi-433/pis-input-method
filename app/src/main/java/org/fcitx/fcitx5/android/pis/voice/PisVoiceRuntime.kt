/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis.voice

import android.content.Context

/** One prepared recognizer per IME process, including scheduled startup warmup. */
object PisVoiceRuntime {
    private var instance: PisVoiceEngine? = null
    @Synchronized fun engine(context: Context): PisVoiceEngine {
        return instance ?: PisVoiceEngine(context.applicationContext).also { instance = it }
    }
    fun prepare(context: Context) {
        if (!context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) return
        val model = engine(context)
        if (!model.ready) { VoicePipeline.prepare(context); model.prepare() }
    }
}
