/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import kotlin.math.abs
import android.view.inputmethod.InputConnection

object PisCandidateEnglish {
    fun fromHint(hint: String): String? {
        val value = hint.trim()
        if (value.endsWith(':') || value.lowercase() in setOf("n.", "v.", "adj.", "adv.", "abbr.", "interj.", "pron.", "prep.", "conj.")) return null
        fun latin(c: Char) = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.LATIN
        return value.takeIf { text -> text.any(::latin) && text.all { latin(it) || it.isDigit() || it == ' ' || it in "'’.,!?:/&()-" } }
    }

    fun isUpward(dx: Float, dy: Float, threshold: Float) = dy <= -threshold && -dy > abs(dx) * 1.2f

    fun commitToEditor(editor: InputConnection, text: String, onAccepted: () -> Unit): Boolean {
        editor.beginBatchEdit()
        try {
            if (!editor.commitText(text, 1)) return false
            onAccepted()
            return true
        } finally {
            editor.endBatchEdit()
        }
    }
}
